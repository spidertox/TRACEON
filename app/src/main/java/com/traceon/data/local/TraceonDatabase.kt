package com.traceon.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.traceon.data.local.dao.ActivityDao
import com.traceon.data.local.dao.AnalyticsDao
import com.traceon.data.local.dao.ContentDao
import com.traceon.data.local.dao.SystemDao
import com.traceon.data.local.dao.TaxonomyDao
import com.traceon.data.local.entity.ActivityEventView
import com.traceon.data.local.entity.ActivitySessionEntity
import com.traceon.data.local.entity.ApplicationEntity
import com.traceon.data.local.entity.BrowserActivityEntity
import com.traceon.data.local.entity.CategoryEntity
import com.traceon.data.local.entity.ContentEntity
import com.traceon.data.local.entity.ContentRelationEntity
import com.traceon.data.local.entity.ContentTagCrossRef
import com.traceon.data.local.entity.ContentTopicCrossRef
import com.traceon.data.local.entity.CreatorEntity
import com.traceon.data.local.entity.ImportJobEntity
import com.traceon.data.local.entity.PermissionStatusEntity
import com.traceon.data.local.entity.PlatformEntity
import com.traceon.data.local.entity.SearchActivityEntity
import com.traceon.data.local.entity.SearchHistoryEntity
import com.traceon.data.local.entity.TagEntity
import com.traceon.data.local.entity.TopicEntity
import com.traceon.data.local.entity.TrackingSourceEntity
import com.traceon.data.local.entity.UserPreferenceEntity

@Database(
    entities = [
        PlatformEntity::class, ApplicationEntity::class, CategoryEntity::class, TopicEntity::class, CreatorEntity::class,
        TagEntity::class, ContentEntity::class, ActivitySessionEntity::class, ImportJobEntity::class,
        BrowserActivityEntity::class, SearchActivityEntity::class, ContentTopicCrossRef::class, ContentTagCrossRef::class,
        ContentRelationEntity::class, TrackingSourceEntity::class, PermissionStatusEntity::class,
        UserPreferenceEntity::class, SearchHistoryEntity::class,
    ],
    views = [ActivityEventView::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TraceonDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun activityDao(): ActivityDao
    abstract fun taxonomyDao(): TaxonomyDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun systemDao(): SystemDao
}
