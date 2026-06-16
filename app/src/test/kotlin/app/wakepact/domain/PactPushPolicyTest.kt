package app.wakepact.domain

import app.wakepact.domain.model.RingState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PactPushPolicyTest {

    private val owner = "owner-uid"
    private val buddy = "buddy-uid"

    @Test
    fun `buddy is notified when a pact-mate finishes proof`() {
        assertTrue(
            PactPushPolicy.shouldNotifyDeactivation(RingState.PROOF_DONE.name, owner, buddy),
        )
    }

    @Test
    fun `owner is never notified about their own proof`() {
        assertFalse(
            PactPushPolicy.shouldNotifyDeactivation(RingState.PROOF_DONE.name, owner, owner),
        )
    }

    @Test
    fun `non proof-done states do not notify`() {
        for (state in listOf(RingState.RINGING, RingState.DEACTIVATED, RingState.AUTO_CLEARED, RingState.MISSED)) {
            assertFalse(
                "state $state should not notify",
                PactPushPolicy.shouldNotifyDeactivation(state.name, owner, buddy),
            )
        }
    }

    @Test
    fun `missing or blank owner uid does not notify`() {
        assertFalse(PactPushPolicy.shouldNotifyDeactivation(RingState.PROOF_DONE.name, null, buddy))
        assertFalse(PactPushPolicy.shouldNotifyDeactivation(RingState.PROOF_DONE.name, "", buddy))
    }

    @Test
    fun `unknown or null state does not notify`() {
        assertFalse(PactPushPolicy.shouldNotifyDeactivation(null, owner, buddy))
        assertFalse(PactPushPolicy.shouldNotifyDeactivation("GARBAGE", owner, buddy))
    }

    @Test
    fun `notifies when self uid is unknown but owner is a real member`() {
        // A buddy whose auth uid couldn't be resolved should still get the call
        // for help rather than silently miss it.
        assertTrue(PactPushPolicy.shouldNotifyDeactivation(RingState.PROOF_DONE.name, owner, null))
    }
}
