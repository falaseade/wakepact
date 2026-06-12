package app.wakepact.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InviteCodesTest {

    @Test
    fun `AC-7_1 generated codes are 6 chars and always valid`() {
        val random = Random(42)
        repeat(200) {
            val code = InviteCodes.random(random)
            assertEquals(6, code.length)
            assertTrue("$code should be valid", InviteCodes.isValid(code))
        }
    }

    @Test
    fun `generated codes never contain ambiguous characters`() {
        val random = Random(7)
        val ambiguous = setOf('0', 'O', '1', 'I', 'L')
        repeat(500) {
            val code = InviteCodes.random(random)
            assertTrue("$code has ambiguous chars", code.none { it in ambiguous })
        }
    }

    @Test
    fun `normalize trims and uppercases user input`() {
        assertEquals("ABC234", InviteCodes.normalize("  abc234 "))
    }

    @Test
    fun `validation rejects wrong length lowercase and ambiguous characters`() {
        assertFalse(InviteCodes.isValid("ABC23"))   // too short
        assertFalse(InviteCodes.isValid("ABC2345")) // too long
        assertFalse(InviteCodes.isValid("abc234"))  // not normalized
        assertFalse(InviteCodes.isValid("ABC23O"))  // ambiguous O
        assertFalse(InviteCodes.isValid("ABC123"))  // ambiguous 1
        assertTrue(InviteCodes.isValid("ABC234"))
    }
}
