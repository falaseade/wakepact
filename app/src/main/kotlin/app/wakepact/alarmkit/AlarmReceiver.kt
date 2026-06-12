package app.wakepact.alarmkit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.wakepact.ring.RingService
import timber.log.Timber

/** Receives the AlarmManager fire and hands off to the foreground RingService. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId == -1L) return
        Timber.i("Alarm %d fired — starting RingService", alarmId)
        ContextCompat.startForegroundService(
            context,
            Intent(context, RingService::class.java).putExtra(RingService.EXTRA_ALARM_ID, alarmId),
        )
    }

    companion object {
        const val ACTION_FIRE = "app.wakepact.action.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
