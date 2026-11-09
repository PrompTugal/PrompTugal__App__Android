package ai.riskmandate.app.ui

import ai.riskmandate.app.Pt
import ai.riskmandate.app.RiskMandateStatus
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
 * A full-width, always-visible note that says what the screen cannot show or what it is not (iOS `GapNote`):
 * screen B's "This is a demonstration" label, the Lab's "tools are fakes" line, D's idle countdown. Amber on its
 * own tint (the "held" tone), words only, never collapsible, never dismissible. Put it above the thing it
 * qualifies, not in a corner. 20sp, so it also reads from a little way off at the stand.
 */
@Composable
fun NoticeBanner(text: String, modifier: Modifier = Modifier) {
    val tone = RiskMandateStatus.palette.held
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 28.sp),
        color = Pt.colors.ink,
        modifier = modifier
            .fillMaxWidth()
            .background(tone.container, RoundedCornerShape(UiTokens.CardRadius))
            .border(BorderStroke(1.dp, tone.content.copy(alpha = 0.5f)), RoundedCornerShape(UiTokens.CardRadius))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(UiTags.BANNER),
    )
}
