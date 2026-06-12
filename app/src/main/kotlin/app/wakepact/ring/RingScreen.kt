package app.wakepact.ring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wakepact.R
import app.wakepact.core.ui.WakeIcons
import app.wakepact.core.util.AlarmTimeFormatter
import app.wakepact.domain.model.RingState

@Composable
fun RingRoute(
    onDone: () -> Unit,
    viewModel: RingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.missing) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    RingScreen(
        state = state,
        onDone = {
            viewModel.dismiss()
            onDone()
        },
    )
}

@Composable
fun RingScreen(
    state: RingUiState,
    onDone: () -> Unit,
) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (state.label.isNotBlank()) {
                    Text(
                        text = state.label,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                }
                when (state.state) {
                    RingState.RINGING -> RingingContent(state)
                    RingState.PROOF_DONE -> ProofDoneContent(state)
                    else -> ResolvedContent(state, onDone)
                }
            }
        }
    }
}

@Composable
private fun RingingContent(state: RingUiState) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { state.steps.toFloat() / state.stepGoal.coerceAtLeast(1) },
            modifier = Modifier.size(220.dp),
            strokeWidth = 12.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = WakeIcons.Walk,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.ring_steps_progress, state.steps, state.stepGoal),
                style = MaterialTheme.typography.displaySmall,
            )
        }
    }
    Text(
        text = stringResource(R.string.ring_walk_instruction),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.ring_walk_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ProofDoneContent(state: RingUiState) {
    Icon(
        imageVector = WakeIcons.Check,
        contentDescription = null,
        modifier = Modifier.size(96.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringResource(R.string.ring_proof_done_live),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    state.graceRemainingMs?.let { remaining ->
        Text(
            text = stringResource(R.string.ring_auto_clear_in, AlarmTimeFormatter.countdown(remaining)),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResolvedContent(state: RingUiState, onDone: () -> Unit) {
    val (icon, message) = when (state.state) {
        RingState.DEACTIVATED -> WakeIcons.Check to (
            state.deactivatedByName
                ?.takeIf { state.liveMode }
                ?.let { stringResource(R.string.ring_deactivated_by, it) }
                ?: stringResource(R.string.ring_deactivated_solo)
            )
        RingState.AUTO_CLEARED -> WakeIcons.Check to stringResource(R.string.ring_auto_cleared)
        else -> WakeIcons.Close to stringResource(R.string.ring_missed)
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(96.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = message,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    Button(onClick = onDone) {
        Text(stringResource(R.string.ring_done))
    }
}
