package app.wakepact.data.pact

import app.wakepact.data.alarm.AlarmRepository
import app.wakepact.data.identity.IdentityRepository
import app.wakepact.domain.model.Pact
import app.wakepact.domain.model.RingEvent
import app.wakepact.domain.model.RingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Solo-mode gateway: no backend, no pact. The feed is the user's own local
 * ring history; remote deactivation never arrives, so the RingService resolves
 * rings itself the moment proof completes (ADR-005).
 */
class LocalPactGateway(
    private val identityRepository: IdentityRepository,
    private val alarmRepository: AlarmRepository,
) : PactGateway {

    override val isLive: Boolean = false

    override suspend fun selfUid(): String = identityRepository.ensureUid()

    override fun pact(): Flow<Pact?> = flowOf(null)

    override fun ringEvents(): Flow<List<RingEvent>> = alarmRepository.recentRingEvents()

    override fun ringEvent(eventId: String): Flow<RingEvent?> =
        MutableStateFlow<RingEvent?>(null).asStateFlow()

    override suspend fun createPact(pactName: String, displayName: String): GatewayResult<Pact> =
        GatewayResult.Failed("Pacts need a configured Firebase backend (solo mode)")

    override suspend fun joinPact(inviteCode: String, displayName: String): GatewayResult<Pact> =
        GatewayResult.Failed("Pacts need a configured Firebase backend (solo mode)")

    override suspend fun leavePact(): GatewayResult<Unit> = GatewayResult.Success(Unit)

    override suspend fun publishRingEvent(event: RingEvent): GatewayResult<Unit> =
        GatewayResult.Success(Unit) // the local record is already the source of truth

    override suspend fun updateRingEvent(
        eventId: String,
        state: RingState,
        proofAtMs: Long?,
        resolvedAtMs: Long?,
        deactivatedByUid: String?,
        deactivatedByName: String?,
    ): GatewayResult<Unit> = GatewayResult.Success(Unit)
}
