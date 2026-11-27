package com.traceon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.traceon.data.local.entity.ApplicationEntity
import com.traceon.data.local.entity.ImportJobEntity
import com.traceon.data.local.entity.PermissionStatusEntity
import com.traceon.data.local.entity.SearchHistoryEntity
import com.traceon.data.local.entity.TrackingSourceEntity
import com.traceon.data.local.entity.UserPreferenceEntity
import com.traceon.domain.model.CollectionMethod
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemDao {
    @Insert suspend fun insertJob(e: ImportJobEntity): Long
    @Update suspend fun updateJob(e: ImportJobEntity)
    @Query("SELECT * FROM import_jobs ORDER BY startedAt DESC LIMIT 50") fun observeJobs(): Flow<List<ImportJobEntity>>
    @Query("DELETE FROM import_jobs") suspend fun deleteAllJobs()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSource(e: TrackingSourceEntity)
    @Query("SELECT * FROM tracking_sources") fun observeSources(): Flow<List<TrackingSourceEntity>>
    @Query("SELECT enabled FROM tracking_sources WHERE method = :method") suspend fun isSourceEnabled(method: CollectionMethod): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPermission(e: PermissionStatusEntity)
    @Query("SELECT * FROM permission_status") fun observePermissions(): Flow<List<PermissionStatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setPref(e: UserPreferenceEntity)
    @Query("SELECT prefValue FROM user_preferences WHERE prefKey = :key") suspend fun getPref(key: String): String?
    @Query("SELECT prefValue FROM user_preferences WHERE prefKey = :key") fun observePref(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertApp(e: ApplicationEntity): Long
    @Query("SELECT * FROM applications WHERE packageName = :pkg") suspend fun getApp(pkg: String): ApplicationEntity?
    @Query("UPDATE applications SET trackingEnabled = :enabled WHERE packageName = :pkg") suspend fun setAppTracking(pkg: String, enabled: Boolean)
    @Query("SELECT * FROM applications") fun observeApps(): Flow<List<ApplicationEntity>>
    @Query("SELECT * FROM applications WHERE trackingEnabled = 1") suspend fun trackedApps(): List<ApplicationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSearchHistory(e: SearchHistoryEntity): Long
    @Query("UPDATE search_history SET createdAt = :t WHERE queryText = :q") suspend fun touchSearchHistory(q: String, t: Long)
    @Query("SELECT * FROM search_history WHERE isSaved = 0 ORDER BY createdAt DESC LIMIT :limit") fun observeRecentSearches(limit: Int): Flow<List<SearchHistoryEntity>>
    @Query("SELECT * FROM search_history WHERE isSaved = 1 ORDER BY createdAt DESC") fun observeSavedSearches(): Flow<List<SearchHistoryEntity>>
    @Query("UPDATE search_history SET isSaved = :saved WHERE queryText = :q") suspend fun markSearchSaved(q: String, saved: Boolean)
    @Query("DELETE FROM search_history WHERE isSaved = 0") suspend fun clearRecentSearches()
    @Query("DELETE FROM search_history") suspend fun deleteAllSearchHistory()
}
