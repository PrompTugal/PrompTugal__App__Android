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
import androidx.compose.ui.graphics.compositeOver
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
    /** The brand accent: kickers, the primary button, the selected item, count pills. Red in variant B, green in A. */
    val accent: Color,
)

private val LightPt = PtColors(
    paper = Color(0xFFF5F1EA), paper2 = Color(0xFFEBE5DA), card = Color(0xFFFFFDF9),
    ink = Color(0xFF111318), muted = Color(0xFF555A63), rule = Color(0xFFD6CFC2),
    red = Color(0xFFAD222B), green = Color(0xFF0B6B1E), amber = Color(0xFF8A5A00), blue = Color(0xFF1F4FA3), accent = Color(0xFFAD222B),
)

private val DarkPt = PtColors(
    paper = Color(0xFF14161B), paper2 = Color(0xFF1C1F25), card = Color(0xFF1F2229),
    ink = Color(0xFFECE7DE), muted = Color(0xFFA7ABB3), rule = Color(0xFF343842),
    red = Color(0xFFFF868A), green = Color(0xFF6FCF7F), amber = Color(0xFFF2C14E), blue = Color(0xFF8FB4FF), accent = Color(0xFFFF868A),
)

/**
 * The two looks (Dinis chose A on 9 Nov 2026):
 * - [PrompTugalRed] (B): the iOS Palette unchanged, red accent. The fallback.
 * - [RiskMandateGreen] (A, shipped): the same iOS structure with RiskMandate's green as the accent (kickers, badges,
 *   primary button, selection), red kept only for "blocked" and errors, and a navy-leaning dark mode taken from
 *   the logo's background. Light accent `#177251` (riskmandate.ai's `#1A7F5A` darkened 10%, because the site's own
 *   green is 4.4:1 on paper and 4.1:1 on its own tint, under AA for small text; `#177251` is at least 4.7:1 on the card, paper,
 *   paper2 and tint); dark accent `#66B78F` (sampled from the logo's wordmark, at least 5.4:1 on the navy card and tint).
 */
enum class ThemeVariant { PrompTugalRed, RiskMandateGreen }

/**
 * The shipped look: A, chosen by Dinis on 9 Nov 2026. B ([ThemeVariant.PrompTugalRed]) is kept as a documented
 * fallback and is still rendered by `UiScreenshotTest.variants`; change this one line to switch back.
 */
val DefaultVariant = ThemeVariant.RiskMandateGreen

private val GreenLightPt = LightPt.copy(accent = Color(0xFF177251))

private val GreenDarkPt = DarkPt.copy(
    paper = Color(0xFF061224), paper2 = Color(0xFF0C1C33), card = Color(0xFF0F2038),
    ink = Color(0xFFE8EEF5), muted = Color(0xFF9FB0C5), rule = Color(0xFF22344E),
    accent = Color(0xFF66B78F),
)

internal fun ptColorsFor(dark: Boolean, variant: ThemeVariant): PtColors = when (variant) {
    ThemeVariant.PrompTugalRed -> if (dark) DarkPt else LightPt
    ThemeVariant.RiskMandateGreen -> if (dark) GreenDarkPt else GreenLightPt
}

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

/** A tone on its own 13% tint over the card colour. */
private fun statusOf(tone: Color, card: Color) = StatusColors(tone.copy(alpha = 0.13f).compositeOver(card), tone)

internal fun statusFor(pt: PtColors) = StatusPalette(
    allowed = statusOf(pt.green, pt.card),
    redacted = statusOf(pt.blue, pt.card),
    held = statusOf(pt.amber, pt.card),
    blocked = statusOf(pt.red, pt.card),
)

val LocalStatusPalette = staticCompositionLocalOf { statusFor(LightPt) }

/** Read the palette inside [RiskMandateTheme] as `RiskMandateStatus.palette`. */
object RiskMandateStatus {
    val palette: StatusPalette
        @Composable get() = LocalStatusPalette.current
}

private fun schemeFor(pt: PtColors, dark: Boolean): ColorScheme {
    val onAccent = if (dark) pt.paper else Color(0xFFFFFFFF)
    val accentTint = pt.accent.copy(alpha = 0.13f).compositeOver(pt.card)
    val redTint = pt.red.copy(alpha = 0.13f).compositeOver(pt.card)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = pt.accent, onPrimary = onAccent,
        primaryContainer = accentTint, onPrimaryContainer = pt.ink,
        secondary = pt.muted, onSecondary = onAccent,
        secondaryContainer = pt.paper2, onSecondaryContainer = pt.ink,
        tertiary = pt.green, onTertiary = onAccent,
        background = pt.paper, onBackground = pt.ink,
        surface = pt.card, onSurface = pt.ink,
        surfaceVariant = pt.paper2, onSurfaceVariant = pt.muted,
        surfaceContainerLowest = if (dark) pt.paper else pt.card,
        surfaceContainerLow = pt.card,
        surfaceContainer = if (dark) pt.card else pt.paper2,
        surfaceContainerHigh = pt.paper2,
        surfaceContainerHighest = pt.rule,
        outline = pt.muted, outlineVariant = pt.rule,
        error = pt.red, onError = onAccent,
        errorContainer = redTint, onErrorContainer = pt.ink,
    )
}

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
fun RiskMandateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    variant: ThemeVariant = DefaultVariant,
    content: @Composable () -> Unit,
) {
    val pt = ptColorsFor(darkTheme, variant)
    CompositionLocalProvider(LocalPtColors provides pt, LocalStatusPalette provides statusFor(pt)) {
        MaterialTheme(colorScheme = schemeFor(pt, darkTheme), typography = PtTypography, shapes = PtShapes, content = content)
    }
}
