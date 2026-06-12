package app.wakepact.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmTimeFormatterTest {

    @Test
    fun `clock zero-pads hours and minutes`() {
        assertEquals("07:05", AlarmTimeFormatter.clock(7, 5))
        assertEquals("23:59", AlarmTimeFormatter.clock(23, 59))
    }

    @Test
    fun `countdown formats minutes and seconds`() {
        assertEquals("2:05", AlarmTimeFormatter.countdown(125_000))
        assertEquals("0:00", AlarmTimeFormatter.countdown(0))
        assertEquals("10:00", AlarmTimeFormatter.countdown(600_000))
    }

    @Test
    fun `countdown clamps negative remainders to zero`() {
        assertEquals("0:00", AlarmTimeFormatter.countdown(-5_000))
    }

    @Test
    fun `hoursMinutesUntil rounds up to the next minute`() {
        // 1 ms until trigger still reads "in 1 min", never "in 0 min".
        assertEquals(0L to 1L, AlarmTimeFormatter.hoursMinutesUntil(1_000_001, 1_000_000))
        assertEquals(3L to 2L, AlarmTimeFormatter.hoursMinutesUntil(182 * 60_000L, 0))
        assertEquals(0L to 0L, AlarmTimeFormatter.hoursMinutesUntil(1_000_000, 1_000_000))
    }
}
