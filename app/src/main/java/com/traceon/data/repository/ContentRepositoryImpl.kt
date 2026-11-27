package com.traceon.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.traceon.core.common.Clock
import com.traceon.core.common.ContentNormalizer
import com.traceon.core.common.NormalizedContent
import com.traceon.core.common.PlatformCatalog
import com.traceon.core.common.TextUtils
import com.traceon.data.local.SearchQueryBuilder
import com.traceon.data.local.TraceonDatabase
import com.traceon.data.local.entity.ActivitySessionEntity
import com.traceon.data.local.entity.BrowserActivityEntity
import com.traceon.data.local.entity.CategoryEntity
import com.traceon.data.local.entity.ContentEntity
import com.traceon.data.local.entity.ContentRelationEntity
import com.traceon.data.local.entity.ContentTagCrossRef
import com.traceon.data.local.entity.ContentTopicCrossRef
import com.traceon.data.local.entity.CreatorEntity
import com.traceon.data.local.entity.PlatformEntity
import com.traceon.data.local.entity.SearchActivityEntity
import com.traceon.data.local.entity.TagEntity
import com.traceon.data.local.entity.TopicEntity
import com.traceon.data.mapper.toDomain
import com.traceon.domain.classification.Candidate
import com.traceon.domain.classification.ClassificationInput
import com.traceon.domain.classification.ContentClassifier
import com.traceon.domain.classification.SimilarityEngine
import com.traceon.domain.classification.Taxonomy
import com.traceon.domain.model.*
import com.traceon.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ContentRepositoryImpl(
    private val db: TraceonDatabase,
    private val classifier: ContentClassifier,
    private val clock: Clock,
) : ContentRepository {
    private val content = db.contentDao()
    private val activity = db.activityDao()
    private val taxonomy = db.taxonomyDao()

    // ---------------------------------------------------------------- write path

    override suspend fun save(draft: ContentDraft): SaveResult = db.withTransaction { saveInternal(draft) }

    override suspend fun saveAll(drafts: List<ContentDraft>, onProgress: (Int) -> Unit): List<SaveResult> {
        val out = ArrayList<SaveResult>(drafts.size)
        drafts.chunked(200).forEach { chunk ->
            db.withTransaction { chunk.forEach { out += saveInternal(it) } }
            onProgress(out.size)
        }
        return out
    }

    override suspend fun mergeInto(existingId: Long, draft: ContentDraft): SaveResult = db.withTransaction {
        val existing = content.get(existingId)
        saveInternal(if (existing == null) draft else draft.copy(dedupKeyOverride = existing.dedupKey))
    }

    private suspend fun saveInternal(d: ContentDraft): SaveResult {
        val n = ContentNormalizer.normalize(d)
        val now = clock.now()
        val platformId = platformId(n.platformSlug)
        val existing = content.findByDedupKey(n.dedupKey)
        val created = existing == null
        val creatorId = d.creator?.trim()?.takeIf { it.isNotEmpty() }?.let { creatorId(it, platformId) }
        val id: Long
        if (existing == null) {
            id = content.insert(newEntity(d, n, platformId, creatorId, now))
        } else {
            id = existing.id
            content.fillBlanks(id, d.description, d.thumbnailUrl, creatorId, now)
            if (d.notes.isNotBlank()) content.fillNotes(id, d.notes, now)
        }
        val inserted = insertEvent(id, d, n)
        if (inserted) {
            if (n.contentType.isReading) content.registerReadEvent(id, d.eventTime, d.durationMs, now)
            else content.registerViewEvent(id, d.eventTime, d.durationMs, now)
        }
        d.tags.forEach { addTagInternal(id, it) }
        if (created || (existing != null && existing.categoryId == null && !existing.categoryLocked)) classifyInternal(id)
        return SaveResult(id, created, !inserted)
    }

    private fun newEntity(d: ContentDraft, n: NormalizedContent, platformId: Long, creatorId: Long?, now: Long) = ContentEntity(
        dedupKey = n.dedupKey,
        platformId = platformId,
        contentType = n.contentType,
        title = (d.searchQuery ?: d.title).trim().ifEmpty { d.url.orEmpty() },
        description = d.description,
        visibleText = d.visibleText.take(20_000),
        url = d.url?.trim(),
        canonicalUrl = n.canonicalUrl,
        platformContentId = n.platformContentId,
        domain = n.domain,
        creatorId = creatorId,
        thumbnailUrl = d.thumbnailUrl,
        firstSeen = d.eventTime,
        lastSeen = d.eventTime,
        collectionMethod = d.collectionMethod,
        metadataConfidence = n.metadataConfidence,
        dataStatus = n.status,
        userNotes = d.notes,
        isDemo = d.isDemo,
        createdAt = now,
        updatedAt = now,
    )

    /** Returns false when the same event already exists (unique index), so re-imports never double count. */
    private suspend fun insertEvent(contentId: Long, d: ContentDraft, n: NormalizedContent): Boolean {
        val query = d.searchQuery
        return when {
            query != null -> activity.insertSearch(
                SearchActivityEntity(
                    contentId = contentId, queryText = query.trim(), engineSlug = n.platformSlug, browserSlug = d.browserSlug,
                    searchUrl = d.url, searchTime = d.eventTime, collectionMethod = d.collectionMethod,
                    importJobId = d.importJobId, isDemo = d.isDemo,
                ),
            ) != -1L
            d.browserSlug != null -> activity.insertBrowser(
                BrowserActivityEntity(
                    contentId = contentId, browserSlug = d.browserSlug, url = d.url.orEmpty(), domain = n.domain,
                    title = d.title, visitTime = d.eventTime, durationMs = d.durationMs, collectionMethod = d.collectionMethod,
                    importJobId = d.importJobId, isDemo = d.isDemo,
                ),
            ) != -1L
            else -> activity.insertSession(
                ActivitySessionEntity(
                    contentId = contentId, startTime = d.eventTime, endTime = d.durationMs?.let { d.eventTime + it },
                    durationMs = d.durationMs, collectionMethod = d.collectionMethod, isDemo = d.isDemo,
                ),
            ) != -1L
        }
    }

    // ---------------------------------------------------------------- taxonomy helpers

    private suspend fun platformId(slug: String): Long = ensurePlatform(taxonomy, slug)

    private suspend fun categoryId(name: String, group: String?): Long {
        taxonomy.categoryId(name)?.let { return it }
        taxonomy.insertCategory(CategoryEntity(name = name, groupName = group, isCustom = group == null))
        return checkNotNull(taxonomy.categoryId(name))
    }

    private suspend fun topicId(name: String, categoryId: Long?): Long {
        taxonomy.topicId(name)?.let { return it }
        taxonomy.insertTopic(TopicEntity(name = name, categoryId = categoryId))
        return checkNotNull(taxonomy.topicId(name))
    }

    private suspend fun creatorId(name: String, platformId: Long?): Long {
        taxonomy.findCreator(name, platformId)?.let { return it }
        taxonomy.insertCreator(CreatorEntity(name = name, platformId = platformId))
        return checkNotNull(taxonomy.findCreator(name, platformId))
    }

    private suspend fun addTagInternal(contentId: Long, raw: String) {
        val name = raw.trim().lowercase().take(40)
        if (name.isEmpty()) return
        val tagId = taxonomy.tagId(name) ?: run {
            taxonomy.insertTag(TagEntity(name = name))
            checkNotNull(taxonomy.tagId(name))
        }
        taxonomy.insertContentTag(ContentTagCrossRef(contentId, tagId))
    }

    // ---------------------------------------------------------------- classification

    private suspend fun classifyInternal(id: Long) {
        val row = content.getRow(id) ?: return
        val c = row.content
        if (c.categoryLocked) return
        val r = classifier.classify(
            ClassificationInput(c.title, c.description, c.visibleText, taxonomy.tagNames(id), c.domain, row.creatorName),
        )
        val catId = r.category?.let { categoryId(it, r.group) }
        content.setAutoClassification(id, catId, r.confidence, r.keywords.joinToString(","), clock.now())
        taxonomy.clearAutoTopics(id)
        for (t in r.topics) {
            val tid = topicId(t.name, categoryId(t.name, t.group))
            taxonomy.insertContentTopic(ContentTopicCrossRef(id, tid, t.confidence, false))
        }
    }

    override suspend fun reclassifyAll(): Int {
        var count = 0
        var offset = 0
        while (true) {
            val page = content.unlockedPage(200, offset)
            if (page.isEmpty()) break
            db.withTransaction { page.forEach { classifyInternal(it.id); count++ } }
            offset += page.size
        }
        return count
    }

    override suspend fun setCategory(id: Long, category: String?) = db.withTransaction {
        val now = clock.now()
        if (category.isNullOrBlank()) {
            content.setUserCategory(id, null, 0f, false, now)
            classifyInternal(id)
        } else {
            val name = category.trim()
            val group = Taxonomy.groupOf(name)
            val catId = categoryId(name, group)
            content.setUserCategory(id, catId, 1f, true, now)
            taxonomy.clearTopics(id)
            taxonomy.insertContentTopic(ContentTopicCrossRef(id, topicId(name, catId), 1f, true))
        }
    }

    override suspend fun createCategory(name: String) {
        val n = name.trim().take(40)
        if (n.isNotEmpty()) categoryId(n, Taxonomy.groupOf(n))
    }

    // ---------------------------------------------------------------- relations

    override suspend fun rebuildRelations(ids: List<Long>) {
        ids.distinct().chunked(100).forEach { chunk -> db.withTransaction { chunk.forEach { rebuildRelationsFor(it) } } }
    }

    private suspend fun rebuildRelationsFor(id: Long) {
        val me = content.get(id) ?: return
        content.clearAutoRelations(id)
        val pool = LinkedHashMap<Long, com.traceon.data.local.dao.CandidateRow>()
        me.categoryId?.let { c -> content.candidatesByCategory(c, id, 300).forEach { pool[it.id] = it } }
        me.creatorId?.let { c -> content.candidatesByCreator(c, id, 100).forEach { pool[it.id] = it } }
        fun cand(i: Long, title: String, kw: String, creator: Long?, platform: Long?, domain: String?) =
            Candidate(i, title, kw.split(",").filter { it.isNotBlank() }.toSet(), creator, platform, domain)
        val self = cand(id, me.title, me.keywords, me.creatorId, me.platformId, me.domain)
        val others = pool.values.map { cand(it.id, it.title, it.keywords, it.creatorId, it.platformId, it.domain) }
        val now = clock.now()
        for (p in SimilarityEngine.propose(self, others)) {
            content.insertRelation(
                ContentRelationEntity(
                    fromContentId = minOf(id, p.otherId), toContentId = maxOf(id, p.otherId),
                    type = p.type, score = p.score, createdAt = now,
                ),
            )
        }
    }

    override suspend fun linkItems(a: Long, b: Long) {
        if (a == b) return
        content.insertRelation(
            ContentRelationEntity(fromContentId = minOf(a, b), toContentId = maxOf(a, b), type = RelationType.USER, score = 1f, isUserCreated = true, createdAt = clock.now()),
        )
    }

    override suspend fun unlinkItems(a: Long, b: Long) = content.deleteUserRelation(a, b)

    // ---------------------------------------------------------------- edits

    override suspend fun updateDetails(id: Long, title: String, description: String, notes: String) {
        content.updateDetails(id, title.trim().ifEmpty { "Untitled" }, description.trim(), notes.trim(), clock.now())
    }

    override suspend fun addTag(id: Long, tag: String) = db.withTransaction { addTagInternal(id, tag) }

    override suspend fun removeTag(id: Long, tag: String) {
        val tagId = taxonomy.tagId(tag.trim().lowercase()) ?: return
        taxonomy.deleteContentTag(id, tagId)
    }

    override suspend fun delete(id: Long) = content.delete(id)

    override suspend fun setBrowserDuration(contentId: Long, visitTime: Long, totalMs: Long) = db.withTransaction {
        val old = activity.browserDuration(contentId, visitTime) ?: 0L
        val delta = totalMs - old
        if (delta > 0) {
            activity.setBrowserDuration(contentId, visitTime, totalMs)
            val type = content.get(contentId)?.contentType
            if (type != null && type.isReading) content.addReadingDuration(contentId, delta, clock.now())
            else content.addViewingDuration(contentId, delta, clock.now())
        }
    }

    // ---------------------------------------------------------------- reads

    override fun observeItem(id: Long): Flow<ContentItem?> = content.observeRow(id).map { it?.toDomain() }

    override fun observeEvents(id: Long): Flow<List<ContentEvent>> = activity.observeEvents(id).map { rows ->
        rows.map { ContentEvent(it.time, it.durationMs, it.method, it.kind, it.source?.takeIf { s -> s.isNotEmpty() }) }
    }

    override fun observeTags(id: Long): Flow<List<String>> = taxonomy.observeTags(id)

    override fun observeTopics(id: Long): Flow<List<TopicScore>> =
        taxonomy.observeTopics(id).map { rows -> rows.map { TopicScore(it.name, it.confidence, it.userSet) } }

    override fun observeRelated(id: Long): Flow<List<RelatedItem>> = content.observeRelationsOf(id).map { rels ->
        val otherIds = rels.map { if (it.fromContentId == id) it.toContentId else it.fromContentId }
        val rows = if (otherIds.isEmpty()) emptyMap() else content.getRows(otherIds).associateBy { it.content.id }
        rels.mapNotNull { r ->
            val other = rows[if (r.fromContentId == id) r.toContentId else r.fromContentId] ?: return@mapNotNull null
            RelatedItem(other.toDomain(), r.type, r.score, r.isUserCreated)
        }
    }

    override fun observeTimeline(filter: TimelineFilter, limit: Int): Flow<List<TimelineEntry>> =
        content.observeTimeline(filter.from, filter.to, filter.platform, filter.kind, limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<ContentItem>> = content.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }
    override fun observeCategories(): Flow<List<String>> = taxonomy.observeCategoryNames()
    override fun observeAllTags(): Flow<List<String>> = taxonomy.observeAllTags()
    override fun observeTopicsOverview(): Flow<List<LabeledValue>> = content.observeTopicsOverview()

    override suspend fun search(query: String, filters: SearchFilters, limit: Int): List<ContentItem> {
        val q = SearchQueryBuilder.build(query, filters, limit)
        return content.searchRows(SimpleSQLiteQuery(q.sql, q.args.toTypedArray())).map { it.toDomain() }
    }

    override suspend fun suggestions(prefix: String, limit: Int): List<String> {
        val p = TextUtils.likeEscape(prefix.trim()) + "%"
        if (prefix.isBlank()) return emptyList()
        val out = LinkedHashSet<String>()
        out += taxonomy.topicSuggestions(p, limit)
        out += taxonomy.tagSuggestions(p, limit)
        out += content.creatorSuggestions(p, limit)
        out += content.domainSuggestions(p, limit)
        out += content.titleSuggestions(p, limit)
        return out.take(limit)
    }

    override suspend fun findSimilarTitles(title: String, limit: Int): List<ContentItem> {
        val want = TextUtils.tokens(title).toSet()
        if (want.size < 2) return emptyList()
        val q = SearchQueryBuilder.build(want.first(), SearchFilters(sort = SearchSort.NEWEST), 200)
        return content.searchRows(SimpleSQLiteQuery(q.sql, q.args.toTypedArray()))
            .map { it.toDomain() }
            .filter { TextUtils.jaccard(want, TextUtils.tokens(it.title).toSet()) >= 0.8 }
            .take(limit)
    }

    override suspend fun itemsForTopic(topic: String, limit: Int): List<ContentItem> =
        content.itemsForTopic(topic, limit).map { it.toDomain() }

    override suspend fun topicCounts(from: Long?, to: Long?, limit: Int): List<LabeledValue> =
        content.topicCounts(from ?: 0L, to ?: Long.MAX_VALUE, limit)

    override suspend fun relatedTopics(topic: String, limit: Int): List<LabeledValue> = content.relatedTopics(topic, limit)

    override suspend fun filterOptions(): FilterOptions = FilterOptions(
        platforms = content.platformOptions().map { it.slug to it.name },
        categories = content.categoryOptions(),
        creators = content.creatorOptions(),
        types = content.typeOptions().sortedBy { it.ordinal },
    )

    override fun observeGraphItems(filter: GraphFilter, limit: Int): Flow<List<GraphItem>> = content.observeGraphItems(
        filter.from ?: 0L, filter.to ?: Long.MAX_VALUE, filter.platform, filter.type?.name, filter.category, filter.creator, limit,
    )

    override fun observeRelationEdges(): Flow<List<RelationEdge>> = content.observeRelationEdges(50_000)
}
