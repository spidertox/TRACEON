package com.traceon.data.imports

import com.traceon.core.common.SearchEngines
import com.traceon.core.common.TimeUtils
import com.traceon.core.common.UrlUtils
import com.traceon.domain.model.CollectionMethod
import com.traceon.domain.model.ContentDraft
import com.traceon.domain.model.DataStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ParseResult(val drafts: List<ContentDraft>, val skipped: Int, val notes: List<String>)

object Csv {
    fun parse(text: String): List<List<String>> {
        val s = text.removePrefix("\uFEFF")
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { sb.append('"'); i++ } else inQuotes = false
                } else sb.append(ch)
            } else if (ch == '"') {
                inQuotes = true
            } else if (ch == ',') {
                row.add(sb.toString()); sb.setLength(0)
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                row.add(sb.toString()); sb.setLength(0)
                if (row.size > 1 || row[0].isNotEmpty()) rows.add(row)
                row = ArrayList()
            } else sb.append(ch)
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) { row.add(sb.toString()); rows.add(row) }
        return rows
    }

    fun escape(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

/** Browsers and Google use different epochs/units. Anything outside 2000..tomorrow is rejected, never guessed. */
object Timestamps {
    private const val CHROME_EPOCH_OFFSET_MS = 11_644_473_600_000L
    private const val MIN_MS = 946_684_800_000L
    private val PATTERNS = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy/MM/dd HH:mm:ss")
        .map { DateTimeFormatter.ofPattern(it) }

    fun fromNumber(v: Double, now: Long): Long? {
        val ms = when {
            v >= 1e16 -> v / 1000.0 - CHROME_EPOCH_OFFSET_MS
            v >= 1e14 -> v / 1000.0
            v >= 1e11 -> v
            v >= 1e8 -> v * 1000.0
            else -> return null
        }.toLong()
        return ms.takeIf { it in MIN_MS..(now + TimeUtils.DAY_MS) }
    }

    fun parse(raw: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        s.toDoubleOrNull()?.let { return fromNumber(it, now) }
        val instant = runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
        if (instant != null) return instant.takeIf { it in MIN_MS..(now + TimeUtils.DAY_MS) }
        for (p in PATTERNS) {
            val t = runCatching { LocalDateTime.parse(s, p).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
            if (t != null) return t.takeIf { it in MIN_MS..(now + TimeUtils.DAY_MS) }
        }
        val d = runCatching { LocalDate.parse(s).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
        return d?.takeIf { it in MIN_MS..(now + TimeUtils.DAY_MS) }
    }
}

private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonObject.str(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { k -> (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } }

internal fun visitDraft(url: String, title: String, time: Long, browserSlug: String, jobId: Long, method: CollectionMethod): ContentDraft? {
    if (UrlUtils.parse(url) == null) return null
    val search = SearchEngines.match(url)
    return if (search != null) {
        ContentDraft(
            title = search.query, url = url.trim(), eventTime = time, collectionMethod = method, origin = DataStatus.IMPORTED,
            browserSlug = browserSlug, searchQuery = search.query, engineSlug = search.engineSlug, importJobId = jobId,
        )
    } else {
        ContentDraft(
            title = title.trim(), url = url.trim(), eventTime = time, collectionMethod = method, origin = DataStatus.IMPORTED,
            browserSlug = browserSlug, importJobId = jobId,
        )
    }
}

/**
 * Supported inputs: Google Takeout Chrome "BrowserHistory.json" ({"Browser History": [...]}), a plain JSON array of
 * {url,title,time} objects, or CSV with url + time columns. Records without a parseable URL or time are skipped, never invented.
 */
object BrowserHistoryParser {
    fun parse(text: String, browserSlug: String, jobId: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): ParseResult {
        val body = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        return if (body.startsWith("{") || body.startsWith("[")) parseJson(body, browserSlug, jobId, now, zone) else parseCsv(body, browserSlug, jobId, now, zone)
    }

    private fun parseJson(text: String, browser: String, jobId: Long, now: Long, zone: ZoneId): ParseResult {
        val root: JsonElement = try {
            JSON.parseToJsonElement(text)
        } catch (e: Exception) {
            return ParseResult(emptyList(), 0, listOf("Not valid JSON: ${e.message?.take(100)}"))
        }
        val array: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["Browser History"] as? JsonArray) ?: (root["history"] as? JsonArray) ?: (root["items"] as? JsonArray)
                ?: return ParseResult(emptyList(), 0, listOf("No \"Browser History\" array found in this JSON file."))
            else -> return ParseResult(emptyList(), 0, listOf("Unsupported JSON structure."))
        }
        val drafts = ArrayList<ContentDraft>()
        var skipped = 0
        for (el in array) {
            val o = el as? JsonObject
            if (o == null) { skipped++; continue }
            val url = o.str("url", "URL", "titleUrl", "link")
            val time = o.str("time_usec", "lastVisitTime", "visitTime", "last_visit_time", "time", "timestamp", "date")?.let { Timestamps.parse(it, now, zone) }
            val draft = if (url != null && time != null) visitDraft(url, o.str("title", "name").orEmpty(), time, browser, jobId, CollectionMethod.BROWSER_IMPORT) else null
            if (draft == null) skipped++ else drafts += draft
        }
        return ParseResult(drafts, skipped, listOf("Skipped rows had no http(s) URL or no readable timestamp."))
    }

    private fun parseCsv(text: String, browser: String, jobId: Long, now: Long, zone: ZoneId): ParseResult {
        val rows = Csv.parse(text)
        if (rows.isEmpty()) return ParseResult(emptyList(), 0, listOf("The file is empty."))
        val header = rows[0].map { it.trim().lowercase() }
        fun col(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
        val urlCol = col("url", "link", "address", "page url")
        val titleCol = col("title", "name", "page title")
        val timeCol = col("time", "timestamp", "visit_time", "visit time", "last_visit_time", "lastvisittime", "date", "visited", "datetime", "date/time")
        if (urlCol == null || timeCol == null) {
            return ParseResult(emptyList(), 0, listOf("CSV needs a url column and a time/date column. Found: ${header.joinToString()}"))
        }
        val drafts = ArrayList<ContentDraft>()
        var skipped = 0
        for (r in rows.drop(1)) {
            val url = r.getOrNull(urlCol)?.trim().orEmpty()
            val time = r.getOrNull(timeCol)?.let { Timestamps.parse(it, now, zone) }
            val draft = if (url.isNotEmpty() && time != null) visitDraft(url, titleCol?.let { r.getOrNull(it) }.orEmpty(), time, browser, jobId, CollectionMethod.BROWSER_IMPORT) else null
            if (draft == null) skipped++ else drafts += draft
        }
        return ParseResult(drafts, skipped, listOf("Day/month formats such as 01/02/2024 are not accepted because they are ambiguous."))
    }
}

/** Google Takeout "My Activity" JSON (also YouTube watch-history.json): Watched / Searched for / Visited entries. */
object GoogleActivityParser {
    fun parse(text: String, jobId: Long, now: Long): ParseResult {
        val root = try {
            JSON.parseToJsonElement(text.trimStart('\uFEFF', ' ', '\n', '\r', '\t'))
        } catch (e: Exception) {
            return ParseResult(emptyList(), 0, listOf("Not valid JSON: ${e.message?.take(100)}"))
        }
        val array = root as? JsonArray ?: return ParseResult(emptyList(), 0, listOf("Expected a JSON array (Takeout My Activity)."))
        val drafts = ArrayList<ContentDraft>()
        var skipped = 0
        var ads = 0
        for (el in array) {
            val o = el as? JsonObject
            val time = o?.str("time")?.let { Timestamps.parse(it, now) }
            if (o == null || time == null) { skipped++; continue }
            val header = o.str("header").orEmpty()
            val title = o.str("title").orEmpty()
            val url = o.str("titleUrl")
            val details = (o["details"] as? JsonArray)?.mapNotNull { ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull }.orEmpty()
            if (details.any { it.contains("Google Ads", ignoreCase = true) }) { ads++; skipped++; continue }
            val channel = (o["subtitles"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonObject)?.str("name") }
            val draft: ContentDraft? = when {
                title.startsWith("Watched ") && url != null && UrlUtils.parse(url) != null -> ContentDraft(
                    title = title.removePrefix("Watched ").trim().let { if (it == url) "" else it },
                    url = url, creator = channel, eventTime = time, collectionMethod = CollectionMethod.GOOGLE_IMPORT,
                    origin = DataStatus.IMPORTED, importJobId = jobId,
                )
                title.startsWith("Searched for ") -> {
                    val q = title.removePrefix("Searched for ").trim()
                    if (q.isEmpty()) null else ContentDraft(
                        title = q, url = url, searchQuery = q, eventTime = time,
                        engineSlug = if (header.contains("YouTube", ignoreCase = true)) "youtube_search" else "google_search",
                        collectionMethod = CollectionMethod.GOOGLE_IMPORT, origin = DataStatus.IMPORTED, importJobId = jobId,
                    )
                }
                title.startsWith("Visited ") && url != null ->
                    visitDraft(url, title.removePrefix("Visited ").trim(), time, "chrome", jobId, CollectionMethod.GOOGLE_IMPORT)
                else -> null
            }
            if (draft == null) skipped++ else drafts += draft
        }
        val notes = ArrayList<String>()
        if (ads > 0) notes += "$ads ad impressions were ignored."
        notes += "Only Watched / Searched for / Visited entries are imported."
        return ParseResult(drafts, skipped, notes)
    }
}
