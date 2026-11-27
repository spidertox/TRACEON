package com.traceon.data.backup

import androidx.room.withTransaction
import com.traceon.core.common.Clock
import com.traceon.core.common.PlatformCatalog
import com.traceon.data.imports.Csv
import com.traceon.data.local.TraceonDatabase
import com.traceon.data.local.entity.ActivitySessionEntity
import com.traceon.data.local.entity.ApplicationEntity
import com.traceon.data.repository.ensurePlatform
import com.traceon.domain.model.CollectionMethod
import com.traceon.domain.model.ContentDraft
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.DataStatus
import com.traceon.domain.repository.BackupRepository
import com.traceon.domain.repository.ContentRepository
import com.traceon.domain.repository.RestoreSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

class BackupRepositoryImpl(
    private val db: TraceonDatabase,
    private val contentRepo: ContentRepository,
    private val clock: Clock,
) : BackupRepository {
    private val content = db.contentDao()
    private val activity = db.activityDao()
    private val taxonomy = db.taxonomyDao()
    private val system = db.systemDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun isEncrypted(bytes: ByteArray): Boolean = BackupCrypto.isEncrypted(bytes)

    override suspend fun export(passphrase: String?): ByteArray = withContext(Dispatchers.IO) {
        val tags = taxonomy.allTagLinks().groupBy({ it.contentId }, { it.name })
        val topics = taxonomy.allTopicLinks().groupBy { it.contentId }
        val events = content.allEvents().groupBy { it.contentId }
        val items = ArrayList<BackupItem>()
        var offset = 0
        while (true) {
            val page = content.pageRows(500, offset)
            if (page.isEmpty()) break
            for (r in page) {
                val c = r.content
                if (c.isDemo) continue // demo samples never enter real backups
                items += BackupItem(
                    dedupKey = c.dedupKey, platformSlug = r.platformSlug, contentType = c.contentType.name, title = c.title,
                    description = c.description, visibleText = c.visibleText, url = c.url, creator = r.creatorName,
                    thumbnailUrl = c.thumbnailUrl, category = r.categoryName, categoryLocked = c.categoryLocked,
                    collectionMethod = c.collectionMethod.name, dataStatus = c.dataStatus.name, notes = c.userNotes,
                    tags = tags[c.id].orEmpty(),
                    topics = topics[c.id].orEmpty().map { BackupTopic(it.name, it.confidence, it.userSet) },
                    events = events[c.id].orEmpty().map { BackupEvent(it.kind, it.eventTime, it.durationMs, it.collectionMethod, it.sourceSlug) },
                )
            }
            offset += page.size
        }
        val sessions = activity.appSessionsForBackup().map { BackupAppSession(it.packageName, it.label, it.startTime, it.endTime, it.durationMs) }
        val plain = json.encodeToString(BackupFile(createdAt = clock.now(), items = items, appSessions = sessions)).toByteArray(Charsets.UTF_8)
        if (passphrase.isNullOrEmpty()) plain else BackupCrypto.encrypt(plain, passphrase)
    }

    override suspend fun restore(bytes: ByteArray, passphrase: String?): RestoreSummary = withContext(Dispatchers.IO) {
        val plain = if (BackupCrypto.isEncrypted(bytes)) {
            require(!passphrase.isNullOrEmpty()) { "This backup is encrypted. Enter its passphrase." }
            BackupCrypto.decrypt(bytes, passphrase)
        } else bytes
        val file = try {
            json.decodeFromString<BackupFile>(String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            throw IllegalArgumentException("This file is not a readable TRACEON backup.")
        }
        require(file.format == BackupFile.FORMAT) { "This file is not a TRACEON backup." }
        require(file.version <= BackupFile.CURRENT) { "This backup was made by a newer TRACEON version." }

        var newItems = 0
        for (item in file.items) {
            val events = item.events.sortedBy { it.time }
            if (events.isEmpty()) continue
            val status = runCatching { DataStatus.valueOf(item.dataStatus) }.getOrDefault(DataStatus.IMPORTED)
            val origin = if (status == DataStatus.MANUAL || status == DataStatus.OBSERVED || status == DataStatus.SAMPLE) status else DataStatus.IMPORTED
            var restoredId = -1L
            for ((index, ev) in events.withIndex()) {
                val draft = ContentDraft(
                    title = item.title, url = item.url,
                    // Only user-chosen types are pinned; everything else is re-derived the same way it was originally.
                    contentType = if (status == DataStatus.MANUAL) runCatching { ContentType.valueOf(item.contentType) }.getOrNull() else null,
                    platformSlug = item.platformSlug, description = item.description, visibleText = item.visibleText,
                    creator = item.creator, thumbnailUrl = item.thumbnailUrl, eventTime = ev.time, durationMs = ev.durationMs,
                    collectionMethod = runCatching { CollectionMethod.valueOf(ev.method) }.getOrDefault(CollectionMethod.MANUAL),
                    origin = origin, tags = item.tags, notes = item.notes,
                    browserSlug = if (ev.kind == "BROWSER") ev.source.ifEmpty { "other_browser" } else null,
                    searchQuery = if (ev.kind == "SEARCH") item.title else null,
                    engineSlug = if (ev.kind == "SEARCH") ev.source.ifEmpty { "google_search" } else null,
                    dedupKeyOverride = item.dedupKey,
                )
                val r = contentRepo.save(draft)
                if (index == 0) { restoredId = r.contentId; if (r.created) newItems++ }
            }
            if (item.categoryLocked && !item.category.isNullOrBlank() && restoredId > 0) contentRepo.setCategory(restoredId, item.category)
        }

        var sessions = 0
        db.withTransaction {
            for (s in file.appSessions) {
                val app = system.getApp(s.packageName) ?: run {
                    val platformId = PlatformCatalog.byPackage(s.packageName)?.let { ensurePlatform(taxonomy, it.slug) }
                    system.insertApp(ApplicationEntity(packageName = s.packageName, label = s.label, platformId = platformId, trackingEnabled = false))
                    system.getApp(s.packageName)
                } ?: continue
                val id = activity.insertSession(
                    ActivitySessionEntity(applicationId = app.id, startTime = s.start, endTime = s.end, durationMs = s.durationMs, collectionMethod = CollectionMethod.USAGE_STATS),
                )
                if (id != -1L) sessions++
            }
        }
        RestoreSummary(file.items.size, newItems, sessions)
    }

    override suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder("title,url,platform,type,category,first_seen,last_seen,events,data_status,collection_method,notes\n")
        var offset = 0
        while (true) {
            val page = content.pageRows(500, offset)
            if (page.isEmpty()) break
            for (r in page) {
                val c = r.content
                if (c.isDemo) continue
                sb.append(
                    listOf(
                        c.title, c.url.orEmpty(), r.platformName.orEmpty(), c.contentType.label, r.categoryName.orEmpty(),
                        Instant.ofEpochMilli(c.firstSeen).toString(), Instant.ofEpochMilli(c.lastSeen).toString(),
                        c.sessionCount.toString(), c.dataStatus.label, c.collectionMethod.label, c.userNotes,
                    ).joinToString(",") { Csv.escape(it) },
                ).append('\n')
            }
            offset += page.size
        }
        sb.toString()
    }
}
