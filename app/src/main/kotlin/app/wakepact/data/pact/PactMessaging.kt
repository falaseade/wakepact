package app.wakepact.data.pact

import app.wakepact.core.util.awaitTask
import com.google.firebase.messaging.FirebaseMessaging
import timber.log.Timber

/**
 * Pact topic membership for FCM buddy push. Each pact maps to the topic
 * `pact-{pactId}`; the Cloud Function publishes a member's `PROOF_DONE` to it
 * (see docs/FIREBASE_SETUP.md and functions/index.js). Subscribing is how a
 * device opts in to being woken when a pact-mate needs the off-switch flipped.
 *
 * Two implementations, chosen by Hilt: [FcmPactMessaging] when Firebase is
 * configured, [NoOpPactMessaging] in solo mode.
 */
interface PactMessaging {
    suspend fun subscribe(pactId: String)
    suspend fun unsubscribe(pactId: String)

    companion object {
        /** Topic name shared with the Cloud Function — keep both in sync. */
        fun topicFor(pactId: String): String = "pact-$pactId"
    }
}

/** FCM-backed topic membership. Failures are logged, never thrown: a missed
 *  subscription only costs a push, never the alarm or the in-app deactivation. */
class FcmPactMessaging : PactMessaging {

    override suspend fun subscribe(pactId: String) {
        runCatching {
            FirebaseMessaging.getInstance().subscribeToTopic(PactMessaging.topicFor(pactId)).awaitTask()
        }.onFailure { Timber.w(it, "Pact topic subscribe failed for %s", pactId) }
    }

    override suspend fun unsubscribe(pactId: String) {
        runCatching {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(PactMessaging.topicFor(pactId)).awaitTask()
        }.onFailure { Timber.w(it, "Pact topic unsubscribe failed for %s", pactId) }
    }
}

/** Solo mode: no backend, nothing to subscribe to. */
object NoOpPactMessaging : PactMessaging {
    override suspend fun subscribe(pactId: String) = Unit
    override suspend fun unsubscribe(pactId: String) = Unit
}
