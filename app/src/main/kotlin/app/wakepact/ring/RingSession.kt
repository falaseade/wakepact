package app.wakepact.ring

import app.wakepact.domain.model.RingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observable state of one ring. Written only by [RingService], read by
 * [RingViewModel] via [RingSessionHolder] (ADR-002: the service owns the
 * session; the activity is a dumb window onto it).
 */
class RingSession(
    val recordId: String,
    val alarmId: Long,
    val ownerUid: String,
    val ownerName: String,
    val label: String,
    val firedAtMs: Long,
    val stepGoal: Int,
    val graceSec: Int,
    val maxRingSec: Int,
) {
    private val _state = MutableStateFlow(RingState.RINGING)
    val state: StateFlow<RingState> = _state.asStateFlow()

    private val _steps = MutableStateFlow(0)
    val steps: StateFlow<Int> = _steps.asStateFlow()

    private val _proofAtMs = MutableStateFlow<Long?>(null)
    val proofAtMs: StateFlow<Long?> = _proofAtMs.asStateFlow()

    private val _deactivatedByName = MutableStateFlow<String?>(null)
    val deactivatedByName: StateFlow<String?> = _deactivatedByName.asStateFlow()

    private val _liveMode = MutableStateFlow(false)
    val liveMode: StateFlow<Boolean> = _liveMode.asStateFlow()

    fun updateSteps(count: Int) {
        _steps.value = count
    }

    fun markProof(atMs: Long) {
        _proofAtMs.value = atMs
        _state.value = RingState.PROOF_DONE
    }

    fun setLiveMode(live: Boolean) {
        _liveMode.value = live
    }

    fun resolve(state: RingState, byName: String?) {
        _deactivatedByName.value = byName
        _state.value = state
    }
}

/**
 * Process-wide slot for the current ring session. Deliberately NOT cleared
 * when the service stops: RingActivity keeps showing the resolution screen
 * until the user taps Done (or the next ring replaces it).
 */
@Singleton
class RingSessionHolder @Inject constructor() {
    private val _session = MutableStateFlow<RingSession?>(null)
    val session: StateFlow<RingSession?> = _session.asStateFlow()

    fun set(session: RingSession) {
        _session.value = session
    }

    fun clear() {
        _session.value = null
    }
}
