package app.wakepact.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Static fallback palette: night-indigo base with an alarm-amber accent.
private val Indigo80 = Color(0xFFBFC3FF)
private val Indigo40 = Color(0xFF4A50C2)
private val Amber80 = Color(0xFFFFB95C)
private val Amber40 = Color(0xFF8A5100)
private val Teal80 = Color(0xFF7FD8C9)
private val Teal40 = Color(0xFF006B5D)

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    secondary = Amber80,
    tertiary = Teal80,
)

private val LightColors = lightColorScheme(
    primary = Indigo40,
    secondary = Amber40,
    tertiary = Teal40,
)

@Composable
fun WakePactTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
