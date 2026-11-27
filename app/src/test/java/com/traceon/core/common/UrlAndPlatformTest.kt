package com.traceon.core.common

import com.traceon.domain.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {
    @Test fun extractsFirstUrlAndTrimsTrailingPunctuation() {
        assertEquals("https://youtu.be/abc123?si=xyz", UrlUtils.extractFirstUrl("Check this out https://youtu.be/abc123?si=xyz. Great!"))
        assertNull(UrlUtils.extractFirstUrl("no link here"))
    }

    @Test fun parsesHostPathAndDecodesParams() {
        val p = UrlUtils.parse("https://www.Example.com/a/b?x=1&y=two%20words#frag")!!
        assertEquals("example.com", p.host)
        assertEquals("/a/b", p.path)
        assertEquals("1", p.param("x"))
        assertEquals("two words", p.param("y"))
    }

    @Test fun rejectsNonWebAndHostlessUrls() {
        assertNull(UrlUtils.parse("ftp://example.com"))
        assertNull(UrlUtils.parse("chrome://settings"))
        assertNull(UrlUtils.parse("https://intranet/"))
        assertNotNull(UrlUtils.parse("https://localhost:8080/x"))
    }

    @Test fun canonicalizeDropsTrackingFragmentWwwAndSortsQuery() {
        assertEquals(
            "https://example.com/path?a=1&b=2",
            UrlUtils.canonicalize("http://www.example.com/path/?utm_source=a&b=2&a=1&fbclid=zzz#top"),
        )
        assertEquals("https://example.com", UrlUtils.canonicalize("https://example.com/"))
    }

    @Test fun sameCanonicalUrlForTrackingVariants() {
        assertEquals(
            UrlUtils.canonicalize("https://www.example.com/a?utm_medium=x"),
            UrlUtils.canonicalize("http://example.com/a/"),
        )
    }
}

class PlatformDetectorTest {
    @Test fun youtubeWatchShortLinkAndMobile() {
        val a = PlatformDetector.detect("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share")!!
        val b = PlatformDetector.detect("https://youtu.be/dQw4w9WgXcQ?si=abc")!!
        val c = PlatformDetector.detect("https://m.youtube.com/watch?v=dQw4w9WgXcQ")!!
        for (m in listOf(a, b, c)) {
            assertEquals("youtube", m.slug)
            assertEquals("dQw4w9WgXcQ", m.contentId)
            assertEquals(ContentType.VIDEO, m.type)
            assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", m.canonicalUrl)
        }
    }

    @Test fun youtubeShortsInstagramReelFacebookReel() {
        assertEquals(ContentType.SHORT, PlatformDetector.detect("https://www.youtube.com/shorts/abc123")!!.type)
        val reel = PlatformDetector.detect("https://www.instagram.com/reel/Cxyz123/?igsh=abc")!!
        assertEquals(ContentType.REEL, reel.type)
        assertEquals("Cxyz123", reel.contentId)
        assertEquals("https://instagram.com/reel/Cxyz123", reel.canonicalUrl)
        assertEquals(ContentType.REEL, PlatformDetector.detect("https://www.facebook.com/reel/12345")!!.type)
    }

    @Test fun unknownSitesAreNotAttributedToAPlatform() {
        assertNull(PlatformDetector.detect("https://example.com/"))
    }

    @Test fun profilePagesAreLowConfidence() {
        val m = PlatformDetector.detect("https://www.instagram.com/someone/")!!
        assertTrue(m.confidence < 0.6f)
        assertNull(m.contentId)
    }
}

class SearchEnginesTest {
    @Test fun recognisesSupportedEngines() {
        assertEquals(SearchMatch("google_search", "kotlin coroutines"), SearchEngines.match("https://www.google.com/search?q=kotlin+coroutines&oq=x"))
        assertEquals(SearchMatch("google_search", "test"), SearchEngines.match("https://www.google.co.in/search?q=test"))
        assertEquals(SearchMatch("bing", "jetpack compose"), SearchEngines.match("https://www.bing.com/search?q=jetpack%20compose"))
        assertEquals(SearchMatch("duckduckgo_search", "hello"), SearchEngines.match("https://duckduckgo.com/?q=hello"))
        assertEquals(SearchMatch("brave_search", "privacy"), SearchEngines.match("https://search.brave.com/search?q=privacy"))
        assertEquals(SearchMatch("yahoo_search", "android"), SearchEngines.match("https://search.yahoo.com/search?p=android"))
        assertEquals(SearchMatch("youtube_search", "lofi beats"), SearchEngines.match("https://www.youtube.com/results?search_query=lofi+beats"))
    }

    @Test fun ignoresNonSearchPagesAndEmptyQueries() {
        assertNull(SearchEngines.match("https://www.google.com/maps"))
        assertNull(SearchEngines.match("https://www.google.com/search?q="))
        assertNull(SearchEngines.match("https://example.com/search?q=x"))
    }
}
