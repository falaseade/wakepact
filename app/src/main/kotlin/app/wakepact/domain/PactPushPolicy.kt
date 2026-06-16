package app.wakepact.domain

import app.wakepact.domain.model.RingState

/**
 * Pure decision for buddy push (ADR-004/005 style: keep the rule pure so it is
 * unit-tested without Android or FCM).
 *
 * A pact member's phone should chime only when *another* member finishes their
 * wake-proof (`PROOF_DONE`) and therefore needs someone to flip the off-switch.
 * It must never buzz the owner about their own proof — that device is already
 * awake and looking at the ring screen — and it must ignore every other state
 * transition (`DEACTIVATED` / `AUTO_CLEARED` / `MISSED` are resolutions, not
 * calls for help).
 */
object PactPushPolicy {

    /**
     * @param incomingState the `state` field carried by the push payload.
     * @param ownerUid the uid of the member whose alarm fired.
     * @param selfUid this device's pact uid (Firebase auth uid when live).
     */
    fun shouldNotifyDeactivation(
        incomingState: String?,
        ownerUid: String?,
        selfUid: String?,
    ): Boolean {
        if (incomingState != RingState.PROOF_DONE.name) return false
        if (ownerUid.isNullOrBlank()) return false
        return ownerUid != selfUid
    }
}
