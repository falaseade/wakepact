package app.wakepact.ring

import app.cash.turbine.test
import app.wakepact.domain.model.RingState
import app.wakepact.testutil.MainDispatcherRule
import app.wakepact.testutil.awaitItemWhere
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RingViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun session(graceSec: Int = 180) = RingSession(
        recordId = "r1", alarmId = 1, ownerUid = "u1", ownerName = "Asha",
        label = "Gym", firedAtMs = System.currentTimeMillis(),
        stepGoal = 30, graceSec = graceSec, maxRingSec = 600,
    )

    @Test
    fun `no session exposes the missing state`() = runTest {
        val vm = RingViewModel(RingSessionHolder())
        vm.uiState.test {
            awaitItemWhere { it.missing }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ringing session streams steps into the ui state`() = runTest {
        val holder = RingSessionHolder()
        val s = session()
        holder.set(s)
        val vm = RingViewModel(holder)
        vm.uiState.test {
            val ringing = awaitItemWhere { !it.missing && it.stepGoal == 30 }
            assertEquals(RingState.RINGING, ringing.state)
            assertEquals(0, ringing.steps)
            assertNull(ringing.graceRemainingMs)

            s.updateSteps(7)
            assertEquals(7, awaitItemWhere { it.steps == 7 }.steps)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AC-4_2 proof done exposes a grace countdown`() = runTest {
        val holder = RingSessionHolder()
        val s = session(graceSec = 180)
        holder.set(s)
        val vm = RingViewModel(holder)
        vm.uiState.test {
            awaitItemWhere { !it.missing && it.stepGoal == 30 }
            s.markProof(System.currentTimeMillis())
            val pending = awaitItemWhere { it.state == RingState.PROOF_DONE && it.graceRemainingMs != null }
            val remaining = pending.graceRemainingMs ?: 0L
            assertTrue("remaining=$remaining", remaining in 170_000..180_000)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AC-4_1 remote deactivation reaches the ui with the buddy's name`() = runTest {
        val holder = RingSessionHolder()
        val s = session()
        holder.set(s)
        val vm = RingViewModel(holder)
        vm.uiState.test {
            awaitItemWhere { !it.missing && it.stepGoal == 30 }
            s.markProof(System.currentTimeMillis())
            s.resolve(RingState.DEACTIVATED, "Marco")
            val resolved = awaitItemWhere { it.state == RingState.DEACTIVATED }
            assertEquals("Marco", resolved.deactivatedByName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismiss clears the retained session`() = runTest {
        val holder = RingSessionHolder()
        holder.set(session())
        val vm = RingViewModel(holder)
        vm.uiState.test {
            awaitItemWhere { !it.missing && it.stepGoal == 30 }
            vm.dismiss()
            assertNull(holder.session.value)
            awaitItemWhere { it.missing }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
