package ai.riskmandate.app.ui

import ai.riskmandate.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Whether the screen is wide enough for two columns, from [FeatureScaffold]'s content area. */
val LocalWideLayout = staticCompositionLocalOf { false }

/**
 * The frame of every feature screen: a title bar (labelled Back button on the left, the feature's title
 * as the heading, optional [actions] on the right) and the content below.
 *
 * - [title] is the feature's own title string (`Feature.title`).
 * - [scrollable] false for content that scrolls itself (a WebView); the content then gets the remaining
 *   height via `Modifier.weight(1f)`. True (default) wraps the content in a vertical scroll, so 200% font
 *   scale never cuts anything off.
 * - [maxContentWidth] caps line length; use [Dp.Unspecified] for full-bleed content (a WebView).
 * - Inside [content], `LocalWideLayout.current` is true on a landscape tablet (840dp or wider).
 *
 * The Back button is a text button with a visible label, not an icon: a stand visitor and a member of
 * staff both find it without guessing. It calls [onBack] (`FeatureContext.onBack`).
 */
@Composable
fun FeatureScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    maxContentWidth: Dp = UiTokens.TextMaxWidth,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth >= UiTokens.WideMinWidth
            val pad = if (wide) UiTokens.ScreenPaddingWide else UiTokens.ScreenPadding
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = pad / 2, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = UiTokens.MinTouch).testTag(UiTags.BACK)) {
                        Text(stringResource(R.string.ui_back), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).testTag(UiTags.TITLE).semantics { heading() },
                    )
                    actions()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CompositionLocalProvider(LocalWideLayout provides wide) {
                    val capped = maxContentWidth != Dp.Unspecified
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                        var column = Modifier.fillMaxHeight()
                        column = if (capped) column.widthIn(max = maxContentWidth).fillMaxWidth() else column.fillMaxWidth()
                        if (scrollable) column = column.verticalScroll(rememberScrollState())
                        Column(column.padding(if (capped) pad else 0.dp), content = content)
                    }
                }
            }
        }
    }
}
