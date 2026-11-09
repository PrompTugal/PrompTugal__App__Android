package ai.riskmandate.app.ui

import ai.riskmandate.app.RiskMandateStatus
import ai.riskmandate.app.StatusColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The outcomes riskmandate.ai uses, plus [Neutral] for facts with no verdict (a version, "nothing collected"). */
enum class ChipKind(val glyph: String) {
    Allowed("✓"), // check
    Redacted("◐"), // half circle
    Held("\u2016"),    // double bar (the pause emoji renders in colour)
    Blocked("✕"),  // cross
    Neutral("•"),  // bullet
}

/**
 * A short status label: a glyph, then a word. Colour is a third cue, never the only one.
 * [label] must already say the status ("Blocked", "Allowed"): the glyph is hidden from TalkBack.
 * One line, never wrapped: give a chip a column wide enough for its words (in a table, a weight of 1.3 or more for a
 * two-word chip). Not clickable. Text size follows the user's font scale; pass [large] for a stand screen read at a distance.
 */
@Composable
fun StatusChip(label: String, kind: ChipKind, modifier: Modifier = Modifier, large: Boolean = false) {
    val colors = chipColors(kind)
    val style = if (large) MaterialTheme.typography.labelLarge.copy(fontSize = 22.sp, lineHeight = 28.sp) else MaterialTheme.typography.labelLarge
    Row(
        modifier = modifier
            .background(colors.container, RoundedCornerShape(50))
            .padding(horizontal = if (large) 16.dp else 12.dp, vertical = if (large) 8.dp else 4.dp)
            .heightIn(min = 24.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(kind.glyph, style = style, color = colors.content, modifier = Modifier.clearAndSetSemantics {})
        Text(label, style = style, fontWeight = FontWeight.SemiBold, color = colors.content, maxLines = 1, softWrap = false)
    }
}

@Composable
internal fun chipColors(kind: ChipKind): StatusColors {
    val p = RiskMandateStatus.palette
    return when (kind) {
        ChipKind.Allowed -> p.allowed
        ChipKind.Redacted -> p.redacted
        ChipKind.Held -> p.held
        ChipKind.Blocked -> p.blocked
        ChipKind.Neutral -> StatusColors(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
