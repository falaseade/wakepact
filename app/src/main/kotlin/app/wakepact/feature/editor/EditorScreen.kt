package app.wakepact.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wakepact.R
import app.wakepact.core.ui.WakeIcons
import app.wakepact.core.util.AlarmTimeFormatter
import app.wakepact.domain.model.Alarm
import kotlinx.coroutines.flow.drop

/** Navigation-hosted editor page (compact layout). */
@Composable
fun EditorRoutePage(
    alarmId: Long,
    onClose: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(alarmId) { viewModel.load(alarmId) }
    LaunchedEffect(Unit) { viewModel.saved.collect { onClose() } }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EditorScreen(
        state = state,
        showBack = true,
        onBack = onClose,
        onTimeChange = viewModel::setTime,
        onToggleDay = viewModel::toggleDay,
        onLabelChange = viewModel::setLabel,
        onStepGoalChange = viewModel::setStepGoal,
        onGraceChange = viewModel::setGraceSec,
        onMaxRingChange = viewModel::setMaxRingSec,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

/** Detail-pane editor (expanded two-pane layout). Keyed per alarm by the host. */
@Composable
fun EditorPane(
    alarmId: Long,
    onClose: () -> Unit,
    viewModel: EditorViewModel,
) {
    LaunchedEffect(alarmId) { viewModel.load(alarmId) }
    LaunchedEffect(Unit) { viewModel.saved.collect { onClose() } }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EditorScreen(
        state = state,
        showBack = false,
        onBack = onClose,
        onTimeChange = viewModel::setTime,
        onToggleDay = viewModel::toggleDay,
        onLabelChange = viewModel::setLabel,
        onStepGoalChange = viewModel::setStepGoal,
        onGraceChange = viewModel::setGraceSec,
        onMaxRingChange = viewModel::setMaxRingSec,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    showBack: Boolean,
    onBack: () -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onToggleDay: (Int) -> Unit,
    onLabelChange: (String) -> Unit,
    onStepGoalChange: (Int) -> Unit,
    onGraceChange: (Int) -> Unit,
    onMaxRingChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isExisting) R.string.editor_title_edit else R.string.editor_title_new,
                        ),
                    )
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(WakeIcons.Back, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                },
                actions = {
                    if (state.isExisting) {
                        IconButton(onClick = onDelete) {
                            Icon(WakeIcons.Delete, contentDescription = stringResource(R.string.cd_delete_alarm))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Re-key the picker whenever load() applies stored values; afterwards
            // the picker is the source of truth and changes flow up via snapshotFlow.
            key(state.loadStamp) {
                val timePickerState = rememberTimePickerState(
                    initialHour = state.hour,
                    initialMinute = state.minute,
                    is24Hour = true,
                )
                LaunchedEffect(timePickerState) {
                    snapshotFlow { timePickerState.hour * 60 + timePickerState.minute }
                        .drop(1)
                        .collect { onTimeChange(timePickerState.hour, timePickerState.minute) }
                }
                TimePicker(state = timePickerState)
            }

            DayOfWeekChips(daysMask = state.daysMask, onToggleDay = onToggleDay)

            OutlinedTextField(
                value = state.label,
                onValueChange = onLabelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.label_hint)) },
                singleLine = true,
            )

            LabeledSlider(
                text = stringResource(R.string.step_goal_label, state.stepGoal),
                value = state.stepGoal,
                range = Alarm.MIN_STEP_GOAL..Alarm.MAX_STEP_GOAL,
                stepSize = 5,
                onChange = onStepGoalChange,
            )
            LabeledSlider(
                text = stringResource(R.string.grace_label, AlarmTimeFormatter.countdown(state.graceSec * 1_000L)),
                value = state.graceSec,
                range = Alarm.MIN_GRACE_SEC..Alarm.MAX_GRACE_SEC,
                stepSize = 30,
                onChange = onGraceChange,
            )
            LabeledSlider(
                text = stringResource(R.string.max_ring_label, state.maxRingSec / 60),
                value = state.maxRingSec,
                range = Alarm.MIN_MAX_RING_SEC..Alarm.MAX_MAX_RING_SEC,
                stepSize = 60,
                onChange = onMaxRingChange,
            )

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun DayOfWeekChips(daysMask: Int, onToggleDay: (Int) -> Unit) {
    val dayNames = stringArrayResource(R.array.week_days_short)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        dayNames.forEachIndexed { index, name ->
            FilterChip(
                selected = daysMask and (1 shl index) != 0,
                onClick = { onToggleDay(index) },
                label = { Text(name) },
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    text: String,
    value: Int,
    range: IntRange,
    stepSize: Int,
    onChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                val snapped = (raw / stepSize).toInt() * stepSize
                onChange(snapped.coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / stepSize - 1,
        )
    }
}
