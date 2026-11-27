package com.traceon.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.traceon.domain.model.CollectionMethod
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.DataStatus
import com.traceon.domain.model.ImportSource
import com.traceon.domain.model.ImportStatus
import com.traceon.domain.model.PlatformKind
import com.traceon.domain.model.RelationType

@Entity(tableName = "platforms", indices = [Index(value = ["slug"], unique = true)])
data class PlatformEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slug: String,
    val name: String,
    val kind: PlatformKind,
)

@Entity(
    tableName = "applications",
    foreignKeys = [ForeignKey(entity = PlatformEntity::class, parentColumns = ["id"], childColumns = ["platformId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["packageName"], unique = true), Index("platformId")],
)
data class ApplicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    val platformId: Long? = null,
    val trackingEnabled: Boolean = false,
)

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val groupName: String? = null,
    val isCustom: Boolean = false,
)

@Entity(
    tableName = "topics",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["name"], unique = true), Index("categoryId")],
)
data class TopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val categoryId: Long? = null,
)

@Entity(
    tableName = "creators",
    foreignKeys = [ForeignKey(entity = PlatformEntity::class, parentColumns = ["id"], childColumns = ["platformId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["name", "platformId"], unique = true), Index("platformId")],
)
data class CreatorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val platformId: Long? = null,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
)

@Entity(
    tableName = "content_items",
    foreignKeys = [
        ForeignKey(entity = PlatformEntity::class, parentColumns = ["id"], childColumns = ["platformId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = ApplicationEntity::class, parentColumns = ["id"], childColumns = ["applicationId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = CreatorEntity::class, parentColumns = ["id"], childColumns = ["creatorId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["dedupKey"], unique = true),
        Index("platformId"), Index("applicationId"), Index("creatorId"), Index("categoryId"),
        Index("lastSeen"), Index("domain"), Index("contentType"),
    ],
)
data class ContentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupKey: String,
    val platformId: Long?,
    val applicationId: Long? = null,
    val contentType: ContentType,
    val title: String,
    val description: String = "",
    val visibleText: String = "",
    val url: String? = null,
    val canonicalUrl: String? = null,
    val platformContentId: String? = null,
    val domain: String? = null,
    val creatorId: Long? = null,
    val thumbnailUrl: String? = null,
    val categoryId: Long? = null,
    val categoryConfidence: Float = 0f,
    val categoryLocked: Boolean = false,
    val keywords: String = "",
    val firstSeen: Long,
    val lastSeen: Long,
    val sessionCount: Int = 0,
    /** null = not measurable (never zero-filled). */
    val viewingDurationMs: Long? = null,
    val readingDurationMs: Long? = null,
    val collectionMethod: CollectionMethod,
    val metadataConfidence: Float = 0f,
    val dataStatus: DataStatus,
    val userNotes: String = "",
    val isDemo: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** App-level sessions have contentId = null; content sessions (watched/shared/manual) have applicationId = null. */
@Entity(
    tableName = "activity_sessions",
    foreignKeys = [
        ForeignKey(entity = ContentEntity::class, parentColumns = ["id"], childColumns = ["contentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ApplicationEntity::class, parentColumns = ["id"], childColumns = ["applicationId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["contentId", "startTime"], unique = true),
        Index(value = ["applicationId", "startTime"], unique = true),
        Index("startTime"),
    ],
)
data class ActivitySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: Long? = null,
    val applicationId: Long? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val durationMs: Long? = null,
    val collectionMethod: CollectionMethod,
    val isDemo: Boolean = false,
)

@Entity(
    tableName = "import_jobs",
)
data class ImportJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: ImportSource,
    val fileName: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: ImportStatus,
    val totalRecords: Int = 0,
    val importedCount: Int = 0,
    val duplicateCount: Int = 0,
    val skippedCount: Int = 0,
    val message: String? = null,
)

@Entity(
    tableName = "browser_activity",
    foreignKeys = [
        ForeignKey(entity = ContentEntity::class, parentColumns = ["id"], childColumns = ["contentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ImportJobEntity::class, parentColumns = ["id"], childColumns = ["importJobId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["url", "visitTime", "browserSlug"], unique = true),
        Index("contentId"), Index("visitTime"), Index("importJobId"), Index("domain"),
    ],
)
data class BrowserActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: Long,
    val browserSlug: String,
    val url: String,
    val domain: String? = null,
    val title: String = "",
    val visitTime: Long,
    val durationMs: Long? = null,
    val collectionMethod: CollectionMethod,
    val importJobId: Long? = null,
    val isDemo: Boolean = false,
)

@Entity(
    tableName = "search_activity",
    foreignKeys = [
        ForeignKey(entity = ContentEntity::class, parentColumns = ["id"], childColumns = ["contentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ImportJobEntity::class, parentColumns = ["id"], childColumns = ["importJobId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["queryText", "engineSlug", "searchTime"], unique = true),
        Index("contentId"), Index("searchTime"), Index("importJobId"),
    ],
)
data class SearchActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: Long,
    val queryText: String,
    val engineSlug: String,
    val browserSlug: String? = null,
    val searchUrl: String? = null,
    val searchTime: Long,
    val collectionMethod: CollectionMethod,
    val importJobId: Long? = null,
    val isDemo: Boolean = false,
)

@Entity(
    tableName = "content_topic",
    primaryKeys = ["contentId", "topicId"],
    foreignKeys = [
        ForeignKey(entity = ContentEntity::class, parentColumns = ["id"], childColumns = ["contentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TopicEntity::class, parentColumns = ["id"], childColumns = ["topicId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("topicId")],
)
data class ContentTopicCrossRef(
    val contentId: Long,
    val topicId: Long,
    val confidence: Float,
    val isUserSet: Boolean = false,
)

@Entity(
    tableName = "content_tag",
    primaryKeys = ["contentId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = ContentEntity::class, parentColumns = ["id"], childColumns = ["contentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class ContentTagCrossRef(val contentId: Long, val tagId: Long)

@Entity(
    tableName = "content_relations",
    foreignKeys = [
        ForeignKey(entity = ContentEntity::class, parentColumns = ["id"], childColumns = ["fromContentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ContentEntity::class, parentColumns = ["id"], childColumns = ["toContentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["fromContentId", "toContentId", "type"], unique = true),
        Index("toContentId"),
    ],
)
data class ContentRelationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromContentId: Long,
    val toContentId: Long,
    val type: RelationType,
    val score: Float,
    val isUserCreated: Boolean = false,
    val createdAt: Long,
)

@Entity(tableName = "tracking_sources", indices = [Index(value = ["method"], unique = true)])
data class TrackingSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val method: CollectionMethod,
    val enabled: Boolean,
    val updatedAt: Long,
)

@Entity(tableName = "permission_status", indices = [Index(value = ["permissionKey"], unique = true)])
data class PermissionStatusEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val permissionKey: String,
    val granted: Boolean,
    val checkedAt: Long,
)

@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val prefKey: String,
    val prefValue: String,
)

@Entity(tableName = "search_history", indices = [Index(value = ["queryText"], unique = true)])
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val queryText: String,
    val isSaved: Boolean = false,
    val createdAt: Long,
)

/** One row per recorded content event, whatever table it lives in. Powers timeline, charts and date queries. */
@DatabaseView(
    viewName = "activity_events",
    value = "SELECT contentId AS contentId, startTime AS eventTime, durationMs AS durationMs, collectionMethod AS collectionMethod, 'SESSION' AS kind, '' AS sourceSlug " +
        "FROM activity_sessions WHERE contentId IS NOT NULL " +
        "UNION ALL SELECT contentId, visitTime, durationMs, collectionMethod, 'BROWSER', browserSlug FROM browser_activity " +
        "UNION ALL SELECT contentId, searchTime, NULL, collectionMethod, 'SEARCH', engineSlug FROM search_activity",
)
data class ActivityEventView(
    val contentId: Long,
    val eventTime: Long,
    val durationMs: Long?,
    val collectionMethod: String,
    val kind: String,
    val sourceSlug: String,
)
