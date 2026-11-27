package com.traceon.data.repository

import androidx.room.withTransaction
import com.traceon.core.common.Clock
import com.traceon.core.common.TimeUtils
import com.traceon.data.local.TraceonDatabase
import com.traceon.data.local.entity.ActivitySessionEntity
import com.traceon.data.local.entity.UserPreferenceEntity
import com.traceon.domain.model.CollectionMethod
import com.traceon.domain.repository.TrackingRepository
import com.traceon.domain.repository.UsageSyncResult
import com.traceon.feature.tracking.SessionBuilder
import com.traceon.feature.tracking.UsageEventSource

class TrackingRepositoryImpl(
    private val db: TraceonDatabase,
    private val source: UsageEventSource,
    private val clock: Clock,
) : TrackingRepository {
    private val system = db.systemDao()
    private val activity = db.activityDao()

    override suspend fun syncUsage(): UsageSyncResult {
        if (system.isSourceEnabled(CollectionMethod.USAGE_STATS) != true) return UsageSyncResult(0, 0)
        if (!source.hasPermission()) return UsageSyncResult(0, 0)
        val apps = system.trackedApps()
        if (apps.isEmpty()) return UsageSyncResult(0, 0)

        val now = clock.now()
        val last = system.getPref(KEY_LAST_SYNC)?.toLongOrNull() ?: 0L
        // Overlap with the previous window so a session that straddled the boundary is completed; the unique index dedupes.
        val from = if (last <= 0L) now - 7 * TimeUtils.DAY_MS else (last - OVERLAP_MS).coerceAtLeast(now - 10 * TimeUtils.DAY_MS)
        val sessions = SessionBuilder.build(source.query(from, now), apps.map { it.packageName }.toSet())
        val byPackage = apps.associateBy { it.packageName }
        var added = 0
        db.withTransaction {
            for (s in sessions) {
                val app = byPackage[s.packageName] ?: continue
                val id = activity.insertSession(
                    ActivitySessionEntity(
                        applicationId = app.id, startTime = s.start, endTime = s.end, durationMs = s.durationMs,
                        collectionMethod = CollectionMethod.USAGE_STATS,
                    ),
                )
                if (id != -1L) added++
            }
            system.setPref(UserPreferenceEntity(KEY_LAST_SYNC, now.toString()))
        }
        return UsageSyncResult(added, apps.size)
    }

    companion object {
        const val KEY_LAST_SYNC = "usage_last_sync"
        private const val OVERLAP_MS = 2 * 3_600_000L
    }
}
