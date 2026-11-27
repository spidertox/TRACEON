package com.traceon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.traceon.data.local.entity.ActivitySessionEntity
import com.traceon.data.local.entity.BrowserActivityEntity
import com.traceon.data.local.entity.SearchActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    /** All three inserts IGNORE on their unique index, so re-importing the same record is a no-op returning -1. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(e: ActivitySessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBrowser(e: BrowserActivityEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSearch(e: SearchActivityEntity): Long

    @Query("SELECT eventTime AS time, durationMs AS durationMs, collectionMethod AS method, kind AS kind, sourceSlug AS source FROM activity_events WHERE contentId = :id ORDER BY eventTime DESC")
    fun observeEvents(id: Long): Flow<List<EventRow>>

    @Query("SELECT durationMs FROM browser_activity WHERE contentId = :contentId AND visitTime = :t LIMIT 1")
    suspend fun browserDuration(contentId: Long, t: Long): Long?

    @Query("UPDATE browser_activity SET durationMs = :d WHERE contentId = :contentId AND visitTime = :t")
    suspend fun setBrowserDuration(contentId: Long, t: Long, d: Long)

    @Query("DELETE FROM activity_sessions WHERE applicationId IS NOT NULL")
    suspend fun deleteAppSessions()

    @Query("DELETE FROM activity_sessions WHERE isDemo = 1 AND applicationId IS NOT NULL")
    suspend fun deleteDemoAppSessions()

    @Query("SELECT a.packageName AS packageName, a.label AS label, s.startTime AS startTime, s.endTime AS endTime, s.durationMs AS durationMs FROM activity_sessions s JOIN applications a ON a.id = s.applicationId")
    suspend fun appSessionsForBackup(): List<AppSessionRow>
}
