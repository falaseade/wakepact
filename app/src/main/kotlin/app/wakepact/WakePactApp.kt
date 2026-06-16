package app.wakepact

import android.app.Application
import android.os.StrictMode
import app.wakepact.data.pact.FirebaseProvider
import app.wakepact.data.pact.PactSubscriptionSync
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class WakePactApp : Application() {

    @Inject lateinit var firebaseProvider: FirebaseProvider
    @Inject lateinit var pactSubscriptionSync: PactSubscriptionSync

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().penaltyLog().build()
            )
        }
        // Force Firebase init early when configured so FCM can register for buddy
        // push; null and harmless in solo mode (ADR-003).
        firebaseProvider.app
        // Mirror FCM topic membership to the stored pact id for the app's lifetime.
        pactSubscriptionSync.start(appScope)
        Timber.i("WakePact application created")
    }
}
