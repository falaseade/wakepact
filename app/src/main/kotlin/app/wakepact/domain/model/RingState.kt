package app.wakepact.domain.model

/**
 * Lifecycle of a single ring (see ADR-005).
 *
 * RINGING      — alarm firing at full volume; owner must complete the step mission.
 * PROOF_DONE   — step goal reached; sound drops to a pending pulse; pact asked to confirm.
 * DEACTIVATED  — a pact member (or the owner themself in solo mode) switched it off.
 * AUTO_CLEARED — proof was done but no pact member confirmed within the grace window.
 * MISSED       — no proof before the max-ring cap; the owner slept through.
 */
enum class RingState { RINGING, PROOF_DONE, DEACTIVATED, AUTO_CLEARED, MISSED;

    val isResolved: Boolean get() = this == DEACTIVATED || this == AUTO_CLEARED || this == MISSED
}

/** A ring event as shared with (and observed from) the pact. */
data class RingEvent(
    val id: String,
    val ownerUid: String,
    val ownerName: String,
    val label: String,
    val firedAtMs: Long,
    val state: RingState,
    val proofAtMs: Long? = null,
    val resolvedAtMs: Long? = null,
    val deactivatedByUid: String? = null,
    val deactivatedByName: String? = null,
)
