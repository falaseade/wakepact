package app.wakepact.data.pact

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.wakepact.MainActivity
import app.wakepact.R
import app.wakepact.data.identity.IdentityRepository
import app.wakepact.domain.PactPushPolicy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

/**
 * Receives the data-only FCM push the Cloud Function sends to a pact's topic and
 * turns it into a "your pact-mate is up — flip the off-switch" heads-up.
 *
 * Data-only (not a notification message) is deliberate: it runs even when the
 * app is backgrounded *and* lets us suppress the owner's own ring locally
 * ([PactPushPolicy]) rather than buzzing the person who just did the walking.
 */
@AndroidEntryPoint
class PactMessagingService : FirebaseMessagingService() {

    @Inject lateinit var identityRepository: IdentityRepository
    @Inject lateinit var messaging: PactMessaging

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val selfUid = currentUid()
        if (!PactPushPolicy.shouldNotifyDeactivation(data[KEY_STATE], data[KEY_OWNER_UID], selfUid)) return

        val ownerName = data[KEY_OWNER_NAME]?.takeIf { it.isNotBlank() }
            ?: getString(R.string.push_buddy_fallback)
        val label = data[KEY_LABEL].orEmpty()
        val eventId = data[KEY_EVENT_ID].orEmpty()
        postDeactivationNotification(ownerName, label, eventId)
    }

    /**
     * A token refresh can drop topic subscriptions; re-subscribe to the current
     * pact so buddy push survives it.
     */
    override fun onNewToken(token: String) {
        val pactId = runCatching { runBlocking { identityRepository.current().pactId } }.getOrNull() ?: return
        runCatching { runBlocking { messaging.subscribe(pactId) } }
            .onFailure { Timber.w(it, "Re-subscribe after token refresh failed") }
    }

    private fun currentUid(): String? =
        runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
            ?: runCatching { runBlocking { identityRepository.current().uid } }.getOrNull()

    private fun postDeactivationNotification(ownerName: String, label: String, eventId: String) {
        // Auto-granted below API 33, a runtime grant at 33+. Checking
        // unconditionally is correct on every level and is the form lint trusts.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Timber.i("Notifications not permitted — buddy still has the in-app Pact card")
            return
        }
        ensureChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_PACT, true)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            eventId.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val body = if (label.isBlank()) getString(R.string.push_proof_body)
        else getString(R.string.push_proof_body_labeled, label)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.push_proof_title, ownerName))
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(this).notify(eventId.hashCode(), notification)
        }.onFailure { Timber.w(it, "Posting buddy push notification failed") }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.pact_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = getString(R.string.pact_channel_description) },
        )
    }

    private companion object {
        const val CHANNEL_ID = "pact_alerts"
        const val KEY_STATE = "state"
        const val KEY_OWNER_UID = "ownerUid"
        const val KEY_OWNER_NAME = "ownerName"
        const val KEY_LABEL = "label"
        const val KEY_EVENT_ID = "eventId"
    }
}
