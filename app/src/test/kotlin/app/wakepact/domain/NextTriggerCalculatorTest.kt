package app.wakepact.domain

import app.wakepact.domain.model.Alarm
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NextTriggerCalculatorTest {

    private val zone = ZoneId.of("Europe/London")
    private val calculator = NextTriggerCalculator()
    private val weekdaysMask = 0b0011111 // Mon..Fri

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0) =
        ZonedDateTime.of(year, month, day, hour, minute, second, 0, zone)

    @Test
    fun `AC-1_1 weekday alarm before its time today fires today`() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, daysMask = weekdaysMask)
        val now = at(2026, 6, 9, 6, 0) // Tuesday 06:00
        val next = calculator.nextTriggerMs(alarm, now)
        assertEquals(at(2026, 6, 9, 7, 0).toInstant().toEpochMilli(), next)
    }

    @Test
    fun `AC-1_2 one-shot whose time passed today fires tomorrow`() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, daysMask = 0)
        val now = at(2026, 6, 9, 8, 0) // Tuesday 08:00, alarm time already gone
        val next = calculator.nextTriggerMs(alarm, now)
        assertEquals(at(2026, 6, 10, 7, 0).toInstant().toEpochMilli(), next)
    }

    @Test
    fun `repeating alarm at exactly fire time skips to the next valid day`() {
        // Re-arming happens at fire time; "now" == today's trigger must not
        // return the trigger that just fired.
        val alarm = Alarm(id = 1, hour = 7, minute = 0, daysMask = weekdaysMask)
        val now = at(2026, 6, 9, 7, 0) // Tuesday exactly 07:00:00
        val next = calculator.nextTriggerMs(alarm, now)
        assertEquals(at(2026, 6, 10, 7, 0).toInstant().toEpochMilli(), next)
    }

    @Test
    fun `one-shot at exactly its time schedules tomorrow`() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, daysMask = 0)
        val now = at(2026, 6, 9, 7, 0)
        val next = calculator.nextTriggerMs(alarm, now)
        assertEquals(at(2026, 6, 10, 7, 0).toInstant().toEpochMilli(), next)
    }

    @Test
    fun `monday-only alarm on tuesday waits for next monday`() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, daysMask = 0b0000001)
        val now = at(2026, 6, 9, 8, 0) // Tuesday
        val next = calculator.nextTriggerMs(alarm, now)
        assertEquals(at(2026, 6, 15, 7, 0).toInstant().toEpochMilli(), next) // Mon 15 Jun
    }

    @Test
    fun `friday-evening alarm wraps the weekend to monday when only weekdays selected`() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, daysMask = weekdaysMask)
        val now = at(2026, 6, 12, 8, 0) // Friday 08:00, today's slot gone
        val next = calculator.nextTriggerMs(alarm, now)
        assertEquals(at(2026, 6, 15, 7, 0).toInstant().toEpochMilli(), next)
    }

    @Test
    fun `seconds are zeroed on the computed trigger`() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, daysMask = weekdaysMask)
        val now = at(2026, 6, 9, 6, 59, second = 30)
        val next = calculator.nextTriggerMs(alarm, now)
        assertEquals(at(2026, 6, 9, 7, 0, second = 0).toInstant().toEpochMilli(), next)
    }
}
