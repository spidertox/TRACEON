package com.traceon.data.local

import com.traceon.core.common.TextUtils
import com.traceon.domain.model.SearchFilters
import com.traceon.domain.model.SearchSort

data class BuiltQuery(val sql: String, val args: List<Any?>)

/** Builds a parameterised search over content. Every user-supplied value is bound, never concatenated. */
object SearchQueryBuilder {
    const val ROW_SELECT =
        "SELECT c.*, p.slug AS platformSlug, p.name AS platformName, cr.name AS creatorName, cat.name AS categoryName " +
            "FROM content_items c LEFT JOIN platforms p ON p.id = c.platformId " +
            "LEFT JOIN creators cr ON cr.id = c.creatorId LEFT JOIN categories cat ON cat.id = c.categoryId"

    fun build(query: String, f: SearchFilters, limit: Int): BuiltQuery {
        val where = ArrayList<String>()
        val args = ArrayList<Any?>()
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(8)
        for (t in tokens) {
            val like = "%" + TextUtils.likeEscape(t) + "%"
            where += "(c.title LIKE ? ESCAPE '\\' OR c.description LIKE ? ESCAPE '\\' OR c.userNotes LIKE ? ESCAPE '\\' " +
                "OR c.url LIKE ? ESCAPE '\\' OR c.domain LIKE ? ESCAPE '\\' OR cr.name LIKE ? ESCAPE '\\' OR cat.name LIKE ? ESCAPE '\\' " +
                "OR c.contentType LIKE ? ESCAPE '\\' " +
                "OR EXISTS (SELECT 1 FROM content_topic ct JOIN topics t ON t.id = ct.topicId WHERE ct.contentId = c.id AND t.name LIKE ? ESCAPE '\\') " +
                "OR EXISTS (SELECT 1 FROM content_tag x JOIN tags g ON g.id = x.tagId WHERE x.contentId = c.id AND g.name LIKE ? ESCAPE '\\'))"
            repeat(10) { args += like }
        }
        f.platform?.let { where += "p.slug = ?"; args += it }
        if (f.types.isNotEmpty()) {
            where += "c.contentType IN (" + f.types.joinToString(",") { "?" } + ")"
            f.types.forEach { args += it.name }
        }
        f.category?.let { where += "cat.name = ?"; args += it }
        f.topic?.let {
            where += "EXISTS (SELECT 1 FROM content_topic ct2 JOIN topics t2 ON t2.id = ct2.topicId WHERE ct2.contentId = c.id AND t2.name = ?)"
            args += it
        }
        f.domain?.let { where += "c.domain = ?"; args += it }
        f.creator?.let { where += "cr.name = ?"; args += it }
        f.tag?.let {
            where += "EXISTS (SELECT 1 FROM content_tag x2 JOIN tags g2 ON g2.id = x2.tagId WHERE x2.contentId = c.id AND g2.name = ?)"
            args += it
        }
        if (f.from != null && f.to != null) {
            where += "c.id IN (SELECT contentId FROM activity_events WHERE eventTime BETWEEN ? AND ?)"
            args += f.from; args += f.to
        }
        val order = when (f.sort) {
            SearchSort.RELEVANCE -> if (tokens.isNotEmpty()) {
                args += "%" + TextUtils.likeEscape(tokens.first()) + "%"
                "(c.title LIKE ? ESCAPE '\\') DESC, c.lastSeen DESC"
            } else "c.lastSeen DESC"
            SearchSort.NEWEST -> "c.lastSeen DESC"
            SearchSort.OLDEST -> "c.firstSeen ASC"
            SearchSort.MOST_REVISITED -> "c.sessionCount DESC, c.lastSeen DESC"
            SearchSort.TITLE -> "c.title COLLATE NOCASE ASC"
        }
        val sql = ROW_SELECT + (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) +
            " ORDER BY $order LIMIT ?"
        args += limit
        return BuiltQuery(sql, args)
    }
}
