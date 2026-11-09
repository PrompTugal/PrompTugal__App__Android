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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
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
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.LlmState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    const val LIVE_RUN = "lab_live_run"
    const val LIVE_STOP = "lab_live_stop"
    const val LIVE_CHIP = "lab_live_chip"
    const val NO_MODEL = "lab_no_model"
    const val OPEN_MODELS = "lab_open_models"
    const val INJECT = "lab_inject"
    const val RUN_ERROR = "lab_run_error"
    fun scenario(id: String) = "lab_scenario_$id"
}

/** Delay between steps while a run is revealed, so a visitor can follow it. All at once with animations off. */
private const val STEP_MS = 700L

/**
 * The ABP Lab, side by side: the same run down a raw lane (fake tools that only record) and an ABP lane
 * (every call checked in Kotlin first). Two sources: **live** on the on-device model when [llm] is ready, and
 * **replay** of a recording (always labelled as a replay). [data] is injected by tests, null loads the bundled
 * files off the main thread. [network] likewise: null reads the device's real network state for scenario 5.
 * [openFeature] takes the visitor to model setup when there is no model.
 */
@Composable
fun LabScreen(
    data: LabData? = null,
    initialScenario: String? = null,
    network: NetworkState? = null,
    llm: LlmService? = null,
    openFeature: (String) -> Unit = {},
) {
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
        LabBody(lab, initialScenario, network ?: remember { NetworkReader.read(context) }, reduce, llm, openFeature)
    }
}

/** What a live run has so far. Compose state, owned by the screen and dropped when the scenario changes. */
private class LiveState {
    var scenario by mutableStateOf<Scenario?>(null)
    var raw by mutableStateOf<PathRecord?>(null)
    var abp by mutableStateOf<PathRecord?>(null)
    var rawDone by mutableStateOf(false)
    var abpDone by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var running by mutableStateOf(false)
    var model by mutableStateOf<String?>(null)
    var job: Job? = null
}

private enum class Source { NONE, LIVE, REPLAY }

@Composable
private fun LabBody(lab: LabData, initialScenario: String?, net: NetworkState, reduce: Boolean, llm: LlmService?, openFeature: (String) -> Unit) {
    var scenarioId by rememberSaveable { mutableStateOf(initialScenario ?: lab.scenarios.first().id) }
    val scenario = lab.scenario(scenarioId) ?: lab.scenarios.first()
    val recording = lab.recordingsFor(scenario.id).firstOrNull()
    var sourceName by rememberSaveable(scenario.id) { mutableStateOf(Source.NONE.name) }
    val source = Source.valueOf(sourceName)
    var replayRun by rememberSaveable(scenario.id) { mutableIntStateOf(0) }
    var shown by remember(scenario.id) { mutableIntStateOf(0) }
    var typed by rememberSaveable { mutableStateOf("") }
    val st = remember(scenario.id) { LiveState() }
    DisposableEffect(st) { onDispose { st.job?.cancel() } }
    val scope = rememberCoroutineScope()

    val llmState = llm?.state?.collectAsState()?.value
    val liveReady = llm != null && (llmState is LlmState.Ready || llmState is LlmState.Idle)
    val live = source == Source.LIVE

    val rawRec: PathRecord? = if (live) st.raw else if (source == Source.REPLAY) recording?.raw else null
    val abpRec: PathRecord? = if (live) st.abp else if (source == Source.REPLAY) recording?.abp else null
    val effective = if (live) st.scenario ?: scenario else scenario
    val rawRows = remember(effective, rawRec) { rawRec?.let { LaneModel.rows(effective, it, abp = false) }.orEmpty() }
    val abpRows = remember(effective, abpRec) { abpRec?.let { LaneModel.rows(effective, it, abp = true) }.orEmpty() }
    val total = maxOf(rawRows.size, abpRows.size)
    LaunchedEffect(scenario.id, replayRun, source) {
        if (source != Source.REPLAY) return@LaunchedEffect
        if (reduce) { shown = total; return@LaunchedEffect }
        shown = 0
        while (shown < total) { delay(STEP_MS); shown++ }
    }

    fun startLive() {
        val l = llm ?: return
        st.job?.cancel()
        val sc = LiveRunner.withInjected(scenario, typed)
        st.scenario = sc; st.raw = null; st.abp = null; st.rawDone = false; st.abpDone = false; st.error = null; st.running = true; st.model = null
        sourceName = Source.LIVE.name
        st.job = scope.launch {
            try {
                coroutineScope {
                    val a = async { LiveRunner.runPath(l, sc, abp = false) { st.raw = it } }
                    val b = async { LiveRunner.runPath(l, sc, abp = true) { st.abp = it } }
                    for ((deferred, isAbp) in listOf(a to false, b to true)) {
                        when (val r = deferred.await()) {
                            is LiveRunner.Result.Done -> {
                                if (isAbp) { st.abp = r.record; st.abpDone = true } else { st.raw = r.record; st.rawDone = true }
                                if (r.modelId != null) st.model = listOfNotNull(r.modelId, r.backend).joinToString(", ")
                            }
                            is LiveRunner.Result.Failed -> st.error = r.reason
                        }
                    }
                }
            } finally {
                st.running = false
            }
        }
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

    if (liveReady) {
        OutlinedTextField(
            value = typed, onValueChange = { typed = it.take(LiveRunner.MAX_INJECT_CHARS) },
            label = { Text(stringResource(R.string.lab_inject_label)) }, supportingText = { Text(stringResource(R.string.lab_inject_note)) },
            maxLines = 3, modifier = Modifier.fillMaxWidth().padding(top = UiTokens.Gap).heightIn(min = 56.dp).testTag(LabTags.INJECT),
        )
        if (live && st.scenario != null && typed.isNotBlank()) Labeled(R.string.lab_inject_shown, typed.trim().take(LiveRunner.MAX_INJECT_CHARS))
    } else {
        SectionCard(stringResource(R.string.lab_no_model_title), Modifier.padding(top = UiTokens.Gap).testTag(LabTags.NO_MODEL)) {
            val loading = llmState as? LlmState.Loading
            Text(
                if (loading != null) stringResource(R.string.lab_loading_model, loading.modelId) else stringResource(R.string.lab_no_model_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (llm != null && loading == null) Button({ openFeature("models") }, Modifier.heightIn(min = 56.dp).testTag(LabTags.OPEN_MODELS)) {
                Text(stringResource(R.string.lab_open_models), fontSize = 20.sp)
            }
        }
    }

    Row(Modifier.padding(vertical = UiTokens.Gap), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (liveReady) {
            if (st.running) OutlinedButton({ st.job?.cancel() }, Modifier.heightIn(min = 56.dp).testTag(LabTags.LIVE_STOP)) { Text(stringResource(R.string.lab_stop), fontSize = 20.sp) }
            else Button({ startLive() }, Modifier.heightIn(min = 56.dp).testTag(LabTags.LIVE_RUN)) { Text(stringResource(R.string.lab_run), fontSize = 20.sp) }
        }
        if (recording != null) {
            val label = stringResource(if (source == Source.REPLAY) R.string.lab_replay_again else R.string.lab_replay)
            val modifier = Modifier.heightIn(min = 56.dp).testTag(LabTags.RUN)
            val go: () -> Unit = { st.job?.cancel(); sourceName = Source.REPLAY.name; replayRun++ }
            if (liveReady) OutlinedButton(go, modifier) { Text(label, fontSize = 20.sp) } else Button(go, modifier) { Text(label, fontSize = 20.sp) }
        }
    }
    if (recording == null && !liveReady) {
        Text(stringResource(R.string.lab_no_recording), style = MaterialTheme.typography.titleMedium)
    }

    if (source == Source.NONE) return
    if (live && st.running) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
        Text(stringResource(R.string.lab_running), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium)
    }
    Provenance(live, recording, scenario, st.model)
    st.error?.takeIf { live }?.let { StatusChip(stringResource(R.string.lab_run_error, it), ChipKind.Blocked, Modifier.padding(vertical = 8.dp).testTag(LabTags.RUN_ERROR)) }

    if (rawRec != null || abpRec != null) {
        val lanes: @Composable (Modifier, Boolean) -> Unit = { m, abp ->
            Lane(
                title = if (abp) R.string.lab_abp_title else R.string.lab_raw_title, sub = if (abp) R.string.lab_abp_sub else R.string.lab_raw_sub,
                rows = if (abp) abpRows else rawRows, total = total, shown = if (live) total else shown,
                replay = if (live) null else recording, tag = if (abp) LabTags.ABP else LabTags.RAW, modifier = m,
            )
        }
        if (LocalWideLayout.current) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiTokens.Gap)) { lanes(Modifier.weight(1f), false); lanes(Modifier.weight(1f), true) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(UiTokens.Gap)) { lanes(Modifier, false); lanes(Modifier, true) }
        }
        val finished = if (live) st.rawDone && st.abpDone && st.error == null else shown >= total
        if (finished && rawRec != null && abpRec != null) Summary(rawRec, abpRec, rawRows, abpRows)
    }
}

@Composable
private fun Provenance(live: Boolean, recording: Recording?, scenario: Scenario, model: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        if (live) {
            StatusChip(stringResource(R.string.lab_chip_live), ChipKind.Allowed, Modifier.testTag(LabTags.LIVE_CHIP))
            model?.let { StatusChip(stringResource(R.string.lab_model_line, it), ChipKind.Neutral) }
        } else if (recording != null) {
            StatusChip(stringResource(R.string.lab_chip_replay), ChipKind.Held)
            if (recording.sourceKind == "sample") StatusChip(stringResource(R.string.lab_chip_sample), ChipKind.Neutral)
            recording.modelLabel?.let { StatusChip(stringResource(R.string.lab_model_line, it), ChipKind.Neutral) }
        }
        if (scenario.failure.heuristic) StatusChip(stringResource(R.string.lab_chip_heuristic), ChipKind.Held)
    }
}

@Composable
private fun Lane(
    title: Int, sub: Int, rows: List<LaneRow>, total: Int, shown: Int,
    replay: Recording?, tag: String, modifier: Modifier,
) {
    SectionCard(stringResource(title), modifier.testTag(tag), large = true) {
        if (replay != null) {
            val what = listOf(replay.backend, replay.recordedAt).filter { !it.isNullOrBlank() }.joinToString(", ")
            Text(
                stringResource(R.string.lab_replay_strip, what.ifEmpty { replay.modelLabel.orEmpty() }),
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag(LabTags.REPLAY_STRIP),
            )
        }
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
private fun Summary(raw: PathRecord, abp: PathRecord, rawRows: List<LaneRow>, abpRows: List<LaneRow>) {
    val rawStep = LaneModel.decisiveStep(rawRows)
    val abpStep = LaneModel.decisiveStep(abpRows)
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    Column(Modifier.padding(top = UiTokens.Gap), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (raw.outcome.failure && rawStep != null) stringResource(R.string.lab_summary_raw_failed, rawStep) else stringResource(R.string.lab_summary_raw_ok),
            fontSize = 28.sp, lineHeight = 34.sp, modifier = live.testTag(LabTags.SUMMARY_RAW),
        )
        Text(
            when {
                abp.outcome.failure -> stringResource(R.string.lab_summary_abp_missed)
                abp.outcome.caught && abpStep != null -> stringResource(R.string.lab_summary_abp_caught, abpStep)
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
