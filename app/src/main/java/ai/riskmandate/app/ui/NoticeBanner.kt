package ai.riskmandate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A full-width, always-visible note: screen B's "This is a demonstration, not a live capture" label.
 * Never collapsible, never dismissible, never colour-only (it is words). Put it above the diagram, not in
 * a corner.
 */
@Composable
fun NoticeBanner(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 28.sp),
        color = scheme.onPrimaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.primaryContainer, RoundedCornerShape(UiTokens.CardRadius))
            .border(BorderStroke(1.dp, scheme.primary), RoundedCornerShape(UiTokens.CardRadius))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(UiTags.BANNER),
    )
}
