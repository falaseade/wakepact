package app.wakepact.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the best available step source at ring time: hardware step detector
 * when present and permitted, accelerometer peak detection otherwise.
 */
@Singleton
class StepSourceSelector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun primary(): StepSource {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
        val hasHardware = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
        return if (permitted && hasHardware) HardwareStepSource(sensorManager) else fallback()
    }

    fun fallback(): StepSource = AccelStepSource(sensorManager)
}
