package app.wakepact.domain

import app.wakepact.domain.model.Alarm
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Pure next-occurrence math for an [Alarm], parameterised on `now` for testability.
 * DST gaps/overlaps are delegated to java.time's `with(LocalTime)` adjustment semantics.
 */
class NextTriggerCalculator @Inject constructor() {

    fun nextTriggerMs(alarm: Alarm, now: ZonedDateTime): Long {
        if (!alarm.isRepeating) {
            var candidate = atAlarmTime(now, alarm)
            if (!candidate.isAfter(now)) candidate = atAlarmTime(now.plusDays(1), alarm)
            return candidate.toInstant().toEpochMilli()
        }
        for (offset in 0..7) {
            val day = now.plusDays(offset.toLong())
            val bit = day.dayOfWeek.value - 1 // Mon=0 … Sun=6
            if (alarm.daysMask and (1 shl bit) != 0) {
                val candidate = atAlarmTime(day, alarm)
                if (candidate.isAfter(now)) return candidate.toInstant().toEpochMilli()
            }
        }
        // daysMask != 0 guarantees a match within 8 days.
        error("No next trigger found for daysMask=${alarm.daysMask}")
    }

    private fun atAlarmTime(day: ZonedDateTime, alarm: Alarm): ZonedDateTime =
        day.withHour(alarm.hour).withMinute(alarm.minute).withSecond(0).withNano(0)
}
