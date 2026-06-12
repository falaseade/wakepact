package app.wakepact.feature.alarms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wakepact.alarmkit.AlarmScheduler
import app.wakepact.data.alarm.AlarmRepository
import app.wakepact.data.pact.PactGateway
import app.wakepact.domain.NextTriggerCalculator
import app.wakepact.domain.model.Alarm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class AlarmRow(
    val alarm: Alarm,
    /** Next fire time in epoch ms when enabled, else null. */
    val nextTriggerMs: Long?,
)

data class PactSummary(val name: String, val memberCount: Int)

data class AlarmsUiState(
    val rows: List<AlarmRow> = emptyList(),
    val pact: PactSummary? = null,
    val nowMs: Long = 0L,
)

@HiltViewModel
class AlarmsViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val calculator: NextTriggerCalculator,
    gateway: PactGateway,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Two-pane selection: [SELECTION_NONE], [SELECTION_NEW], or an alarm id. */
    val selectedRaw: StateFlow<Long> = savedStateHandle.getStateFlow(KEY_SELECTED, SELECTION_NONE)

    /** Bumped per "new alarm" so each new draft gets a fresh editor ViewModel. */
    val newStamp: StateFlow<Int> = savedStateHandle.getStateFlow(KEY_NEW_STAMP, 0)

    private val nowTicker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val uiState: StateFlow<AlarmsUiState> =
        combine(alarmRepository.alarms(), gateway.pact(), nowTicker) { alarms, pact, now ->
            val zoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
            AlarmsUiState(
                rows = alarms.map { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        nextTriggerMs = if (alarm.enabled) calculator.nextTriggerMs(alarm, zoned) else null,
                    )
                },
                pact = pact?.let { PactSummary(it.name, it.members.size) },
                nowMs = now,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmsUiState())

    fun select(alarmId: Long?) {
        savedStateHandle[KEY_SELECTED] = alarmId ?: SELECTION_NONE
    }

    fun selectNew() {
        savedStateHandle[KEY_NEW_STAMP] = newStamp.value + 1
        savedStateHandle[KEY_SELECTED] = SELECTION_NEW
    }

    fun toggle(alarm: Alarm) {
        viewModelScope.launch {
            val enabling = !alarm.enabled
            alarmRepository.setEnabled(alarm.id, enabling)
            if (enabling) {
                scheduler.scheduleNext(alarm.copy(enabled = true))
            } else {
                scheduler.cancel(alarm.id)
            }
        }
    }

    fun delete(alarm: Alarm) {
        viewModelScope.launch {
            scheduler.cancel(alarm.id)
            alarmRepository.delete(alarm.id)
            if (selectedRaw.value == alarm.id) select(null)
        }
    }

    companion object {
        const val SELECTION_NONE = -2L
        const val SELECTION_NEW = -1L
        private const val KEY_SELECTED = "selected_alarm"
        private const val KEY_NEW_STAMP = "new_stamp"
    }
}
