package ai.riskmandate.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand navy (the launcher icon background, #1B2A41) as the primary. Fixed schemes, not dynamic
// colour, so the app looks the same on every device and in the store screenshots.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B2A41),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF3F5F90),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF191C20),
    onSurfaceVariant = Color(0xFF43474E),
    outlineVariant = Color(0xFFC3C6CF),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF284777),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFAAC7FF),
    background = Color(0xFF0F1722),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF0F1722),
    onSurface = Color(0xFFE3E2E6),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outlineVariant = Color(0xFF43474E),
    error = Color(0xFFFFB4AB),
)

/** Material 3, light or dark following the system unless a test or preview forces it. */
@Composable
fun RiskMandateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
