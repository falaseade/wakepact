package app.wakepact.ring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wakepact.domain.model.RingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RingUiState(
    val missing: Boolean = false,
    val label: String = "",
    val state: RingState = RingState.RINGING,
    val steps: Int = 0,
    val stepGoal: Int = 1,
    val liveMode: Boolean = false,
    val deactivatedByName: String? = null,
    /** Non-null while PROOF_DONE: time until the grace window auto-clears. */
    val graceRemainingMs: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RingViewModel @Inject constructor(
    private val sessionHolder: RingSessionHolder,
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val uiState: StateFlow<RingUiState> = sessionHolder.session
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(RingUiState(missing = true))
            } else {
                combine(
                    session.state,
                    session.steps,
                    session.proofAtMs,
                    session.deactivatedByName,
                    ticker,
                ) { state, steps, proofAt, byName, now ->
                    RingUiState(
                        missing = false,
                        label = session.label,
                        state = state,
                        steps = steps,
                        stepGoal = session.stepGoal,
                        liveMode = session.liveMode.value,
                        deactivatedByName = byName,
                        graceRemainingMs = if (state == RingState.PROOF_DONE && proofAt != null) {
                            (proofAt + session.graceSec * 1_000L - now).coerceAtLeast(0)
                        } else {
                            null
                        },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RingUiState())

    /** Clears the retained session once the user acknowledges the outcome. */
    fun dismiss() {
        sessionHolder.clear()
    }
}
