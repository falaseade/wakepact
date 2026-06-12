package app.wakepact.steps

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Hardware step detector (TYPE_STEP_DETECTOR): one event per step from the
 * low-power sensor hub. Needs ACTIVITY_RECOGNITION on API 29+, which is why
 * [StepSourceSelector] checks the permission before choosing this source.
 */
class HardwareStepSource(
    private val sensorManager: SensorManager,
) : StepSource {

    override val name: String = "hardware"

    override fun steps(): Flow<Long> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (sensor == null) {
            close(IllegalStateException("No hardware step detector on this device"))
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(System.currentTimeMillis())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        if (!registered) {
            close(IllegalStateException("Step detector registration refused"))
            return@callbackFlow
        }
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
