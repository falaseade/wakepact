package app.wakepact.data.pact

import app.wakepact.domain.model.Pact
import app.wakepact.domain.model.RingEvent
import app.wakepact.domain.model.RingState
import kotlinx.coroutines.flow.Flow

/** Outcome of a gateway operation. The alarm experience must never depend on Success. */
sealed interface GatewayResult<out T> {
    data class Success<T>(val value: T) : GatewayResult<T>
    data object Offline : GatewayResult<Nothing>
    data class Failed(val message: String) : GatewayResult<Nothing>
}

/**
 * Abstraction over the social layer (ADR-003). Two implementations:
 * [FirestorePactGateway] when a Firebase project is configured, otherwise
 * [LocalPactGateway] (solo mode) — chosen once at startup by Hilt.
 */
interface PactGateway {

    /** True when a real backend is configured; false in solo mode. */
    val isLive: Boolean

    /** The uid this device publishes under (auth uid when live, local uid otherwise). */
    suspend fun selfUid(): String

    /** Live snapshot of the pact this user belongs to, or null. */
    fun pact(): Flow<Pact?>

    /** Pact feed (own local ring history in solo mode), newest first, capped at ~50. */
    fun ringEvents(): Flow<List<RingEvent>>

    /** Live view of a single ring event (how the owner hears a buddy's deactivation). */
    fun ringEvent(eventId: String): Flow<RingEvent?>

    suspend fun createPact(pactName: String, displayName: String): GatewayResult<Pact>

    suspend fun joinPact(inviteCode: String, displayName: String): GatewayResult<Pact>

    suspend fun leavePact(): GatewayResult<Unit>

    suspend fun publishRingEvent(event: RingEvent): GatewayResult<Unit>

    suspend fun updateRingEvent(
        eventId: String,
        state: RingState,
        proofAtMs: Long?,
        resolvedAtMs: Long?,
        deactivatedByUid: String? = null,
        deactivatedByName: String? = null,
    ): GatewayResult<Unit>
}
