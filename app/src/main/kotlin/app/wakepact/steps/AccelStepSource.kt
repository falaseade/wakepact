package app.wakepact.steps

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

/**
 * Accelerometer peak-detection fallback for devices without a step detector
 * (or without the ACTIVITY_RECOGNITION grant). Gravity is removed with a
 * low-pass filter; a step is a hysteresis-gated peak in the linear-acceleration
 * magnitude with an adaptive threshold and a refractory period. Deliberately
 * permissive — the cadence gate lives in StepChainValidator (ADR-004).
 */
class AccelStepSource(
    private val sensorManager: SensorManager,
) : StepSource {

    override val name: String = "accelerometer"

    override fun steps(): Flow<Long> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            close(IllegalStateException("No accelerometer on this device"))
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            private val gravity = FloatArray(3)
            private var initialized = false
            private var emaPeak = 3f
            private var lastStepAtMs = 0L
            private var abovePeak = false

            override fun onSensorChanged(event: SensorEvent) {
                if (!initialized) {
                    event.values.copyInto(gravity)
                    initialized = true
                    return
                }
                for (i in 0..2) gravity[i] = GRAVITY_ALPHA * gravity[i] + (1f - GRAVITY_ALPHA) * event.values[i]
                val x = event.values[0] - gravity[0]
                val y = event.values[1] - gravity[1]
                val z = event.values[2] - gravity[2]
                val magnitude = sqrt(x * x + y * y + z * z)
                val threshold = (emaPeak * 0.5f).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
                if (magnitude > threshold && !abovePeak) {
                    abovePeak = true
                    val now = System.currentTimeMillis()
                    if (now - lastStepAtMs >= REFRACTORY_MS) {
                        lastStepAtMs = now
                        emaPeak = 0.7f * emaPeak + 0.3f * magnitude
                        trySend(now)
                    }
                } else if (magnitude < threshold * RELEASE_FRACTION) {
                    abovePeak = false
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        if (!registered) {
            close(IllegalStateException("Accelerometer registration refused"))
            return@callbackFlow
        }
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    private companion object {
        const val GRAVITY_ALPHA = 0.8f
        const val MIN_THRESHOLD = 1.5f
        const val MAX_THRESHOLD = 4f
        const val RELEASE_FRACTION = 0.6f
        const val REFRACTORY_MS = 300L
    }
}
