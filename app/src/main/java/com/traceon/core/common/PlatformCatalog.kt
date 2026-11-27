package com.traceon.core.common

import com.traceon.domain.model.PlatformKind

data class PlatformDef(
    val slug: String,
    val name: String,
    val kind: PlatformKind,
    val packages: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
)

object PlatformCatalog {
    val all: List<PlatformDef> = listOf(
        PlatformDef("youtube", "YouTube", PlatformKind.SOCIAL, listOf("com.google.android.youtube"), listOf("youtube.com", "youtu.be")),
        PlatformDef("instagram", "Instagram", PlatformKind.SOCIAL, listOf("com.instagram.android"), listOf("instagram.com")),
        PlatformDef("facebook", "Facebook", PlatformKind.SOCIAL, listOf("com.facebook.katana", "com.facebook.lite"), listOf("facebook.com", "fb.watch", "fb.com")),
        PlatformDef("snapchat", "Snapchat", PlatformKind.SOCIAL, listOf("com.snapchat.android"), listOf("snapchat.com")),
        PlatformDef("moj", "Moj", PlatformKind.SOCIAL, listOf("in.mohalla.video"), listOf("mojapp.in")),
        PlatformDef("josh", "Josh", PlatformKind.SOCIAL, listOf("com.eterno.shortvideos"), listOf("myjosh.in")),
        PlatformDef("chrome", "Google Chrome", PlatformKind.BROWSER, listOf("com.android.chrome")),
        PlatformDef("brave", "Brave", PlatformKind.BROWSER, listOf("com.brave.browser")),
        PlatformDef("firefox", "Firefox", PlatformKind.BROWSER, listOf("org.mozilla.firefox")),
        PlatformDef("edge", "Microsoft Edge", PlatformKind.BROWSER, listOf("com.microsoft.emmx")),
        PlatformDef("opera", "Opera", PlatformKind.BROWSER, listOf("com.opera.browser")),
        PlatformDef("samsung_internet", "Samsung Internet", PlatformKind.BROWSER, listOf("com.sec.android.app.sbrowser")),
        PlatformDef("duckduckgo_browser", "DuckDuckGo Browser", PlatformKind.BROWSER, listOf("com.duckduckgo.mobile.android")),
        PlatformDef("traceon_browser", "TRACEON Browser", PlatformKind.BROWSER),
        PlatformDef("other_browser", "Other browser", PlatformKind.BROWSER),
        PlatformDef("google_search", "Google Search", PlatformKind.SEARCH_ENGINE),
        PlatformDef("bing", "Bing", PlatformKind.SEARCH_ENGINE),
        PlatformDef("duckduckgo_search", "DuckDuckGo Search", PlatformKind.SEARCH_ENGINE),
        PlatformDef("brave_search", "Brave Search", PlatformKind.SEARCH_ENGINE),
        PlatformDef("yahoo_search", "Yahoo Search", PlatformKind.SEARCH_ENGINE),
        PlatformDef("youtube_search", "YouTube Search", PlatformKind.SEARCH_ENGINE),
        PlatformDef("web", "Web", PlatformKind.WEB),
        PlatformDef("other", "Other", PlatformKind.OTHER),
    )

    private val bySlug = all.associateBy { it.slug }
    private val byPackage = all.flatMap { d -> d.packages.map { it to d } }.toMap()

    val browsers: List<PlatformDef> = all.filter { it.kind == PlatformKind.BROWSER }
    val trackableApps: List<PlatformDef> = all.filter { it.packages.isNotEmpty() }

    fun bySlug(slug: String?): PlatformDef? = slug?.let { bySlug[it] }
    fun byPackage(pkg: String): PlatformDef? = byPackage[pkg]
    fun nameOf(slug: String?): String = bySlug(slug)?.name ?: slug ?: "Unknown"

    /** Graph level 1: social platforms stand alone; the rest collapse into three groups. */
    fun groupOf(slug: String?): Pair<String, String> {
        val d = bySlug(slug)
        return when (d?.kind) {
            PlatformKind.SOCIAL -> d.slug to d.name
            PlatformKind.BROWSER, PlatformKind.WEB -> "browsers" to "Browsers & Web"
            PlatformKind.SEARCH_ENGINE -> "search" to "Search Engines"
            else -> "other" to "Other Applications"
        }
    }
}
