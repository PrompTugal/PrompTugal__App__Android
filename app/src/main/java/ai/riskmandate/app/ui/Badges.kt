package ai.riskmandate.app.ui

import ai.riskmandate.app.Pt
import ai.riskmandate.app.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A count in a capsule (iOS `CountPill`): "9 new", "3 held". Red when [count] is above zero, muted at
 * zero; background is the colour at 12%. Read as one item: "<count> <label>". Use it as the badge on a
 * sidebar destination or as a [TitledCard] accessory.
 */
@Composable
fun CountPill(count: Int, label: String, modifier: Modifier = Modifier, color: Color = Pt.colors.red) {
    val tone = if (count > 0) color else Pt.colors.muted
    Row(
        modifier = modifier
            .background(tone.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(count.toString(), style = Pt.type.number, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = tone)
        Text(label, style = MaterialTheme.typography.labelMedium, color = tone)
    }
}

/** How a screen is built (iOS `SurfaceKind`): native Compose, a web page, or a mix. */
enum class SurfaceKind { Native, Web, Hybrid }

/**
 * The small "Native / Web / Hybrid" badge (iOS `KindBadge`): green, blue or amber on its own 13% tint with a
 * hairline outline and a leading dot. For the host to show on each destination when debug mode is on, and
 * on screen A (Web) so a visitor knows the page is riskmandate.ai's. Reads "<kind> screen".
 */
@Composable
fun KindBadge(kind: SurfaceKind, modifier: Modifier = Modifier) {
    val c = Pt.colors
    val (tone, label) = when (kind) {
        SurfaceKind.Native -> c.green to stringResource(R.string.ui_kind_native)
        SurfaceKind.Web -> c.blue to stringResource(R.string.ui_kind_web)
        SurfaceKind.Hybrid -> c.amber to stringResource(R.string.ui_kind_hybrid)
    }
    val spoken = stringResource(R.string.ui_kind_screen, label)
    Row(
        modifier = modifier
            .background(tone.copy(alpha = 0.13f), CircleShape)
            .border(BorderStroke(1.dp, tone.copy(alpha = 0.4f)), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).background(tone, CircleShape).clearAndSetSemantics {})
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = tone)
    }
}
