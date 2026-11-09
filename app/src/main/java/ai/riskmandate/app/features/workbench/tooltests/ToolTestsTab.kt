package ai.riskmandate.app.features.workbench.tooltests

import ai.riskmandate.app.R
import ai.riskmandate.app.experiments.AndroidDeviceGuard
import ai.riskmandate.app.experiments.CaseRun
import ai.riskmandate.app.experiments.PassStat
import ai.riskmandate.app.experiments.RunSettings
import ai.riskmandate.app.experiments.StopReason
import ai.riskmandate.app.experiments.ToolCatalogue
import ai.riskmandate.app.experiments.ToolMode
import ai.riskmandate.app.experiments.ToolTestAssets
import ai.riskmandate.app.experiments.ToolTestReport
import ai.riskmandate.app.experiments.ToolTestSnapshot
import ai.riskmandate.app.experiments.ToolTestSuite
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.LlmState
import ai.riskmandate.app.ui.ChipKind
import ai.riskmandate.app.ui.SectionCard
import ai.riskmandate.app.ui.StatTile
import ai.riskmandate.app.ui.StatusChip
import ai.riskmandate.app.ui.UiTokens
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

object ToolTestTags {
    const val RUN = "tt_run"
    const val STOP = "tt_stop"
    const val SCOPE_ALL = "tt_scope_all"
    const val MODE_NATIVE = "tt_mode_native"
    const val MODE_TEXT = "tt_mode_text"
    const val MODE_BOTH = "tt_mode_both"
    const val REPEATS = "tt_repeats"
    const val REPEATS_PLUS = "tt_repeats_plus"
    const val REPEATS_MINUS = "tt_repeats_minus"
    const val PROGRESS = "tt_progress"
    const val OVERALL_NATIVE = "tt_overall_native"
    const val OVERALL_TEXT = "tt_overall_text"
    const val UNFINISHED = "tt_unfinished"
    const val STOP_REASON = "tt_stop_reason"
    const val NO_MODEL = "tt_no_model"
    const val OPEN_MODELS = "tt_open_models"
    const val FAILURE = "tt_failure"
    const val NOTHING_KEPT = "tt_nothing_kept"
    fun kind(id: String) = "tt_kind_$id"
    fun case(id: String) = "tt_case_$id"
    fun scope(id: String) = "tt_scope_$id"
    fun scopeCase(id: String) = "tt_scope_case_$id"
}

/** What a tool-test run has so far. Compose state; saved across rotation through [ToolTestSnapshot]. */
internal class SuiteState {
    var runs by mutableStateOf<List<CaseRun>>(emptyList())
    var planned by mutableIntStateOf(0)
    var running by mutableStateOf(false)
    var stop by mutableStateOf<StopReason?>(null)
    var batteryStart by mutableStateOf<Int?>(null)
    var batteryEnd by mutableStateOf<Int?>(null)
    var outcome by mutableStateOf<ToolTestSuite.Outcome?>(null)
    @Volatile var stopRequested = false
    var job: Job? = null
}

/** A rotation stops a running suite and keeps what it had (the same rule as the Lab's loop). */
private val suiteSaver: Saver<SuiteState, String> = Saver(
    save = { ToolTestSnapshot.encode(it.runs, it.planned, if (it.running) StopReason.INTERRUPTED else it.stop) },
    restore = { text -> ToolTestSnapshot.decode(text)?.let { r -> SuiteState().also { st -> st.runs = r.runs; st.planned = r.planned; st.stop = r.stop } } },
)

/**
 * Tool tests (Workbench tab 3): functional evals of tool calling on the on-device model. Run one case, a kind or
 * all, in the runtime's native mode, the text protocol or both, N times each; see the pass rate per mode, per
 * kind and per case, and every failure with the raw output. Fakes only; output is plain text. [llm] null or no
 * model shows setup. [onBusy] tells the host a run is going (it holds stand mode's idle reset). The tab draws a
 * plain column: the hosting screen owns scrolling and the frame. [catalogue] is injected by tests; null loads
 * the bundled cases off the main thread.
 */
@Composable
fun ToolTestsTab(
    llm: LlmService?,
    openFeature: (String) -> Unit = {},
    onBusy: (Boolean) -> Unit = {},
    settings: RunSettings = RunSettings(),
    catalogue: ToolCatalogue? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val loaded by produceState(catalogue) { if (catalogue == null) value = withContext(Dispatchers.IO) { ToolTestAssets.load(context.assets) } }
    val cat = loaded
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiTokens.Gap)) {
        if (cat == null) { Text(stringResource(R.string.workbench_tt_loading), style = MaterialTheme.typography.titleMedium); return@Column }
        if (cat.cases.isEmpty()) { Text(stringResource(R.string.workbench_tt_none), style = MaterialTheme.typography.titleMedium); return@Column }
        if (cat.problems.isNotEmpty()) Text(stringResource(R.string.workbench_tt_problems, cat.problems.size), style = MaterialTheme.typography.bodyMedium)
        Body(cat, llm, openFeature, onBusy, settings)
    }
}

@Composable
private fun Body(cat: ToolCatalogue, llm: LlmService?, openFeature: (String) -> Unit, onBusy: (Boolean) -> Unit, settings: RunSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = rememberSaveable(saver = suiteSaver) { SuiteState() }
    var kindId by rememberSaveable { mutableStateOf("") }
    var caseId by rememberSaveable { mutableStateOf("") }
    var modeName by rememberSaveable { mutableStateOf("BOTH") }
    var repeats by rememberSaveable { mutableIntStateOf(3) }
    DisposableEffect(st) { onDispose { st.job?.cancel() } }
    val view = LocalView.current
    DisposableEffect(st.running) {
        if (st.running) view.keepScreenOn = true
        onDispose { if (st.running) view.keepScreenOn = false }
    }
    LaunchedEffect(st.running) { onBusy(st.running) }
    DisposableEffect(Unit) { onDispose { onBusy(false) } }

    val llmState = llm?.state?.collectAsState()?.value
    val ready = llm != null && (llmState is LlmState.Ready || llmState is LlmState.Idle)
    val selected = when {
        caseId.isNotEmpty() -> listOfNotNull(cat.case(caseId))
        kindId.isNotEmpty() -> cat.casesOf(kindId)
        else -> cat.cases
    }.ifEmpty { cat.cases }
    val modes = when (modeName) { "NATIVE" -> listOf(ToolMode.NATIVE); "TEXT" -> listOf(ToolMode.TEXT); else -> ToolMode.entries }
    val planned = ToolTestSuite.plan(selected, modes, repeats).size

    fun start() {
        val l = llm ?: return
        st.job?.cancel()
        st.runs = emptyList(); st.stop = null; st.outcome = null; st.stopRequested = false; st.planned = planned; st.running = true
        val guard = AndroidDeviceGuard(context)
        st.job = scope.launch {
            try {
                val out = ToolTestSuite.run(
                    l, selected, modes, repeats, settings, null, guard, stopRequested = { st.stopRequested },
                    onRun = { r, _, p -> st.runs = st.runs + r; st.planned = p },
                )
                st.outcome = out; st.stop = out.stop; st.batteryStart = out.batteryStartPct; st.batteryEnd = out.batteryEndPct
            } finally {
                st.running = false
            }
        }
    }

    Text(stringResource(R.string.workbench_tt_intro), style = MaterialTheme.typography.bodyLarge)

    // Scope: all, one kind, or one case.
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScopeButton(stringResource(R.string.workbench_tt_scope_all), kindId.isEmpty(), ToolTestTags.SCOPE_ALL, !st.running) { kindId = ""; caseId = "" }
        for (k in cat.kinds) ScopeButton(k.title, kindId == k.id, ToolTestTags.scope(k.id), !st.running) { kindId = k.id; caseId = "" }
    }
    if (kindId.isNotEmpty()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScopeButton(stringResource(R.string.workbench_tt_whole_kind), caseId.isEmpty(), ToolTestTags.scopeCase("whole"), !st.running) { caseId = "" }
            for (c in cat.casesOf(kindId)) ScopeButton(c.id, caseId == c.id, ToolTestTags.scopeCase(c.id), !st.running) { caseId = c.id }
        }
    }

    // Mode and repeats.
    Text(stringResource(R.string.workbench_tt_mode_help), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScopeButton(stringResource(R.string.workbench_tt_native), modeName == "NATIVE", ToolTestTags.MODE_NATIVE, !st.running) { modeName = "NATIVE" }
        ScopeButton(stringResource(R.string.workbench_tt_text), modeName == "TEXT", ToolTestTags.MODE_TEXT, !st.running) { modeName = "TEXT" }
        ScopeButton(stringResource(R.string.workbench_tt_both), modeName == "BOTH", ToolTestTags.MODE_BOTH, !st.running) { modeName = "BOTH" }
    }
    val repeatsLabel = stringResource(R.string.workbench_tt_repeats, repeats)
    val fewer = stringResource(R.string.workbench_tt_decrease, repeatsLabel)
    val more = stringResource(R.string.workbench_tt_increase, repeatsLabel)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ repeats = (repeats - 1).coerceAtLeast(1) }, Modifier.heightIn(min = 56.dp).semantics { contentDescription = fewer }.testTag(ToolTestTags.REPEATS_MINUS), enabled = !st.running) { Text(stringResource(R.string.workbench_tt_minus), fontSize = 22.sp) }
        Text(repeatsLabel, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag(ToolTestTags.REPEATS))
        OutlinedButton({ repeats = (repeats + 1).coerceAtMost(ToolTestSuite.MAX_REPEATS) }, Modifier.heightIn(min = 56.dp).semantics { contentDescription = more }.testTag(ToolTestTags.REPEATS_PLUS), enabled = !st.running) { Text(stringResource(R.string.workbench_tt_plus), fontSize = 22.sp) }
        Text(stringResource(R.string.workbench_tt_planned, planned), style = MaterialTheme.typography.bodyMedium)
    }

    if (!ready) {
        SectionCard(stringResource(R.string.workbench_tt_no_model_title), Modifier.testTag(ToolTestTags.NO_MODEL)) {
            val loading = llmState as? LlmState.Loading
            Text(if (loading != null) stringResource(R.string.workbench_tt_loading_model, loading.modelId) else stringResource(R.string.workbench_tt_no_model_body), style = MaterialTheme.typography.bodyLarge)
            if (llm != null && loading == null) Button({ openFeature("models") }, Modifier.heightIn(min = 56.dp).testTag(ToolTestTags.OPEN_MODELS)) { Text(stringResource(R.string.workbench_tt_open_models), fontSize = 20.sp) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (st.running) OutlinedButton({ st.stopRequested = true }, Modifier.heightIn(min = 56.dp).testTag(ToolTestTags.STOP)) { Text(stringResource(R.string.workbench_tt_stop), fontSize = 20.sp) }
            else Button({ start() }, Modifier.heightIn(min = 56.dp).testTag(ToolTestTags.RUN)) { Text(stringResource(R.string.workbench_tt_run), fontSize = 20.sp) }
        }
    }
    if (st.running) {
        Text(
            stringResource(if (st.stopRequested) R.string.workbench_tt_loop_stopping else R.string.workbench_tt_loop_run_of, (st.runs.size + 1).coerceAtMost(st.planned.coerceAtLeast(1)), st.planned),
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(ToolTestTags.PROGRESS).semantics { liveRegion = LiveRegionMode.Polite },
        )
        LinearProgressIndicator(progress = { if (st.planned == 0) 0f else st.runs.size.toFloat() / st.planned }, modifier = Modifier.fillMaxWidth())
    }
    if (st.runs.isNotEmpty() || st.stop != null) Results(cat, st)
}

@Composable
private fun ScopeButton(label: String, selected: Boolean, tag: String, enabled: Boolean, onClick: () -> Unit) {
    val selectedText = stringResource(R.string.workbench_tt_selected)
    val mod = Modifier.heightIn(min = 56.dp).testTag(tag).semantics { if (selected) stateDescription = selectedText }
    if (selected) Button(onClick, mod, enabled = enabled) { Text(label, fontSize = 18.sp) } else OutlinedButton(onClick, mod, enabled = enabled) { Text(label, fontSize = 18.sp) }
}

private fun pct(s: PassStat): String = s.rate?.let { String.format(Locale.getDefault(), "%.0f%%", it * 100) } ?: "-"

@Composable
private fun Results(cat: ToolCatalogue, st: SuiteState) {
    val report = remember(st.runs) { ToolTestReport(st.runs) }
    val modes = report.modes()
    val short = mapOf(ToolMode.NATIVE to stringResource(R.string.workbench_tt_native_short), ToolMode.TEXT to stringResource(R.string.workbench_tt_text_short))
    Column(verticalArrangement = Arrangement.spacedBy(UiTokens.Gap)) {
        // Overall, one tile per mode: the headline is which way of asking works on this model.
        Row(horizontalArrangement = Arrangement.spacedBy(UiTokens.Gap), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            for (m in modes) {
                val s = report.overall(m)
                StatTile(
                    "${pct(s)}  " + stringResource(R.string.workbench_tt_stat_of, s.passed, s.finished), stringResource(if (m == ToolMode.NATIVE) R.string.workbench_tt_native else R.string.workbench_tt_text),
                    Modifier.testTag(if (m == ToolMode.NATIVE) ToolTestTags.OVERALL_NATIVE else ToolTestTags.OVERALL_TEXT), large = true,
                )
            }
            val unfinished = report.overall().notFinished
            if (unfinished > 0) StatTile(unfinished.toString(), stringResource(R.string.workbench_tt_stat_unfinished), Modifier.testTag(ToolTestTags.UNFINISHED))
        }
        stopText(st.stop)?.let { Text(stringResource(it), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(ToolTestTags.STOP_REASON)) }

        SectionCard(stringResource(R.string.workbench_tt_by_kind)) {
            for (k in cat.kinds) {
                if (report.byKind(k.id).finished + report.byKind(k.id).notFinished == 0) continue
                Row(Modifier.fillMaxWidth().testTag(ToolTestTags.kind(k.id)), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(modes.joinToString("   ") { m -> report.byKind(k.id, m).let { "${short.getValue(m)} ${it.passed}/${it.finished}" } }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        SectionCard(stringResource(R.string.workbench_tt_by_case)) {
            for (c in cat.cases) {
                if (report.byCase(c.id).let { it.finished + it.notFinished } == 0) continue
                Column(Modifier.fillMaxWidth().testTag(ToolTestTags.case(c.id)).padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c.id, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(
                            modes.joinToString("   ") { m -> report.byCase(c.id, m).let { "${short.getValue(m)} ${it.passed}/${it.finished}" + (it.meanMs?.let { ms -> " ${ms} ms" } ?: "") } },
                            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(c.tests, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val failures = report.failures()
        if (failures.isNotEmpty()) {
            Text(stringResource(R.string.workbench_tt_failures, failures.size), style = MaterialTheme.typography.titleLarge)
            for (f in failures.take(MAX_FAILURES_SHOWN)) Failure(f)
            if (failures.size > MAX_FAILURES_SHOWN) Text(stringResource(R.string.workbench_tt_more_failures, failures.size - MAX_FAILURES_SHOWN), style = MaterialTheme.typography.bodyMedium)
            if (failures.any { it.steps.isEmpty() }) Text(stringResource(R.string.workbench_tt_nothing_kept), Modifier.testTag(ToolTestTags.NOTHING_KEPT), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private const val MAX_FAILURES_SHOWN = 20

@Composable
private fun modeShort(m: ToolMode) = stringResource(if (m == ToolMode.NATIVE) R.string.workbench_tt_native_short else R.string.workbench_tt_text_short)

@Composable
private fun Failure(r: CaseRun) {
    SectionCard(r.caseId + "  ·  " + modeShort(r.mode) + "  ·  " + stringResource(R.string.workbench_tt_run_n, r.rep + 1), Modifier.testTag(ToolTestTags.FAILURE)) {
        for (c in r.checks.filter { !it.passed }) {
            StatusChip(c.label, ChipKind.Blocked)
            if (c.detail.isNotEmpty()) Text(c.detail, style = MaterialTheme.typography.bodyMedium)
        }
        // The raw output, as plain selectable text: what the model said and called, and its answer.
        val raw = if (r.calls.isEmpty() && r.steps.size == 1 && r.finalText.isEmpty()) r.steps.first().text else ToolTestSnapshot.rawExcerpt(r).ifEmpty { "" }
        if (raw.isNotEmpty()) SelectionContainer { Text(raw, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace) }
    }
}

@androidx.annotation.StringRes
private fun stopText(stop: StopReason?): Int? = when (stop) {
    StopReason.LOW_BATTERY -> R.string.workbench_tt_stop_low_battery
    StopReason.HOT -> R.string.workbench_tt_stop_hot
    StopReason.TIME_CAP -> R.string.workbench_tt_stop_time
    StopReason.ERRORS -> R.string.workbench_tt_stop_errors
    StopReason.STOPPED -> R.string.workbench_tt_stop_user
    StopReason.INTERRUPTED -> R.string.workbench_tt_stop_turned
    StopReason.DONE, null -> null
}
