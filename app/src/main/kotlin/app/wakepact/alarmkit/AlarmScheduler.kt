package app.wakepact.alarmkit

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.wakepact.MainActivity
import app.wakepact.domain.NextTriggerCalculator
import app.wakepact.domain.model.Alarm
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single owner of AlarmManager interactions. `setAlarmClock` makes the alarm
 * exempt from Doze and puts the system clock icon in the status bar; delivery
 * via [AlarmReceiver] grants the temporary allowlist that lets [app.wakepact.ring.RingService]
 * start as a foreground service from the background (ADR-002).
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calculator: NextTriggerCalculator,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Schedules the next occurrence of [alarm]; returns the trigger epoch ms. */
    @SuppressLint("ScheduleExactAlarm") // guarded by canScheduleExact() just below
    fun scheduleNext(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()): Long {
        val triggerAtMs = calculator.nextTriggerMs(alarm, now)
        val firePi = firePendingIntent(alarm.id)
        if (canScheduleExact()) {
            val showPi = PendingIntent.getActivity(
                context,
                alarm.id.toInt(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, showPi), firePi)
        } else {
            Timber.w("Exact alarms unavailable — falling back to inexact while-idle alarm")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, firePi)
        }
        Timber.i("Scheduled alarm %d at %d", alarm.id, triggerAtMs)
        return triggerAtMs
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(firePendingIntent(alarmId))
        Timber.i("Cancelled alarm %d", alarmId)
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun firePendingIntent(alarmId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_FIRE
                putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
