package app.wakepact.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Locale-neutral numeric formatting helpers (clock faces, countdowns).
 * Sentence-shaped text stays in string resources, composed in the UI layer.
 */
object AlarmTimeFormatter {

    fun clock(hour: Int, minute: Int): String =
        String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    /** "Tue 07:00" for feed rows, in the device zone and locale day name. */
    fun dayClock(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
        val day = t.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        return "$day ${clock(t.hour, t.minute)}"
    }

    /** "m:ss" countdown for the grace window. */
    fun countdown(remainingMs: Long): String {
        val total = remainingMs.coerceAtLeast(0L) / 1_000L
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
    }

    /** Hours/minutes split for "in X h Y min" string resources. */
    fun hoursMinutesUntil(triggerAtMs: Long, nowMs: Long): Pair<Long, Long> {
        val totalMin = ((triggerAtMs - nowMs).coerceAtLeast(0L) + 59_999L) / 60_000L
        return totalMin / 60 to totalMin % 60
    }
}
