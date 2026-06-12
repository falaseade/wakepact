package app.wakepact.ring

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.wakepact.core.ui.theme.WakePactTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Lock-screen window onto the current [RingSession] (ADR-002). Holds no ring
 * logic: killing this activity changes nothing about the alarm. Back is
 * swallowed while the ring is unresolved — the only ways out are walking,
 * the pact, or the policy timers.
 */
@AndroidEntryPoint
class RingActivity : ComponentActivity() {

    @Inject lateinit var sessionHolder: RingSessionHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION") // pre-API-27 path for setShowWhenLocked/setTurnScreenOn
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) {
            val state = sessionHolder.session.value?.state?.value
            if (state == null || state.isResolved) finish()
            // otherwise swallow: an unresolved alarm is not dismissible with Back
        }
        setContent {
            WakePactTheme {
                RingRoute(onDone = { finish() })
            }
        }
    }
}
