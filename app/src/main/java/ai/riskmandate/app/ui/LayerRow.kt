package ai.riskmandate.app.ui

import ai.riskmandate.app.R
import ai.riskmandate.app.RiskMandateStatus
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Where one layer is in screen B's story. */
enum class LayerState {
    /** Not reached yet: neutral, quiet. */
    Waiting,

    /** The planted instruction is at this layer now: amber, pulsing is the caller's choice (none built in). */
    Checking,

    /** This layer stopped it: green, with a "Stopped here" chip. */
    Stopped,

    /** The instruction already went past it or was stopped above: dimmed. */
    Passed,
}

/**
 * One row of screen B's six-layer diagram: a number, the layer's name, a one-line description and, when
 * lit, a chip. Colour animates between states (snaps when the user removed animations).
 *
 * Reading distance: with [large] the name is 44sp bold and the chip 22sp, sized for 2 metres on a
 * 10" tablet at the stand; six rows fit in 800dp of landscape height with description hidden, so pass
 * [description] = null for the stand view and show it in the detail panel instead. Without [large]
 * (phone, or a text-heavy view) it is 20sp.
 *
 * TalkBack reads "<n>. <name>, <state>. <description>" as one item; the state is its stateDescription.
 *
 * @param stateLabel the chip text for [LayerState.Checking] and [LayerState.Stopped] (shown only then);
 *   it must say the status in words, e.g. "Stopped here".
 */
@Composable
fun LayerRow(
    index: Int,
    name: String,
    state: LayerState,
    stateLabel: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    large: Boolean = false,
) {
    val palette = RiskMandateStatus.palette
    val scheme = MaterialTheme.colorScheme
    val (container, content, border) = when (state) {
        LayerState.Waiting -> Triple(scheme.surface, scheme.onSurface, scheme.outlineVariant)
        LayerState.Checking -> Triple(palette.held.container, palette.held.content, palette.held.content)
        LayerState.Stopped -> Triple(palette.allowed.container, palette.allowed.content, palette.allowed.content)
        LayerState.Passed -> Triple(scheme.surfaceVariant, scheme.onSurfaceVariant, scheme.outlineVariant)
    }
    val spec = if (rememberReduceMotion()) snap<androidx.compose.ui.graphics.Color>() else tween(450)
    val bg by animateColorAsState(container, spec, label = "layerBg")
    val fg by animateColorAsState(content, spec, label = "layerFg")
    val line by animateColorAsState(border, spec, label = "layerLine")

    val stateText = when (state) {
        LayerState.Waiting -> stringResource(R.string.ui_layer_waiting)
        LayerState.Checking -> stringResource(R.string.ui_layer_checking)
        LayerState.Stopped -> stringResource(R.string.ui_layer_stopped)
        LayerState.Passed -> stringResource(R.string.ui_layer_passed)
    }
    val badge: Dp = if (large) 64.dp else 40.dp
    val borderWidth = if (state == LayerState.Stopped || state == LayerState.Checking) 3.dp else 1.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(UiTokens.CardRadius))
            .border(BorderStroke(borderWidth, line), RoundedCornerShape(UiTokens.CardRadius))
            .padding(horizontal = 16.dp, vertical = if (large) 8.dp else 12.dp)
            .heightIn(min = if (large) 80.dp else UiTokens.MinTouch)
            .testTag(UiTags.layer(index))
            .semantics(mergeDescendants = true) { stateDescription = stateText },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(badge).border(BorderStroke(2.dp, fg), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                index.toString(),
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = if (large) 32.sp else 18.sp,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = if (large) 44.sp else 20.sp,
                lineHeight = if (large) 50.sp else 26.sp,
            )
            if (description != null) {
                Text(description, color = fg, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (state == LayerState.Stopped || state == LayerState.Checking) {
            val kind = if (state == LayerState.Stopped) ChipKind.Blocked else ChipKind.Held
            // On the lit row the chip keeps its own colours so it stays the strongest mark on the row.
            StatusChip(stateLabel, kind, large = large)
        }
    }
}
