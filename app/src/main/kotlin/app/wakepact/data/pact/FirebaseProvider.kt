package app.wakepact.data.pact

import android.content.Context
import app.wakepact.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initialises Firebase programmatically from BuildConfig fields sourced from
 * local.properties (ADR-003). No google-services.json, no Gradle plugin: an
 * absent or broken configuration degrades to solo mode instead of breaking
 * the build or the alarm.
 */
@Singleton
class FirebaseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val app: FirebaseApp? by lazy {
        val projectId: String? = BuildConfig.FIREBASE_PROJECT_ID
        val applicationId: String? = BuildConfig.FIREBASE_APP_ID
        val apiKey: String? = BuildConfig.FIREBASE_API_KEY
        // Sender ID (project number) is optional: it only unlocks FCM buddy push.
        // Without it Firestore/Auth still work, so don't gate solo-vs-live on it.
        val senderId: String? = BuildConfig.FIREBASE_SENDER_ID
        if (projectId.isNullOrBlank() || applicationId.isNullOrBlank() || apiKey.isNullOrBlank()) {
            Timber.i("Firebase not configured — WakePact running in solo mode")
            null
        } else {
            runCatching {
                FirebaseApp.getApps(context).firstOrNull()
                    ?: FirebaseApp.initializeApp(
                        context,
                        FirebaseOptions.Builder()
                            .setProjectId(projectId)
                            .setApplicationId(applicationId)
                            .setApiKey(apiKey)
                            // Required for FCM registration; harmless when blank.
                            .apply { if (!senderId.isNullOrBlank()) setGcmSenderId(senderId) }
                            .build(),
                    )
            }.onFailure {
                Timber.e(it, "Firebase initialisation failed — falling back to solo mode")
            }.getOrNull()
        }
    }
}
