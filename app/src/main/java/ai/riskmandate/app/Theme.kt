package ai.riskmandate.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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

/** A background and the text colour that reads on it (every pair is at least 7:1, AAA). */
data class StatusColors(val container: Color, val content: Color)

/**
 * Outcome colours for status chips and layer rows. They follow the four outcomes riskmandate.ai shows
 * (allowed, redacted, held, blocked) but are never the only signal: every use also carries a word
 * and a glyph, so colour-blind viewers lose nothing. Shared by `ui/`; not part of Material's scheme.
 */
data class StatusPalette(
    val allowed: StatusColors,
    val redacted: StatusColors,
    val held: StatusColors,
    val blocked: StatusColors,
)

private val LightStatus = StatusPalette(
    allowed = StatusColors(Color(0xFFDDF1E6), Color(0xFF0B3B29)),
    redacted = StatusColors(Color(0xFFDCE6FB), Color(0xFF12307F)),
    held = StatusColors(Color(0xFFFFF3C4), Color(0xFF5A3200)),
    blocked = StatusColors(Color(0xFFFBE0DC), Color(0xFF7A1F16)),
)

private val DarkStatus = StatusPalette(
    allowed = StatusColors(Color(0xFF0B3B29), Color(0xFF7FF0BF)),
    redacted = StatusColors(Color(0xFF1B2D63), Color(0xFFC4D3FF)),
    held = StatusColors(Color(0xFF4A3200), Color(0xFFFFC772)),
    blocked = StatusColors(Color(0xFF5C1A14), Color(0xFFFFB4AB)),
)

val LocalStatusPalette = staticCompositionLocalOf { LightStatus }

/** Read the palette inside [RiskMandateTheme] as `RiskMandateStatus.palette`. */
object RiskMandateStatus {
    val palette: StatusPalette
        @Composable get() = LocalStatusPalette.current
}

/** Material 3, light or dark following the system unless a test or preview forces it. */
@Composable
fun RiskMandateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusPalette provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}
