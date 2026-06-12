package app.wakepact.feature.pact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wakepact.R
import app.wakepact.core.ui.WakeIcons
import app.wakepact.core.util.AlarmTimeFormatter
import app.wakepact.core.util.InviteCodes
import app.wakepact.domain.model.RingEvent
import app.wakepact.domain.model.RingState
import kotlinx.coroutines.launch

@Composable
fun PactRoute(
    onBack: () -> Unit,
    viewModel: PactViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val resId = when (message) {
                PactMessage.OFFLINE -> R.string.msg_offline
                PactMessage.CREATE_FAILED -> R.string.msg_create_failed
                PactMessage.JOIN_FAILED -> R.string.msg_join_failed
                PactMessage.LEFT -> R.string.msg_left
                PactMessage.DEACTIVATED_OK -> R.string.msg_deactivated
                PactMessage.NAME_SAVED -> R.string.msg_name_saved
            }
            scope.launch { snackbarHostState.showSnackbar(context.getString(resId)) }
        }
    }

    PactScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSaveName = viewModel::setDisplayName,
        onCreatePact = viewModel::createPact,
        onJoinPact = viewModel::joinPact,
        onLeavePact = viewModel::leavePact,
        onDeactivate = viewModel::deactivate,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PactScreen(
    state: PactUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSaveName: (String) -> Unit,
    onCreatePact: (String) -> Unit,
    onJoinPact: (String) -> Unit,
    onLeavePact: () -> Unit,
    onDeactivate: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pact_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(WakeIcons.Back, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "identity") { IdentityCard(state, onSaveName) }

            if (!state.isLive) {
                item(key = "solo") { SoloCard() }
            } else if (state.pact == null) {
                item(key = "create") { CreatePactCard(state.busy, onCreatePact) }
                item(key = "join") { JoinPactCard(state.busy, onJoinPact) }
            } else {
                item(key = "pact") { PactCard(state, onLeavePact) }
            }

            if (state.pending.isNotEmpty()) {
                item(key = "pending_header") {
                    Text(
                        text = stringResource(R.string.pending_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                items(state.pending, key = { "pending-" + it.id }) { event ->
                    PendingCard(event = event, busy = state.busy, onDeactivate = onDeactivate)
                }
            }

            item(key = "feed_header") {
                Text(
                    text = stringResource(R.string.feed_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (state.feed.isEmpty()) {
                item(key = "feed_empty") {
                    Text(
                        text = stringResource(R.string.feed_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.feed, key = { "feed-" + it.id }) { event ->
                    FeedRow(event)
                }
            }
        }
    }
}

@Composable
private fun IdentityCard(state: PactUiState, onSaveName: (String) -> Unit) {
    var draft by rememberSaveable(state.displayName) { mutableStateOf(state.displayName) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.display_name_label)) },
                singleLine = true,
            )
            TextButton(
                onClick = { onSaveName(draft) },
                enabled = draft.isNotBlank() && draft.trim() != state.displayName,
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun SoloCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.pact_solo_card),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CreatePactCard(busy: Boolean, onCreatePact: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.create_pact_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.pact_name_label)) },
                singleLine = true,
            )
            Button(onClick = { onCreatePact(name) }, enabled = !busy && name.isNotBlank()) {
                Text(stringResource(R.string.create_pact_button))
            }
        }
    }
}

@Composable
private fun JoinPactCard(busy: Boolean, onJoinPact: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val normalized = InviteCodes.normalize(code)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.join_pact_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.invite_code_label)) },
                singleLine = true,
            )
            Button(
                onClick = { onJoinPact(normalized) },
                enabled = !busy && InviteCodes.isValid(normalized),
            ) {
                Text(stringResource(R.string.join_pact_button))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PactCard(state: PactUiState, onLeavePact: () -> Unit) {
    val pact = state.pact ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = pact.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(R.string.invite_code_share),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                Text(
                    text = pact.inviteCode,
                    style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 8.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = stringResource(R.string.members_title),
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pact.members.forEach { member ->
                    AssistChip(onClick = {}, label = { Text(member.name) })
                }
            }
            TextButton(onClick = onLeavePact, enabled = !state.busy) {
                Text(stringResource(R.string.leave_pact))
            }
        }
    }
}

@Composable
private fun PendingCard(event: RingEvent, busy: Boolean, onDeactivate: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = when (event.state) {
                    RingState.RINGING -> stringResource(R.string.pending_ringing, event.ownerName)
                    else -> stringResource(R.string.pending_proof, event.ownerName)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (event.label.isNotBlank()) {
                Text(text = event.label, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { onDeactivate(event.id) },
                enabled = !busy && event.state == RingState.PROOF_DONE,
            ) {
                Text(stringResource(R.string.deactivate_button))
            }
        }
    }
}

@Composable
private fun FeedRow(event: RingEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (event.state) {
            RingState.DEACTIVATED, RingState.AUTO_CLEARED -> WakeIcons.Check
            RingState.MISSED -> WakeIcons.Close
            else -> WakeIcons.Alarm
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.ownerName + if (event.label.isNotBlank()) " · " + event.label else "",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = feedStateText(event),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = AlarmTimeFormatter.dayClock(event.firedAtMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun feedStateText(event: RingEvent): String = when (event.state) {
    RingState.RINGING -> stringResource(R.string.feed_ringing)
    RingState.PROOF_DONE -> stringResource(R.string.feed_proof)
    RingState.DEACTIVATED ->
        event.deactivatedByName?.let { stringResource(R.string.feed_deactivated_by, it) }
            ?: stringResource(R.string.feed_deactivated)
    RingState.AUTO_CLEARED -> stringResource(R.string.feed_auto_cleared)
    RingState.MISSED -> stringResource(R.string.feed_missed)
}
