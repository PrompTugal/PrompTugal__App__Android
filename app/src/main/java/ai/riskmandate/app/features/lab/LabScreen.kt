package ai.riskmandate.app.features.lab

import ai.riskmandate.app.R
import ai.riskmandate.app.ui.ChipKind
import ai.riskmandate.app.ui.FeatureContent
import ai.riskmandate.app.ui.LocalWideLayout
import ai.riskmandate.app.ui.NoticeBanner
import ai.riskmandate.app.ui.SectionCard
import ai.riskmandate.app.ui.StatusChip
import ai.riskmandate.app.ui.UiTokens
import ai.riskmandate.app.ui.rememberReduceMotion
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

object LabTags {
    const val BANNER = "lab_banner"
    const val RUN = "lab_run"
    const val RAW = "lab_path_raw"
    const val ABP = "lab_path_abp"
    const val RESULT_RAW = "lab_result_raw"
    const val RESULT_ABP = "lab_result_abp"
    const val NETWORK = "lab_network"
    fun scenario(id: String) = "lab_scenario_$id"
}

/** Delay between steps while a run is revealed, so a visitor can follow it. Snaps to the end for reduced motion. */
private const val STEP_MS = 700L

/**
 * The ABP Lab, side by side: the same turns run down a raw path (fake tools that only record) and down the
 * ABP path (every call checked in Kotlin first). Replay only for now: [recordings] supplies the turns.
 * [network] is injected for tests; null reads the device's real state.
 */
@Composable
fun LabScreen(
    recordings: List<Recording> = LabPresets.ALL,
    initialScenario: String = LabScenarios.PLANTED,
    network: NetworkState? = null,
) {
    val context = LocalContext.current
    val reduce = rememberReduceMotion()
    var scenarioId by rememberSaveable { mutableStateOf(initialScenario) }
    val scenario = LabScenarios.byId(scenarioId) ?: LabScenarios.ALL.first()
    val recording = recordings.firstOrNull { it.scenarioId == scenario.id }
    val net = remember(network) { network ?: NetworkReader.read(context) }
    var runId by rememberSaveable(scenario.id) { mutableIntStateOf(0) }
    var shown by remember(scenario.id) { mutableIntStateOf(0) }

    val result = remember(scenario.id, recording, net) {
        recording?.let { LabRunner.runBoth(scenario, it.raw, it.abp, net) }
    }
    val total = result?.let { maxOf(it.raw.events.size, it.abp.events.size) } ?: 0
    LaunchedEffect(scenario.id, runId) {
        if (runId == 0) { shown = 0; return@LaunchedEffect }
        if (reduce) { shown = total; return@LaunchedEffect }
        shown = 0
        while (shown < total) { delay(STEP_MS); shown++ }
    }

    FeatureContent(maxContentWidth = 1200.dp) {
        if (recording?.sample != false) NoticeBanner(stringResource(R.string.lab_banner_sample), Modifier.testTag(LabTags.BANNER))
        Text(stringResource(R.string.lab_fakes_note), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))

        Row(
            Modifier.padding(vertical = UiTokens.Gap).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (s in LabScenarios.ALL) {
                val selected = s.id == scenario.id
                val select = { scenarioId = s.id }
                val selectedText = stringResource(R.string.lab_selected)
                val mod = Modifier.heightIn(min = UiTokens.MinTouch).testTag(LabTags.scenario(s.id)).semantics { if (selected) stateDescription = selectedText }
                if (selected) Button(select, mod) { Text(stringResource(s.title)) } else OutlinedButton(select, mod) { Text(stringResource(s.title)) }
            }
        }

        SectionCard(stringResource(scenario.title)) {
            Text(stringResource(scenario.blurb), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.lab_failure_is, stringResource(scenario.failure)), style = MaterialTheme.typography.bodyMedium)
            LabeledText(R.string.lab_task_label, scenario.task)
            if (scenario.document.isNotEmpty()) LabeledText(R.string.lab_document_label, scenario.document)
        }

        if (scenario.selfKnowledge) {
            SectionCard(stringResource(R.string.lab_net_title), Modifier.padding(top = UiTokens.Gap).testTag(LabTags.NETWORK)) {
                val state = if (net.connected) stringResource(R.string.lab_net_connected, stringResource(transportName(net.transport)))
                else stringResource(R.string.lab_net_offline)
                StatusChip(state, if (net.connected) ChipKind.Held else ChipKind.Neutral)
                Text(stringResource(R.string.lab_net_note), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Button(
            onClick = { runId++ },
            enabled = result != null,
            modifier = Modifier.padding(vertical = UiTokens.Gap).heightIn(min = 56.dp).testTag(LabTags.RUN),
        ) { Text(stringResource(if (runId == 0) R.string.lab_run else R.string.lab_run_again)) }

        if (result != null && runId > 0) {
            val raw: @Composable (Modifier) -> Unit = { m ->
                PathPanel(R.string.lab_raw_title, R.string.lab_raw_sub, result.raw, shown, total, false, LabTags.RAW, LabTags.RESULT_RAW, m)
            }
            val abp: @Composable (Modifier) -> Unit = { m ->
                PathPanel(R.string.lab_abp_title, R.string.lab_abp_sub, result.abp, shown, total, true, LabTags.ABP, LabTags.RESULT_ABP, m)
            }
            if (LocalWideLayout.current) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiTokens.Gap)) {
                    raw(Modifier.weight(1f)); abp(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(UiTokens.Gap)) { raw(Modifier); abp(Modifier) }
            }
        }
    }
}

@Composable
private fun LabeledText(@StringRes label: Int, text: String) {
    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Plain text, monospaced: the planted strings are shown, never parsed or opened.
    Text(text, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
}

@Composable
private fun PathPanel(
    @StringRes title: Int,
    @StringRes sub: Int,
    path: PathResult,
    shown: Int,
    total: Int,
    abp: Boolean,
    tag: String,
    resultTag: String,
    modifier: Modifier,
) {
    SectionCard(stringResource(title), modifier.testTag(tag)) {
        Text(stringResource(sub), style = MaterialTheme.typography.bodyMedium)
        path.events.take(shown).forEachIndexed { i, e -> EventRow(i + 1, e) }
        if (shown >= total) {
            val failed = !abp && path.failed
            val msg = when {
                !abp && failed -> stringResource(R.string.lab_result_raw_failed)
                !abp -> stringResource(R.string.lab_result_raw_ok)
                path.stopped > 0 -> stringResource(R.string.lab_result_abp_stopped, path.stopped)
                else -> stringResource(R.string.lab_result_abp_none)
            }
            val kind = if (!abp) (if (failed) ChipKind.Blocked else ChipKind.Neutral) else (if (path.stopped > 0) ChipKind.Allowed else ChipKind.Neutral)
            StatusChip(msg, kind, Modifier.testTag(resultTag).semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

@Composable
private fun EventRow(n: Int, e: Event) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.lab_step, n), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        e.say?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        e.call?.let { Text(formatCall(it), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace) }
        e.claim?.let { Text(formatClaim(it), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace) }
        OutcomeChip(e.outcome)
    }
}

@Composable
private fun OutcomeChip(o: Outcome) {
    when (o) {
        is Outcome.Ran -> StatusChip(
            stringResource(if (o.violation) R.string.lab_chip_ran_violation else R.string.lab_chip_ran),
            if (o.violation) ChipKind.Blocked else ChipKind.Neutral,
        )
        is Outcome.Stopped -> {
            StatusChip(stringResource(R.string.lab_chip_stopped, stringResource(blockName(o.reason))), ChipKind.Allowed)
            Text(o.detail, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        is Outcome.ClaimShown -> StatusChip(
            stringResource(if (o.invented) R.string.lab_chip_invented else R.string.lab_chip_in_graph),
            if (o.invented) ChipKind.Blocked else ChipKind.Neutral,
        )
        is Outcome.ClaimChecked ->
            if (o.flag == null) StatusChip(stringResource(R.string.lab_chip_within), ChipKind.Neutral)
            else StatusChip(stringResource(R.string.lab_chip_flagged, stringResource(flagName(o.flag))), ChipKind.Allowed)
        is Outcome.Answer -> if (o.remote) StatusChip(stringResource(R.string.lab_chip_remote), ChipKind.Blocked)
    }
}

internal fun formatCall(c: ToolCall): String =
    c.name + "(" + c.args.entries.joinToString(", ") { "${it.key}=\"${it.value}\"" } + ")"

internal fun formatClaim(c: Claim): String =
    "${c.subject} | ${c.relation} | ${c.obj}" + (c.cites?.let { "  [$it]" } ?: "")

@StringRes
internal fun blockName(b: Block): Int = when (b) {
    Block.UNKNOWN_TOOL -> R.string.lab_block_unknown_tool
    Block.BAD_ARGUMENTS -> R.string.lab_block_bad_arguments
    Block.OUT_OF_MANDATE -> R.string.lab_block_out_of_mandate
    Block.READ_ONLY -> R.string.lab_block_read_only
    Block.EGRESS_DESTINATION -> R.string.lab_block_egress_destination
    Block.EGRESS_SECRET -> R.string.lab_block_egress_secret
}

@StringRes
internal fun flagName(f: Flag): Int = when (f) {
    Flag.UNCITED -> R.string.lab_flag_uncited
    Flag.MISMATCH -> R.string.lab_flag_mismatch
    Flag.UNKNOWN_CITE -> R.string.lab_flag_unknown_cite
    Flag.CONTRADICTS_DEVICE -> R.string.lab_flag_contradicts_device
}

@StringRes
internal fun transportName(t: Transport): Int = when (t) {
    Transport.WIFI -> R.string.lab_net_wifi
    Transport.CELLULAR -> R.string.lab_net_cellular
    Transport.ETHERNET -> R.string.lab_net_ethernet
    Transport.OTHER, Transport.NONE -> R.string.lab_net_other
}
