package ai.riskmandate.app.ui

import ai.riskmandate.app.Pt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer

/** The colour a [StatTile]'s number takes (iOS `StatTile.Tone`). */
enum class Tone { Neutral, Good, Warn, Bad }

/**
 * One number with its label, an optional detail line and a tone (iOS `StatTile`): the Lab's "1 in 3",
 * "20 of 20 caught"; the chat's tokens per second. [value] and [label] are formatted by the caller, in
 * the user's language. TalkBack reads "<label>: <value>, <detail>" as one item.
 *
 * With [large] the value is 72sp bold (about 12 mm cap height on a 10" tablet, readable from 2 m) and the
 * label 24sp; otherwise the value is 28sp semibold with tabular digits and the label 14sp muted.
 * Tone colours (green, amber, red) are at least 5.8:1 on the card colour; the tone is never the only cue,
 * so [label] or [detail] says what is good or bad.
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    detail: String? = null,
    tone: Tone = Tone.Neutral,
) {
    val c = Pt.colors
    val color = toneColor(tone)
    Card(modifier.semantics(mergeDescendants = true) {}, padding = if (large) Space.L else Space.M) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontSize = if (large) 24.sp else 14.sp,
            lineHeight = if (large) 30.sp else 20.sp,
            color = c.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = Pt.type.number,
            fontSize = if (large) 72.sp else 28.sp,
            lineHeight = if (large) 80.sp else 34.sp,
            fontWeight = if (large) FontWeight.Bold else FontWeight.SemiBold,
            color = color,
        )
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The text colour for a [Tone]; the one place `Tone.Bad` meets red (see ColourRulesTest). */
@Composable
internal fun toneColor(tone: Tone): androidx.compose.ui.graphics.Color {
    val c = Pt.colors
    return when (tone) {
        Tone.Neutral -> c.ink
        Tone.Good -> c.green
        Tone.Warn -> c.amber
        Tone.Bad -> c.red
    }
}

/** Collects the tiles of a [StatGrid]. */
class StatGridScope internal constructor() {
    internal val tiles = mutableListOf<@Composable () -> Unit>()

    /** Adds one tile: `tile { StatTile("4", "Only you", tone = Tone.Bad) }`. */
    fun tile(content: @Composable () -> Unit) { tiles += content }
}

/**
 * A grid of stat tiles that wraps on narrow widths (iOS `StatGrid`): as many columns as fit at [minTile]
 * wide, equal widths, 12 dp gaps, the last row left-aligned. Read order is row by row.
 */
@Composable
fun StatGrid(modifier: Modifier = Modifier, minTile: Dp = 140.dp, content: StatGridScope.() -> Unit) {
    val tiles = StatGridScope().apply(content).tiles
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = Space.M
        val columns = ((maxWidth + gap) / (minTile + gap)).toInt().coerceIn(1, tiles.size.coerceAtLeast(1))
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            tiles.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { tile -> Column(Modifier.weight(1f)) { tile() } }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
