package com.traceon.core.common

import com.traceon.domain.model.CollectionMethod
import com.traceon.domain.model.ContentDraft
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.DataStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

private fun draft(
    title: String = "T", url: String? = null, origin: DataStatus = DataStatus.IMPORTED, type: ContentType? = null,
    query: String? = null, engine: String? = null, platform: String? = null,
) = ContentDraft(
    title = title, url = url, contentType = type, platformSlug = platform, eventTime = 1L,
    collectionMethod = CollectionMethod.BROWSER_IMPORT, origin = origin, searchQuery = query, engineSlug = engine,
)

class ContentNormalizerTest {
    @Test fun platformUrlGetsContentIdKeyAndExactStatus() {
        val n = ContentNormalizer.normalize(draft("Some video", "https://youtu.be/abc", DataStatus.OBSERVED))
        assertEquals("youtube", n.platformSlug)
        assertEquals(ContentType.VIDEO, n.contentType)
        assertEquals("youtube:abc", n.dedupKey)
        assertEquals(DataStatus.OBSERVED, n.status)
        assertEquals("abc", n.platformContentId)
    }

    @Test fun trackingVariantsOfTheSameUrlShareOneKey() {
        val a = ContentNormalizer.normalize(draft("A", "https://www.example.com/a?utm_source=x"))
        val b = ContentNormalizer.normalize(draft("A (other title)", "http://example.com/a/"))
        assertEquals(a.dedupKey, b.dedupKey)
        assertEquals("url:https://example.com/a", a.dedupKey)
    }

    @Test fun similarTitlesWithoutAUrlAreNeverMergedAutomatically() {
        val a = ContentNormalizer.normalize(draft("My Note", origin = DataStatus.MANUAL)) { "id-1" }
        val b = ContentNormalizer.normalize(draft("My Note", origin = DataStatus.MANUAL)) { "id-2" }
        assertNotEquals(a.dedupKey, b.dedupKey)
        assertEquals(DataStatus.MANUAL, a.status)
    }

    @Test fun genericPageTypeIsMarkedInferred() {
        val n = ContentNormalizer.normalize(draft("Post", "https://blog.example.org/post"))
        assertEquals(ContentType.BLOG, n.contentType)
        assertEquals("web", n.platformSlug)
        assertEquals(DataStatus.INFERRED, n.status)
    }

    @Test fun missingTitleIsReportedAsUnavailableNotInvented() {
        val n = ContentNormalizer.normalize(draft("", "https://example.com/x"))
        assertEquals(DataStatus.UNAVAILABLE, n.status)
    }

    @Test fun searchQueriesKeyByEngineAndNormalizedText() {
        val n = ContentNormalizer.normalize(draft("q", query = "Kotlin  Coroutines", engine = "bing"))
        assertEquals(ContentType.SEARCH_QUERY, n.contentType)
        assertEquals("search:bing:kotlin coroutines", n.dedupKey)
        assertEquals("bing", n.platformSlug)
    }

    @Test fun metadataConfidenceStaysInRange() {
        val n = ContentNormalizer.normalize(draft("Title", "https://youtu.be/abc"))
        assertTrue(n.metadataConfidence in 0f..1f)
    }
}

class TextUtilsTest {
    @Test fun tokensKeepCombiningMarksSoIndicWordsStayWhole() {
        assertEquals(listOf("hello", "world", "\u0928\u092E\u0938\u094D\u0924\u0947"), TextUtils.tokens("Hello, WORLD! \u0928\u092E\u0938\u094D\u0924\u0947"))
    }

    @Test fun keywordsAreWeightedAndDeterministic() {
        assertEquals(
            listOf("kotlin", "coroutines", "tutorial"),
            TextUtils.keywords(listOf("Kotlin coroutines tutorial" to 3, "kotlin flow" to 1), 3),
        )
    }

    @Test fun jaccard() {
        assertEquals(0.5, TextUtils.jaccard(setOf("a", "b", "c"), setOf("b", "c", "d")), 1e-9)
        assertEquals(0.0, TextUtils.jaccard(emptySet(), setOf("a")), 1e-9)
    }

    @Test fun likeEscapeNeutralisesWildcards() {
        assertEquals("50\\%\\_off\\\\", TextUtils.likeEscape("50%_off\\"))
    }
}

class SharedContentParserTest {
    @Test fun splitsLinkTitleAndNote() {
        val s = SharedContentParser.parse("Check this https://youtu.be/abc123?si=x", "Cool video")
        assertEquals("https://youtu.be/abc123?si=x", s.url)
        assertEquals("Cool video", s.title)
        assertEquals("Check this", s.note)
    }

    @Test fun urlOnlyShareHasNoInventedTitle() {
        val s = SharedContentParser.parse("https://example.com/page", null)
        assertEquals("https://example.com/page", s.url)
        assertEquals("", s.title)
    }

    @Test fun firstLineBecomesTitleWhenNoSubject() {
        val s = SharedContentParser.parse("Great article\nhttps://example.com/a", null)
        assertEquals("Great article", s.title)
        assertEquals("", s.note)
    }

    @Test fun nothingSharedIsEmpty() {
        val s = SharedContentParser.parse(null, null)
        assertNull(s.url)
        assertFalse(s.title.isNotEmpty())
    }
}

class TimeUtilsTest {
    private val utc = ZoneId.of("UTC")
    private val now = 1795521600000L // 2026-11-24T12:00:00Z (a Tuesday)

    @Test fun durationFormattingNeverShowsZeroForUnmeasurable() {
        assertEquals("n/a", TimeUtils.formatDuration(null))
        assertEquals("<1m", TimeUtils.formatDuration(30_000))
        assertEquals("5m", TimeUtils.formatDuration(300_000))
        assertEquals("1h 2m", TimeUtils.formatDuration(3_725_000))
    }

    @Test fun presetRanges() {
        assertEquals(1794960000000L to 1795564799999L, TimeUtils.presetRange(RangePreset.LAST_7, now, utc))
        assertEquals(1795392000000L to 1795478399999L, TimeUtils.presetRange(RangePreset.YESTERDAY, now, utc))
    }

    @Test fun pickerRangeUsesWholeLocalDays() {
        assertEquals(1793577600000L to 1793836799999L, TimeUtils.pickerRange(1793577600000L, 1793750400000L, utc))
    }
}
