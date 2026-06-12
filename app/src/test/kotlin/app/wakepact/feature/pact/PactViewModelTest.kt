package app.wakepact.feature.pact

import app.cash.turbine.test
import app.wakepact.data.identity.Identity
import app.wakepact.data.identity.IdentityRepository
import app.wakepact.data.pact.GatewayResult
import app.wakepact.domain.model.RingEvent
import app.wakepact.domain.model.RingState
import app.wakepact.testutil.FakePactGateway
import app.wakepact.testutil.MainDispatcherRule
import app.wakepact.testutil.awaitItemWhere
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PactViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val self = Identity(uid = "local-uid", displayName = "Asha", pactId = "pact-1")
    private val identityRepo = mockk<IdentityRepository> {
        every { identity } returns MutableStateFlow(self)
        coEvery { ensureDisplayName() } returns "Asha"
        coEvery { current() } returns self
        coEvery { setDisplayName(any()) } just Runs
    }
    private val gateway = FakePactGateway(isLive = true, uid = "self-uid")

    private fun event(
        id: String,
        ownerUid: String,
        state: RingState,
        proofAtMs: Long? = null,
    ) = RingEvent(
        id = id, ownerUid = ownerUid, ownerName = "Marco", label = "Gym",
        firedAtMs = 100L, state = state, proofAtMs = proofAtMs,
    )

    private fun viewModel() = PactViewModel(gateway, identityRepo)

    @Test
    fun `AC-3_1 another member's unresolved ring appears in pending without refresh`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItemWhere { it.displayName == "Asha" }
            // The gateway pushes — no user action in between.
            gateway.eventsFlow.value = listOf(
                event("e1", ownerUid = "buddy-uid", state = RingState.PROOF_DONE, proofAtMs = 200L),
                event("e2", ownerUid = "self-uid", state = RingState.PROOF_DONE, proofAtMs = 200L), // own
                event("e3", ownerUid = "buddy-uid", state = RingState.DEACTIVATED), // resolved
            )
            val state = awaitItemWhere { it.feed.size == 3 }
            assertEquals(listOf("e1"), state.pending.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AC-3_2 deactivating writes my auth uid and removes the event from pending`() = runTest {
        gateway.eventsFlow.value = listOf(
            event("e1", ownerUid = "buddy-uid", state = RingState.PROOF_DONE, proofAtMs = 200L),
        )
        val vm = viewModel()
        vm.messages.test {
            vm.deactivate("e1")
            advanceUntilIdle()
            assertEquals(PactMessage.DEACTIVATED_OK, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        val call = gateway.updateCalls.single()
        assertEquals("e1", call.eventId)
        assertEquals(RingState.DEACTIVATED, call.state)
        assertEquals("self-uid", call.byUid) // auth uid, not the local DataStore uid
        assertEquals("Asha", call.byName)
        assertNull(call.proofAtMs) // owner's proof timestamp must be preserved, not overwritten
        assertNotNull(call.resolvedAtMs)
        vm.uiState.test {
            assertTrue(awaitItemWhere { it.feed.isNotEmpty() }.pending.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AC-7_2 joining a pact lands me in the member list`() = runTest {
        val vm = viewModel()
        vm.joinPact("ABC234")
        advanceUntilIdle()
        assertEquals("ABC234" to "Asha", gateway.joinCalls.single())
        vm.uiState.test {
            val state = awaitItemWhere { it.pact != null }
            assertTrue(state.pact?.members.orEmpty().any { it.name == "Asha" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AC-7_1 creating a pact sends the trimmed name and my display name`() = runTest {
        val vm = viewModel()
        vm.createPact("  Flat 4B  ")
        advanceUntilIdle()
        assertEquals("Flat 4B" to "Asha", gateway.createCalls.single())
        vm.uiState.test {
            assertEquals("Flat 4B", awaitItemWhere { it.pact != null }.pact?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blank pact name never reaches the gateway`() = runTest {
        val vm = viewModel()
        vm.createPact("   ")
        advanceUntilIdle()
        assertTrue(gateway.createCalls.isEmpty())
    }

    @Test
    fun `offline create surfaces the offline message`() = runTest {
        gateway.createResult = GatewayResult.Offline
        val vm = viewModel()
        vm.messages.test {
            vm.createPact("Flat 4B")
            advanceUntilIdle()
            assertEquals(PactMessage.OFFLINE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed join surfaces the join-failed message`() = runTest {
        gateway.joinResult = GatewayResult.Failed("no such pact")
        val vm = viewModel()
        vm.messages.test {
            vm.joinPact("ZZZZZZ")
            advanceUntilIdle()
            assertEquals(PactMessage.JOIN_FAILED, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `leaving the pact clears it and confirms`() = runTest {
        gateway.pactFlow.value = null
        val vm = viewModel()
        vm.joinPact("ABC234")
        advanceUntilIdle()
        vm.messages.test {
            vm.leavePact()
            advanceUntilIdle()
            assertEquals(PactMessage.LEFT, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(gateway.pactFlow.value)
    }

    @Test
    fun `saving a display name persists and confirms`() = runTest {
        val vm = viewModel()
        vm.messages.test {
            vm.setDisplayName("Zed")
            advanceUntilIdle()
            assertEquals(PactMessage.NAME_SAVED, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { identityRepo.setDisplayName("Zed") }
    }

    @Test
    fun `AC-8_1 the feed passes gateway events through in gateway order`() = runTest {
        gateway.eventsFlow.value = listOf(
            event("new", ownerUid = "buddy-uid", state = RingState.DEACTIVATED),
            event("old", ownerUid = "buddy-uid", state = RingState.MISSED),
        )
        val vm = viewModel()
        vm.uiState.test {
            assertEquals(
                listOf("new", "old"),
                awaitItemWhere { it.feed.size == 2 }.feed.map { it.id },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `solo gateway reports not live`() = runTest {
        val soloGateway = FakePactGateway(isLive = false)
        val vm = PactViewModel(soloGateway, identityRepo)
        vm.uiState.test {
            assertEquals(false, awaitItemWhere { it.displayName == "Asha" }.isLive)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
