package app.wakepact.alarmkit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.wakepact.data.alarm.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Re-arms every enabled alarm after reboot (AlarmManager state does not survive it). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val alarms = alarmRepository.enabledAlarms()
                alarms.forEach { scheduler.scheduleNext(it) }
                Timber.i("Boot: rescheduled %d alarm(s)", alarms.size)
            } catch (t: Throwable) {
                Timber.e(t, "Boot reschedule failed")
            } finally {
                pending.finish()
            }
        }
    }
}
