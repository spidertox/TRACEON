package com.traceon.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.traceon.domain.model.DayCount
import com.traceon.domain.model.LabeledValue
import kotlinx.coroutines.flow.Flow

/** Every statistic shown in the app comes from one of these aggregates over real rows. */
@Dao
interface AnalyticsDao {
    @Query("SELECT COUNT(*) FROM content_items") fun totalItems(): Flow<Int>

    @Query("SELECT contentType AS label, COUNT(*) AS value FROM content_items GROUP BY contentType")
    fun itemsByType(): Flow<List<LabeledValue>>

    @Query("SELECT c.contentType AS label, COUNT(*) AS value FROM activity_events e JOIN content_items c ON c.id = e.contentId GROUP BY c.contentType")
    fun eventsByType(): Flow<List<LabeledValue>>

    @Query("SELECT SUM(viewingDurationMs) FROM content_items") fun viewingMs(): Flow<Long?>
    @Query("SELECT SUM(readingDurationMs) FROM content_items") fun readingMs(): Flow<Long?>

    @Query("SELECT a.label AS label, COALESCE(SUM(s.durationMs), 0) AS value FROM activity_sessions s JOIN applications a ON a.id = s.applicationId WHERE s.applicationId IS NOT NULL AND s.startTime >= :sinceMs GROUP BY a.id ORDER BY value DESC LIMIT :limit")
    fun topApps(sinceMs: Long, limit: Int): Flow<List<LabeledValue>>

    @Query("SELECT domain AS label, COUNT(*) AS value FROM browser_activity WHERE domain IS NOT NULL GROUP BY domain ORDER BY value DESC LIMIT :limit")
    fun topDomains(limit: Int): Flow<List<LabeledValue>>

    @Query("SELECT cat.name AS label, COUNT(*) AS value FROM content_items c JOIN categories cat ON cat.id = c.categoryId GROUP BY cat.id ORDER BY value DESC LIMIT :limit")
    fun topCategories(limit: Int): Flow<List<LabeledValue>>

    @Query("SELECT t.name AS label, COUNT(*) AS value FROM content_topic ct JOIN topics t ON t.id = ct.topicId GROUP BY t.id ORDER BY value DESC LIMIT :limit")
    fun topTopics(limit: Int): Flow<List<LabeledValue>>

    @Query("SELECT COALESCE(p.name, 'Unknown') AS label, COUNT(*) AS value FROM content_items c LEFT JOIN platforms p ON p.id = c.platformId GROUP BY c.platformId ORDER BY value DESC")
    fun platformItems(): Flow<List<LabeledValue>>

    @Query("SELECT COALESCE(p.name, 'Unknown') AS label, COUNT(*) AS value FROM activity_events e JOIN content_items c ON c.id = e.contentId LEFT JOIN platforms p ON p.id = c.platformId GROUP BY c.platformId ORDER BY value DESC")
    fun platformEvents(): Flow<List<LabeledValue>>

    @Query("SELECT browserSlug AS label, COUNT(*) AS value FROM browser_activity GROUP BY browserSlug ORDER BY value DESC")
    fun browserActivity(): Flow<List<LabeledValue>>

    @Query("SELECT engineSlug AS label, COUNT(*) AS value FROM search_activity GROUP BY engineSlug ORDER BY value DESC")
    fun engineActivity(): Flow<List<LabeledValue>>

    @Query("SELECT COUNT(*) FROM search_activity") fun searchCount(): Flow<Int>
    @Query("SELECT COUNT(DISTINCT contentId) FROM browser_activity") fun distinctPages(): Flow<Int>
    @Query("SELECT COUNT(DISTINCT domain) FROM browser_activity WHERE domain IS NOT NULL") fun distinctDomains(): Flow<Int>

    @Query("SELECT (eventTime + :offsetMs) / 86400000 AS day, COUNT(*) AS value FROM activity_events WHERE eventTime >= :sinceMs GROUP BY day ORDER BY day")
    fun dailyActivity(sinceMs: Long, offsetMs: Long): Flow<List<DayCount>>

    @Query("SELECT (visitTime + :offsetMs) / 86400000 AS day, COUNT(*) AS value FROM browser_activity WHERE visitTime >= :sinceMs GROUP BY day ORDER BY day")
    fun dailyBrowsing(sinceMs: Long, offsetMs: Long): Flow<List<DayCount>>

    @Query("SELECT (searchTime + :offsetMs) / 86400000 AS day, COUNT(*) AS value FROM search_activity WHERE searchTime >= :sinceMs GROUP BY day ORDER BY day")
    fun dailySearches(sinceMs: Long, offsetMs: Long): Flow<List<DayCount>>

    @Query("SELECT c.title AS label, COUNT(*) AS value FROM browser_activity b JOIN content_items c ON c.id = b.contentId GROUP BY b.contentId HAVING COUNT(*) > 1 ORDER BY value DESC LIMIT :limit")
    fun repeatedPages(limit: Int): Flow<List<LabeledValue>>
}
