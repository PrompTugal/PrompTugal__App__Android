package ai.riskmandate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One big number with its label: the Lab's "1 in 3", "7 of 20 failed", "20 of 20 caught"; the chat's
 * tokens per second. [value] is the figure as text (the caller formats it, in the user's language);
 * [label] says what it counts. TalkBack reads "<label>: <value>" as one item.
 *
 * With [large] the value is 72sp bold (readable from 2 m on a 10" tablet, about 12 mm cap height) and the
 * label 24sp; otherwise 28sp and 16sp. Put the label first in the reading order and the figure big:
 * a number without its label never stands alone on a screen that is read from a distance.
 */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, large: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .border(BorderStroke(1.dp, scheme.outlineVariant), RoundedCornerShape(UiTokens.CardRadius))
            .padding(horizontal = 16.dp, vertical = if (large) 12.dp else 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontSize = if (large) 24.sp else 16.sp,
            color = scheme.onSurfaceVariant,
        )
        Text(
            value,
            fontSize = if (large) 72.sp else 28.sp,
            lineHeight = if (large) 80.sp else 34.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
        )
    }
}
