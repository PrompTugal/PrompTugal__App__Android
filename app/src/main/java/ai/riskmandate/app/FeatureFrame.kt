package ai.riskmandate.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

object FeatureFrameTags {
    const val BACK = "feature_back"
    const val TITLE = "feature_title"
}

/**
 * The host's frame around every summit feature: system-bar insets, a visible Back button (48dp, so
 * stand mode, where the back gesture may be pinned off, still has a way home) and the title as a
 * heading. TalkBack order: Back, title, then the feature's own content. A feature draws only its
 * content and must not add its own title or inset padding.
 */
@Composable
fun FeatureFrame(@StringRes title: Int, onBack: () -> Unit, content: @Composable () -> Unit) {
    val backLabel = stringResource(R.string.licences_back_description)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(FeatureFrameTags.BACK)
                    .semantics { onClick(label = backLabel, action = null) },
            ) { Text(stringResource(R.string.licences_back)) }
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp).testTag(FeatureFrameTags.TITLE).semantics { heading() },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}
