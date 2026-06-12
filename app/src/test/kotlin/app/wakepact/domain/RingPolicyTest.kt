package app.wakepact.domain

import app.wakepact.domain.model.RingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingPolicyTest {

    private val firedAt = 1_000_000L
    private val graceSec = 180
    private val maxRingSec = 600

    @Test
    fun `still ringing before the max-ring cap resolves nothing`() {
        val verdict = RingPolicy.evaluate(
            nowMs = firedAt + 599_999, firedAtMs = firedAt,
            proofAtMs = null, graceSec = graceSec, maxRingSec = maxRingSec,
        )
        assertNull(verdict)
    }

    @Test
    fun `AC-5_1 no proof at the max-ring cap resolves MISSED`() {
        val verdict = RingPolicy.evaluate(
            nowMs = RingPolicy.missedDeadlineMs(firedAt, maxRingSec), firedAtMs = firedAt,
            proofAtMs = null, graceSec = graceSec, maxRingSec = maxRingSec,
        )
        assertEquals(RingState.MISSED, verdict)
    }

    @Test
    fun `proof beats missed - past the cap with proof done is not MISSED`() {
        val proofAt = firedAt + 500_000
        val verdict = RingPolicy.evaluate(
            nowMs = firedAt + 650_000, // past max-ring cap, inside grace
            firedAtMs = firedAt, proofAtMs = proofAt,
            graceSec = graceSec, maxRingSec = maxRingSec,
        )
        assertNull(verdict)
    }

    @Test
    fun `AC-4_2 silent pact through the grace window resolves AUTO_CLEARED`() {
        val proofAt = firedAt + 120_000
        val verdict = RingPolicy.evaluate(
            nowMs = RingPolicy.autoClearDeadlineMs(proofAt, graceSec),
            firedAtMs = firedAt, proofAtMs = proofAt,
            graceSec = graceSec, maxRingSec = maxRingSec,
        )
        assertEquals(RingState.AUTO_CLEARED, verdict)
    }

    @Test
    fun `inside the grace window resolves nothing`() {
        val proofAt = firedAt + 120_000
        val verdict = RingPolicy.evaluate(
            nowMs = proofAt + graceSec * 1_000L - 1,
            firedAtMs = firedAt, proofAtMs = proofAt,
            graceSec = graceSec, maxRingSec = maxRingSec,
        )
        assertNull(verdict)
    }

    @Test
    fun `deadline helpers are simple arithmetic`() {
        assertEquals(firedAt + 600_000, RingPolicy.missedDeadlineMs(firedAt, maxRingSec))
        assertEquals(2_000_000 + 180_000, RingPolicy.autoClearDeadlineMs(2_000_000, graceSec))
    }
}
