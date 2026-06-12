package app.wakepact.ring

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.wakepact.R
import app.wakepact.alarmkit.AlarmScheduler
import app.wakepact.data.alarm.AlarmRepository
import app.wakepact.data.identity.IdentityRepository
import app.wakepact.data.pact.GatewayResult
import app.wakepact.data.pact.PactGateway
import app.wakepact.domain.RingPolicy
import app.wakepact.domain.StepChainValidator
import app.wakepact.domain.model.Alarm
import app.wakepact.domain.model.RingEvent
import app.wakepact.domain.model.RingState
import app.wakepact.steps.StepSourceSelector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Foreground service that owns a ring from fire to resolution (ADR-002):
 * audio + vibration, the step mission, the unattended-resolution timers
 * (ADR-005) and the pact live-link. RingActivity is only a window onto the
 * [RingSession] this service publishes via [RingSessionHolder].
 */
@AndroidEntryPoint
class RingService : Service() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var identityRepository: IdentityRepository
    @Inject lateinit var gateway: PactGateway
    @Inject lateinit var stepSourceSelector: StepSourceSelector
    @Inject lateinit var sessionHolder: RingSessionHolder
    @Inject lateinit var scheduler: AlarmScheduler

    // Main.immediate keeps all state transitions serialized on one thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var session: RingSession? = null
    private var startedAlarmId: Long? = null // null until the first start command
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var missionJob: Job? = null
    private var pulseJob: Job? = null
    private var resolved = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground immediately — this must precede any suspend work.
        startForegroundWithNotification(getString(R.string.ring_notification_ringing))
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        val started = startedAlarmId
        if (started == null) {
            startedAlarmId = alarmId
            scope.launch { begin(alarmId) }
        } else if (alarmId != -1L && alarmId != started && alarmId != session?.alarmId) {
            // A different alarm fired while this ring is active (REVIEW #1).
            scope.launch { handleOverlappingAlarm(alarmId) }
        }
        return START_STICKY
    }

    /**
     * MVP overlap policy: the live ring keeps the stage; the newcomer skips
     * this occurrence but must not silently die — re-arm a repeating alarm
     * (normally done in [begin]) or switch a one-shot off.
     */
    private suspend fun handleOverlappingAlarm(alarmId: Long) {
        val alarm = alarmRepository.alarm(alarmId) ?: return
        Timber.w(
            "Alarm %d fired while ring %s is active — skipping this occurrence",
            alarmId, session?.recordId ?: "(starting)",
        )
        if (alarm.isRepeating) scheduler.scheduleNext(alarm) else alarmRepository.setEnabled(alarm.id, false)
    }

    /** Builds (or resumes) the session and launches every ring subsystem. */
    private suspend fun begin(requestedAlarmId: Long) {
        val s = createOrResumeSession(requestedAlarmId) ?: run {
            Timber.e("No alarm and no active ring record — stopping service")
            stopSelf()
            return
        }
        session = s
        sessionHolder.set(s)
        acquireWakeLock(s)
        if (s.state.value == RingState.RINGING) {
            startAlarmSound()
            startVibration()
        }
        launchMissionPipeline(s)
        launchPolicyTimers(s)
        launchLiveLink(s)
    }

    private suspend fun createOrResumeSession(requestedAlarmId: Long): RingSession? {
        // START_STICKY restart (or unknown id): resume the unresolved record if any.
        if (requestedAlarmId == -1L) {
            val active = alarmRepository.activeRing() ?: return null
            Timber.w("Resuming active ring %s after process restart", active.event.id)
            val alarm = alarmRepository.alarm(active.alarmId)
            val s = RingSession(
                recordId = active.event.id,
                alarmId = active.alarmId,
                ownerUid = active.event.ownerUid,
                ownerName = active.event.ownerName,
                label = active.event.label,
                firedAtMs = active.event.firedAtMs,
                stepGoal = alarm?.stepGoal ?: Alarm.DEFAULT_STEP_GOAL,
                graceSec = alarm?.graceSec ?: Alarm.DEFAULT_GRACE_SEC,
                maxRingSec = alarm?.maxRingSec ?: Alarm.DEFAULT_MAX_RING_SEC,
            )
            active.event.proofAtMs?.let { s.markProof(it) }
            return s
        }

        val alarm = alarmRepository.alarm(requestedAlarmId) ?: return null
        val identity = identityRepository.current()
        val ownerUid = withTimeoutOrNull(UID_TIMEOUT_MS) { gateway.selfUid() } ?: identity.uid
        val event = RingEvent(
            id = UUID.randomUUID().toString(),
            ownerUid = ownerUid,
            ownerName = identity.displayName,
            label = alarm.label,
            firedAtMs = System.currentTimeMillis(),
            state = RingState.RINGING,
        )
        alarmRepository.insertRingRecord(event, alarm.id)
        // Re-arm repeating alarms now; one-shots switch off for next time.
        if (alarm.isRepeating) scheduler.scheduleNext(alarm) else alarmRepository.setEnabled(alarm.id, false)
        Timber.i(
            "Ring %s started for alarm %d (goal=%d steps, grace=%ds, maxRing=%ds)",
            event.id, alarm.id, alarm.stepGoal, alarm.graceSec, alarm.maxRingSec,
        )
        return RingSession(
            recordId = event.id,
            alarmId = alarm.id,
            ownerUid = ownerUid,
            ownerName = identity.displayName,
            label = alarm.label,
            firedAtMs = event.firedAtMs,
            stepGoal = alarm.stepGoal,
            graceSec = alarm.graceSec,
            maxRingSec = alarm.maxRingSec,
        )
    }

    // --- Step mission (ADR-004) ---

    private fun launchMissionPipeline(s: RingSession) {
        if (s.proofAtMs.value != null) return // resumed past proof
        missionJob = scope.launch {
            val validator = StepChainValidator()
            val source = stepSourceSelector.primary()
            Timber.i("Step mission using %s source, goal=%d", source.name, s.stepGoal)
            source.steps()
                .catch { e ->
                    Timber.e(e, "Primary step source failed — switching to accelerometer fallback")
                    emitAll(stepSourceSelector.fallback().steps())
                }
                .collect { timestampMs ->
                    val validated = validator.onStep(timestampMs)
                    s.updateSteps(validated.coerceAtMost(s.stepGoal))
                    if (validated >= s.stepGoal) {
                        onProofDone(s)
                        cancel() // ends collection and unregisters the sensor
                    }
                }
        }
    }

    private fun onProofDone(s: RingSession) {
        if (s.state.value != RingState.RINGING) return
        val at = System.currentTimeMillis()
        s.markProof(at)
        stopAlarmSound()
        stopVibration()
        Timber.i("Ring %s proof complete (%d steps)", s.recordId, s.stepGoal)
        scope.launch {
            alarmRepository.updateRingRecord(
                s.recordId, RingState.PROOF_DONE,
                proofAtMs = at, resolvedAtMs = null, byUid = null, byName = null,
            )
            if (s.liveMode.value) {
                startPendingPulse(s)
                updateNotification(getString(R.string.ring_notification_pending))
                val result = gateway.updateRingEvent(s.recordId, RingState.PROOF_DONE, at, resolvedAtMs = null)
                if (result !is GatewayResult.Success) {
                    Timber.w("PROOF_DONE publish failed (%s) — grace timer still applies", result)
                }
            } else {
                // Solo (ADR-005): completing the mission deactivates immediately.
                val identity = identityRepository.current()
                resolve(s, RingState.DEACTIVATED, identity.uid, identity.displayName)
            }
        }
    }

    // --- Unattended resolution (ADR-005) ---

    private fun launchPolicyTimers(s: RingSession) {
        scope.launch {
            val deadline = RingPolicy.missedDeadlineMs(s.firedAtMs, s.maxRingSec)
            delay((deadline - System.currentTimeMillis()).coerceAtLeast(0))
            val verdict = RingPolicy.evaluate(
                System.currentTimeMillis(), s.firedAtMs, s.proofAtMs.value, s.graceSec, s.maxRingSec,
            )
            if (verdict == RingState.MISSED && s.state.value == RingState.RINGING) {
                Timber.w("Ring %s missed — no proof within %ds", s.recordId, s.maxRingSec)
                resolve(s, RingState.MISSED, byUid = null, byName = null)
            }
        }
        scope.launch {
            val proofAt = s.proofAtMs.filterNotNull().first()
            val deadline = RingPolicy.autoClearDeadlineMs(proofAt, s.graceSec)
            delay((deadline - System.currentTimeMillis()).coerceAtLeast(0))
            val verdict = RingPolicy.evaluate(
                System.currentTimeMillis(), s.firedAtMs, proofAt, s.graceSec, s.maxRingSec,
            )
            if (verdict == RingState.AUTO_CLEARED && s.state.value == RingState.PROOF_DONE) {
                Timber.i("Ring %s auto-cleared — pact silent for %ds after proof", s.recordId, s.graceSec)
                resolve(s, RingState.AUTO_CLEARED, byUid = null, byName = null)
            }
        }
    }

    // --- Pact live-link ---

    private fun launchLiveLink(s: RingSession) {
        scope.launch {
            val pactId = identityRepository.current().pactId
            if (!gateway.isLive || pactId == null) {
                s.setLiveMode(false)
                resolveNowIfSoloProofDone(s)
                return@launch
            }
            val publish = gateway.publishRingEvent(snapshotEvent(s))
            if (publish !is GatewayResult.Success) {
                Timber.w("Ring publish failed (%s) — solo resolution for this ring", publish)
                s.setLiveMode(false)
                resolveNowIfSoloProofDone(s)
                return@launch
            }
            s.setLiveMode(true)
            if (s.state.value == RingState.PROOF_DONE) {
                startPendingPulse(s)
                updateNotification(getString(R.string.ring_notification_pending))
            }
            gateway.ringEvent(s.recordId).collect { remote ->
                if (remote?.state == RingState.DEACTIVATED) {
                    Timber.i("Ring %s deactivated remotely by %s", s.recordId, remote.deactivatedByName)
                    resolve(
                        s, RingState.DEACTIVATED,
                        remote.deactivatedByUid, remote.deactivatedByName, fromRemote = true,
                    )
                }
            }
        }
    }

    /** Solo semantics: if proof already landed by the time we know we're solo, finish now. */
    private suspend fun resolveNowIfSoloProofDone(s: RingSession) {
        if (s.state.value == RingState.PROOF_DONE) {
            val identity = identityRepository.current()
            resolve(s, RingState.DEACTIVATED, identity.uid, identity.displayName)
        }
    }

    private fun snapshotEvent(s: RingSession): RingEvent = RingEvent(
        id = s.recordId,
        ownerUid = s.ownerUid,
        ownerName = s.ownerName,
        label = s.label,
        firedAtMs = s.firedAtMs,
        state = s.state.value,
        proofAtMs = s.proofAtMs.value,
        resolvedAtMs = null,
        deactivatedByUid = null,
        deactivatedByName = null,
    )

    // --- Resolution ---

    private fun resolve(
        s: RingSession,
        state: RingState,
        byUid: String?,
        byName: String?,
        fromRemote: Boolean = false,
    ) {
        if (resolved) return
        resolved = true
        Timber.i("Ring %s resolved: %s by=%s", s.recordId, state, byName ?: "-")
        missionJob?.cancel()
        pulseJob?.cancel()
        stopAlarmSound()
        stopVibration()
        val at = System.currentTimeMillis()
        s.resolve(state, byName)
        scope.launch {
            alarmRepository.updateRingRecord(s.recordId, state, s.proofAtMs.value, at, byUid, byName)
            if (s.liveMode.value && !fromRemote) {
                val result = gateway.updateRingEvent(s.recordId, state, s.proofAtMs.value, at, byUid, byName)
                if (result !is GatewayResult.Success) {
                    Timber.w("Resolution publish failed (%s) — local record stands", result)
                }
            }
            stopSelf()
        }
    }

    // --- Alarm noise ---

    private fun startAlarmSound() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@RingService, uri)
                isLooping = true
                setWakeMode(this@RingService, PowerManager.PARTIAL_WAKE_LOCK)
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        }.onFailure { Timber.e(it, "Alarm sound failed to start — vibration continues") }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
            }
            player.release()
        }
        mediaPlayer = null
    }

    private fun vibratorInstance(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") // VIBRATOR_SERVICE replaced by VibratorManager in API 31
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private fun startVibration() {
        runCatching {
            val v = vibrator ?: vibratorInstance().also { vibrator = it }
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 500), 0))
        }.onFailure { Timber.e(it, "Vibration failed to start") }
    }

    private fun stopVibration() {
        runCatching { vibrator?.cancel() }
    }

    /** Soft tick every few seconds while waiting for the pact, so a pocketed phone stays noticeable. */
    private fun startPendingPulse(s: RingSession) {
        pulseJob?.cancel()
        pulseJob = scope.launch {
            val v = vibrator ?: vibratorInstance().also { vibrator = it }
            while (s.state.value == RingState.PROOF_DONE) {
                runCatching { v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)) }
                delay(PULSE_PERIOD_MS)
            }
        }
    }

    // --- Wake lock ---

    private fun acquireWakeLock(s: RingSession) {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                acquire((s.maxRingSec + s.graceSec + 60) * 1_000L)
            }
        }.onFailure { Timber.e(it, "Wake lock acquire failed") }
    }

    // --- Notification ---

    private fun startForegroundWithNotification(text: String) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(text), type)
    }

    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS guarded by canNotify() just below
    private fun updateNotification(text: String) {
        if (!canNotify()) return
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun buildNotification(text: String): Notification {
        val fullScreen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.ring_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.ring_channel_description)
            setSound(null, null) // MediaPlayer owns the audio
            enableVibration(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopAlarmSound()
        stopVibration()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        // sessionHolder intentionally NOT cleared: RingActivity shows the
        // resolution until the user taps Done; the next ring replaces it.
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val CHANNEL_ID = "wakepact.ring"
        private const val NOTIFICATION_ID = 0xA1A
        private const val WAKE_LOCK_TAG = "wakepact:ring"
        private const val UID_TIMEOUT_MS = 3_000L
        private const val PULSE_PERIOD_MS = 5_000L
    }
}
