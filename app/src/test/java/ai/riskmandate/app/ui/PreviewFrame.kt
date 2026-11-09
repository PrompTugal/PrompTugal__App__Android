package ai.riskmandate.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Test-only stand-in for the host's FeatureFrame (which owns Back, the title and insets): a title line
 * and the content. Not shipped; the real frame is app.android's.
 */
@Composable
internal fun PreviewFrame(
    title: String,
    scrollable: Boolean = true,
    maxContentWidth: androidx.compose.ui.unit.Dp = UiTokens.TextMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(24.dp, 12.dp))
            FeatureContent(Modifier.weight(1f), scrollable, maxContentWidth, content)
        }
    }
}
