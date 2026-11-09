package ai.riskmandate.app.ui

import ai.riskmandate.app.Pt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The basic surface (iOS `Card`): the card colour on the paper background, 14 dp radius, a hairline rule.
 * With [onClick] the whole card is one button (at least 48 dp high, with the Button role); without it
 * it is not interactive. Content is a column with 8 dp between children.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = Space.L,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(UiTokens.CardRadius)
    val border = BorderStroke(1.dp, Pt.colors.rule.copy(alpha = 0.7f))
    val inner: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(padding), verticalArrangement = Arrangement.spacedBy(Space.S), content = content)
    }
    if (onClick == null) {
        Surface(modifier.fillMaxWidth(), shape = shape, color = Pt.colors.card, border = border, content = inner)
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().heightIn(min = UiTokens.MinTouch).semantics { role = Role.Button },
            shape = shape, color = Pt.colors.card, border = border, content = inner,
        )
    }
}

/**
 * The small red uppercase label above a headline (iOS `Kicker`). Pass the text in its normal case, in the
 * user's language: it is upper-cased here. 12sp semibold with 1.4sp tracking; red is 6.8:1 on the card
 * colour in light and dark, so it is safe as text.
 */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = Pt.colors.red) {
    Text(text.uppercase(), modifier = modifier, style = Pt.type.kicker, color = color)
}

/**
 * A card with an optional kicker, a serif headline and an optional [accessory] at the end (a [CountPill],
 * a [StatusChip]) (iOS `TitledCard`). The headline is a heading for TalkBack. [large] makes the
 * headline 36sp bold, for a lane header read from 2 m (the Lab).
 */
@Composable
fun TitledCard(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    large: Boolean = false,
    onClick: (() -> Unit)? = null,
    accessory: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.S)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (kicker != null) Kicker(kicker)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = if (large) 36.sp else TextUnit.Unspecified,
                    lineHeight = if (large) 42.sp else TextUnit.Unspecified,
                    fontWeight = if (large) FontWeight.Bold else null,
                    color = Pt.colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
            accessory()
        }
        content()
    }
}

/**
 * A screen header (iOS `ScreenHeader`): kicker, serif display title, one muted line. The title is the
 * screen's heading for TalkBack. Inside the host's `FeatureFrame` use this as the first item of a [Page]
 * instead of a second title bar.
 */
@Composable
fun ScreenHeader(kicker: String, title: String, modifier: Modifier = Modifier, summary: String? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
        Kicker(kicker)
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = Pt.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.bodyLarge, color = Pt.colors.muted)
        }
    }
}
