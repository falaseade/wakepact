package app.wakepact.testutil

import app.wakepact.data.pact.GatewayResult
import app.wakepact.data.pact.PactGateway
import app.wakepact.domain.model.Pact
import app.wakepact.domain.model.PactMember
import app.wakepact.domain.model.RingEvent
import app.wakepact.domain.model.RingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory gateway fake: behaves like a tiny backend (writes mutate the
 * flows) and records calls, with injectable failure results per operation.
 */
class FakePactGateway(
    override var isLive: Boolean = true,
    private val uid: String = "self-uid",
) : PactGateway {

    val pactFlow = MutableStateFlow<Pact?>(null)
    val eventsFlow = MutableStateFlow<List<RingEvent>>(emptyList())

    var createResult: GatewayResult<Pact>? = null // null = behave normally
    var joinResult: GatewayResult<Pact>? = null
    var leaveResult: GatewayResult<Unit> = GatewayResult.Success(Unit)
    var publishResult: GatewayResult<Unit> = GatewayResult.Success(Unit)
    var updateResult: GatewayResult<Unit> = GatewayResult.Success(Unit)

    data class UpdateCall(
        val eventId: String,
        val state: RingState,
        val proofAtMs: Long?,
        val resolvedAtMs: Long?,
        val byUid: String?,
        val byName: String?,
    )

    val updateCalls = mutableListOf<UpdateCall>()
    val createCalls = mutableListOf<Pair<String, String>>()
    val joinCalls = mutableListOf<Pair<String, String>>()

    override suspend fun selfUid(): String = uid

    override fun pact(): Flow<Pact?> = pactFlow

    override fun ringEvents(): Flow<List<RingEvent>> = eventsFlow

    override fun ringEvent(eventId: String): Flow<RingEvent?> =
        eventsFlow.map { list -> list.find { it.id == eventId } }

    override suspend fun createPact(pactName: String, displayName: String): GatewayResult<Pact> {
        createCalls += pactName to displayName
        createResult?.let { return it }
        val pact = Pact("pact-1", pactName, "ABC234", listOf(PactMember(uid, displayName)))
        pactFlow.value = pact
        return GatewayResult.Success(pact)
    }

    override suspend fun joinPact(inviteCode: String, displayName: String): GatewayResult<Pact> {
        joinCalls += inviteCode to displayName
        joinResult?.let { return it }
        val existing = pactFlow.value ?: Pact("pact-1", "Flat 4B", inviteCode, emptyList())
        val joined = existing.copy(members = existing.members + PactMember(uid, displayName))
        pactFlow.value = joined
        return GatewayResult.Success(joined)
    }

    override suspend fun leavePact(): GatewayResult<Unit> {
        if (leaveResult is GatewayResult.Success) pactFlow.value = null
        return leaveResult
    }

    override suspend fun publishRingEvent(event: RingEvent): GatewayResult<Unit> {
        if (publishResult is GatewayResult.Success) {
            eventsFlow.value = listOf(event) + eventsFlow.value.filterNot { it.id == event.id }
        }
        return publishResult
    }

    override suspend fun updateRingEvent(
        eventId: String,
        state: RingState,
        proofAtMs: Long?,
        resolvedAtMs: Long?,
        deactivatedByUid: String?,
        deactivatedByName: String?,
    ): GatewayResult<Unit> {
        updateCalls += UpdateCall(eventId, state, proofAtMs, resolvedAtMs, deactivatedByUid, deactivatedByName)
        if (updateResult is GatewayResult.Success) {
            eventsFlow.value = eventsFlow.value.map { event ->
                if (event.id != eventId) event else event.copy(
                    state = state,
                    proofAtMs = proofAtMs ?: event.proofAtMs,
                    resolvedAtMs = resolvedAtMs ?: event.resolvedAtMs,
                    deactivatedByUid = deactivatedByUid ?: event.deactivatedByUid,
                    deactivatedByName = deactivatedByName ?: event.deactivatedByName,
                )
            }
        }
        return updateResult
    }
}
