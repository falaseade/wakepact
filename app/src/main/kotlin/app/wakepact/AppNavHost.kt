package app.wakepact

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.wakepact.feature.alarms.AlarmsRoute
import app.wakepact.feature.editor.EditorRoutePage
import app.wakepact.feature.pact.PactRoute
import kotlinx.serialization.Serializable

@Serializable
object AlarmsDest

@Serializable
data class EditorDest(val alarmId: Long)

@Serializable
object PactDest

@Composable
fun AppNavHost(
    openPactSignal: Boolean = false,
    onPactConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    // A buddy-push tap deep-links straight to the Pact tab.
    LaunchedEffect(openPactSignal) {
        if (openPactSignal) {
            navController.navigate(PactDest) { launchSingleTop = true }
            onPactConsumed()
        }
    }
    NavHost(navController = navController, startDestination = AlarmsDest) {
        composable<AlarmsDest> {
            AlarmsRoute(
                onEditAlarm = { id -> navController.navigate(EditorDest(id)) },
                onAddAlarm = { navController.navigate(EditorDest(alarmId = -1L)) },
                onOpenPact = { navController.navigate(PactDest) },
            )
        }
        composable<EditorDest> { entry ->
            val dest = entry.toRoute<EditorDest>()
            EditorRoutePage(
                alarmId = dest.alarmId,
                onClose = { navController.popBackStack() },
            )
        }
        composable<PactDest> {
            PactRoute(onBack = { navController.popBackStack() })
        }
    }
}
