package com.traceon.domain.usecase

import com.traceon.core.common.TextUtils
import com.traceon.core.common.TimeUtils
import com.traceon.domain.classification.ClassificationInput
import com.traceon.domain.classification.ContentClassifier
import com.traceon.domain.model.ContentType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class DiscoveryMode { LIST, TOP_TOPICS, CONNECTIONS, COUNT }

data class DiscoveryQuery(
    val mode: DiscoveryMode,
    val from: Long?,
    val to: Long?,
    val timeLabel: String?,
    val platform: String?,
    val types: Set<ContentType>,
    val topic: String?,
    val terms: List<String>,
) {
    /** Human-readable interpretation, shown to the user so nothing is hidden. */
    fun describe(): String {
        val parts = ArrayList<String>()
        parts += when (mode) {
            DiscoveryMode.LIST -> "list"; DiscoveryMode.TOP_TOPICS -> "top topics"
            DiscoveryMode.CONNECTIONS -> "search-to-video connections"; DiscoveryMode.COUNT -> "count"
        }
        if (types.isNotEmpty()) parts += types.joinToString("/") { it.plural.lowercase() }
        platform?.let { parts += "platform: $it" }
        topic?.let { parts += "topic: $it" }
        if (terms.isNotEmpty()) parts += "keywords: ${terms.joinToString(", ")}"
        timeLabel?.let { parts += "time: $it" }
        return parts.joinToString(" - ")
    }
}

object DiscoveryParser {
    private val WEEKDAYS = DayOfWeek.values().associateBy { it.name.lowercase() }
    private val LAST_N_DAYS = Regex("""last (\d{1,3}) days""")
    private val PLATFORMS = mapOf("youtube" to "youtube", "instagram" to "instagram", "facebook" to "facebook", "snapchat" to "snapchat", "moj" to "moj", "josh" to "josh")
    private val TYPE_WORDS = mapOf(
        "reels" to setOf(ContentType.REEL), "reel" to setOf(ContentType.REEL),
        "shorts" to setOf(ContentType.SHORT),
        "videos" to setOf(ContentType.VIDEO, ContentType.SHORT, ContentType.REEL),
        "articles" to setOf(ContentType.ARTICLE, ContentType.BLOG), "article" to setOf(ContentType.ARTICLE, ContentType.BLOG),
        "websites" to setOf(ContentType.WEBSITE), "website" to setOf(ContentType.WEBSITE),
        "searches" to setOf(ContentType.SEARCH_QUERY), "queries" to setOf(ContentType.SEARCH_QUERY),
    )
    private val FILLER = setOf(
        "show", "find", "list", "give", "tell", "what", "which", "did", "does", "all", "content", "topics", "topic", "related", "between",
        "visit", "visited", "read", "explore", "explored", "explore", "search", "searched", "watch", "watched", "saved", "previously",
        "last", "this", "week", "month", "year", "today", "yesterday", "days", "many", "much", "count", "total", "connections", "most",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "tutorials", "tutorial",
    )

    fun parse(question: String, now: Long, zone: ZoneId = ZoneId.systemDefault(), classifier: ContentClassifier = ContentClassifier()): DiscoveryQuery {
        val q = question.lowercase()
        val words = TextUtils.tokens(q)
        val mode = when {
            q.contains("connection") || (q.contains("between") && q.contains("search") && (q.contains("video") || q.contains("watch"))) -> DiscoveryMode.CONNECTIONS
            q.contains("most explored") || q.contains("top topics") || (q.contains("topics") && q.contains("most")) -> DiscoveryMode.TOP_TOPICS
            q.startsWith("how many") || q.contains("count") -> DiscoveryMode.COUNT
            else -> DiscoveryMode.LIST
        }
        val time = resolveTime(q, now, zone)
        val types = words.firstNotNullOfOrNull { TYPE_WORDS[it] } ?: emptySet()
        val platform = words.firstNotNullOfOrNull { PLATFORMS[it] }
        val topic = classifier.classify(ClassificationInput(title = question)).let { if (it.confidence >= 0.3f) it.category else null }
        val terms = if (topic != null) emptyList() else words.filter {
            it.length >= 3 && it !in TextUtils.STOPWORDS && it !in FILLER && it !in TYPE_WORDS && it !in PLATFORMS
        }.distinct().take(4)
        return DiscoveryQuery(mode, time?.first, time?.second, time?.third, platform, types, topic, terms)
    }

    private fun resolveTime(q: String, now: Long, zone: ZoneId): Triple<Long, Long, String>? {
        val today = TimeUtils.toLocalDate(now, zone)
        fun day(d: LocalDate, label: String) = Triple(TimeUtils.startOfDay(d, zone), TimeUtils.endOfDay(d, zone), label)
        fun span(a: LocalDate, b: LocalDate, label: String) = Triple(TimeUtils.startOfDay(a, zone), TimeUtils.endOfDay(b, zone), label)
        LAST_N_DAYS.find(q)?.let { m ->
            val n = m.groupValues[1].toLong().coerceAtLeast(1)
            return span(today.minusDays(n - 1), today, "last $n days")
        }
        for ((name, dow) in WEEKDAYS) {
            if (!Regex("""\b$name\b""").containsMatchIn(q)) continue
            val date = if (q.contains("last $name")) today.with(TemporalAdjusters.previous(dow)) else today.with(TemporalAdjusters.previousOrSame(dow))
            return day(date, TimeUtils.formatDate(TimeUtils.startOfDay(date, zone), zone))
        }
        return when {
            q.contains("yesterday") -> day(today.minusDays(1), "yesterday")
            q.contains("today") -> day(today, "today")
            q.contains("last week") -> {
                val monday = today.with(TemporalAdjusters.previous(DayOfWeek.MONDAY)).minusWeeks(if (today.dayOfWeek == DayOfWeek.MONDAY) 0 else 1)
                span(monday, monday.plusDays(6), "last week")
            }
            q.contains("this week") -> span(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today, "this week")
            q.contains("last month") -> {
                val first = today.withDayOfMonth(1).minusMonths(1)
                span(first, first.withDayOfMonth(first.lengthOfMonth()), "last month")
            }
            q.contains("this month") -> span(today.withDayOfMonth(1), today, "this month")
            q.contains("last year") -> span(LocalDate.of(today.year - 1, 1, 1), LocalDate.of(today.year - 1, 12, 31), "last year")
            q.contains("this year") -> span(LocalDate.of(today.year, 1, 1), today, "this year")
            else -> null
        }
    }
}
