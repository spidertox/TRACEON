package com.traceon.domain

import com.traceon.domain.model.ContentType
import com.traceon.domain.usecase.DiscoveryMode
import com.traceon.domain.usecase.DiscoveryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DiscoveryParserTest {
    private val utc = ZoneId.of("UTC")
    private val now = 1795521600000L // Tuesday 2026-11-24T12:00:00Z

    private fun parse(q: String) = DiscoveryParser.parse(q, now, utc)

    @Test fun lastSundayIsTheMostRecentPastSunday() {
        val q = parse("What did I watch last Sunday?")
        assertEquals(1795305600000L, q.from)
        assertEquals(1795391999999L, q.to)
        assertEquals(DiscoveryMode.LIST, q.mode)
        assertNull(q.topic)
        assertTrue(q.terms.isEmpty())
    }

    @Test fun yesterdayLastWeekLastMonthAndLastNDays() {
        assertEquals(1795392000000L to 1795478399999L, parse("What did I read yesterday").let { it.from to it.to })
        assertEquals(1794787200000L to 1795391999999L, parse("Show videos from last week").let { it.from to it.to })
        assertEquals(1790812800000L to 1793491199999L, parse("What did I search last month").let { it.from to it.to })
        val n = parse("What did I read in the last 7 days")
        assertEquals(1794960000000L to 1795564799999L, n.from to n.to)
        assertEquals("last 7 days", n.timeLabel)
    }

    @Test fun noTimeWordMeansNoTimeFilter() {
        val q = parse("Show all content related to machine learning")
        assertNull(q.from)
        assertNull(q.to)
        assertEquals("Machine Learning", q.topic)
    }

    @Test fun topicQuestionsMapToTheTaxonomy() {
        assertEquals("Artificial Intelligence", parse("What AI topics did I explore?").topic)
        assertEquals("Programming", parse("Which programming tutorials did I watch?").topic)
        assertEquals("Android", parse("What websites did I visit about Android?").topic)
    }

    @Test fun modes() {
        assertEquals(DiscoveryMode.TOP_TOPICS, parse("Which topics have I explored most?").mode)
        assertEquals(DiscoveryMode.CONNECTIONS, parse("Show connections between my searches and videos").mode)
        assertEquals(DiscoveryMode.COUNT, parse("How many YouTube videos did I watch yesterday?").mode)
    }

    @Test fun platformAndTypeAreExtracted() {
        val q = parse("How many YouTube videos did I watch yesterday?")
        assertEquals("youtube", q.platform)
        assertEquals(setOf(ContentType.VIDEO, ContentType.SHORT, ContentType.REEL), q.types)
        assertNotNull(q.from)
    }

    @Test fun withoutATopicRemainingKeywordsBecomeSearchTerms() {
        val q = parse("Find my notes about zebra")
        assertNull(q.topic)
        assertEquals(listOf("notes", "zebra"), q.terms)
    }

    @Test fun interpretationIsHumanReadable() {
        assertTrue(parse("Show all content related to machine learning").describe().contains("topic: Machine Learning"))
    }
}
