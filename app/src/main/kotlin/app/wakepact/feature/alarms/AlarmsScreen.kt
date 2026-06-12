package app.wakepact.feature.alarms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wakepact.R
import app.wakepact.core.ui.WakeIcons
import app.wakepact.core.util.AlarmTimeFormatter
import app.wakepact.domain.model.Alarm
import app.wakepact.feature.editor.EditorPane

@Composable
fun AlarmsRoute(
    onEditAlarm: (Long) -> Unit,
    onAddAlarm: () -> Unit,
    onOpenPact: () -> Unit,
    viewModel: AlarmsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedRaw by viewModel.selectedRaw.collectAsStateWithLifecycle()
    val newStamp by viewModel.newStamp.collectAsStateWithLifecycle()

    BoxWithConstraints {
        val expanded = maxWidth >= 840.dp
        if (expanded) {
            AlarmsTwoPane(
                state = state,
                selectedRaw = selectedRaw,
                newStamp = newStamp,
                onSelect = { viewModel.select(it) },
                onAdd = { viewModel.selectNew() },
                onOpenPact = onOpenPact,
                onToggle = viewModel::toggle,
                onCloseEditor = { viewModel.select(null) },
            )
        } else {
            AlarmsScreen(
                state = state,
                onAlarmClick = onEditAlarm,
                onAdd = onAddAlarm,
                onOpenPact = onOpenPact,
                onToggle = viewModel::toggle,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen(
    state: AlarmsUiState,
    onAlarmClick: (Long) -> Unit,
    onAdd: () -> Unit,
    onOpenPact: () -> Unit,
    onToggle: (Alarm) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenPact) {
                        Icon(WakeIcons.People, contentDescription = stringResource(R.string.cd_open_pact))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(WakeIcons.Add, contentDescription = stringResource(R.string.cd_add_alarm))
            }
        },
    ) { innerPadding ->
        AlarmListContent(
            state = state,
            onAlarmClick = onAlarmClick,
            onToggle = onToggle,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmsTwoPane(
    state: AlarmsUiState,
    selectedRaw: Long,
    newStamp: Int,
    onSelect: (Long?) -> Unit,
    onAdd: () -> Unit,
    onOpenPact: () -> Unit,
    onToggle: (Alarm) -> Unit,
    onCloseEditor: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenPact) {
                        Icon(WakeIcons.People, contentDescription = stringResource(R.string.cd_open_pact))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(WakeIcons.Add, contentDescription = stringResource(R.string.cd_add_alarm))
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AlarmListContent(
                state = state,
                onAlarmClick = { onSelect(it) },
                onToggle = onToggle,
                modifier = Modifier.weight(0.45f),
            )
            Box(modifier = Modifier.weight(0.55f)) {
                if (selectedRaw == AlarmsViewModel.SELECTION_NONE) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.select_alarm_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // One ViewModel per pane target so switching alarms (or starting
                    // another new draft) resets cleanly.
                    val editorKey = if (selectedRaw == AlarmsViewModel.SELECTION_NEW) {
                        "editor-new-$newStamp"
                    } else {
                        "editor-$selectedRaw"
                    }
                    EditorPane(
                        alarmId = selectedRaw,
                        onClose = onCloseEditor,
                        viewModel = hiltViewModel(key = editorKey),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmListContent(
    state: AlarmsUiState,
    onAlarmClick: (Long) -> Unit,
    onToggle: (Alarm) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        NotificationPermissionBanner()
        if (state.rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_alarms),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.rows, key = { it.alarm.id }) { row ->
                    AlarmCard(
                        row = row,
                        nowMs = state.nowMs,
                        onClick = { onAlarmClick(row.alarm.id) },
                        onToggle = { onToggle(row.alarm) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(
    row: AlarmRow,
    nowMs: Long,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val alarm = row.alarm
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AlarmTimeFormatter.clock(alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.displaySmall,
                )
                if (alarm.label.isNotBlank()) {
                    Text(text = alarm.label, style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    text = repeatSummary(alarm.daysMask),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                row.nextTriggerMs?.let { trigger ->
                    val (hours, minutes) = AlarmTimeFormatter.hoursMinutesUntil(trigger, nowMs)
                    Text(
                        text = if (hours > 0) {
                            stringResource(R.string.in_hours_minutes, hours, minutes)
                        } else {
                            stringResource(R.string.in_minutes, minutes)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Switch(checked = alarm.enabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun repeatSummary(daysMask: Int): String {
    if (daysMask == 0) return stringResource(R.string.repeat_once)
    val names = stringArrayResource(R.array.week_days_short)
    return (0..6).filter { daysMask and (1 shl it) != 0 }.joinToString(" ") { names[it] }
}

/**
 * On API 33+ the full-screen ring depends on the notification permission;
 * ask for it up front instead of failing silently at 7 AM.
 */
@Composable
private fun NotificationPermissionBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    if (granted) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.notif_permission_rationale),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text(stringResource(R.string.notif_permission_button))
            }
        }
    }
}
