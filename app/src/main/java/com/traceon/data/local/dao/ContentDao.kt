package com.traceon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.traceon.data.local.SearchQueryBuilder.ROW_SELECT
import com.traceon.data.local.entity.ContentEntity
import com.traceon.data.local.entity.ContentRelationEntity
import com.traceon.data.local.entity.ActivityEventView
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.GraphItem
import com.traceon.domain.model.LabeledValue
import com.traceon.domain.model.RelationEdge
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: ContentEntity): Long

    @Query("SELECT * FROM content_items WHERE dedupKey = :key LIMIT 1")
    suspend fun findByDedupKey(key: String): ContentEntity?

    @Query("SELECT * FROM content_items WHERE id = :id")
    suspend fun get(id: Long): ContentEntity?

    @Query(ROW_SELECT + " WHERE c.id = :id")
    fun observeRow(id: Long): Flow<ContentRow?>

    @Query(ROW_SELECT + " WHERE c.id = :id")
    suspend fun getRow(id: Long): ContentRow?

    @Query(ROW_SELECT + " WHERE c.id IN (:ids)")
    suspend fun getRows(ids: List<Long>): List<ContentRow>

    @Query(ROW_SELECT + " ORDER BY c.lastSeen DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ContentRow>>

    @Query(ROW_SELECT + " ORDER BY c.id LIMIT :limit OFFSET :offset")
    suspend fun pageRows(limit: Int, offset: Int): List<ContentRow>

    @RawQuery
    suspend fun searchRows(query: SupportSQLiteQuery): List<ContentRow>

    @Query(
        "UPDATE content_items SET lastSeen = MAX(lastSeen, :t), firstSeen = MIN(firstSeen, :t), sessionCount = sessionCount + 1, " +
            "viewingDurationMs = CASE WHEN :dur IS NULL THEN viewingDurationMs ELSE COALESCE(viewingDurationMs, 0) + :dur END, updatedAt = :now WHERE id = :id",
    )
    suspend fun registerViewEvent(id: Long, t: Long, dur: Long?, now: Long)

    @Query(
        "UPDATE content_items SET lastSeen = MAX(lastSeen, :t), firstSeen = MIN(firstSeen, :t), sessionCount = sessionCount + 1, " +
            "readingDurationMs = CASE WHEN :dur IS NULL THEN readingDurationMs ELSE COALESCE(readingDurationMs, 0) + :dur END, updatedAt = :now WHERE id = :id",
    )
    suspend fun registerReadEvent(id: Long, t: Long, dur: Long?, now: Long)

    @Query("UPDATE content_items SET viewingDurationMs = COALESCE(viewingDurationMs, 0) + :delta, updatedAt = :now WHERE id = :id")
    suspend fun addViewingDuration(id: Long, delta: Long, now: Long)

    @Query("UPDATE content_items SET readingDurationMs = COALESCE(readingDurationMs, 0) + :delta, updatedAt = :now WHERE id = :id")
    suspend fun addReadingDuration(id: Long, delta: Long, now: Long)

    @Query("UPDATE content_items SET title = :title, description = :description, userNotes = :notes, updatedAt = :now WHERE id = :id")
    suspend fun updateDetails(id: Long, title: String, description: String, notes: String, now: Long)

    @Query("UPDATE content_items SET userNotes = :notes, updatedAt = :now WHERE id = :id AND userNotes = ''")
    suspend fun fillNotes(id: Long, notes: String, now: Long)

    @Query("UPDATE content_items SET categoryId = :categoryId, categoryConfidence = :confidence, keywords = :keywords, updatedAt = :now WHERE id = :id AND categoryLocked = 0")
    suspend fun setAutoClassification(id: Long, categoryId: Long?, confidence: Float, keywords: String, now: Long)

    @Query("UPDATE content_items SET categoryId = :categoryId, categoryConfidence = :confidence, categoryLocked = :locked, updatedAt = :now WHERE id = :id")
    suspend fun setUserCategory(id: Long, categoryId: Long?, confidence: Float, locked: Boolean, now: Long)

    @Query(
        "UPDATE content_items SET description = CASE WHEN description = '' THEN :description ELSE description END, " +
            "thumbnailUrl = COALESCE(thumbnailUrl, :thumb), creatorId = COALESCE(creatorId, :creatorId), updatedAt = :now WHERE id = :id",
    )
    suspend fun fillBlanks(id: Long, description: String, thumb: String?, creatorId: Long?, now: Long)

    @Query("DELETE FROM content_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM content_items WHERE isDemo = 1")
    suspend fun deleteDemo(): Int

    @Query("DELETE FROM content_items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM content_items WHERE isDemo = 1")
    fun observeDemoCount(): Flow<Int>

    @Query("SELECT * FROM content_items WHERE categoryLocked = 0 ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun unlockedPage(limit: Int, offset: Int): List<ContentEntity>

    @Query("SELECT id, title, keywords, creatorId, platformId, domain FROM content_items WHERE categoryId = :categoryId AND id != :id ORDER BY lastSeen DESC LIMIT :limit")
    suspend fun candidatesByCategory(categoryId: Long, id: Long, limit: Int): List<CandidateRow>

    @Query("SELECT id, title, keywords, creatorId, platformId, domain FROM content_items WHERE creatorId = :creatorId AND id != :id ORDER BY lastSeen DESC LIMIT :limit")
    suspend fun candidatesByCreator(creatorId: Long, id: Long, limit: Int): List<CandidateRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelation(e: ContentRelationEntity): Long

    @Query("DELETE FROM content_relations WHERE isUserCreated = 0 AND (fromContentId = :id OR toContentId = :id)")
    suspend fun clearAutoRelations(id: Long)

    @Query("DELETE FROM content_relations WHERE isUserCreated = 1 AND ((fromContentId = :a AND toContentId = :b) OR (fromContentId = :b AND toContentId = :a))")
    suspend fun deleteUserRelation(a: Long, b: Long)

    @Query("SELECT * FROM content_relations WHERE fromContentId = :id OR toContentId = :id ORDER BY score DESC LIMIT 30")
    fun observeRelationsOf(id: Long): Flow<List<ContentRelationEntity>>

    @Query("SELECT fromContentId AS fromId, toContentId AS toId, type AS type, score AS score FROM content_relations ORDER BY score DESC LIMIT :limit")
    fun observeRelationEdges(limit: Int): Flow<List<RelationEdge>>

    @Query(
        "SELECT c.id AS id, c.title AS title, p.slug AS platformSlug, c.contentType AS contentType, cat.name AS category, cr.name AS creator, " +
            "c.sessionCount AS sessionCount, c.lastSeen AS lastSeen FROM content_items c " +
            "LEFT JOIN platforms p ON p.id = c.platformId LEFT JOIN categories cat ON cat.id = c.categoryId LEFT JOIN creators cr ON cr.id = c.creatorId " +
            "WHERE c.lastSeen BETWEEN :fromMs AND :toMs AND (:platform IS NULL OR p.slug = :platform) AND (:type IS NULL OR c.contentType = :type) " +
            "AND (:category IS NULL OR cat.name = :category) AND (:creator IS NULL OR cr.name = :creator) ORDER BY c.lastSeen DESC LIMIT :limit",
    )
    fun observeGraphItems(fromMs: Long, toMs: Long, platform: String?, type: String?, category: String?, creator: String?, limit: Int): Flow<List<GraphItem>>

    @Query(
        "SELECT c.*, p.slug AS platformSlug, p.name AS platformName, cr.name AS creatorName, cat.name AS categoryName, " +
            "e.eventTime AS eventTime, e.durationMs AS eventDurationMs, e.collectionMethod AS eventMethod, e.sourceSlug AS eventSource " +
            "FROM activity_events e JOIN content_items c ON c.id = e.contentId " +
            "LEFT JOIN platforms p ON p.id = c.platformId LEFT JOIN creators cr ON cr.id = c.creatorId LEFT JOIN categories cat ON cat.id = c.categoryId " +
            "WHERE e.eventTime BETWEEN :fromMs AND :toMs AND (:platform IS NULL OR p.slug = :platform) AND (:kind IS NULL OR e.kind = :kind) " +
            "ORDER BY e.eventTime DESC, e.contentId DESC LIMIT :limit",
    )
    fun observeTimeline(fromMs: Long, toMs: Long, platform: String?, kind: String?, limit: Int): Flow<List<TimelineRow>>

    @Query("SELECT DISTINCT p.slug AS slug, p.name AS name FROM platforms p JOIN content_items c ON c.platformId = p.id ORDER BY p.name")
    suspend fun platformOptions(): List<PlatformOption>

    @Query("SELECT DISTINCT cat.name FROM categories cat JOIN content_items c ON c.categoryId = cat.id ORDER BY cat.name")
    suspend fun categoryOptions(): List<String>

    @Query("SELECT DISTINCT cr.name FROM creators cr JOIN content_items c ON c.creatorId = cr.id ORDER BY cr.name LIMIT 200")
    suspend fun creatorOptions(): List<String>

    @Query("SELECT DISTINCT contentType FROM content_items")
    suspend fun typeOptions(): List<ContentType>

    @Query("SELECT DISTINCT title FROM content_items WHERE title LIKE :p ESCAPE '\\' ORDER BY lastSeen DESC LIMIT :limit")
    suspend fun titleSuggestions(p: String, limit: Int): List<String>

    @Query("SELECT DISTINCT domain FROM content_items WHERE domain LIKE :p ESCAPE '\\' LIMIT :limit")
    suspend fun domainSuggestions(p: String, limit: Int): List<String>

    @Query("SELECT DISTINCT cr.name FROM creators cr WHERE cr.name LIKE :p ESCAPE '\\' LIMIT :limit")
    suspend fun creatorSuggestions(p: String, limit: Int): List<String>

    @Query("SELECT t.name AS label, COUNT(*) AS value FROM content_topic ct JOIN topics t ON t.id = ct.topicId JOIN content_items c ON c.id = ct.contentId WHERE c.lastSeen BETWEEN :fromMs AND :toMs GROUP BY t.id ORDER BY value DESC LIMIT :limit")
    suspend fun topicCounts(fromMs: Long, toMs: Long, limit: Int): List<LabeledValue>

    @Query("SELECT t2.name AS label, COUNT(*) AS value FROM content_topic a JOIN topics ta ON ta.id = a.topicId JOIN content_topic b ON b.contentId = a.contentId AND b.topicId != a.topicId JOIN topics t2 ON t2.id = b.topicId WHERE ta.name = :topic GROUP BY t2.id ORDER BY value DESC LIMIT :limit")
    suspend fun relatedTopics(topic: String, limit: Int): List<LabeledValue>

    @Query("SELECT t.name AS label, COUNT(*) AS value FROM content_topic ct JOIN topics t ON t.id = ct.topicId GROUP BY t.id ORDER BY value DESC")
    fun observeTopicsOverview(): Flow<List<LabeledValue>>

    @Query(ROW_SELECT + " WHERE c.id IN (SELECT ct.contentId FROM content_topic ct JOIN topics t ON t.id = ct.topicId WHERE t.name = :topic) ORDER BY c.lastSeen DESC LIMIT :limit")
    suspend fun itemsForTopic(topic: String, limit: Int): List<ContentRow>

    @Query("SELECT contentId, eventTime, durationMs, collectionMethod, kind, sourceSlug FROM activity_events")
    suspend fun allEvents(): List<ActivityEventView>
}
