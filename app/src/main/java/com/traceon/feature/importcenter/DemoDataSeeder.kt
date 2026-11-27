package com.traceon.feature.importcenter

import com.traceon.core.common.Clock
import com.traceon.core.common.TimeUtils
import com.traceon.domain.model.CollectionMethod
import com.traceon.domain.model.ContentDraft
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.DataStatus
import com.traceon.domain.repository.ContentRepository

/**
 * Sample data for exploring the UI. Every record is flagged isDemo, titled "[Demo] ...", uses reserved
 * example domains, is excluded from backups/exports, and is removable in one tap.
 */
class DemoDataSeeder(private val content: ContentRepository, private val clock: Clock) {
    private data class Seed(val platform: String?, val type: ContentType, val title: String, val creator: String?, val web: String? = null, val browser: String? = null)

    private val seeds = listOf(
        Seed("youtube", ContentType.VIDEO, "Kotlin coroutines explained for Android developers", "Demo Channel A"),
        Seed("youtube", ContentType.VIDEO, "Machine learning basics: gradient descent", "Demo Channel B"),
        Seed("youtube", ContentType.VIDEO, "Jetpack Compose animation tutorial part 1", "Demo Channel A"),
        Seed("youtube", ContentType.VIDEO, "Jetpack Compose animation tutorial part 2", "Demo Channel A"),
        Seed("youtube", ContentType.VIDEO, "How large language models work", "Demo Channel C"),
        Seed("youtube", ContentType.VIDEO, "Ethical hacking roadmap for beginners", "Demo Channel D"),
        Seed("youtube", ContentType.SHORT, "Funny cat compilation", "Demo Creator E"),
        Seed("youtube", ContentType.SHORT, "Python tip: list comprehension", "Demo Creator F"),
        Seed("youtube", ContentType.SHORT, "Quick calculus trick", "Demo Creator G"),
        Seed("instagram", ContentType.REEL, "Home workout in ten minutes", "demo_fit"),
        Seed("instagram", ContentType.REEL, "Street food travel vlog", "demo_travel"),
        Seed("instagram", ContentType.REEL, "Startup pitch deck tips", "demo_biz"),
        Seed("instagram", ContentType.REEL, "Relatable meme of the day", "demo_memes"),
        Seed("facebook", ContentType.VIDEO, "Mutual fund investing for beginners", "Demo Page H"),
        Seed("facebook", ContentType.REEL, "Guitar cover of a classic song", "Demo Page I"),
        Seed("snapchat", ContentType.SHORT, "Gaming highlight: clutch win", "demo_gamer"),
        Seed(null, ContentType.DOCUMENTATION, "Android Gradle plugin release notes", null, "https://docs.example.com/android/gradle", "chrome"),
        Seed(null, ContentType.ARTICLE, "Introduction to cybersecurity phishing defense", null, "https://news.example.net/security/phishing", "chrome"),
        Seed(null, ContentType.BLOG, "Web development with React and Vite", null, "https://blog.example.org/react-vite", "brave"),
        Seed(null, ContentType.TUTORIAL, "Python programming tutorial for data science", null, "https://learn.example.com/python-data", "chrome"),
        Seed(null, ContentType.ARTICLE, "Nutrition and healthy diet guide", null, "https://health.example.org/diet", "firefox"),
        Seed(null, ContentType.WEBSITE, "Startup founder community", null, "https://startups.example.net/", "chrome"),
        Seed(null, ContentType.ARTICLE, "Stock market news and nifty update", null, "https://finance.example.com/nifty", "edge"),
    )

    private val searches = listOf(
        "kotlin coroutines", "jetpack compose navigation", "machine learning roadmap", "react vite setup", "phishing examples",
        "gaming laptop under 80000", "python data science tutorial", "mutual fund sip calculator",
    )

    suspend fun seed(): Int {
        val now = clock.now()
        var count = 0
        val ids = ArrayList<Long>()
        seeds.forEachIndexed { i, s ->
            val repeats = if (i % 4 == 0) 2 else 1
            repeat(repeats) { r ->
                val t = now - ((i * 5 + r * 9) % 30) * TimeUtils.DAY_MS - ((i * 37 + r * 211) % 1200) * 60_000L
                ids += content.save(
                    ContentDraft(
                        title = "[Demo] ${s.title}", url = s.web, contentType = s.type, platformSlug = s.platform, creator = s.creator,
                        eventTime = t, durationMs = if (s.web == null) (60 + (i * 53) % 900) * 1_000L else null,
                        collectionMethod = CollectionMethod.DEMO, origin = DataStatus.SAMPLE, browserSlug = s.browser,
                        isDemo = true, dedupKeyOverride = "demo:content:$i",
                    ),
                ).contentId
                count++
            }
        }
        searches.forEachIndexed { i, q ->
            ids += content.save(
                ContentDraft(
                    title = q, searchQuery = q, engineSlug = if (i % 3 == 0) "bing" else "google_search",
                    eventTime = now - (i * 3 % 14) * TimeUtils.DAY_MS - (i * 97 % 900) * 60_000L,
                    collectionMethod = CollectionMethod.DEMO, origin = DataStatus.SAMPLE, browserSlug = "chrome",
                    isDemo = true, dedupKeyOverride = "demo:search:$i",
                ),
            ).contentId
            count++
        }
        content.rebuildRelations(ids)
        return count
    }
}
