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

    val CardRadius = 16.dp
    val CardPadding = 20.dp
    val Gap = 16.dp
}

/** Test tags for the shared components, so feature tests do not invent their own. */
object UiTags {
    const val BANNER = "ui_banner"
    fun layer(index: Int) = "ui_layer_$index"
}
