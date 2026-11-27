package com.traceon.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class RangePreset(val label: String) {
    TODAY("Today"), YESTERDAY("Yesterday"), LAST_7("Last 7 days"), LAST_30("Last 30 days"), LAST_90("Last 90 days"), CUSTOM("Custom"),
}

object TimeUtils {
    const val DAY_MS = 86_400_000L

    fun startOfDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        startOfDay(date.plusDays(1), zone) - 1

    fun toLocalDate(ms: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun presetRange(p: RangePreset, now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val today = toLocalDate(now, zone)
        fun span(daysBack: Long) = startOfDay(today.minusDays(daysBack), zone) to endOfDay(today, zone)
        return when (p) {
            RangePreset.TODAY, RangePreset.CUSTOM -> span(0)
            RangePreset.YESTERDAY -> startOfDay(today.minusDays(1), zone) to endOfDay(today.minusDays(1), zone)
            RangePreset.LAST_7 -> span(6)
            RangePreset.LAST_30 -> span(29)
            RangePreset.LAST_90 -> span(89)
        }
    }

    /** Converts a DatePicker's UTC-midnight selection into local day bounds. */
    fun pickerRange(startUtcMs: Long, endUtcMs: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> =
        startOfDay(LocalDate.ofEpochDay(startUtcMs / DAY_MS), zone) to endOfDay(LocalDate.ofEpochDay(endUtcMs / DAY_MS), zone)

    private fun fmt(pattern: String) = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    private val dateFmt = fmt("EEE, d MMM yyyy")
    private val shortFmt = fmt("d MMM")
    private val timeFmt = fmt("HH:mm")

    fun formatDate(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = dateFmt.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun formatShort(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = shortFmt.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun formatTime(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = timeFmt.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun formatDateTime(ms: Long): String = "${formatShort(ms)} ${formatTime(ms)}"

    fun dayHeader(ms: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = toLocalDate(ms, zone)
        val today = toLocalDate(now, zone)
        return when (d) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> formatDate(ms, zone)
        }
    }

    /** "n/a" means not measurable, never zero. */
    fun formatDuration(ms: Long?): String = when {
        ms == null -> "n/a"
        ms <= 0L -> "0m"
        ms < 60_000L -> "<1m"
        ms < 3_600_000L -> "${ms / 60_000}m"
        else -> "${ms / 3_600_000}h ${(ms % 3_600_000) / 60_000}m"
    }
}
