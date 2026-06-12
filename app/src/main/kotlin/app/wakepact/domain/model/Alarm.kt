package app.wakepact.domain.model

/**
 * Domain model for a wake pact alarm.
 *
 * [daysMask] bit convention: bit 0 = Monday … bit 6 = Sunday (ISO `DayOfWeek.value - 1`).
 * A mask of 0 means a one-shot alarm (next occurrence of [hour]:[minute], then auto-disables).
 */
data class Alarm(
    val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val daysMask: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val stepGoal: Int = DEFAULT_STEP_GOAL,
    val graceSec: Int = DEFAULT_GRACE_SEC,
    val maxRingSec: Int = DEFAULT_MAX_RING_SEC,
) {
    val isRepeating: Boolean get() = daysMask != 0

    companion object {
        const val DEFAULT_STEP_GOAL = 30
        const val MIN_STEP_GOAL = 10
        const val MAX_STEP_GOAL = 100
        const val DEFAULT_GRACE_SEC = 180
        const val MIN_GRACE_SEC = 60
        const val MAX_GRACE_SEC = 600
        const val DEFAULT_MAX_RING_SEC = 600
        const val MIN_MAX_RING_SEC = 300
        const val MAX_MAX_RING_SEC = 1800
    }
}
