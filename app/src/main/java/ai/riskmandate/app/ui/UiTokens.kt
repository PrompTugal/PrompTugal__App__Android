package ai.riskmandate.app.ui

import androidx.compose.ui.unit.dp

/** Shared sizes for the summit screens. Change them here, not per screen. */
object UiTokens {
    /** Material's minimum touch target. */
    val MinTouch = 48.dp

    /** Page padding; larger on a tablet at a stand, where the device sits on a stand, not in a hand. */
    val ScreenPadding = 24.dp
    val ScreenPaddingWide = 40.dp

    /** At and above this width (landscape tablets) screens use the wide padding and two columns. */
    val WideMinWidth = 840.dp

    /** Reading width for text screens (C, and the text parts of B). */
    val TextMaxWidth = 720.dp

    /** The iOS `Space.radius`: 14 for every card. */
    val CardRadius = 14.dp
    val CardPadding = 16.dp
    val Gap = 16.dp

    /** The iOS `Page` default: dashboards never get wider than this. */
    val PageMaxWidth = 1200.dp

    /** The iOS `Space.readable`: running text never gets wider than this. */
    val Readable = 680.dp
}

/** The iOS `Space`: a 4-point grid. */
object Space {
    val Xs = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
}

/** Test tags for the shared components, so feature tests do not invent their own. */
object UiTags {
    const val BANNER = "ui_banner"
    fun layer(index: Int) = "ui_layer_$index"
}
