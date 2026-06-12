package app.wakepact.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wakepact.alarmkit.AlarmScheduler
import app.wakepact.data.alarm.AlarmRepository
import app.wakepact.domain.model.Alarm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val hour: Int = 7,
    val minute: Int = 0,
    val daysMask: Int = 0,
    val label: String = "",
    val stepGoal: Int = Alarm.DEFAULT_STEP_GOAL,
    val graceSec: Int = Alarm.DEFAULT_GRACE_SEC,
    val maxRingSec: Int = Alarm.DEFAULT_MAX_RING_SEC,
    val isExisting: Boolean = false,
    /** Bumped whenever load() applies stored values, so the TimePicker re-keys. */
    val loadStamp: Int = 0,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val hour = savedStateHandle.getStateFlow(K_HOUR, 7)
    private val minute = savedStateHandle.getStateFlow(K_MINUTE, 0)
    private val daysMask = savedStateHandle.getStateFlow(K_DAYS, 0)
    private val label = savedStateHandle.getStateFlow(K_LABEL, "")
    private val stepGoal = savedStateHandle.getStateFlow(K_STEPS, Alarm.DEFAULT_STEP_GOAL)
    private val graceSec = savedStateHandle.getStateFlow(K_GRACE, Alarm.DEFAULT_GRACE_SEC)
    private val maxRingSec = savedStateHandle.getStateFlow(K_MAX_RING, Alarm.DEFAULT_MAX_RING_SEC)
    private val loadedId = savedStateHandle.getStateFlow(K_LOADED_ID, 0L)
    private val loadStamp = savedStateHandle.getStateFlow(K_LOAD_STAMP, 0)

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    /** Emits after a successful save or delete; the host closes the editor. */
    val saved = _saved.receiveAsFlow()

    val uiState: StateFlow<EditorUiState> = combine(
        combine(hour, minute, daysMask, label) { h, m, d, l -> TimeAndLabel(h, m, d, l) },
        combine(stepGoal, graceSec, maxRingSec) { s, g, r -> Mission(s, g, r) },
        loadedId,
        loadStamp,
    ) { time, mission, id, stamp ->
        EditorUiState(
            hour = time.hour,
            minute = time.minute,
            daysMask = time.daysMask,
            label = time.label,
            stepGoal = mission.stepGoal,
            graceSec = mission.graceSec,
            maxRingSec = mission.maxRingSec,
            isExisting = id > 0L,
            loadStamp = stamp,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    /**
     * Loads an existing alarm into the editor exactly once per ViewModel
     * instance: after process death, SavedStateHandle already holds the user's
     * in-progress edits and must not be clobbered by a reload.
     */
    fun load(alarmId: Long) {
        if (savedStateHandle.get<Boolean>(K_LOADED) == true) return
        savedStateHandle[K_LOADED] = true
        if (alarmId <= 0L) return
        viewModelScope.launch {
            val alarm = alarmRepository.alarm(alarmId) ?: return@launch
            savedStateHandle[K_HOUR] = alarm.hour
            savedStateHandle[K_MINUTE] = alarm.minute
            savedStateHandle[K_DAYS] = alarm.daysMask
            savedStateHandle[K_LABEL] = alarm.label
            savedStateHandle[K_STEPS] = alarm.stepGoal
            savedStateHandle[K_GRACE] = alarm.graceSec
            savedStateHandle[K_MAX_RING] = alarm.maxRingSec
            savedStateHandle[K_LOADED_ID] = alarm.id
            savedStateHandle[K_LOAD_STAMP] = loadStamp.value + 1
        }
    }

    fun setTime(hour: Int, minute: Int) {
        savedStateHandle[K_HOUR] = hour.coerceIn(0, 23)
        savedStateHandle[K_MINUTE] = minute.coerceIn(0, 59)
    }

    fun toggleDay(dayIndex: Int) {
        savedStateHandle[K_DAYS] = daysMask.value xor (1 shl dayIndex)
    }

    fun setLabel(value: String) {
        savedStateHandle[K_LABEL] = value.take(MAX_LABEL_LENGTH)
    }

    fun setStepGoal(value: Int) {
        savedStateHandle[K_STEPS] = value.coerceIn(Alarm.MIN_STEP_GOAL, Alarm.MAX_STEP_GOAL)
    }

    fun setGraceSec(value: Int) {
        savedStateHandle[K_GRACE] = value.coerceIn(Alarm.MIN_GRACE_SEC, Alarm.MAX_GRACE_SEC)
    }

    fun setMaxRingSec(value: Int) {
        savedStateHandle[K_MAX_RING] = value.coerceIn(Alarm.MIN_MAX_RING_SEC, Alarm.MAX_MAX_RING_SEC)
    }

    /** Saves (always enabled — saving expresses intent to wake) and schedules. */
    fun save() {
        viewModelScope.launch {
            val alarm = Alarm(
                id = loadedId.value,
                hour = hour.value,
                minute = minute.value,
                daysMask = daysMask.value,
                label = label.value.trim(),
                enabled = true,
                stepGoal = stepGoal.value,
                graceSec = graceSec.value,
                maxRingSec = maxRingSec.value,
            )
            val id = alarmRepository.save(alarm)
            scheduler.scheduleNext(alarm.copy(id = id))
            _saved.send(Unit)
        }
    }

    fun delete() {
        val id = loadedId.value
        if (id <= 0L) return
        viewModelScope.launch {
            scheduler.cancel(id)
            alarmRepository.delete(id)
            _saved.send(Unit)
        }
    }

    private data class TimeAndLabel(val hour: Int, val minute: Int, val daysMask: Int, val label: String)
    private data class Mission(val stepGoal: Int, val graceSec: Int, val maxRingSec: Int)

    private companion object {
        const val K_HOUR = "hour"
        const val K_MINUTE = "minute"
        const val K_DAYS = "days"
        const val K_LABEL = "label"
        const val K_STEPS = "steps"
        const val K_GRACE = "grace"
        const val K_MAX_RING = "max_ring"
        const val K_LOADED = "loaded"
        const val K_LOADED_ID = "loaded_id"
        const val K_LOAD_STAMP = "load_stamp"
        const val MAX_LABEL_LENGTH = 40
    }
}
