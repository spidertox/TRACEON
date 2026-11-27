package com.traceon.data

import com.traceon.data.backup.BackupCrypto
import com.traceon.data.imports.BrowserHistoryParser
import com.traceon.data.imports.Csv
import com.traceon.data.imports.GoogleActivityParser
import com.traceon.data.imports.Timestamps
import com.traceon.data.local.SearchQueryBuilder
import com.traceon.domain.model.CollectionMethod
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.SearchFilters
import com.traceon.domain.model.SearchSort
import com.traceon.feature.tracking.RawUsageEvent
import com.traceon.feature.tracking.SessionBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

private const val NOW = 1_800_000_000_000L

class CsvAndTimestampTest {
    @Test fun csvHandlesQuotesCommasAndTrailingNewline() {
        val rows = Csv.parse("a,b\n\"x,1\",\"he said \"\"hi\"\"\"\n")
        assertEquals(listOf(listOf("a", "b"), listOf("x,1", "he said \"hi\"")), rows)
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("plain", Csv.escape("plain"))
    }

    @Test fun numericTimestampsAreNormalisedFromEveryEpoch() {
        val ms = 1_700_000_000_000L
        assertEquals(ms, Timestamps.fromNumber(ms.toDouble(), NOW))
        assertEquals(ms, Timestamps.fromNumber(ms * 1000.0, NOW))
        assertEquals(ms, Timestamps.fromNumber(13_344_473_600_000_000.0, NOW))
        assertEquals(ms, Timestamps.fromNumber(1_700_000_000.0, NOW))
    }

    @Test fun implausibleTimestampsAreRejectedNotGuessed() {
        assertNull(Timestamps.fromNumber(5.0, NOW))
        assertNull(Timestamps.fromNumber((NOW + 10 * 86_400_000L).toDouble(), NOW))
        assertNull(Timestamps.parse("garbage", NOW))
    }

    @Test fun textTimestamps() {
        val utc = ZoneId.of("UTC")
        assertEquals(1709633730000L, Timestamps.parse("2024-03-05T10:15:30Z", NOW, utc))
        assertEquals(1709633730000L, Timestamps.parse("2024-03-05T10:15:30.000Z", NOW, utc))
        assertEquals(1709633730000L, Timestamps.parse("2024-03-05 10:15:30", NOW, utc))
        assertNull(Timestamps.parse("05/03/2024 10:15:30", NOW, utc))
    }
}

class BrowserHistoryParserTest {
    @Test fun takeoutJsonSkipsInvalidRowsAndSplitsSearches() {
        val json = """{"Browser History":[
            {"title":"Kotlin docs","url":"https://kotlinlang.org/docs/home.html","time_usec":1700000000000000},
            {"title":"Search","url":"https://www.google.com/search?q=compose+tips","time_usec":1700000100000000},
            {"title":"bad","url":"chrome://settings","time_usec":1700000200000000},
            {"title":"no time","url":"https://example.com"}]}"""
        val r = BrowserHistoryParser.parse(json, "chrome", 7L, NOW)
        assertEquals(2, r.drafts.size)
        assertEquals(2, r.skipped)
        val page = r.drafts[0]
        assertEquals("https://kotlinlang.org/docs/home.html", page.url)
        assertEquals(1_700_000_000_000L, page.eventTime)
        assertEquals("chrome", page.browserSlug)
        assertEquals(CollectionMethod.BROWSER_IMPORT, page.collectionMethod)
        assertEquals(7L, page.importJobId)
        val search = r.drafts[1]
        assertEquals("compose tips", search.searchQuery)
        assertEquals("google_search", search.engineSlug)
    }

    @Test fun csvWithUrlAndTimeColumns() {
        val csv = "url,title,visit_time\nhttps://example.com/a,Example A,2024-03-05 10:15:30\nnot a url,Bad,2024-03-05 10:15:30\n"
        val r = BrowserHistoryParser.parse(csv, "firefox", 1L, NOW)
        assertEquals(1, r.drafts.size)
        assertEquals(1, r.skipped)
        assertEquals("Example A", r.drafts[0].title)
        assertEquals("firefox", r.drafts[0].browserSlug)
    }

    @Test fun csvWithoutRequiredColumnsExplainsWhy() {
        val r = BrowserHistoryParser.parse("title,date\nx,2024-01-01", "chrome", 1L, NOW)
        assertTrue(r.drafts.isEmpty())
        assertTrue(r.notes.first().contains("url"))
    }

    @Test fun brokenJsonIsReportedNotThrown() {
        val r = BrowserHistoryParser.parse("{not json", "chrome", 1L, NOW)
        assertTrue(r.drafts.isEmpty())
        assertTrue(r.notes.first().startsWith("Not valid JSON"))
    }
}

class GoogleActivityParserTest {
    private val json = """[
      {"header":"YouTube","title":"Watched Kotlin in 100 seconds","titleUrl":"https://www.youtube.com/watch?v=abc123","subtitles":[{"name":"Fireship","url":"https://www.youtube.com/channel/x"}],"time":"2024-03-05T10:15:30.000Z"},
      {"header":"YouTube","title":"Searched for lofi beats","titleUrl":"https://www.youtube.com/results?search_query=lofi+beats","time":"2024-03-05T11:00:00.000Z"},
      {"header":"YouTube","title":"Watched https://www.youtube.com/watch?v=gone","titleUrl":"https://www.youtube.com/watch?v=gone","time":"2024-03-05T12:00:00.000Z"},
      {"header":"YouTube","title":"Watched Some Ad","titleUrl":"https://www.youtube.com/watch?v=ad1","time":"2024-03-05T13:00:00.000Z","details":[{"name":"From Google Ads"}]},
      {"header":"Chrome","title":"Visited Example","titleUrl":"https://example.com/","time":"2024-03-05T14:00:00.000Z"},
      {"header":"Maps","title":"Used Maps","time":"2024-03-05T15:00:00.000Z"}]"""

    @Test fun importsWatchSearchAndVisitAndSkipsAdsAndUnknownEntries() {
        val r = GoogleActivityParser.parse(json, 3L, NOW)
        assertEquals(4, r.drafts.size)
        assertEquals(2, r.skipped)
        assertTrue(r.notes.any { it.contains("1 ad") })

        val watch = r.drafts[0]
        assertEquals("Kotlin in 100 seconds", watch.title)
        assertEquals("Fireship", watch.creator)
        assertEquals(1709633730000L, watch.eventTime)
        assertEquals(CollectionMethod.GOOGLE_IMPORT, watch.collectionMethod)

        val search = r.drafts[1]
        assertEquals("lofi beats", search.searchQuery)
        assertEquals("youtube_search", search.engineSlug)

        assertEquals("", r.drafts[2].title) // deleted video: title unknown, not invented
        assertEquals("chrome", r.drafts[3].browserSlug)
    }
}

class SessionBuilderTest {
    private fun fg(t: Long, p: String = "a") = RawUsageEvent(t, p, RawUsageEvent.Type.FOREGROUND)
    private fun bg(t: Long, p: String = "a") = RawUsageEvent(t, p, RawUsageEvent.Type.BACKGROUND)

    @Test fun mergesFragmentsDropsShortAndUnwatchedAndOpenSessions() {
        val events = listOf(
            fg(0), bg(10_000), fg(12_000), bg(30_000), // one visit split by activity switch -> 0..30s
            fg(100_000), bg(101_000), // 1 s: too short
            fg(0, "other"), bg(60_000, "other"), // not watched
            fg(200_000), // still open: not recorded yet
        )
        val s = SessionBuilder.build(events, setOf("a"))
        assertEquals(1, s.size)
        assertEquals(0L, s[0].start)
        assertEquals(30_000L, s[0].end)
        assertEquals(30_000L, s[0].durationMs)
    }

    @Test fun runawaySessionsAreCapped() {
        val s = SessionBuilder.build(listOf(fg(0), bg(10 * 3_600_000L)), setOf("a"))
        assertEquals(6 * 3_600_000L, s.single().durationMs)
    }

    @Test fun unsortedInputIsHandled() {
        val s = SessionBuilder.build(listOf(bg(20_000), fg(0)), setOf("a"))
        assertEquals(20_000L, s.single().durationMs)
    }
}

class SearchQueryBuilderTest {
    @Test fun everyValueIsBoundAndPlaceholdersMatchArguments() {
        val q = SearchQueryBuilder.build("kotlin coroutines", SearchFilters(platform = "youtube", types = setOf(ContentType.VIDEO, ContentType.SHORT)), 50)
        assertEquals(q.args.size, q.sql.count { it == '?' })
        assertEquals("%kotlin%", q.args.first())
        assertEquals(50, q.args.last())
        assertTrue(q.sql.contains("p.slug = ?"))
        assertTrue(q.sql.contains("c.contentType IN (?,?)"))
        assertTrue(q.sql.trimEnd().endsWith("LIMIT ?"))
    }

    @Test fun wildcardsInUserInputAreEscaped() {
        val q = SearchQueryBuilder.build("100%_off", SearchFilters(), 10)
        assertEquals("%100\\%\\_off%", q.args.first())
    }

    @Test fun hostileInputNeverReachesSqlText() {
        val q = SearchQueryBuilder.build("x'; DROP TABLE content_items;--", SearchFilters(), 10)
        assertFalse(q.sql.contains("DROP"))
        assertEquals(q.args.size, q.sql.count { it == '?' })
    }

    @Test fun emptyQueryWithNoFiltersIsJustNewestFirst() {
        val q = SearchQueryBuilder.build("", SearchFilters(sort = SearchSort.NEWEST), 10)
        assertFalse(q.sql.contains("WHERE"))
        assertTrue(q.sql.contains("ORDER BY c.lastSeen DESC LIMIT ?"))
        assertEquals(listOf<Any?>(10), q.args)
    }

    @Test fun eventWindowUsesTheEventsView() {
        val q = SearchQueryBuilder.build("", SearchFilters(from = 100L, to = 200L), 5)
        assertTrue(q.sql.contains("activity_events"))
        assertTrue(q.args.containsAll(listOf<Any?>(100L, 200L)))
    }
}

class BackupCryptoTest {
    @Test fun roundTripAndWrongPassphrase() {
        val plain = "{\"format\":\"traceon-backup\"}".toByteArray()
        val enc = BackupCrypto.encrypt(plain, "correct horse")
        assertTrue(BackupCrypto.isEncrypted(enc))
        assertFalse(BackupCrypto.isEncrypted(plain))
        assertArrayEquals(plain, BackupCrypto.decrypt(enc, "correct horse"))
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(enc, "wrong") }
    }

    @Test fun eachEncryptionUsesFreshSaltAndIv() {
        val plain = "same".toByteArray()
        assertNotEquals(BackupCrypto.encrypt(plain, "p").toList(), BackupCrypto.encrypt(plain, "p").toList())
    }

    @Test fun tamperedCiphertextIsRejected() {
        val enc = BackupCrypto.encrypt("data".toByteArray(), "p")
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 0x01).toByte()
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(enc, "p") }
    }
}
