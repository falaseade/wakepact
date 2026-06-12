package app.wakepact.feature.pact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wakepact.data.identity.IdentityRepository
import app.wakepact.data.pact.GatewayResult
import app.wakepact.data.pact.PactGateway
import app.wakepact.domain.model.Pact
import app.wakepact.domain.model.RingEvent
import app.wakepact.domain.model.RingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-shot user-facing outcomes; the UI maps these to string resources. */
enum class PactMessage {
    OFFLINE,
    CREATE_FAILED,
    JOIN_FAILED,
    LEFT,
    DEACTIVATED_OK,
    NAME_SAVED,
}

data class PactUiState(
    val isLive: Boolean = false,
    val displayName: String = "",
    val pact: Pact? = null,
    /** Unresolved rings owned by *other* members — the ones that need this user. */
    val pending: List<RingEvent> = emptyList(),
    val feed: List<RingEvent> = emptyList(),
    val busy: Boolean = false,
)

@HiltViewModel
class PactViewModel @Inject constructor(
    private val gateway: PactGateway,
    private val identityRepository: IdentityRepository,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val selfUidFlow = flow { emit(gateway.selfUid()) }

    private val _messages = Channel<PactMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { identityRepository.ensureDisplayName() }
    }

    val uiState: StateFlow<PactUiState> = combine(
        identityRepository.identity,
        gateway.pact(),
        gateway.ringEvents(),
        selfUidFlow,
        busy,
    ) { identity, pact, events, selfUid, isBusy ->
        PactUiState(
            isLive = gateway.isLive,
            displayName = identity.displayName,
            pact = pact,
            pending = events.filter { it.ownerUid != selfUid && !it.state.isResolved },
            feed = events,
            busy = isBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PactUiState(isLive = gateway.isLive))

    fun setDisplayName(name: String) {
        viewModelScope.launch {
            identityRepository.setDisplayName(name)
            _messages.send(PactMessage.NAME_SAVED)
        }
    }

    fun createPact(pactName: String) {
        val trimmed = pactName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            busy.value = true
            val name = identityRepository.ensureDisplayName()
            when (gateway.createPact(trimmed, name)) {
                is GatewayResult.Success -> Unit // pact flow updates the UI
                GatewayResult.Offline -> _messages.send(PactMessage.OFFLINE)
                is GatewayResult.Failed -> _messages.send(PactMessage.CREATE_FAILED)
            }
            busy.value = false
        }
    }

    fun joinPact(inviteCode: String) {
        if (inviteCode.isBlank()) return
        viewModelScope.launch {
            busy.value = true
            val name = identityRepository.ensureDisplayName()
            when (gateway.joinPact(inviteCode, name)) {
                is GatewayResult.Success -> Unit
                GatewayResult.Offline -> _messages.send(PactMessage.OFFLINE)
                is GatewayResult.Failed -> _messages.send(PactMessage.JOIN_FAILED)
            }
            busy.value = false
        }
    }

    fun leavePact() {
        viewModelScope.launch {
            busy.value = true
            when (gateway.leavePact()) {
                is GatewayResult.Success -> _messages.send(PactMessage.LEFT)
                GatewayResult.Offline -> _messages.send(PactMessage.OFFLINE)
                is GatewayResult.Failed -> _messages.send(PactMessage.OFFLINE)
            }
            busy.value = false
        }
    }

    /** The core social action: a buddy switches off the owner's proven alarm. */
    fun deactivate(eventId: String) {
        viewModelScope.launch {
            busy.value = true
            // Auth uid (not the local DataStore uid) so security rules can verify it.
            val uid = gateway.selfUid()
            val name = identityRepository.ensureDisplayName()
            val result = gateway.updateRingEvent(
                eventId = eventId,
                state = RingState.DEACTIVATED,
                proofAtMs = null, // preserve the owner's value
                resolvedAtMs = System.currentTimeMillis(),
                deactivatedByUid = uid,
                deactivatedByName = name,
            )
            when (result) {
                is GatewayResult.Success -> _messages.send(PactMessage.DEACTIVATED_OK)
                GatewayResult.Offline -> _messages.send(PactMessage.OFFLINE)
                is GatewayResult.Failed -> _messages.send(PactMessage.OFFLINE)
            }
            busy.value = false
        }
    }
}
