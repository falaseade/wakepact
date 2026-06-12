package app.wakepact.domain

/**
 * Anti-spoof step accounting (ADR-004).
 *
 * A raw step event only *counts* when it is part of a rhythmic chain:
 *  - the interval since the previous step must lie in [minIntervalMs, maxIntervalMs]
 *    (~0.5–4 steps/second — outside that is shaking or random jolts);
 *  - a chain must reach [minChainLength] steps before any of it is credited; the first
 *    two steps are provisional and credited retroactively when the third lands;
 *  - any out-of-rhythm event breaks the chain and starts a new provisional one.
 *
 * Honest threat model: this defeats lazy single shakes, table taps and slow phone
 * nudges from bed. A determined person rhythmically swinging the phone for the full
 * goal is doing arm cardio at 6 AM — we call that a win, not a bypass.
 */
class StepChainValidator(
    private val minIntervalMs: Long = 250L,
    private val maxIntervalMs: Long = 2_000L,
    private val minChainLength: Int = 3,
) {
    private var lastStepAtMs: Long = NO_STEP
    private var chainLength: Int = 0

    /** Total steps credited so far. */
    var validatedSteps: Int = 0
        private set

    /**
     * Feed one raw step event; returns the updated [validatedSteps] count.
     */
    fun onStep(timestampMs: Long): Int {
        val interval = timestampMs - lastStepAtMs
        chainLength = if (lastStepAtMs == NO_STEP || interval !in minIntervalMs..maxIntervalMs) {
            1
        } else {
            chainLength + 1
        }
        lastStepAtMs = timestampMs
        when {
            chainLength == minChainLength -> validatedSteps += minChainLength
            chainLength > minChainLength -> validatedSteps += 1
        }
        return validatedSteps
    }

    fun reset() {
        lastStepAtMs = NO_STEP
        chainLength = 0
        validatedSteps = 0
    }

    private companion object {
        const val NO_STEP = Long.MIN_VALUE
    }
}
