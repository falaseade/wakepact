package app.wakepact.steps

import kotlinx.coroutines.flow.Flow

/** A cold stream of raw step detections, one epoch-ms timestamp per step. */
interface StepSource {
    /** Short name for logging ("hardware" / "accelerometer"). */
    val name: String

    /**
     * Emits a timestamp per detected step while collected; registering on
     * collect, unregistering on cancellation. Fails the flow if the sensor is
     * missing or registration is refused.
     */
    fun steps(): Flow<Long>
}
