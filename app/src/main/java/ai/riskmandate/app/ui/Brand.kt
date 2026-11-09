package ai.riskmandate.app.ui

import ai.riskmandate.app.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The RiskMandate monogram (vector redraw of the logo Dinis shared on 9 Nov 2026: white "RM" with green
 * corner brackets) on a navy rounded square: the mark for the sidebar's brand block and the splash. Navy
 * in both light and dark, because the letters are white. Decorative (the wordmark beside it carries the
 * name); pass a [contentDescription] only where it stands alone. A proposal until Dinis approves the redraw
 * (`docs/design/brand/README-redraw.md`).
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 56.dp, contentDescription: String? = null) {
    Box(
        modifier.size(size).background(colorResource(R.color.rm_navy), RoundedCornerShape(size / 4)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_rm_monogram), contentDescription, Modifier.padding(size / 8).size(size))
    }
}

/**
 * "Risk" in the text colour and "Mandate" in the brand green, as the logo sets it. The green is the logo's
 * (`#66B78F`) on dark backgrounds and riskmandate.ai's green darkened to `#177251` (5.8:1 on the card colour) on light ones:
 * the logo's mint is too pale for text on paper. The typeface is the system sans in semibold: the logo's own
 * geometric face is not available, and the lettering is not a redraw of the logo's.
 */
@Composable
fun BrandWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 22.sp) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val green = colorResource(if (dark) R.color.rm_green else R.color.rm_green_on_paper)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground)) { append(stringResource(R.string.brand_risk)) }
        withStyle(SpanStyle(color = green)) { append(stringResource(R.string.brand_mandate)) }
    }
    Text(text, modifier, fontSize = fontSize, fontWeight = FontWeight.SemiBold, lineHeight = fontSize * 1.2f)
}

/**
 * The sidebar's brand block (iOS `PublicShell`'s brand block): the mark, the wordmark and one muted line
 * ([subtitle]: "Web Summit", or the device label on a managed tablet). Not interactive. No tagline: the
 * logo's "AI agent governance and security" line is deliberately not shown on any screen.
 */
@Composable
fun BrandBlock(subtitle: String?, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.M),
    ) {
        BrandMark(size = 56.dp)
        androidx.compose.foundation.layout.Column {
            BrandWordmark(fontSize = 22.sp)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
