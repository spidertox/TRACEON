package com.traceon.domain.model

/** Input to the single write path. Everything a collector knows; nothing is invented. */
data class ContentDraft(
    val title: String,
    val url: String? = null,
    val contentType: ContentType? = null,
    val platformSlug: String? = null,
    val description: String = "",
    val visibleText: String = "",
    val creator: String? = null,
    val thumbnailUrl: String? = null,
    val eventTime: Long,
    val durationMs: Long? = null,
    val collectionMethod: CollectionMethod,
    /** OBSERVED, IMPORTED, MANUAL or SAMPLE: where the record came from. */
    val origin: DataStatus,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val browserSlug: String? = null,
    val searchQuery: String? = null,
    val engineSlug: String? = null,
    val importJobId: Long? = null,
    val isDemo: Boolean = false,
    val dedupKeyOverride: String? = null,
)

data class SaveResult(val contentId: Long, val created: Boolean, val duplicateEvent: Boolean)

data class ContentItem(
    val id: Long,
    val title: String,
    val description: String,
    val visibleText: String,
    val url: String?,
    val canonicalUrl: String?,
    val domain: String?,
    val platformSlug: String?,
    val platformName: String?,
    val contentType: ContentType,
    val creator: String?,
    val thumbnailUrl: String?,
    val category: String?,
    val categoryConfidence: Float,
    val categoryLocked: Boolean,
    val keywords: List<String>,
    val firstSeen: Long,
    val lastSeen: Long,
    val sessionCount: Int,
    val viewingDurationMs: Long?,
    val readingDurationMs: Long?,
    val collectionMethod: CollectionMethod,
    val metadataConfidence: Float,
    val dataStatus: DataStatus,
    val notes: String,
    val isDemo: Boolean,
)

data class TimelineEntry(
    val item: ContentItem,
    val eventTime: Long,
    val durationMs: Long?,
    val method: CollectionMethod,
    val source: String?,
)

data class ContentEvent(val time: Long, val durationMs: Long?, val method: CollectionMethod, val kind: String, val source: String?)
data class TopicScore(val name: String, val confidence: Float, val userSet: Boolean)
data class RelatedItem(val item: ContentItem, val type: RelationType, val score: Float, val userCreated: Boolean)
data class LabeledValue(val label: String, val value: Long)
data class DayCount(val day: Long, val value: Long)

data class ImportJob(
    val id: Long, val source: ImportSource, val fileName: String, val startedAt: Long, val finishedAt: Long?,
    val status: ImportStatus, val total: Int, val imported: Int, val duplicates: Int, val skipped: Int, val message: String?,
)

data class ImportSummary(val jobId: Long, val total: Int, val imported: Int, val duplicates: Int, val skipped: Int, val message: String?)

data class SearchFilters(
    val platform: String? = null,
    val types: Set<ContentType> = emptySet(),
    val category: String? = null,
    val topic: String? = null,
    val domain: String? = null,
    val creator: String? = null,
    val tag: String? = null,
    /** Event-time window: items with at least one recorded event inside it. */
    val from: Long? = null,
    val to: Long? = null,
    val sort: SearchSort = SearchSort.RELEVANCE,
)

data class TimelineFilter(val from: Long, val to: Long, val platform: String? = null, val kind: String? = null)

data class GraphFilter(
    val platform: String? = null, val type: ContentType? = null, val category: String? = null,
    val creator: String? = null, val from: Long? = null, val to: Long? = null,
)

data class GraphItem(
    val id: Long, val title: String, val platformSlug: String?, val contentType: ContentType,
    val category: String?, val creator: String?, val sessionCount: Int, val lastSeen: Long,
)

data class RelationEdge(val fromId: Long, val toId: Long, val type: RelationType, val score: Float)

data class FilterOptions(
    val platforms: List<Pair<String, String>>,
    val categories: List<String>,
    val creators: List<String>,
    val types: List<ContentType>,
)

data class TrackedApp(val packageName: String, val label: String, val platformSlug: String?, val installed: Boolean, val enabled: Boolean)
