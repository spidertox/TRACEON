package com.traceon.feature.tracking

data class RawUsageEvent(val time: Long, val packageName: String, val type: Type) {
    enum class Type { FOREGROUND, BACKGROUND }
}

data class AppSession(val packageName: String, val start: Long, val end: Long) {
    val durationMs: Long get() = end - start
}

/**
 * Turns OS foreground/background events into app sessions for the apps the user opted in to.
 * This is app-level usage only. It cannot know which video, reel or page was on screen.
 */
object SessionBuilder {
    fun build(
        events: List<RawUsageEvent>,
        watched: Set<String>,
        minDurationMs: Long = 3_000,
        mergeGapMs: Long = 15_000,
        maxSessionMs: Long = 6 * 3_600_000L,
    ): List<AppSession> {
        val open = HashMap<String, Long>()
        val raw = ArrayList<AppSession>()
        for (e in events.sortedBy { it.time }) {
            if (e.packageName !in watched) continue
            when (e.type) {
                RawUsageEvent.Type.FOREGROUND -> if (!open.containsKey(e.packageName)) open[e.packageName] = e.time
                RawUsageEvent.Type.BACKGROUND -> {
                    val start = open.remove(e.packageName)
                    if (start != null && e.time > start) raw += AppSession(e.packageName, start, minOf(e.time, start + maxSessionMs))
                }
            }
        }
        // Activity-level events split one app visit into many fragments; join fragments separated by short gaps.
        val merged = ArrayList<AppSession>()
        for (s in raw.sortedWith(compareBy({ it.packageName }, { it.start }))) {
            val last = merged.lastOrNull()
            if (last != null && last.packageName == s.packageName && s.start - last.end <= mergeGapMs) {
                merged[merged.size - 1] = AppSession(last.packageName, last.start, maxOf(last.end, s.end))
            } else merged += s
        }
        return merged.filter { it.durationMs >= minDurationMs }.sortedBy { it.start }
    }
}
