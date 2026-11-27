package com.traceon.domain.usecase

import com.traceon.core.common.Clock
import com.traceon.core.common.TextUtils
import com.traceon.domain.model.ContentItem
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.LabeledValue
import com.traceon.domain.model.SearchFilters
import com.traceon.domain.model.SearchSort
import com.traceon.domain.repository.ContentRepository

data class DiscoveryAnswer(
    val summary: String,
    /** How the question was interpreted, shown verbatim so nothing is hidden. */
    val interpretation: String,
    val records: List<ContentItem> = emptyList(),
    val topics: List<LabeledValue> = emptyList(),
    val pairs: List<Pair<ContentItem, ContentItem>> = emptyList(),
)

/**
 * Answers questions strictly from stored records (offline, rule-based). Every answer lists the records it is built on;
 * with no matching record it says so instead of guessing.
 */
class DiscoveryEngine(private val repo: ContentRepository, private val clock: Clock) {

    suspend fun answer(question: String): DiscoveryAnswer {
        if (question.isBlank()) return DiscoveryAnswer("Ask a question about your own library, e.g. \"What did I watch last Sunday?\"", "")
        val q = DiscoveryParser.parse(question, clock.now())
        val interpretation = q.describe()
        val filters = SearchFilters(platform = q.platform, types = q.types, topic = q.topic, from = q.from, to = q.to, sort = SearchSort.NEWEST)
        return when (q.mode) {
            DiscoveryMode.TOP_TOPICS -> {
                val topics = repo.topicCounts(q.from, q.to, 10)
                if (topics.isEmpty()) none(interpretation)
                else DiscoveryAnswer("Your most explored topics${q.timeLabel?.let { " ($it)" }.orEmpty()}, by number of stored items:", interpretation, topics = topics)
            }
            DiscoveryMode.COUNT -> {
                val items = repo.search(q.terms.joinToString(" "), filters, 5000)
                if (items.isEmpty()) none(interpretation)
                else DiscoveryAnswer("${items.size}${if (items.size == 5000) "+" else ""} matching stored items. Most recent shown:", interpretation, records = items.take(10))
            }
            DiscoveryMode.CONNECTIONS -> connections(q, interpretation)
            DiscoveryMode.LIST -> {
                val items = repo.search(q.terms.joinToString(" "), filters, 30)
                if (items.isEmpty()) none(interpretation)
                else DiscoveryAnswer("Found ${items.size}${if (items.size == 30) "+" else ""} stored item${if (items.size == 1) "" else "s"}:", interpretation, records = items)
            }
        }
    }

    private suspend fun connections(q: DiscoveryQuery, interpretation: String): DiscoveryAnswer {
        val base = SearchFilters(from = q.from, to = q.to, sort = SearchSort.NEWEST)
        val searches = repo.search("", base.copy(types = setOf(ContentType.SEARCH_QUERY)), 60)
        val watched = repo.search("", base.copy(types = setOf(ContentType.VIDEO, ContentType.SHORT, ContentType.REEL, ContentType.ARTICLE, ContentType.BLOG, ContentType.TUTORIAL)), 300)
        val pairs = ArrayList<Triple<ContentItem, ContentItem, Int>>()
        for (s in searches) {
            val words = TextUtils.keywords(listOf(s.title to 1), 6).toSet()
            if (words.isEmpty()) continue
            for (w in watched) {
                val overlap = (TextUtils.tokens(w.title).toSet() + w.keywords).count { it in words }
                if (overlap > 0) pairs += Triple(s, w, overlap)
            }
        }
        val top = pairs.sortedByDescending { it.third }.take(15).map { it.first to it.second }
        return if (top.isEmpty()) none(interpretation)
        else DiscoveryAnswer("Search queries that share keywords with things you watched or read:", interpretation, pairs = top)
    }

    private fun none(interpretation: String) = DiscoveryAnswer(
        "I found no stored records matching that. I can only answer from what is in your TRACEON library; try a broader question or import more history.",
        interpretation,
    )
}
