package app.wakepact.feature.editor

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.wakepact.alarmkit.AlarmScheduler
import app.wakepact.data.alarm.AlarmRepository
import app.wakepact.domain.model.Alarm
import app.wakepact.testutil.MainDispatcherRule
import app.wakepact.testutil.awaitItemWhere
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditorViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = mockk<AlarmRepository>()
    private val scheduler = mockk<AlarmScheduler>(relaxUnitFun = true)

    private val storedAlarm = Alarm(
        id = 5, hour = 6, minute = 45, daysMask = 0b0011111,
        label = "Gym", enabled = true, stepGoal = 40, graceSec = 240, maxRingSec = 900,
    )

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        EditorViewModel(repo, scheduler, handle)

    @Test
    fun `a new alarm starts from the documented defaults`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(7, state.hour)
            assertEquals(0, state.minute)
            assertEquals(0, state.daysMask)
            assertEquals(Alarm.DEFAULT_STEP_GOAL, state.stepGoal)
            assertEquals(Alarm.DEFAULT_GRACE_SEC, state.graceSec)
            assertEquals(Alarm.DEFAULT_MAX_RING_SEC, state.maxRingSec)
            assertEquals(false, state.isExisting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loading an existing alarm fills the form and bumps the load stamp`() = runTest {
        coEvery { repo.alarm(5L) } returns storedAlarm
        val vm = viewModel()
        vm.load(5L)
        advanceUntilIdle()
        vm.uiState.test {
            val state = awaitItemWhere { it.isExisting }
            assertEquals(6, state.hour)
            assertEquals(45, state.minute)
            assertEquals("Gym", state.label)
            assertEquals(40, state.stepGoal)
            assertEquals(1, state.loadStamp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load is once-only - process-death edits are not clobbered by a reload`() = runTest {
        // After process death the handle already carries the user's edits.
        val handle = SavedStateHandle(mapOf("loaded" to true, "hour" to 9))
        val vm = viewModel(handle)
        vm.load(5L)
        advanceUntilIdle()
        coVerify(exactly = 0) { repo.alarm(any()) }
        vm.uiState.test {
            assertEquals(9, awaitItem().hour)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mission settings are clamped to the documented bounds`() = runTest {
        val vm = viewModel()
        vm.setStepGoal(999)
        vm.setGraceSec(1)
        vm.setMaxRingSec(99_999)
        vm.uiState.test {
            val state = awaitItemWhere { it.stepGoal == Alarm.MAX_STEP_GOAL }
            assertEquals(Alarm.MIN_GRACE_SEC, state.graceSec)
            assertEquals(Alarm.MAX_MAX_RING_SEC, state.maxRingSec)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling a day twice returns the mask to empty`() = runTest {
        val vm = viewModel()
        vm.toggleDay(2)
        vm.uiState.test {
            assertEquals(0b100, awaitItemWhere { it.daysMask != 0 }.daysMask)
            vm.toggleDay(2)
            assertEquals(0, awaitItemWhere { it.daysMask == 0 }.daysMask)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save persists an enabled alarm and schedules it under the returned id`() = runTest {
        coEvery { repo.save(any()) } returns 42L
        val vm = viewModel()
        vm.setTime(6, 30)
        vm.saved.test {
            vm.save()
            advanceUntilIdle()
            awaitItem() // editor closes exactly once
            cancelAndIgnoreRemainingEvents()
        }
        val savedAlarm = slot<Alarm>()
        coVerify { repo.save(capture(savedAlarm)) }
        assertTrue(savedAlarm.captured.enabled)
        assertEquals(6, savedAlarm.captured.hour)
        val scheduled = slot<Alarm>()
        verify { scheduler.scheduleNext(capture(scheduled)) }
        assertEquals(42L, scheduled.captured.id)
    }

    @Test
    fun `delete on a never-saved draft is a no-op`() = runTest {
        val vm = viewModel()
        vm.delete()
        advanceUntilIdle()
        coVerify(exactly = 0) { repo.delete(any()) }
    }

    @Test
    fun `delete on an existing alarm cancels its schedule then removes it`() = runTest {
        coEvery { repo.alarm(5L) } returns storedAlarm
        coEvery { repo.delete(5L) } just Runs
        val vm = viewModel()
        vm.load(5L)
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        verify { scheduler.cancel(5L) }
        coVerify { repo.delete(5L) }
    }
}
