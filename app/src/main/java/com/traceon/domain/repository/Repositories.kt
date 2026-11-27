package com.traceon.domain.repository

import com.traceon.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ContentRepository {
    suspend fun save(draft: ContentDraft): SaveResult
    suspend fun saveAll(drafts: List<ContentDraft>, onProgress: (Int) -> Unit = {}): List<SaveResult>
    /** User-confirmed merge: records the draft as another event of an existing item. */
    suspend fun mergeInto(existingId: Long, draft: ContentDraft): SaveResult

    fun observeItem(id: Long): Flow<ContentItem?>
    fun observeEvents(id: Long): Flow<List<ContentEvent>>
    fun observeTags(id: Long): Flow<List<String>>
    fun observeTopics(id: Long): Flow<List<TopicScore>>
    fun observeRelated(id: Long): Flow<List<RelatedItem>>
    fun observeTimeline(filter: TimelineFilter, limit: Int): Flow<List<TimelineEntry>>
    fun observeRecent(limit: Int): Flow<List<ContentItem>>
    fun observeCategories(): Flow<List<String>>
    fun observeAllTags(): Flow<List<String>>
    fun observeTopicsOverview(): Flow<List<LabeledValue>>

    suspend fun search(query: String, filters: SearchFilters, limit: Int = 100): List<ContentItem>
    suspend fun suggestions(prefix: String, limit: Int = 8): List<String>
    suspend fun findSimilarTitles(title: String, limit: Int = 3): List<ContentItem>
    suspend fun itemsForTopic(topic: String, limit: Int = 100): List<ContentItem>
    suspend fun topicCounts(from: Long?, to: Long?, limit: Int): List<LabeledValue>
    suspend fun relatedTopics(topic: String, limit: Int): List<LabeledValue>
    suspend fun filterOptions(): FilterOptions

    suspend fun updateDetails(id: Long, title: String, description: String, notes: String)
    suspend fun setCategory(id: Long, category: String?)
    suspend fun createCategory(name: String)
    suspend fun addTag(id: Long, tag: String)
    suspend fun removeTag(id: Long, tag: String)
    suspend fun delete(id: Long)
    suspend fun linkItems(a: Long, b: Long)
    suspend fun unlinkItems(a: Long, b: Long)
    suspend fun rebuildRelations(ids: List<Long>)
    suspend fun reclassifyAll(): Int
    suspend fun setBrowserDuration(contentId: Long, visitTime: Long, totalMs: Long)

    fun observeGraphItems(filter: GraphFilter, limit: Int): Flow<List<GraphItem>>
    fun observeRelationEdges(): Flow<List<RelationEdge>>
}

interface AnalyticsRepository {
    fun totalItems(): Flow<Int>
    fun itemsByType(): Flow<List<LabeledValue>>
    fun eventsByType(): Flow<List<LabeledValue>>
    fun viewingMs(): Flow<Long?>
    fun readingMs(): Flow<Long?>
    fun topApps(sinceMs: Long, limit: Int): Flow<List<LabeledValue>>
    fun topDomains(limit: Int): Flow<List<LabeledValue>>
    fun topCategories(limit: Int): Flow<List<LabeledValue>>
    fun topTopics(limit: Int): Flow<List<LabeledValue>>
    fun platformItems(): Flow<List<LabeledValue>>
    fun platformEvents(): Flow<List<LabeledValue>>
    fun browserActivity(): Flow<List<LabeledValue>>
    fun engineActivity(): Flow<List<LabeledValue>>
    fun searchCount(): Flow<Int>
    fun distinctPages(): Flow<Int>
    fun distinctDomains(): Flow<Int>
    fun dailyActivity(days: Int): Flow<List<DayCount>>
    fun dailyBrowsing(days: Int): Flow<List<DayCount>>
    fun dailySearches(days: Int): Flow<List<DayCount>>
    fun repeatedPages(limit: Int): Flow<List<LabeledValue>>
    fun demoCount(): Flow<Int>
}

interface SettingsRepository {
    fun themeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
    fun sourceStates(): Flow<Map<CollectionMethod, Boolean>>
    suspend fun setSourceEnabled(method: CollectionMethod, enabled: Boolean)
    fun trackedApps(installed: (String) -> Boolean): Flow<List<TrackedApp>>
    suspend fun setAppTracking(packageName: String, label: String, platformSlug: String?, enabled: Boolean)
    suspend fun recordPermission(key: String, granted: Boolean)
    fun permissionStates(): Flow<Map<String, Boolean>>
    suspend fun getPref(key: String): String?
    suspend fun setPref(key: String, value: String)
    fun recentSearches(limit: Int): Flow<List<String>>
    fun savedSearches(): Flow<List<String>>
    suspend fun rememberSearch(query: String)
    suspend fun setSearchSaved(query: String, saved: Boolean)
    suspend fun clearRecentSearches()
    suspend fun deleteDemoData(): Int
    suspend fun deleteAllData()
}

interface ImportRepository {
    suspend fun importFile(source: ImportSource, fileName: String, text: String, browserSlug: String, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ImportSummary
    fun observeJobs(): Flow<List<ImportJob>>
}

data class UsageSyncResult(val sessionsAdded: Int, val appsConsidered: Int)

interface TrackingRepository {
    /** Reads OS usage events for apps the user enabled and stores sessions. No-op without permission or consent. */
    suspend fun syncUsage(): UsageSyncResult
}

data class RestoreSummary(val items: Int, val newItems: Int, val appSessions: Int)

interface BackupRepository {
    suspend fun export(passphrase: String?): ByteArray
    suspend fun restore(bytes: ByteArray, passphrase: String?): RestoreSummary
    suspend fun exportCsv(): String
    fun isEncrypted(bytes: ByteArray): Boolean
}
