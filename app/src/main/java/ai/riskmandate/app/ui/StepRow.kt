package ai.riskmandate.app.ui

import ai.riskmandate.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One step in a lane of the Lab (and anywhere a run is listed): a number, what happened in one line
 * ([title], e.g. "Proposed: share_document"), optionally the call's arguments in monospace ([detail],
 * model output, shown as plain text and never as a link), and on the right a [StatusChip] verdict.
 *
 * The left and right lanes of the Lab use the same row, so a step lines up with its twin. With [large]
 * the title is 28sp bold and the chip 22sp (2 m); the detail stays 18sp for people at the tablet.
 * TalkBack reads "<n>. <title>, <verdict>. <detail>".
 */
@Composable
fun StepRow(
    index: Int,
    title: String,
    verdict: String,
    kind: ChipKind,
    modifier: Modifier = Modifier,
    detail: String? = null,
    large: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.ui_step_number, index),
            fontSize = if (large) 28.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = if (large) 28.sp else 18.sp,
                lineHeight = if (large) 34.sp else 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            if (detail != null) {
                Text(detail, fontSize = 18.sp, fontFamily = FontFamily.Monospace, color = scheme.onSurfaceVariant)
            }
        }
        StatusChip(verdict, kind, large = large)
    }
}
