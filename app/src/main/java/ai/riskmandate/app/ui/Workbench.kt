package ai.riskmandate.app.ui

import ai.riskmandate.app.Pt
import ai.riskmandate.app.RiskMandateStatus
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Components for dense, staff-only screens (the Workbench): see docs/design/workbench.md. Everything here keeps
 * 14sp as the smallest text and 48 dp for anything you can touch; static rows may be shorter.
 */

/**
 * A segmented control (the iOS tab switcher): a paper2 track with the selected segment raised on the card colour
 * and a hairline. 48 dp high, equal-width segments, `Role.Tab` with `selected` for TalkBack, selected label is
 * semibold (not colour alone). Use it for 2 to 4 sibling views (Run, Experiments, Tool tests).
 */
@Composable
fun SegmentedControl(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Pt.colors
    Row(
        modifier.fillMaxWidth().background(c.paper2, RoundedCornerShape(12.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = UiTokens.MinTouch)
                    .background(if (on) c.card else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
                    .then(if (on) Modifier.border(BorderStroke(1.dp, c.rule.copy(alpha = 0.7f)), RoundedCornerShape(10.dp)) else Modifier)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) c.accent else c.ink)
            }
        }
    }
}

/**
 * One label and value in a stats panel: the label muted on the left, the value in tabular digits on the right,
 * and optionally where the number came from ([source], a short tag such as "runtime" or "app": the brief
 * wants every figure labelled). 28 dp minimum (static, not touchable). Keep [source] to one short word ("runtime", "app"): it has a fixed 64 dp slot so values line up. Read as "<label>: <value>, <source>".
 * Pass "..." as the value while a run is in progress; tabular digits keep the layout still as numbers change.
 */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier, source: String? = null, tone: Tone = Tone.Neutral) {
    val c = Pt.colors
    val color = toneColor(tone)
    Row(
        modifier.fillMaxWidth().heightIn(min = 28.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(value, style = Pt.type.tabular, color = color, textAlign = TextAlign.End)
        if (source != null) {
            Text(
                source,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                color = c.muted,
                modifier = Modifier.width(64.dp).background(c.paper2, CircleShape).padding(horizontal = 6.dp, vertical = 1.dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** A table column: [weight] is its share of the width; [numeric] right-aligns it and sets tabular digits. */
data class TableColumn(val header: String, val weight: Float = 1f, val numeric: Boolean = false)

/** A table cell: plain [text] (the usual case), or [content] for a chip or a bar. [text] is still the spoken value. */
class TableCell(val text: String, val content: (@Composable () -> Unit)? = null)

/**
 * A dense table (iOS has none, the Workbench needs it): a header row in muted semibold, hairline rules between
 * rows, numbers right-aligned in tabular digits, text truncated at the end. Rows are 40 dp when static and 48 dp
 * with [onRowClick] (touch target); the [selectedRow] is tinted with the accent at 12% and has `selected`
 * semantics. Each row is read as one item, "<header>: <value>" per cell. It does not scroll by itself: put it in
 * the screen's scroll, or in a `LazyColumn` of your own if it can hold hundreds of rows (the run log).
 */
@Composable
fun DataTable(
    columns: List<TableColumn>,
    rows: List<List<TableCell>>,
    modifier: Modifier = Modifier,
    onRowClick: ((Int) -> Unit)? = null,
    selectedRow: Int? = null,
) {
    val c = Pt.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            columns.forEach { col ->
                Text(
                    col.header, Modifier.weight(col.weight),
                    style = MaterialTheme.typography.titleSmall, color = c.muted,
                    textAlign = if (col.numeric) TextAlign.End else TextAlign.Start,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(color = c.rule)
        rows.forEachIndexed { r, cells ->
            val spoken = columns.indices.joinToString(", ") { i -> "${columns[i].header}: ${cells.getOrNull(i)?.text.orEmpty()}" }
            val selected = r == selectedRow
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = if (onRowClick != null) UiTokens.MinTouch else 40.dp)
                    .background(if (selected) c.accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                    .then(if (onRowClick != null) Modifier.selectable(selected = selected, role = Role.Button, onClick = { onRowClick(r) }) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics(mergeDescendants = true) { contentDescription = spoken },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                columns.forEachIndexed { i, col ->
                    val cell = cells.getOrNull(i)
                    Box(Modifier.weight(col.weight), contentAlignment = if (col.numeric) Alignment.CenterEnd else Alignment.CenterStart) {
                        when {
                            cell == null -> {}
                            cell.content != null -> cell.content.invoke()
                            else -> Text(
                                cell.text,
                                style = if (col.numeric) Pt.type.tabular else MaterialTheme.typography.bodyMedium,
                                color = c.ink,
                                textAlign = if (col.numeric) TextAlign.End else TextAlign.Start,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = c.rule.copy(alpha = 0.5f))
        }
    }
}

/**
 * Pass and fail as a bar and as words (the Workbench's pass rate per case and per kind): green for [passed],
 * the blocked tone for the rest (a failed case is a blocked outcome, the only red the screen uses), a paper2
 * track when nothing has run. [text] is the caller's localised words, for example "17 of 20", and is always
 * shown beside the bar: the bar is never the only cue. 8 dp bar, [barWidth] wide.
 */
@Composable
fun PassRateBar(passed: Int, total: Int, text: String, modifier: Modifier = Modifier, barWidth: androidx.compose.ui.unit.Dp = 96.dp) {
    val status = RiskMandateStatus.palette
    val c = Pt.colors
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.width(barWidth).heightIn(min = 8.dp).background(c.paper2, CircleShape)) {
            if (total > 0) {
                val good = passed.coerceIn(0, total).toFloat() / total
                if (good > 0f) Box(Modifier.weight(good).fillMaxHeight().heightIn(min = 8.dp).background(status.allowed.content, CircleShape))
                if (good < 1f) Box(Modifier.weight(1f - good).fillMaxHeight().heightIn(min = 8.dp).background(status.blocked.content.copy(alpha = 0.85f), CircleShape))
            } else Box(Modifier.heightIn(min = 8.dp))
        }
        Text(text, style = Pt.type.tabular, color = c.ink, maxLines = 1, softWrap = false)
    }
}
