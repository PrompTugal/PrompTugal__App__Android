package ai.riskmandate.app.features.workbench.tooltests

import ai.riskmandate.app.Pt
import ai.riskmandate.app.R
import ai.riskmandate.app.experiments.AndroidDeviceGuard
import ai.riskmandate.app.experiments.CaseRun
import ai.riskmandate.app.experiments.CodeJudge
import ai.riskmandate.app.experiments.CloudJudge
import ai.riskmandate.app.experiments.JudgeLayer
import ai.riskmandate.app.experiments.JudgeOutcome
import ai.riskmandate.app.experiments.JudgePanel
import ai.riskmandate.app.experiments.JudgePrompt
import ai.riskmandate.app.experiments.JudgeReport
import ai.riskmandate.app.experiments.LocalModelJudge
import ai.riskmandate.app.experiments.PanelResult
import ai.riskmandate.app.experiments.PassStat
import ai.riskmandate.app.experiments.PromptSize
import ai.riskmandate.app.experiments.RunSettings
import ai.riskmandate.app.experiments.StopReason
import ai.riskmandate.app.experiments.ToolCase
import ai.riskmandate.app.experiments.ToolCatalogue
import ai.riskmandate.app.experiments.ToolMode
import ai.riskmandate.app.experiments.ToolTestAssets
import ai.riskmandate.app.experiments.ToolTestReport
import ai.riskmandate.app.experiments.ToolTestSnapshot
import ai.riskmandate.app.experiments.ToolTestSuite
import ai.riskmandate.app.llm.LlmLocation
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.LlmState
import ai.riskmandate.app.ui.ChipKind
import ai.riskmandate.app.ui.DataTable
import ai.riskmandate.app.ui.LocalWideLayout
import ai.riskmandate.app.ui.PassRateBar
import ai.riskmandate.app.ui.SectionCard
import ai.riskmandate.app.ui.SegmentedControl
import ai.riskmandate.app.ui.StatRow
import ai.riskmandate.app.ui.StatTile
import ai.riskmandate.app.ui.StatusChip
import ai.riskmandate.app.ui.StepRow
import ai.riskmandate.app.ui.TableCell
import ai.riskmandate.app.ui.TableColumn
import ai.riskmandate.app.ui.TitledCard
import ai.riskmandate.app.ui.Tone
import ai.riskmandate.app.ui.UiTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
    const val OVERALL_ANSWERS = "tt_overall_answers"
    const val UNFINISHED = "tt_unfinished"
    const val STOP_REASON = "tt_stop_reason"
    const val NO_MODEL = "tt_no_model"
    const val OPEN_MODELS = "tt_open_models"
    const val FAILURE = "tt_failure"
    const val NOTHING_KEPT = "tt_nothing_kept"
    const val RUN_KIND = "tt_run_kind"
    const val RUN_CASE = "tt_run_case"
    const val JUDGE_LOCAL = "tt_judge_local"
    const val JUDGE_CLOUD = "tt_judge_cloud"
    const val CLOUD_ON = "tt_cloud_on"
    const val DISAGREEMENTS = "tt_disagreements"
    const val RAW = "tt_raw"
    const val PROMPT_SIZE = "tt_prompt_size"
    const val TABLE = "tt_table"
    fun run(i: Int) = "tt_run_$i"
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
    /** What the judge panel made of each finished run. Not saved across rotation: it is read live. */
    var judged by mutableStateOf<List<PanelResult>>(emptyList())
    @Volatile var stopRequested = false
    var job: Job? = null
}

/** A rotation stops a running suite and keeps what it had (the same rule as the Lab's loop). */
private val suiteSaver: Saver<SuiteState, String> = Saver(
    save = { ToolTestSnapshot.encode(it.runs, it.planned, if (it.running) StopReason.INTERRUPTED else it.stop) },
    restore = { text -> ToolTestSnapshot.decode(text)?.let { r -> SuiteState().also { st -> st.runs = r.runs; st.planned = r.planned; st.stop = r.stop } } },
)


private val REPEAT_CHOICES = listOf(1, 3, 5, 10)
private val MODE_CHOICES = listOf(ToolMode.NATIVE, ToolMode.TEXT, null) // null: both

/**
 * Tool tests (Workbench tab 3): functional evals of tool calling on the on-device model, laid out as design's two
 * panes: cases by kind with pass rates on the left, the selected case with its runs and raw output on the right
 * (one column on a narrow screen). Run all, a kind or a case, in the runtime's native mode, the text protocol or
 * both, N times each. Fakes only; output is plain text; every check runs in code. There is no export here.
 *
 * [llm] null or no model shows setup. [cloudLlm] (the host passes it only when a key is stored and stand mode is
 * off) adds an opt-in cloud judge: it sees only the synthetic case and the output, never anything staff typed, and
 * the tab says "Cloud" with the model while it is on. [onBusy] tells the host a run is going. The tab draws a plain
 * column: the hosting screen owns scrolling and the frame. [catalogue] is injected by tests; null loads the
 * bundled cases off the main thread.
 */
@Composable
fun ToolTestsTab(
    llm: LlmService?,
    openFeature: (String) -> Unit = {},
    onBusy: (Boolean) -> Unit = {},
    settings: RunSettings = RunSettings(),
    catalogue: ToolCatalogue? = null,
    modifier: Modifier = Modifier,
    cloudLlm: LlmService? = null,
) {
    val context = LocalContext.current
    val loaded by produceState(catalogue) { if (catalogue == null) value = withContext(Dispatchers.IO) { ToolTestAssets.load(context.assets) } }
    val prompts by produceState<Map<String, JudgePrompt>>(emptyMap()) { value = withContext(Dispatchers.IO) { ToolTestAssets.loadJudgePrompts(context.assets).first } }
    val cat = loaded
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiTokens.Gap)) {
        if (cat == null) { Text(stringResource(R.string.workbench_tt_loading), style = MaterialTheme.typography.titleMedium); return@Column }
        if (cat.cases.isEmpty()) { Text(stringResource(R.string.workbench_tt_none), style = MaterialTheme.typography.titleMedium); return@Column }
        if (cat.problems.isNotEmpty()) Text(stringResource(R.string.workbench_tt_problems, cat.problems.size), style = MaterialTheme.typography.bodyMedium)
        Body(cat, llm, cloudLlm?.takeIf { it.location == LlmLocation.CLOUD }, prompts[JUDGE_PROMPT_ID], openFeature, onBusy, settings)
    }
}

private const val JUDGE_PROMPT_ID = "tool-output"

private fun modelIdOf(s: LlmState?): String? = when (s) {
    is LlmState.Ready -> s.modelId
    is LlmState.Idle -> s.modelId
    is LlmState.Loading -> s.modelId
    is LlmState.Failed -> s.modelId
    else -> null
}

/** A selection in the table: a kind row ("k:<id>") or a case row ("c:<id>"). */
private fun kindSel(id: String) = "k:$id"
private fun caseSel(id: String) = "c:$id"

@Composable
private fun Body(
    cat: ToolCatalogue, llm: LlmService?, cloud: LlmService?, judgePrompt: JudgePrompt?,
    openFeature: (String) -> Unit, onBusy: (Boolean) -> Unit, settings: RunSettings,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = rememberSaveable(saver = suiteSaver) { SuiteState() }
    var sel by rememberSaveable { mutableStateOf("") }
    var modeIndex by rememberSaveable { mutableIntStateOf(2) }
    var repeatIndex by rememberSaveable { mutableIntStateOf(1) }
    var pickedRun by rememberSaveable { mutableIntStateOf(-1) }
    // Judges start off after any rotation or visit: the cloud judge in particular is a visible, per-visit choice.
    var judgeLocal by remember { mutableStateOf(false) }
    var judgeCloud by remember { mutableStateOf(false) }
    DisposableEffect(st) { onDispose { st.job?.cancel() } }
    val view = LocalView.current
    DisposableEffect(st.running) {
        if (st.running) view.keepScreenOn = true
        onDispose { if (st.running) view.keepScreenOn = false }
    }
    LaunchedEffect(st.running) { onBusy(st.running) }
    DisposableEffect(Unit) { onDispose { onBusy(false) } }

    val llmState = llm?.state?.collectAsState()?.value
    val cloudState = cloud?.state?.collectAsState()?.value
    val ready = llm != null && (llmState is LlmState.Ready || llmState is LlmState.Idle)
    val cloudReady = cloud != null && (cloudState is LlmState.Ready || cloudState is LlmState.Idle)
    val cloudOn = judgeCloud && cloudReady && judgePrompt != null
    val repeats = REPEAT_CHOICES[repeatIndex]
    val modes = MODE_CHOICES[modeIndex]?.let { listOf(it) } ?: ToolMode.entries

    val selCase = if (sel.startsWith("c:")) cat.case(sel.drop(2)) else null
    val selKind = when { selCase != null -> selCase.kind; sel.startsWith("k:") -> sel.drop(2); else -> null }
    fun casesFor(scope: String): List<ToolCase> = when (scope) {
        "case" -> listOfNotNull(selCase)
        "kind" -> selKind?.let { cat.casesOf(it) }.orEmpty()
        else -> cat.cases
    }

    fun start(which: String) {
        val l = llm ?: return
        val cases = casesFor(which).ifEmpty { return }
        st.job?.cancel()
        val panel = if ((judgeLocal || cloudOn) && judgePrompt != null) {
            JudgePanel(listOf(CodeJudge()) + listOfNotNull(
                if (judgeLocal) LocalModelJudge(l, judgePrompt) else null,
                if (cloudOn) CloudJudge(cloud!!, judgePrompt) else null,
            ))
        } else null
        st.runs = emptyList(); st.judged = emptyList(); st.stop = null; st.outcome = null; st.stopRequested = false
        st.planned = ToolTestSuite.plan(cases, modes, repeats).size; st.running = true; pickedRun = -1
        val guard = AndroidDeviceGuard(context)
        st.job = scope.launch {
            try {
                val out = ToolTestSuite.run(
                    l, cases, modes, repeats, settings, null, guard, stopRequested = { st.stopRequested },
                    onRun = { r, _, p -> st.runs = st.runs + r; st.planned = p },
                    panel = panel, onJudged = { st.judged = st.judged + it },
                )
                st.outcome = out; st.stop = out.stop; st.batteryStart = out.batteryStartPct; st.batteryEnd = out.batteryEndPct
            } finally {
                st.running = false
            }
        }
    }

    val report = remember(st.runs) { ToolTestReport(st.runs) }
    val judgeReport = remember(st.judged) { JudgeReport(st.judged) }
    val left: @Composable () -> Unit = {
        Left(cat, st, report, judgeReport, sel, { sel = it; pickedRun = -1 }, repeats, ready, llmState, llm != null, openFeature,
            selCase, selKind, cat.kinds.firstOrNull { it.id == selKind }?.title, ::start)
    }
    val right: @Composable () -> Unit = {
        Right(
            cat, st, report, judgeReport, selCase, selKind, modeIndex, { modeIndex = it }, repeatIndex, { repeatIndex = it },
            pickedRun, { pickedRun = it }, judgeLocal, { judgeLocal = it }, judgeCloud, { judgeCloud = it },
            cloud, cloudReady, cloudState, judgePrompt != null,
        )
    }
    if (cloudOn) CloudOnBanner(modelIdOf(cloudState).orEmpty())
    if (LocalWideLayout.current) {
        Row(horizontalArrangement = Arrangement.spacedBy(UiTokens.Gap), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(UiTokens.Gap)) { left() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiTokens.Gap)) { right() }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.Gap)) { left(); right() }
    }
}

/** Visible for as long as the cloud judge is on: what is sent, where, and which model. Never an unlabelled mix. */
@Composable
private fun CloudOnBanner(modelId: String) {
    Row(Modifier.fillMaxWidth().testTag(ToolTestTags.CLOUD_ON).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(stringResource(R.string.workbench_tt_cloud_on), ChipKind.Held)
        Text(stringResource(R.string.workbench_tt_cloud_on_detail, modelId), style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted)
    }
}

private fun pct(s: PassStat): String = s.rate?.let { String.format(Locale.getDefault(), "%.0f%%", it * 100) } ?: "-"

private fun toneOf(s: PassStat): Tone = when (val r = s.rate) {
    null -> Tone.Neutral
    else -> if (r >= 0.85) Tone.Good else if (r >= 0.60) Tone.Warn else Tone.Bad
}

@Composable
private fun ofText(s: PassStat) = stringResource(R.string.workbench_tt_stat_of, s.passed, s.finished)

@Composable
private fun rateCell(s: PassStat): TableCell {
    val none = stringResource(R.string.workbench_tt_dash)
    val text = if (s.finished == 0) none else ofText(s)
    return TableCell(text) { if (s.finished == 0) Text(none, style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted) else PassRateBar(s.passed, s.finished, text, barWidth = 44.dp) }
}

@Composable
private fun modeName(m: ToolMode) = stringResource(if (m == ToolMode.NATIVE) R.string.workbench_tt_native else R.string.workbench_tt_text)

@Composable
private fun Left(
    cat: ToolCatalogue, st: SuiteState, report: ToolTestReport, judgeReport: JudgeReport, sel: String, onSel: (String) -> Unit,
    repeats: Int, ready: Boolean, llmState: LlmState?, hasService: Boolean, openFeature: (String) -> Unit,
    selCase: ToolCase?, selKind: String?, kindTitle: String?, start: (String) -> Unit,
) {
    val runsPerCase = stringResource(R.string.workbench_tt_runs_per_case, repeats)
    // Overall, one tile per way of asking: the headline is which one works on this model. Cases with no tools have no
    // way of asking to compare, so they are counted apart, in their own "Answers" tile.
    val answerIds = remember(cat) { cat.cases.filter { it.tools.isEmpty() }.map { it.id }.toSet() }
    val toolReport = remember(st.runs) { ToolTestReport(st.runs.filter { it.caseId !in answerIds }) }
    val answerReport = remember(st.runs) { ToolTestReport(st.runs.filter { it.caseId in answerIds }) }
    Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, horizontalArrangement = Arrangement.spacedBy(UiTokens.Gap)) {
        for (m in ToolMode.entries) {
            val s = toolReport.overall(m)
            StatTile(
                if (s.finished == 0) stringResource(R.string.workbench_tt_dash) else ofText(s), stringResource(if (m == ToolMode.NATIVE) R.string.workbench_tt_native_calls else R.string.workbench_tt_text_protocol),
                Modifier.weight(1f).testTag(if (m == ToolMode.NATIVE) ToolTestTags.OVERALL_NATIVE else ToolTestTags.OVERALL_TEXT),
                detail = if (s.finished == 0) null else "${pct(s)}, $runsPerCase", tone = toneOf(s),
            )
        }
        if (answerIds.isNotEmpty()) {
            val s = answerReport.overall()
            StatTile(
                if (s.finished == 0) stringResource(R.string.workbench_tt_dash) else ofText(s), stringResource(R.string.workbench_tt_answers),
                Modifier.weight(1f).testTag(ToolTestTags.OVERALL_ANSWERS),
                detail = if (s.finished == 0) null else "${pct(s)}, $runsPerCase", tone = toneOf(s),
            )
        }
    }
    val unfinished = report.overall().notFinished
    if (unfinished > 0) StatRow(stringResource(R.string.workbench_tt_stat_unfinished), unfinished.toString(), Modifier.testTag(ToolTestTags.UNFINISHED))
    stopText(st.stop)?.let { Text(stringResource(it), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(ToolTestTags.STOP_REASON)) }

    TitledCard(stringResource(R.string.workbench_tt_by_kind), kicker = stringResource(R.string.workbench_tt_checks_in_code),
        accessory = { Text(stringResource(R.string.workbench_tt_case_count, cat.cases.size), style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted) }) {
        if (!ready) NoModel(llmState, hasService, openFeature)
        else Controls(st, sel, selCase, selKind, kindTitle, start)
        if (st.running) {
            Text(
                stringResource(if (st.stopRequested) R.string.workbench_tt_loop_stopping else R.string.workbench_tt_loop_run_of, (st.runs.size + 1).coerceAtMost(st.planned.coerceAtLeast(1)), st.planned),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(ToolTestTags.PROGRESS).semantics { liveRegion = LiveRegionMode.Polite },
            )
            LinearProgressIndicator(progress = { if (st.planned == 0) 0f else st.runs.size.toFloat() / st.planned }, modifier = Modifier.fillMaxWidth())
        }
        // One row per kind (its two pass rates), then its cases.
        val ids = ArrayList<String>()
        val rows = ArrayList<List<TableCell>>()
        for (k in cat.kinds) {
            val cs = cat.casesOf(k.id)
            if (cs.isEmpty()) continue
            ids += kindSel(k.id)
            rows += listOf(
                TableCell(k.title) { Text(k.title, style = MaterialTheme.typography.titleSmall, color = Pt.colors.ink) },
                rateCell(report.byKind(k.id, ToolMode.NATIVE)), rateCell(report.byKind(k.id, ToolMode.TEXT)),
            )
            for (c in cs) {
                ids += caseSel(c.id)
                rows += listOf(TableCell(c.id), rateCell(report.byCase(c.id, ToolMode.NATIVE)), rateCell(report.byCase(c.id, ToolMode.TEXT)))
            }
        }
        DataTable(
            listOf(
                TableColumn(stringResource(R.string.workbench_tt_col_case), 1.7f),
                TableColumn(stringResource(R.string.workbench_tt_native), 1.8f),
                TableColumn(stringResource(R.string.workbench_tt_text), 1.8f),
            ),
            rows,
            Modifier.testTag(ToolTestTags.TABLE).semantics { collectionInfo = CollectionInfo(rows.size, 3) },
            onRowClick = { onSel(ids[it]) },
            selectedRow = ids.indexOf(sel).takeIf { it >= 0 },
        )
    }
    if (judgeReport.judged > 0) {
        StatRow(stringResource(R.string.workbench_tt_disagreements), judgeReport.disagreements.size.toString(), Modifier.testTag(ToolTestTags.DISAGREEMENTS), tone = if (judgeReport.disagreements.isEmpty()) Tone.Neutral else Tone.Warn)
    }
}

@Composable
private fun NoModel(llmState: LlmState?, hasService: Boolean, openFeature: (String) -> Unit) {
    Column(Modifier.testTag(ToolTestTags.NO_MODEL), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val loading = llmState as? LlmState.Loading
        Text(stringResource(R.string.workbench_tt_no_model_title), style = MaterialTheme.typography.titleMedium)
        Text(if (loading != null) stringResource(R.string.workbench_tt_loading_model, loading.modelId) else stringResource(R.string.workbench_tt_no_model_body), style = MaterialTheme.typography.bodyLarge)
        if (hasService && loading == null) Button({ openFeature("models") }, Modifier.heightIn(min = UiTokens.MinTouch).testTag(ToolTestTags.OPEN_MODELS)) { Text(stringResource(R.string.workbench_tt_open_models)) }
    }
}

@Composable
private fun Controls(st: SuiteState, sel: String, selCase: ToolCase?, selKind: String?, kindTitle: String?, start: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (st.running) {
            OutlinedButton({ st.stopRequested = true }, Modifier.heightIn(min = UiTokens.MinTouch).testTag(ToolTestTags.STOP)) { Text(stringResource(R.string.workbench_tt_stop)) }
        } else {
            Button({ start("all") }, Modifier.heightIn(min = UiTokens.MinTouch).testTag(ToolTestTags.RUN)) { Text(stringResource(R.string.workbench_tt_run_all)) }
            if (selKind != null) OutlinedButton({ start("kind") }, Modifier.heightIn(min = UiTokens.MinTouch).testTag(ToolTestTags.RUN_KIND)) { Text(stringResource(R.string.workbench_tt_run_kind, kindTitle.orEmpty())) }
            if (selCase != null) OutlinedButton({ start("case") }, Modifier.heightIn(min = UiTokens.MinTouch).testTag(ToolTestTags.RUN_CASE)) { Text(stringResource(R.string.workbench_tt_run_case, selCase.id)) }
        }
    }
}

@Composable
private fun Right(
    cat: ToolCatalogue, st: SuiteState, report: ToolTestReport, judgeReport: JudgeReport, selCase: ToolCase?, selKind: String?,
    modeIndex: Int, onMode: (Int) -> Unit, repeatIndex: Int, onRepeat: (Int) -> Unit, picked: Int, onPick: (Int) -> Unit,
    judgeLocal: Boolean, onLocal: (Boolean) -> Unit, judgeCloud: Boolean, onCloud: (Boolean) -> Unit,
    cloud: LlmService?, cloudReady: Boolean, cloudState: LlmState?, haveJudgePrompt: Boolean,
) {
    val running = st.running
    // The two ways to ask, and how often: apply to the next run, whichever scope it has.
    TitledCard(stringResource(R.string.workbench_tt_setup)) {
        Text(stringResource(R.string.workbench_tt_mode_help), style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted)
        SegmentedControl(listOf(stringResource(R.string.workbench_tt_native), stringResource(R.string.workbench_tt_text), stringResource(R.string.workbench_tt_both)), modeIndex, { if (!running) onMode(it) }, Modifier.testTag(ToolTestTags.MODE_BOTH))
        Text(stringResource(R.string.workbench_tt_repeats_label), style = MaterialTheme.typography.titleSmall, color = Pt.colors.muted)
        SegmentedControl(REPEAT_CHOICES.map { stringResource(R.string.workbench_tt_repeat_n, it) }, repeatIndex, { if (!running) onRepeat(it) }, Modifier.testTag(ToolTestTags.REPEATS))
    }
    Judges(judgeLocal, onLocal, judgeCloud, onCloud, cloud, cloudReady, cloudState, haveJudgePrompt, running)
    when {
        selCase != null -> CaseDetail(selCase, cat.kinds.firstOrNull { it.id == selCase.kind }?.title.orEmpty(), st, report, judgeReport, picked, onPick)
        selKind != null -> KindDetail(cat.kinds.firstOrNull { it.id == selKind }?.title.orEmpty(), selKind, cat, report, judgeReport)
        else -> Text(stringResource(R.string.workbench_tt_pick), style = MaterialTheme.typography.bodyLarge, color = Pt.colors.muted)
    }
}

@Composable
private fun Judges(
    local: Boolean, onLocal: (Boolean) -> Unit, cloudOn: Boolean, onCloud: (Boolean) -> Unit,
    cloud: LlmService?, cloudReady: Boolean, cloudState: LlmState?, havePrompt: Boolean, running: Boolean,
) {
    TitledCard(stringResource(R.string.workbench_tt_judges), kicker = stringResource(R.string.workbench_tt_judges_kicker)) {
        Text(stringResource(R.string.workbench_tt_judges_help), style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted)
        if (!havePrompt) { Text(stringResource(R.string.workbench_tt_judges_missing), style = MaterialTheme.typography.bodyMedium); return@TitledCard }
        SwitchRow(stringResource(R.string.workbench_tt_judge_local), local, onLocal, !running, ToolTestTags.JUDGE_LOCAL)
        if (cloud != null) {
            SwitchRow(stringResource(R.string.workbench_tt_judge_cloud), cloudOn, onCloud, !running && cloudReady, ToolTestTags.JUDGE_CLOUD)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(stringResource(R.string.workbench_tt_cloud), ChipKind.Held)
                Text(stringResource(R.string.workbench_tt_cloud_via, modelIdOf(cloudState) ?: "-"), style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted)
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean, tag: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = UiTokens.MinTouch).testTag(tag)
            .selectable(selected = on, enabled = enabled, role = Role.Switch, onClick = { onChange(!on) }),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(on, null, enabled = enabled)
    }
}

@Composable
private fun KindDetail(title: String, kindId: String, cat: ToolCatalogue, report: ToolTestReport, judgeReport: JudgeReport) {
    TitledCard(title, kicker = stringResource(R.string.workbench_tt_kind_kicker)) {
        StatRow(stringResource(R.string.workbench_tt_case_count_label), cat.casesOf(kindId).size.toString())
        for (m in ToolMode.entries) report.byKind(kindId, m).let { s ->
            StatRow(modeName(m), if (s.finished == 0) "-" else ofText(s), tone = toneOf(s))
        }
        DisagreementRow(judgeReport.byKind()[kindId] ?: 0, judgeReport.judged > 0)
    }
}

/** A label over a longer value (tool names, the expected outcome), which a one-line StatRow would squeeze. */
@Composable
private fun Stacked(label: String, value: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DisagreementRow(n: Int, any: Boolean) {
    if (any) StatRow(stringResource(R.string.workbench_tt_disagreements), n.toString(), tone = if (n == 0) Tone.Neutral else Tone.Warn)
}

@Composable
private fun CaseDetail(c: ToolCase, kindTitle: String, st: SuiteState, report: ToolTestReport, judgeReport: JudgeReport, picked: Int, onPick: (Int) -> Unit) {
    val runs = st.runs.filter { it.caseId == c.id }
    val native = report.byCase(c.id, ToolMode.NATIVE)
    val nativeChip = if (native.finished == 0) stringResource(R.string.workbench_tt_no_runs) else stringResource(R.string.workbench_tt_chip_native, native.passed, native.finished)
    TitledCard(c.id, kicker = kindTitle, accessory = {
        StatusChip(nativeChip, if (native.finished == 0) ChipKind.Neutral else if (native.passed == native.finished) ChipKind.Allowed else ChipKind.Blocked)
    }) {
        Text(stringResource(R.string.workbench_tt_what_tests, c.tests), style = MaterialTheme.typography.bodyLarge)
        Stacked(stringResource(R.string.workbench_tt_tools_offered), c.tools.joinToString(", ") { it.name })
        Stacked(stringResource(R.string.workbench_tt_expected), c.checks.joinToString("; ") { it.label })
        // Prompt size per way of asking: characters always, tokens when the runtime reported them for a run.
        Column(Modifier.testTag(ToolTestTags.PROMPT_SIZE)) {
            for (m in ToolMode.entries) {
                val tokens = runs.lastOrNull { it.mode == m && it.promptTokens != null }?.promptTokens
                val chars = PromptSize.chars(c, m)
                StatRow(stringResource(R.string.workbench_tt_prompt_size, modeName(m)), if (tokens != null) stringResource(R.string.workbench_tt_chars_tokens, chars, tokens) else stringResource(R.string.workbench_tt_chars, chars))
            }
        }
        DisagreementRow(judgeReport.byCase()[c.id] ?: 0, judgeReport.judged > 0)
    }
    if (runs.isEmpty()) return
    val failedFirst = runs.indexOfFirst { it.finished && !it.passed }
    val shown = if (picked in runs.indices) picked else failedFirst.coerceAtLeast(0)
    TitledCard(stringResource(R.string.workbench_tt_runs)) {
        Column(Modifier.semantics { collectionInfo = CollectionInfo(runs.size, 1) }) {
            runs.forEachIndexed { i, r ->
                val (verdict, kind) = verdictOf(r)
                val tps = r.decodeTokensPerSecond
                val title = if (tps != null) stringResource(R.string.workbench_tt_run_title_tps, modeName(r.mode), String.format(Locale.getDefault(), "%.1f", tps))
                else stringResource(R.string.workbench_tt_run_title_ms, modeName(r.mode), r.ms)
                val panel = st.judged.firstOrNull { it.caseId == r.caseId && it.mode == r.mode && it.rep == r.rep }
                StepRow(
                    index = i + 1, title = title, verdict = verdict, kind = kind, dense = true,
                    detail = panel?.let { judgeLine(it) },
                    modifier = Modifier.testTag(ToolTestTags.run(i)).selectable(selected = i == shown, role = Role.Button, onClick = { onPick(i) }),
                )
            }
        }
    }
    val r = runs[shown]
    if (r.finished && !r.passed || st.judged.any { it.caseId == r.caseId && it.mode == r.mode && it.rep == r.rep }) RawOutput(r, st.judged.firstOrNull { it.caseId == r.caseId && it.mode == r.mode && it.rep == r.rep })
    if (runs.any { it.finished && !it.passed && it.steps.isEmpty() }) Text(stringResource(R.string.workbench_tt_nothing_kept), Modifier.testTag(ToolTestTags.NOTHING_KEPT), style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted)
}

@Composable
private fun verdictOf(r: CaseRun): Pair<String, ChipKind> = when {
    !r.finished -> stringResource(R.string.workbench_tt_did_not_finish) to ChipKind.Neutral
    r.passed -> stringResource(R.string.workbench_tt_passed) to ChipKind.Allowed
    else -> (r.checks.firstOrNull { !it.passed }?.let { failureName(it.failure) } ?: stringResource(R.string.workbench_tt_fail_check_failed)) to ChipKind.Blocked
}

@Composable
private fun failureName(key: String?): String = stringResource(
    when (key) {
        "wrong_tool" -> R.string.workbench_tt_fail_wrong_tool
        "arguments_invalid" -> R.string.workbench_tt_fail_arguments_invalid
        "value_mismatch" -> R.string.workbench_tt_fail_value_mismatch
        "unoffered_tool" -> R.string.workbench_tt_fail_unoffered_tool
        "leaked_value" -> R.string.workbench_tt_fail_leaked_value
        "tool_when_none_fits" -> R.string.workbench_tt_fail_tool_when_none_fits
        "no_answer_after_result" -> R.string.workbench_tt_fail_no_answer_after_result
        "too_many_calls" -> R.string.workbench_tt_fail_too_many_calls
        "answer_missing_words" -> R.string.workbench_tt_fail_answer_missing_words
        "forbidden_words" -> R.string.workbench_tt_fail_forbidden_words
        "too_long" -> R.string.workbench_tt_fail_too_long
        "wrong_line_count" -> R.string.workbench_tt_fail_wrong_line_count
        "not_json_only" -> R.string.workbench_tt_fail_not_json_only
        "number_out_of_range" -> R.string.workbench_tt_fail_number_out_of_range
        "ungrounded_claim" -> R.string.workbench_tt_fail_ungrounded_claim
        else -> R.string.workbench_tt_fail_check_failed
    },
)

@Composable
private fun layerName(l: JudgeLayer) = stringResource(when (l) { JudgeLayer.CODE -> R.string.workbench_tt_layer_code; JudgeLayer.LOCAL -> R.string.workbench_tt_layer_local; JudgeLayer.CLOUD -> R.string.workbench_tt_cloud })

@Composable
private fun outcomeName(o: JudgeOutcome) = stringResource(when (o) { JudgeOutcome.PASS -> R.string.workbench_tt_o_pass; JudgeOutcome.FAIL -> R.string.workbench_tt_o_fail; JudgeOutcome.UNSURE -> R.string.workbench_tt_o_unsure })

/** One line: each layer and what it said, so a disagreement is visible next to the run. */
@Composable
private fun judgeLine(p: PanelResult): String {
    val parts = p.verdicts.map { layerName(it.layer) + " " + outcomeName(it.outcome).lowercase() }
    val line = parts.joinToString(", ")
    return if (p.disagreements.isEmpty()) line else stringResource(R.string.workbench_tt_disagree_line, line)
}

/** The raw output of the selected run as plain selectable text, and the judges' reasons under it. */
@Composable
private fun RawOutput(r: CaseRun, panel: PanelResult?) {
    TitledCard(stringResource(R.string.workbench_tt_raw), Modifier.testTag(ToolTestTags.FAILURE)) {
        for (c in r.checks.filter { !it.passed }) {
            StatusChip(failureName(c.failure), ChipKind.Blocked)
            if (c.detail.isNotEmpty()) Text(c.detail, style = MaterialTheme.typography.bodyMedium)
        }
        val raw = if (r.calls.isEmpty() && r.steps.size == 1 && r.finalText.isEmpty()) r.steps.first().text else ToolTestSnapshot.rawExcerpt(r)
        if (raw.isNotEmpty()) SelectionContainer { Text(raw, Modifier.testTag(ToolTestTags.RAW), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace) }
        for (v in panel?.verdicts.orEmpty().filter { it.layer != JudgeLayer.CODE }) {
            Text(layerName(v.layer) + (v.modelId?.let { " ($it)" } ?: "") + ": " + outcomeName(v.outcome) + ", " + v.reason, style = MaterialTheme.typography.bodyMedium)
        }
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
