package app.wakepact

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.wakepact.core.ui.theme.WakePactTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Flipped true when launched from a buddy-push notification so the nav host
    // jumps straight to the Pact tab and its pending deactivate card.
    private var openPact by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openPact = intent?.shouldOpenPact() == true
        setContent {
            WakePactTheme {
                AppNavHost(
                    openPactSignal = openPact,
                    onPactConsumed = { openPact = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.shouldOpenPact()) openPact = true
    }

    private fun Intent.shouldOpenPact(): Boolean = getBooleanExtra(EXTRA_OPEN_PACT, false)

    companion object {
        const val EXTRA_OPEN_PACT = "app.wakepact.OPEN_PACT"
    }
}
