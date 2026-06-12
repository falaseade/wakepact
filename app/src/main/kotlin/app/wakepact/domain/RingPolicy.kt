package app.wakepact.domain

import app.wakepact.domain.model.RingState

/**
 * Pure unattended-alarm resolution rules (ADR-005):
 *  - PROOF_DONE + grace window elapsed with no pact confirmation -> AUTO_CLEARED;
 *  - still RINGING at the max-ring cap (no proof) -> MISSED;
 *  - once proof is done, the max-ring cap no longer applies (the loud phase is over;
 *    the grace timer governs).
 */
object RingPolicy {

    /** Epoch ms after which a proof-less ring resolves as MISSED. */
    fun missedDeadlineMs(firedAtMs: Long, maxRingSec: Int): Long =
        firedAtMs + maxRingSec * 1_000L

    /** Epoch ms after which an unconfirmed proof resolves as AUTO_CLEARED. */
    fun autoClearDeadlineMs(proofAtMs: Long, graceSec: Int): Long =
        proofAtMs + graceSec * 1_000L

    /**
     * @return the terminal state the ring should transition to at [nowMs],
     * or null if it should keep going.
     */
    fun evaluate(
        nowMs: Long,
        firedAtMs: Long,
        proofAtMs: Long?,
        graceSec: Int,
        maxRingSec: Int,
    ): RingState? = when {
        proofAtMs != null && nowMs >= autoClearDeadlineMs(proofAtMs, graceSec) -> RingState.AUTO_CLEARED
        proofAtMs == null && nowMs >= missedDeadlineMs(firedAtMs, maxRingSec) -> RingState.MISSED
        else -> null
    }
}
