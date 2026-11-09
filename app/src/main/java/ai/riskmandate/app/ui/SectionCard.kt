package ai.riskmandate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A titled block of content: screen C's "Reach", "Mandate" and "Gap", the notes under B's diagram.
 * [large] makes the title 36sp bold, for a lane header read from 2 m (the Lab). The title is a heading for TalkBack. Outlined, not elevated, so it reads the same in light and dark.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiTokens.CardRadius),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(UiTokens.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontSize = if (large) 36.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                fontWeight = if (large) androidx.compose.ui.text.font.FontWeight.Bold else null,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}
