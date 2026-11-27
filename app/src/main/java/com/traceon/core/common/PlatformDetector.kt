package com.traceon.core.common

import com.traceon.domain.model.ContentType

data class PlatformMatch(
    val slug: String,
    val contentId: String?,
    val type: ContentType,
    val canonicalUrl: String?,
    val confidence: Float,
)

object PlatformDetector {
    fun detect(url: String): PlatformMatch? {
        val p = UrlUtils.parse(url) ?: return null
        val host = p.host.removePrefix("m.")
        val segs = p.path.split('/').filter { it.isNotEmpty() }
        fun on(d: String) = host == d || host.endsWith(".$d")
        return when {
            host == "youtu.be" -> segs.firstOrNull()?.let {
                PlatformMatch("youtube", it, ContentType.VIDEO, "https://youtube.com/watch?v=$it", 0.95f)
            }
            on("youtube.com") -> youtube(p, segs)
            on("instagram.com") -> instagram(segs)
            host == "fb.watch" -> segs.firstOrNull()?.let { PlatformMatch("facebook", it, ContentType.VIDEO, "https://fb.watch/$it", 0.9f) }
            on("facebook.com") -> facebook(p, segs)
            on("snapchat.com") -> if (segs.size >= 2 && segs[0] == "spotlight")
                PlatformMatch("snapchat", segs[1], ContentType.SHORT, "https://snapchat.com/spotlight/${segs[1]}", 0.9f)
            else PlatformMatch("snapchat", null, ContentType.WEBSITE, null, 0.5f)
            on("mojapp.in") -> segs.lastOrNull()?.let { PlatformMatch("moj", it, ContentType.SHORT, null, 0.6f) }
                ?: PlatformMatch("moj", null, ContentType.WEBSITE, null, 0.5f)
            on("myjosh.in") -> segs.lastOrNull()?.let { PlatformMatch("josh", it, ContentType.SHORT, null, 0.6f) }
                ?: PlatformMatch("josh", null, ContentType.WEBSITE, null, 0.5f)
            else -> null
        }
    }

    private fun youtube(p: ParsedUrl, segs: List<String>): PlatformMatch {
        val first = segs.firstOrNull()
        return when {
            first == "shorts" && segs.size >= 2 ->
                PlatformMatch("youtube", segs[1], ContentType.SHORT, "https://youtube.com/shorts/${segs[1]}", 0.95f)
            first == "watch" -> p.param("v")?.let {
                PlatformMatch("youtube", it, ContentType.VIDEO, "https://youtube.com/watch?v=$it", 0.95f)
            } ?: PlatformMatch("youtube", null, ContentType.WEBSITE, null, 0.5f)
            (first == "live" || first == "embed") && segs.size >= 2 ->
                PlatformMatch("youtube", segs[1], ContentType.VIDEO, "https://youtube.com/watch?v=${segs[1]}", 0.85f)
            else -> PlatformMatch("youtube", null, ContentType.WEBSITE, null, 0.5f)
        }
    }

    private fun instagram(segs: List<String>): PlatformMatch {
        val first = segs.firstOrNull()
        return when {
            (first == "reel" || first == "reels") && segs.size >= 2 ->
                PlatformMatch("instagram", segs[1], ContentType.REEL, "https://instagram.com/reel/${segs[1]}", 0.95f)
            first == "p" && segs.size >= 2 ->
                PlatformMatch("instagram", segs[1], ContentType.POST, "https://instagram.com/p/${segs[1]}", 0.9f)
            first == "tv" && segs.size >= 2 ->
                PlatformMatch("instagram", segs[1], ContentType.VIDEO, "https://instagram.com/tv/${segs[1]}", 0.85f)
            else -> PlatformMatch("instagram", null, ContentType.WEBSITE, null, 0.5f)
        }
    }

    private fun facebook(p: ParsedUrl, segs: List<String>): PlatformMatch {
        val vi = segs.indexOf("videos")
        return when {
            segs.firstOrNull() == "reel" && segs.size >= 2 ->
                PlatformMatch("facebook", segs[1], ContentType.REEL, "https://facebook.com/reel/${segs[1]}", 0.9f)
            segs.firstOrNull() == "watch" && p.param("v") != null ->
                PlatformMatch("facebook", p.param("v"), ContentType.VIDEO, "https://facebook.com/watch/?v=${p.param("v")}", 0.9f)
            vi >= 0 && segs.size > vi + 1 ->
                PlatformMatch("facebook", segs[vi + 1], ContentType.VIDEO, "https://facebook.com/videos/${segs[vi + 1]}", 0.85f)
            else -> PlatformMatch("facebook", null, ContentType.WEBSITE, null, 0.5f)
        }
    }
}

data class SearchMatch(val engineSlug: String, val query: String)

object SearchEngines {
    private val GOOGLE_HOST = Regex("""^google\.[a-z]{2,3}(\.[a-z]{2})?$""")

    fun match(url: String): SearchMatch? {
        val p = UrlUtils.parse(url) ?: return null
        val host = p.host
        val path = p.path.trimEnd('/')
        val (slug, raw) = when {
            GOOGLE_HOST.matches(host) && path == "/search" -> "google_search" to p.param("q")
            (host == "bing.com" || host.endsWith(".bing.com")) && path == "/search" -> "bing" to p.param("q")
            host == "duckduckgo.com" && (path.isEmpty() || path == "/html") -> "duckduckgo_search" to p.param("q")
            host == "search.brave.com" && path == "/search" -> "brave_search" to p.param("q")
            host.endsWith("search.yahoo.com") && path == "/search" -> "yahoo_search" to p.param("p")
            (host == "youtube.com" || host.endsWith(".youtube.com")) && path == "/results" -> "youtube_search" to p.param("search_query")
            else -> return null
        }
        val query = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return SearchMatch(slug, query)
    }
}

/** Domain/path heuristics for generic web pages. Always low-to-medium confidence: the type is a guess. */
object WebHeuristics {
    private val COURSE = setOf("coursera.org", "udemy.com", "edx.org", "khanacademy.org", "udacity.com", "skillshare.com", "nptel.ac.in")
    private val TUTORIAL = setOf("w3schools.com", "geeksforgeeks.org", "freecodecamp.org", "tutorialspoint.com", "realpython.com", "baeldung.com", "javatpoint.com")
    private val BLOG = setOf("medium.com", "substack.com", "dev.to", "hashnode.dev", "blogspot.com", "wordpress.com")
    private val NEWS = setOf("bbc.com", "bbc.co.uk", "cnn.com", "nytimes.com", "reuters.com", "theguardian.com", "ndtv.com", "thehindu.com", "hindustantimes.com", "indiatimes.com")
    private val DOCS = setOf("developer.android.com", "kotlinlang.org", "docs.python.org", "learn.microsoft.com", "developer.mozilla.org")

    fun classify(host: String, path: String): Pair<ContentType, Float> {
        val lp = path.lowercase()
        fun on(set: Set<String>) = set.any { host == it || host.endsWith(".$it") }
        return when {
            lp.endsWith(".pdf") -> ContentType.DOCUMENT to 0.8f
            on(DOCS) || host.startsWith("docs.") || host.startsWith("developer.") || lp.contains("/docs/") || lp.contains("/documentation") -> ContentType.DOCUMENTATION to 0.6f
            on(COURSE) -> ContentType.COURSE to 0.7f
            on(TUTORIAL) || lp.contains("tutorial") -> ContentType.TUTORIAL to 0.65f
            on(BLOG) || host.startsWith("blog.") || lp.contains("/blog") -> ContentType.BLOG to 0.6f
            on(NEWS) || lp.contains("/news/") || lp.contains("/article") -> ContentType.ARTICLE to 0.55f
            lp.isEmpty() || lp == "/" -> ContentType.WEBSITE to 0.6f
            else -> ContentType.WEBSITE to 0.4f
        }
    }
}
