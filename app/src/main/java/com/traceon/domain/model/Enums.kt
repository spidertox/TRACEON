package com.traceon.domain.model

enum class PlatformKind { SOCIAL, BROWSER, SEARCH_ENGINE, WEB, OTHER }

enum class ContentType(val label: String, val plural: String, val isReading: Boolean = false) {
    REEL("Reel", "Reels"),
    SHORT("Short", "Shorts"),
    VIDEO("Video", "Videos"),
    ARTICLE("Article", "Articles", true),
    BLOG("Blog", "Blogs", true),
    TUTORIAL("Tutorial", "Tutorials", true),
    DOCUMENTATION("Documentation", "Documentation", true),
    COURSE("Course", "Courses", true),
    DOCUMENT("Document", "Documents", true),
    WEBSITE("Website", "Websites", true),
    SEARCH_QUERY("Search query", "Search queries"),
    POST("Post", "Posts"),
    OTHER("Other", "Other");

    val isWatchable: Boolean get() = this == REEL || this == SHORT || this == VIDEO
}

enum class CollectionMethod(val label: String) {
    USAGE_STATS("App usage"),
    ACCESSIBILITY("Accessibility"),
    SHARE("Shared to TRACEON"),
    BROWSER_IMPORT("Browser history import"),
    GOOGLE_IMPORT("Google activity import"),
    MANUAL("Manual entry"),
    TRACEON_BROWSER("TRACEON browser"),
    DEMO("Demo data"),
}

/** How trustworthy / complete a record is. Never inflated: unknown titles are UNAVAILABLE, guessed types INFERRED. */
enum class DataStatus(val label: String) {
    OBSERVED("Observed on device"),
    IMPORTED("Imported"),
    MANUAL("Entered manually"),
    INFERRED("Inferred"),
    UNAVAILABLE("Metadata unavailable"),
    SAMPLE("Demo sample"),
}

enum class RelationType(val label: String) {
    SIMILAR("Similar content"),
    SAME_CREATOR("Same creator"),
    LEARNING_SEQUENCE("Learning sequence"),
    CROSS_PLATFORM("Cross-platform"),
    USER("Linked by you"),
}

enum class ImportSource(val label: String) {
    BROWSER_HISTORY("Browser history"),
    GOOGLE_ACTIVITY("Google activity"),
}

enum class ImportStatus { RUNNING, DONE, FAILED }
enum class ThemeMode { DARK, LIGHT, SYSTEM }

enum class SearchSort(val label: String) {
    RELEVANCE("Relevance"), NEWEST("Newest"), OLDEST("Oldest"),
    MOST_REVISITED("Most revisited"), TITLE("Title A-Z"),
}
