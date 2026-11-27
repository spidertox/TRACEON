package com.traceon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.traceon.data.local.entity.CategoryEntity
import com.traceon.data.local.entity.ContentTagCrossRef
import com.traceon.data.local.entity.ContentTopicCrossRef
import com.traceon.data.local.entity.CreatorEntity
import com.traceon.data.local.entity.PlatformEntity
import com.traceon.data.local.entity.TagEntity
import com.traceon.data.local.entity.TopicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaxonomyDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPlatform(e: PlatformEntity): Long
    @Query("SELECT id FROM platforms WHERE slug = :slug") suspend fun platformId(slug: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategory(e: CategoryEntity): Long
    @Query("SELECT id FROM categories WHERE name = :name") suspend fun categoryId(name: String): Long?
    @Query("SELECT name FROM categories ORDER BY isCustom, name") fun observeCategoryNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTopic(e: TopicEntity): Long
    @Query("SELECT id FROM topics WHERE name = :name") suspend fun topicId(name: String): Long?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertContentTopic(x: ContentTopicCrossRef)
    @Query("DELETE FROM content_topic WHERE contentId = :contentId AND isUserSet = 0") suspend fun clearAutoTopics(contentId: Long)
    @Query("DELETE FROM content_topic WHERE contentId = :contentId") suspend fun clearTopics(contentId: Long)
    @Query("SELECT t.name AS name, ct.confidence AS confidence, ct.isUserSet AS userSet FROM content_topic ct JOIN topics t ON t.id = ct.topicId WHERE ct.contentId = :contentId ORDER BY ct.confidence DESC")
    fun observeTopics(contentId: Long): Flow<List<TopicRow>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTag(e: TagEntity): Long
    @Query("SELECT id FROM tags WHERE name = :name") suspend fun tagId(name: String): Long?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertContentTag(x: ContentTagCrossRef)
    @Query("DELETE FROM content_tag WHERE contentId = :contentId AND tagId = :tagId") suspend fun deleteContentTag(contentId: Long, tagId: Long)
    @Query("SELECT g.name FROM content_tag x JOIN tags g ON g.id = x.tagId WHERE x.contentId = :contentId ORDER BY g.name") fun observeTags(contentId: Long): Flow<List<String>>
    @Query("SELECT g.name FROM content_tag x JOIN tags g ON g.id = x.tagId WHERE x.contentId = :contentId ORDER BY g.name") suspend fun tagNames(contentId: Long): List<String>
    @Query("SELECT name FROM tags ORDER BY name") fun observeAllTags(): Flow<List<String>>
    @Query("SELECT name FROM tags WHERE name LIKE :p ESCAPE '\\' ORDER BY name LIMIT :limit") suspend fun tagSuggestions(p: String, limit: Int): List<String>
    @Query("SELECT t.name FROM topics t WHERE t.name LIKE :p ESCAPE '\\' ORDER BY t.name LIMIT :limit") suspend fun topicSuggestions(p: String, limit: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCreator(e: CreatorEntity): Long
    @Query("SELECT id FROM creators WHERE name = :name AND platformId IS :platformId LIMIT 1") suspend fun findCreator(name: String, platformId: Long?): Long?

    @Query("SELECT x.contentId AS contentId, g.name AS name FROM content_tag x JOIN tags g ON g.id = x.tagId")
    suspend fun allTagLinks(): List<ContentNameRow>

    @Query("SELECT ct.contentId AS contentId, t.name AS name, ct.confidence AS confidence, ct.isUserSet AS userSet FROM content_topic ct JOIN topics t ON t.id = ct.topicId")
    suspend fun allTopicLinks(): List<TopicLinkRow>

    @Query("DELETE FROM topics") suspend fun deleteAllTopics()
    @Query("DELETE FROM creators") suspend fun deleteAllCreators()
    @Query("DELETE FROM tags") suspend fun deleteAllTags()
}
