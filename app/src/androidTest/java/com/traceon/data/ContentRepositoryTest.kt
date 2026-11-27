package com.traceon.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.traceon.core.common.Clock
import com.traceon.data.backup.BackupRepositoryImpl
import com.traceon.data.local.TraceonDatabase
import com.traceon.data.repository.AnalyticsRepositoryImpl
import com.traceon.data.repository.ContentRepositoryImpl
import com.traceon.data.repository.ImportRepositoryImpl
import com.traceon.domain.classification.ContentClassifier
import com.traceon.domain.model.CollectionMethod
import com.traceon.domain.model.ContentDraft
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.DataStatus
import com.traceon.domain.model.ImportSource
import com.traceon.domain.model.RelationType
import com.traceon.domain.model.SearchFilters
import com.traceon.domain.model.TimelineFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentRepositoryTest {
    private lateinit var db: TraceonDatabase
    private lateinit var repo: ContentRepositoryImpl
    private val now = 1_800_000_000_000L
    private val clock = Clock { now }

    private fun newDb(): TraceonDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TraceonDatabase::class.java).allowMainThreadQueries().build()

    @Before fun setUp() {
        db = newDb()
        repo = ContentRepositoryImpl(db, ContentClassifier(), clock)
    }

    @After fun tearDown() { db.close() }

    private fun page(url: String, t: Long, title: String = "Some page", browser: String? = "chrome") = ContentDraft(
        title = title, url = url, eventTime = t, collectionMethod = CollectionMethod.BROWSER_IMPORT, origin = DataStatus.IMPORTED, browserSlug = browser,
    )

    @Test fun sameUrlIsOneItemWithTwoEvents() = runBlocking {
        val a = repo.save(page("https://www.example.com/a?utm_source=x", 1_000))
        val b = repo.save(page("http://example.com/a/", 2_000))
        assertTrue(a.created)
        assertFalse(b.created)
        assertEquals(a.contentId, b.contentId)
        val item = repo.observeItem(a.contentId).first()!!
        assertEquals(2, item.sessionCount)
        assertEquals(1_000L, item.firstSeen)
        assertEquals(2_000L, item.lastSeen)
    }

    @Test fun identicalEventIsIgnoredSoReimportsNeverDoubleCount() = runBlocking {
        val first = repo.save(page("https://example.com/x", 5_000))
        val again = repo.save(page("https://example.com/x", 5_000))
        assertTrue(again.duplicateEvent)
        assertEquals(1, repo.observeItem(first.contentId).first()!!.sessionCount)
    }

    @Test fun searchFindsByTitleDomainAndTag() = runBlocking {
        val r = repo.save(page("https://docs.example.org/guide", 1_000, "Gradle build guide").copy(tags = listOf("Build")))
        assertEquals(listOf(r.contentId), repo.search("gradle", SearchFilters()).map { it.id })
        assertEquals(listOf(r.contentId), repo.search("docs.example.org", SearchFilters()).map { it.id })
        assertEquals(listOf(r.contentId), repo.search("build", SearchFilters(tag = "build")).map { it.id })
        assertTrue(repo.search("zzzz-nothing", SearchFilters()).isEmpty())
    }

    @Test fun classificationIsStoredAndUserCategorySurvivesReclassification() = runBlocking {
        val r = repo.save(page("https://example.com/k", 1_000, "Kotlin coroutines tutorial for Android"))
        assertEquals("Android", repo.observeItem(r.contentId).first()!!.category)
        repo.setCategory(r.contentId, "My Custom")
        repo.reclassifyAll()
        val kept = repo.observeItem(r.contentId).first()!!
        assertEquals("My Custom", kept.category)
        assertTrue(kept.categoryLocked)
        repo.setCategory(r.contentId, null)
        assertEquals("Android", repo.observeItem(r.contentId).first()!!.category)
    }

    @Test fun itemWithNoEvidenceStaysUnclassified() = runBlocking {
        val r = repo.save(page("https://example.com/q", 1_000, "Zxqv blorp"))
        assertNull(repo.observeItem(r.contentId).first()!!.category)
    }

    @Test fun deleteRemovesTheItemAndItsEvents() = runBlocking {
        val r = repo.save(page("https://example.com/del", 1_000))
        repo.delete(r.contentId)
        assertNull(repo.observeItem(r.contentId).first())
        assertTrue(repo.observeEvents(r.contentId).first().isEmpty())
        assertTrue(repo.observeTimeline(TimelineFilter(0, Long.MAX_VALUE), 10).first().isEmpty())
    }

    @Test fun timelineIsNewestFirstAndFilteredByRange() = runBlocking {
        repo.save(page("https://example.com/old", 1_000, "Old"))
        repo.save(page("https://example.com/new", 9_000, "New"))
        val all = repo.observeTimeline(TimelineFilter(0, Long.MAX_VALUE), 10).first()
        assertEquals(listOf("New", "Old"), all.map { it.item.title })
        val recent = repo.observeTimeline(TimelineFilter(5_000, Long.MAX_VALUE), 10).first()
        assertEquals(listOf("New"), recent.map { it.item.title })
    }

    @Test fun sameCreatorItemsAreRelated() = runBlocking {
        fun video(id: String, title: String) = ContentDraft(
            title = title, url = "https://www.youtube.com/watch?v=$id", creator = "Channel A", eventTime = 1_000,
            collectionMethod = CollectionMethod.GOOGLE_IMPORT, origin = DataStatus.IMPORTED,
        )
        val a = repo.save(video("aaa", "Kotlin basics"))
        val b = repo.save(video("bbb", "Cooking pasta"))
        repo.rebuildRelations(listOf(a.contentId, b.contentId))
        val related = repo.observeRelated(a.contentId).first()
        assertEquals(listOf(b.contentId), related.map { it.item.id })
        assertEquals(RelationType.SAME_CREATOR, related.single().type)
    }

    @Test fun userLinksAreKeptAndRemovable() = runBlocking {
        val a = repo.save(page("https://example.com/1", 1_000, "First"))
        val b = repo.save(page("https://example.com/2", 1_000, "Second"))
        repo.linkItems(a.contentId, b.contentId)
        assertEquals(RelationType.USER, repo.observeRelated(a.contentId).first().single().type)
        repo.unlinkItems(a.contentId, b.contentId)
        assertTrue(repo.observeRelated(a.contentId).first().isEmpty())
    }

    @Test fun importIsIdempotent() = runBlocking {
        val importer = ImportRepositoryImpl(db, repo, clock)
        val json = """[
          {"header":"YouTube","title":"Watched Kotlin in 100 seconds","titleUrl":"https://www.youtube.com/watch?v=abc123","time":"2024-03-05T10:15:30.000Z"},
          {"header":"YouTube","title":"Searched for lofi beats","titleUrl":"https://www.youtube.com/results?search_query=lofi+beats","time":"2024-03-05T11:00:00.000Z"},
          {"header":"Chrome","title":"Visited Example","titleUrl":"https://example.com/","time":"2024-03-05T14:00:00.000Z"}]"""
        val first = importer.importFile(ImportSource.GOOGLE_ACTIVITY, "activity.json", json, "chrome")
        assertEquals(3, first.imported)
        assertEquals(0, first.duplicates)
        val second = importer.importFile(ImportSource.GOOGLE_ACTIVITY, "activity.json", json, "chrome")
        assertEquals(0, second.imported)
        assertEquals(3, second.duplicates)
        assertEquals(3, AnalyticsRepositoryImpl(db.analyticsDao(), db.contentDao(), clock).totalItems().first())
    }

    @Test fun statisticsComeFromRealRows() = runBlocking {
        val analytics = AnalyticsRepositoryImpl(db.analyticsDao(), db.contentDao(), clock)
        assertEquals(0, analytics.totalItems().first())
        assertNull(analytics.viewingMs().first()) // nothing measurable is "n/a", not zero
        repo.save(page("https://example.com/p", 1_000))
        repo.save(
            ContentDraft(
                title = "Clip", url = "https://www.youtube.com/shorts/s1", eventTime = 2_000, durationMs = 45_000,
                collectionMethod = CollectionMethod.GOOGLE_IMPORT, origin = DataStatus.IMPORTED,
            ),
        )
        assertEquals(2, analytics.totalItems().first())
        assertEquals(45_000L, analytics.viewingMs().first())
        val byType = analytics.itemsByType().first().associate { it.label to it.value }
        assertEquals(1L, byType[ContentType.SHORT.name])
    }

    @Test fun backupRoundTripAndEncryptedBackupNeedsThePassphrase() = runBlocking {
        repo.save(page("https://example.com/keep", 3_000, "Keep me").copy(tags = listOf("later"), notes = "my note"))
        val backup = BackupRepositoryImpl(db, repo, clock)
        val plain = backup.export(null)
        val encrypted = backup.export("s3cret")
        assertFalse(backup.isEncrypted(plain))
        assertTrue(backup.isEncrypted(encrypted))

        val db2 = newDb()
        try {
            val repo2 = ContentRepositoryImpl(db2, ContentClassifier(), clock)
            val backup2 = BackupRepositoryImpl(db2, repo2, clock)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { backup2.restore(encrypted, "wrong") } }
            val summary = backup2.restore(encrypted, "s3cret")
            assertEquals(1, summary.newItems)
            val restored = repo2.search("keep", SearchFilters()).single()
            assertEquals("my note", restored.notes)
            assertEquals(listOf("later"), repo2.observeTags(restored.id).first())
            assertEquals(1, restored.sessionCount)
            // restoring again merges instead of duplicating
            assertEquals(0, backup2.restore(plain, null).newItems)
        } finally {
            db2.close()
        }
    }
}
