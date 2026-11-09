package ai.riskmandate.app.features.lab

import ai.riskmandate.app.R
import ai.riskmandate.app.ui.ChipKind
import ai.riskmandate.app.ui.FeatureContent
import ai.riskmandate.app.ui.LocalWideLayout
import ai.riskmandate.app.ui.NoticeBanner
import ai.riskmandate.app.ui.SectionCard
import ai.riskmandate.app.ui.StatusChip
import ai.riskmandate.app.ui.StepRow
import ai.riskmandate.app.ui.UiTokens
import ai.riskmandate.app.ui.rememberReduceMotion
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object LabTags {
    const val BANNER = "lab_banner"
    const val RUN = "lab_run"
    const val RAW = "lab_path_raw"
    const val ABP = "lab_path_abp"
    const val SUMMARY_RAW = "lab_summary_raw"
    const val SUMMARY_ABP = "lab_summary_abp"
    const val NETWORK = "lab_network"
    const val REPLAY_STRIP = "lab_replay_strip"
    fun scenario(id: String) = "lab_scenario_$id"
}

/** Delay between steps while a run is revealed, so a visitor can follow it. All at once with animations off. */
private const val STEP_MS = 700L

/**
 * The ABP Lab, side by side: the same run down a raw lane (fake tools that only record) and an ABP lane
 * (every call checked in Kotlin first). This build replays recordings (and labels them as replays);
 * [data] is injected by tests, null loads the bundled files off the main thread. [network] likewise: null
 * reads the device's real network state for scenario 5.
 */
@Composable
fun LabScreen(data: LabData? = null, initialScenario: String? = null, network: NetworkState? = null) {
    val context = LocalContext.current
    val reduce = rememberReduceMotion()
    val loaded by produceState(data) {
        if (data == null) value = withContext(Dispatchers.IO) { LabAssets.load(context.assets) }
    }
    val lab = loaded
    FeatureContent(maxContentWidth = 1200.dp) {
        NoticeBanner(stringResource(R.string.lab_banner_fakes), Modifier.testTag(LabTags.BANNER))
        if (lab == null) {
            Text(stringResource(R.string.lab_loading), Modifier.padding(top = UiTokens.Gap), style = MaterialTheme.typography.titleMedium)
            return@FeatureContent
        }
        if (lab.scenarios.isEmpty()) {
            Text(stringResource(R.string.lab_none), Modifier.padding(top = UiTokens.Gap), style = MaterialTheme.typography.titleMedium)
            return@FeatureContent
        }
        if (lab.problems.isNotEmpty()) Text(stringResource(R.string.lab_problems, lab.problems.size), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
        LabBody(lab, initialScenario, network ?: remember { NetworkReader.read(context) }, reduce)
    }
}

@Composable
private fun LabBody(lab: LabData, initialScenario: String?, net: NetworkState, reduce: Boolean) {
    var scenarioId by rememberSaveable { mutableStateOf(initialScenario ?: lab.scenarios.first().id) }
    val scenario = lab.scenario(scenarioId) ?: lab.scenarios.first()
    val recording = lab.recordingsFor(scenario.id).firstOrNull()
    var runId by rememberSaveable(scenario.id) { mutableIntStateOf(0) }
    var shown by remember(scenario.id) { mutableIntStateOf(0) }

    val rawRows = remember(scenario, recording) { recording?.let { LaneModel.rows(scenario, it.raw, abp = false) }.orEmpty() }
    val abpRows = remember(scenario, recording) { recording?.let { LaneModel.rows(scenario, it.abp, abp = true) }.orEmpty() }
    val total = maxOf(rawRows.size, abpRows.size)
    LaunchedEffect(scenario.id, runId) {
        if (runId == 0) { shown = 0; return@LaunchedEffect }
        if (reduce) { shown = total; return@LaunchedEffect }
        shown = 0
        while (shown < total) { delay(STEP_MS); shown++ }
    }

    Row(
        Modifier.padding(vertical = UiTokens.Gap).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (s in lab.scenarios) {
            val selected = s.id == scenario.id
            val selectedText = stringResource(R.string.lab_selected)
            val mod = Modifier.heightIn(min = 56.dp).testTag(LabTags.scenario(s.id)).semantics { if (selected) stateDescription = selectedText }
            if (selected) Button({ scenarioId = s.id }, mod) { Text(s.title, fontSize = 20.sp) } else OutlinedButton({ scenarioId = s.id }, mod) { Text(s.title, fontSize = 20.sp) }
        }
    }

    SectionCard(scenario.title) {
        if (scenario.description.isNotEmpty()) Text(scenario.description, style = MaterialTheme.typography.bodyLarge)
        Labeled(R.string.lab_task_label, scenario.user)
        Labeled(R.string.lab_system_label, scenario.system)
    }

    if (scenario.failure.type == FailureType.CLAIMS_REMOTE) {
        SectionCard(stringResource(R.string.lab_net_title), Modifier.padding(top = UiTokens.Gap).testTag(LabTags.NETWORK)) {
            val state = if (net.connected) stringResource(R.string.lab_net_connected, stringResource(transportName(net.transport))) else stringResource(R.string.lab_net_offline)
            StatusChip(state, if (net.connected) ChipKind.Held else ChipKind.Neutral)
            Text(stringResource(R.string.lab_net_note), style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (recording == null) {
        Text(stringResource(R.string.lab_no_recording), Modifier.padding(top = UiTokens.Gap), style = MaterialTheme.typography.titleMedium)
        return
    }

    Provenance(recording, scenario)
    Button(
        onClick = { runId++ },
        modifier = Modifier.padding(vertical = UiTokens.Gap).heightIn(min = 56.dp).testTag(LabTags.RUN),
    ) { Text(stringResource(if (runId == 0) R.string.lab_replay else R.string.lab_replay_again), fontSize = 20.sp) }

    if (runId > 0) {
        val lanes: @Composable (Modifier, Boolean) -> Unit = { m, abp ->
            Lane(
                title = if (abp) R.string.lab_abp_title else R.string.lab_raw_title, sub = if (abp) R.string.lab_abp_sub else R.string.lab_raw_sub,
                rows = if (abp) abpRows else rawRows, otherCount = if (abp) rawRows.size else abpRows.size, total = total, shown = shown,
                recording = recording, tag = if (abp) LabTags.ABP else LabTags.RAW, modifier = m,
            )
        }
        if (LocalWideLayout.current) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiTokens.Gap)) { lanes(Modifier.weight(1f), false); lanes(Modifier.weight(1f), true) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(UiTokens.Gap)) { lanes(Modifier, false); lanes(Modifier, true) }
        }
        if (shown >= total) Summary(recording, rawRows, abpRows)
    }
}

@Composable
private fun Provenance(recording: Recording, scenario: Scenario) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        StatusChip(stringResource(R.string.lab_chip_replay), ChipKind.Held)
        if (recording.sourceKind == "sample") StatusChip(stringResource(R.string.lab_chip_sample), ChipKind.Neutral)
        recording.modelLabel?.let { StatusChip(stringResource(R.string.lab_model_line, it), ChipKind.Neutral) }
        if (scenario.failure.heuristic) StatusChip(stringResource(R.string.lab_chip_heuristic), ChipKind.Held)
    }
}

@Composable
private fun Lane(
    title: Int, sub: Int, rows: List<LaneRow>, otherCount: Int, total: Int, shown: Int,
    recording: Recording, tag: String, modifier: Modifier,
) {
    SectionCard(stringResource(title), modifier.testTag(tag), large = true) {
        val what = listOf(recording.backend, recording.recordedAt).filter { !it.isNullOrBlank() }.joinToString(", ")
        Text(
            stringResource(R.string.lab_replay_strip, what.ifEmpty { recording.modelLabel.orEmpty() }),
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag(LabTags.REPLAY_STRIP),
        )
        Text(stringResource(sub), style = MaterialTheme.typography.bodyMedium)
        for (n in 0 until minOf(shown, total)) {
            val r = rows.getOrNull(n)
            if (r == null) {
                StepRow(n + 1, stringResource(R.string.lab_not_reached), stringResource(R.string.lab_not_reached), ChipKind.Neutral, large = true)
                continue
            }
            r.say?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            StepRow(
                index = n + 1,
                title = if (r.titleArg != null) stringResource(r.titleRes, r.titleArg) else stringResource(r.titleRes),
                verdict = if (r.chipArg != null) stringResource(r.chipRes, r.chipArg) else stringResource(r.chipRes),
                kind = r.kind, detail = r.detail, large = true,
            )
            r.note?.let { Text(it, style = MaterialTheme.typography.bodyLarge, fontSize = 18.sp, fontFamily = FontFamily.Monospace) }
        }
    }
}

@Composable
private fun Summary(recording: Recording, rawRows: List<LaneRow>, abpRows: List<LaneRow>) {
    val rawStep = LaneModel.decisiveStep(rawRows)
    val abpStep = LaneModel.decisiveStep(abpRows)
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    Column(Modifier.padding(top = UiTokens.Gap), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (recording.raw.outcome.failure && rawStep != null) stringResource(R.string.lab_summary_raw_failed, rawStep) else stringResource(R.string.lab_summary_raw_ok),
            fontSize = 28.sp, lineHeight = 34.sp, modifier = live.testTag(LabTags.SUMMARY_RAW),
        )
        Text(
            when {
                recording.abp.outcome.failure -> stringResource(R.string.lab_summary_abp_missed)
                recording.abp.outcome.caught && abpStep != null -> stringResource(R.string.lab_summary_abp_caught, abpStep)
                else -> stringResource(R.string.lab_summary_abp_none)
            },
            fontSize = 28.sp, lineHeight = 34.sp, modifier = live.testTag(LabTags.SUMMARY_ABP),
        )
    }
}

@Composable
private fun Labeled(label: Int, text: String) {
    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Plain, selectable, monospaced text: planted strings are shown, never parsed, linked or opened.
    SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace) }
}

@androidx.annotation.StringRes
internal fun transportName(t: Transport): Int = when (t) {
    Transport.WIFI -> R.string.lab_net_wifi
    Transport.CELLULAR -> R.string.lab_net_cellular
    Transport.ETHERNET -> R.string.lab_net_ethernet
    Transport.OTHER, Transport.NONE -> R.string.lab_net_other
}
