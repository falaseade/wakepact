package app.wakepact.ring

import app.wakepact.domain.model.RingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingSessionTest {

    private fun session() = RingSession(
        recordId = "r1", alarmId = 1, ownerUid = "u1", ownerName = "Asha",
        label = "Gym", firedAtMs = 0, stepGoal = 30, graceSec = 180, maxRingSec = 600,
    )

    @Test
    fun `a fresh session is ringing with zero steps`() {
        val s = session()
        assertEquals(RingState.RINGING, s.state.value)
        assertEquals(0, s.steps.value)
        assertNull(s.proofAtMs.value)
    }

    @Test
    fun `markProof transitions to PROOF_DONE and records the moment`() {
        val s = session()
        s.markProof(42L)
        assertEquals(RingState.PROOF_DONE, s.state.value)
        assertEquals(42L, s.proofAtMs.value)
    }

    @Test
    fun `AC-4_1 resolving as DEACTIVATED carries the deactivator's name`() {
        val s = session()
        s.markProof(42L)
        s.resolve(RingState.DEACTIVATED, "Marco")
        assertEquals(RingState.DEACTIVATED, s.state.value)
        assertEquals("Marco", s.deactivatedByName.value)
    }

    @Test
    fun `AC-6_1 solo resolution is DEACTIVATED without losing proof`() {
        val s = session()
        s.markProof(42L)
        s.resolve(RingState.DEACTIVATED, "Asha")
        assertEquals(42L, s.proofAtMs.value)
        assertEquals(RingState.DEACTIVATED, s.state.value)
    }

    @Test
    fun `holder retains the session until cleared`() {
        val holder = RingSessionHolder()
        val s = session()
        holder.set(s)
        assertEquals(s, holder.session.value)
        holder.clear()
        assertNull(holder.session.value)
    }
}
