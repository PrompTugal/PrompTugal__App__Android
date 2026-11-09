package ai.riskmandate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Whether the screen is wide enough for two columns, from [FeatureContent]'s width. */
val LocalWideLayout = staticCompositionLocalOf { false }

/**
 * The content area of a feature screen, for use inside the host's `FeatureFrame` (which draws Back, the
 * title heading and the window insets, so this does not).
 *
 * - [scrollable] false for content that scrolls itself (a WebView, a list); the content then gets the
 *   remaining height via `Modifier.weight(1f)`. True (default) wraps it in a vertical scroll, so 200% font
 *   scale never cuts anything off.
 * - [maxContentWidth] caps line length and centres the column; [Dp.Unspecified] for full-bleed content.
 * - Padding is 24dp, 40dp on a landscape tablet. Inside [content], `LocalWideLayout.current` is true at
 *   840dp wide or more (landscape tablets): use it to go to two columns.
 */
@Composable
fun FeatureContent(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    maxContentWidth: Dp = UiTokens.TextMaxWidth,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= UiTokens.WideMinWidth
        val pad = if (wide) UiTokens.ScreenPaddingWide else UiTokens.ScreenPadding
        val capped = maxContentWidth != Dp.Unspecified
        CompositionLocalProvider(LocalWideLayout provides wide) {
            var column = Modifier.fillMaxHeight()
            column = if (capped) column.widthIn(max = maxContentWidth).fillMaxWidth() else column.fillMaxWidth()
            if (scrollable) column = column.verticalScroll(rememberScrollState())
            Column(column.padding(if (capped) pad else 0.dp), verticalArrangement = verticalArrangement, content = content)
        }
    }
}
