package ai.riskmandate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * The scrolling page every dashboard uses (iOS `Page`): the content centred, at most [maxWidth] wide
 * (1200 dp, or [UiTokens.Readable] for text pages), 16 dp between children, padded 24 dp (40 dp on a
 * landscape tablet), on the theme's paper background. Use it as the root of a feature's screen inside the
 * host's `FeatureFrame`, with a [ScreenHeader] first. `LocalWideLayout.current` is true inside it at 840 dp
 * or more. For content that scrolls itself (a WebView, a list) use [FeatureContent] with `scrollable = false`.
 */
@Composable
fun Page(
    modifier: Modifier = Modifier,
    maxWidth: Dp = UiTokens.PageMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) = FeatureContent(modifier, scrollable = true, maxContentWidth = maxWidth, verticalArrangement = Arrangement.spacedBy(Space.L), content = content)

/**
 * Two equal columns on a wide screen (840 dp or more), one column on a narrow one (iOS `Columns`).
 * Reading order is leading then trailing in both.
 */
@Composable
fun Columns(
    modifier: Modifier = Modifier,
    leading: @Composable ColumnScope.() -> Unit,
    trailing: @Composable ColumnScope.() -> Unit,
) {
    if (LocalWideLayout.current) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.L)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.L), content = leading)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.L), content = trailing)
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.L)) {
            leading()
            trailing()
        }
    }
}
