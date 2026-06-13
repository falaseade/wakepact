package app.wakepact.feature.alarms

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.wakepact.alarmkit.AlarmScheduler
import app.wakepact.data.alarm.AlarmRepository
import app.wakepact.domain.NextTriggerCalculator
import app.wakepact.domain.model.Alarm
import app.wakepact.domain.model.Pact
import app.wakepact.domain.model.PactMember
import app.wakepact.testutil.FakePactGateway
import app.wakepact.testutil.MainDispatcherRule
import app.wakepact.testutil.awaitItemWhere
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlarmsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val enabledAlarm = Alarm(id = 1, hour = 7, minute = 0, daysMask = 0b0011111, enabled = true)
    private val disabledAlarm = Alarm(id = 2, hour = 9, minute = 30, daysMask = 0, enabled = false)

    private val alarmsFlow = MutableStateFlow(listOf(enabledAlarm, disabledAlarm))
    private val repo = mockk<AlarmRepository> {
        every { alarms() } returns alarmsFlow
        coEvery { setEnabled(any(), any()) } just Runs
        coEvery { delete(any()) } just Runs
    }
    private val scheduler = mockk<AlarmScheduler>(relaxed = true)
    private val gateway = FakePactGateway(isLive = true)

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        AlarmsViewModel(repo, scheduler, NextTriggerCalculator(), gateway, handle)

    @Test
    fun `rows expose a next trigger only for enabled alarms`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            val state = awaitItemWhere { it.rows.size == 2 }
            val byId = state.rows.associateBy { it.alarm.id }
            assertNotNull(byId.getValue(1L).nextTriggerMs)
            assertNull(byId.getValue(2L).nextTriggerMs)
            assertTrue(state.nowMs > 0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pact summary appears when the gateway has a pact`() = runTest {
        gateway.pactFlow.value = Pact(
            id = "p1", name = "Flat 4B", inviteCode = "ABC234",
            members = listOf(PactMember("u1", "Asha"), PactMember("u2", "Marco")),
        )
        val vm = viewModel()
        vm.uiState.test {
            val state = awaitItemWhere { it.pact != null }
            assertEquals("Flat 4B", state.pact?.name)
            assertEquals(2, state.pact?.memberCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty repository yields an empty row list not a crash`() = runTest {
        alarmsFlow.value = emptyList()
        val vm = viewModel()
        vm.uiState.test {
            assertTrue(awaitItemWhere { it.nowMs > 0 }.rows.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling a disabled alarm enables and schedules it`() = runTest {
        val vm = viewModel()
        vm.toggle(disabledAlarm)
        advanceUntilIdle()
        coVerify { repo.setEnabled(2L, true) }
        val scheduled = slot<Alarm>()
        verify { scheduler.scheduleNext(capture(scheduled)) }
        assertEquals(2L, scheduled.captured.id)
        assertTrue(scheduled.captured.enabled)
    }

    @Test
    fun `toggling an enabled alarm disables it and cancels the schedule`() = runTest {
        val vm = viewModel()
        vm.toggle(enabledAlarm)
        advanceUntilIdle()
        coVerify { repo.setEnabled(1L, false) }
        verify { scheduler.cancel(1L) }
    }

    @Test
    fun `deleting the selected alarm cancels deletes and clears the selection`() = runTest {
        val vm = viewModel()
        vm.select(1L)
        vm.delete(enabledAlarm)
        advanceUntilIdle()
        verify { scheduler.cancel(1L) }
        coVerify { repo.delete(1L) }
        assertEquals(AlarmsViewModel.SELECTION_NONE, vm.selectedRaw.value)
    }

    @Test
    fun `each new-alarm request gets a fresh stamp`() = runTest {
        val vm = viewModel()
        assertEquals(0, vm.newStamp.value)
        vm.selectNew()
        assertEquals(AlarmsViewModel.SELECTION_NEW, vm.selectedRaw.value)
        assertEquals(1, vm.newStamp.value)
        vm.selectNew()
        assertEquals(2, vm.newStamp.value)
    }
}
