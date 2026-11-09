package ai.riskmandate.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The PrompTugal look, from the iOS apps (PrompTugal__App__iOS, PTCore/Design/Theme.swift, 9 Nov 2026):
 * ink, red and green on warm paper; serif headlines; a small red uppercase kicker above them; 14 dp radius
 * cards with a hairline rule; a 4-point grid. The hex values are the iOS Palette's, light and dark.
 * Fixed schemes, not dynamic colour, so the app looks the same on every tablet and in the store screenshots.
 * Mapping and reasoning: docs/design/android-theme.md.
 */

/** The iOS `Palette`, as Compose colours. Read inside the theme as `Pt.colors`. */
data class PtColors(
    val paper: Color,
    val paper2: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val rule: Color,
    val red: Color,
    val green: Color,
    val amber: Color,
    val blue: Color,
)

private val LightPt = PtColors(
    paper = Color(0xFFF5F1EA), paper2 = Color(0xFFEBE5DA), card = Color(0xFFFFFDF9),
    ink = Color(0xFF111318), muted = Color(0xFF555A63), rule = Color(0xFFD6CFC2),
    red = Color(0xFFAD222B), green = Color(0xFF0B6B1E), amber = Color(0xFF8A5A00), blue = Color(0xFF1F4FA3),
)

private val DarkPt = PtColors(
    paper = Color(0xFF14161B), paper2 = Color(0xFF1C1F25), card = Color(0xFF1F2229),
    ink = Color(0xFFECE7DE), muted = Color(0xFFA7ABB3), rule = Color(0xFF343842),
    red = Color(0xFFFF868A), green = Color(0xFF6FCF7F), amber = Color(0xFFF2C14E), blue = Color(0xFF8FB4FF),
)

val LocalPtColors = staticCompositionLocalOf { LightPt }

/** `Pt.colors.red`, `Pt.type.kicker`: the PrompTugal tokens that Material's scheme has no slot for. */
object Pt {
    val colors: PtColors
        @Composable get() = LocalPtColors.current
    val type: PtType get() = PtTypeInstance
}

/** The iOS `Typo`: styles Material's typography has no slot for. */
class PtType {
    /** The small uppercase label above a headline. Apply `.uppercase()` to the text as well. */
    val kicker = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)

    /** Numbers: sans, semibold, tabular digits (the iOS rounded design has no Android equivalent). */
    val number = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

    /** Tool calls, arguments, model output: monospace. */
    val data = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp)
}

private val PtTypeInstance = PtType()

/** Colours for outcomes (chips, layer rows, steps): solid tints of the palette's tones over the card colour. */
data class StatusColors(val container: Color, val content: Color)

/**
 * Allowed = green, redacted = blue, held = amber, blocked = red, as riskmandate.ai and the iOS apps use them.
 * Each pair is the tone on its own 13% tint over the card colour: at least 4.9:1 in light and 5.5:1 in dark
 * (WCAG AA for text, measured with the formula in docs/design/android-theme.md). Never the only signal:
 * every use also carries a word and a glyph.
 */
data class StatusPalette(
    val allowed: StatusColors,
    val redacted: StatusColors,
    val held: StatusColors,
    val blocked: StatusColors,
)

private val LightStatus = StatusPalette(
    allowed = StatusColors(Color(0xFFDFEADD), LightPt.green),
    redacted = StatusColors(Color(0xFFE2E6EE), LightPt.blue),
    held = StatusColors(Color(0xFFF0E8D9), LightPt.amber),
    blocked = StatusColors(Color(0xFFF4E1DE), LightPt.red),
)

private val DarkStatus = StatusPalette(
    allowed = StatusColors(Color(0xFF293834), DarkPt.green),
    redacted = StatusColors(Color(0xFF2E3545), DarkPt.blue),
    held = StatusColors(Color(0xFF3A372E), DarkPt.amber),
    blocked = StatusColors(Color(0xFF3C2F36), DarkPt.red),
)

val LocalStatusPalette = staticCompositionLocalOf { LightStatus }

/** Read the palette inside [RiskMandateTheme] as `RiskMandateStatus.palette`. */
object RiskMandateStatus {
    val palette: StatusPalette
        @Composable get() = LocalStatusPalette.current
}

private val LightColors = lightColorScheme(
    primary = LightPt.red,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF4E1DE),
    onPrimaryContainer = Color(0xFF6E141B),
    secondary = LightPt.muted,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LightPt.paper2,
    onSecondaryContainer = LightPt.ink,
    tertiary = LightPt.green,
    onTertiary = Color(0xFFFFFFFF),
    background = LightPt.paper,
    onBackground = LightPt.ink,
    surface = LightPt.card,
    onSurface = LightPt.ink,
    surfaceVariant = LightPt.paper2,
    onSurfaceVariant = LightPt.muted,
    surfaceContainerLowest = LightPt.card,
    surfaceContainerLow = LightPt.card,
    surfaceContainer = LightPt.paper2,
    surfaceContainerHigh = LightPt.paper2,
    surfaceContainerHighest = Color(0xFFE3DDD0),
    outline = LightPt.muted,
    outlineVariant = LightPt.rule,
    error = LightPt.red,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF4E1DE),
    onErrorContainer = Color(0xFF6E141B),
)

private val DarkColors = darkColorScheme(
    primary = DarkPt.red,
    onPrimary = DarkPt.paper,
    primaryContainer = Color(0xFF3C2F36),
    onPrimaryContainer = Color(0xFFFFD9DA),
    secondary = DarkPt.muted,
    onSecondary = DarkPt.paper,
    secondaryContainer = DarkPt.paper2,
    onSecondaryContainer = DarkPt.ink,
    tertiary = DarkPt.green,
    onTertiary = DarkPt.paper,
    background = DarkPt.paper,
    onBackground = DarkPt.ink,
    surface = DarkPt.card,
    onSurface = DarkPt.ink,
    surfaceVariant = DarkPt.paper2,
    onSurfaceVariant = DarkPt.muted,
    surfaceContainerLowest = DarkPt.paper,
    surfaceContainerLow = DarkPt.card,
    surfaceContainer = DarkPt.card,
    surfaceContainerHigh = DarkPt.paper2,
    surfaceContainerHighest = Color(0xFF262A32),
    outline = DarkPt.muted,
    outlineVariant = DarkPt.rule,
    error = DarkPt.red,
    onError = DarkPt.paper,
    errorContainer = Color(0xFF3C2F36),
    onErrorContainer = Color(0xFFFFD9DA),
)

/**
 * Serif for headlines (display, headline, title), the system sans for everything else, as the iOS
 * `Typo` does. FontFamily.Serif is the device's own serif (Noto Serif on Android): nothing is bundled,
 * so no font licence to record. Sizes are in sp and follow the user's font scale.
 */
private val Serif = FontFamily.Serif

private val PtTypography = Typography(
    displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
    displayMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp),
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
    // iOS `display` (large title): 34.
    headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    // iOS `title` (title2): 22 bold serif; headlineSmall is 24 for feature titles.
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    // iOS `headline`: 17 semibold serif (card titles).
    titleMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)

/** Radius 14 for cards, dialogs and menus (the iOS `Space.radius`); buttons and chips stay capsules. */
private val PtShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Material 3 in the PrompTugal look, light or dark following the system unless a test or preview forces it. */
@Composable
fun RiskMandateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(
        LocalPtColors provides if (darkTheme) DarkPt else LightPt,
        LocalStatusPalette provides if (darkTheme) DarkStatus else LightStatus,
    ) {
        MaterialTheme(colorScheme = scheme, typography = PtTypography, shapes = PtShapes, content = content)
    }
}
