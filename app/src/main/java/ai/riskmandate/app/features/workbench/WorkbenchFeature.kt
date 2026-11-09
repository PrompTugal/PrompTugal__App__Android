package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.features.HoldIdleWhile
import ai.riskmandate.app.experiments.AndroidDeviceGuard
import ai.riskmandate.app.experiments.DeviceGuard
import ai.riskmandate.app.experiments.RunSettings as ToolRunSettings
import ai.riskmandate.app.features.workbench.tooltests.ToolTestsTab
import ai.riskmandate.app.llm.DeviceProbe
import ai.riskmandate.app.llm.DeviceSample
import ai.riskmandate.app.llm.RunRecord
import ai.riskmandate.app.llm.LlmLocation
import ai.riskmandate.app.llm.LlmState
import ai.riskmandate.app.ui.ChipKind
import ai.riskmandate.app.ui.SegmentedControl
import ai.riskmandate.app.ui.StatusChip
import ai.riskmandate.app.llm.prompts.Prompts
import ai.riskmandate.app.llm.LlmRunner
import ai.riskmandate.app.ui.FeatureContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Workbench (brief `briefs/2026-11-09__on-device-llm/workbench.md`): an experiment control centre for the
 * on-device model. Tabs: Run and Experiments (dev.llm), Tool tests (dev.lab). Staff only: switched off and
 * reached from Diagnostics > Preview, hidden in stand mode by the host. Everything runs on the device.
 */
object WorkbenchFeature : Feature {
    override val id = "workbench"
    // The conductor moves the title to features.xml (feature_workbench_title) when adding this to FEATURES.
    override val title = R.string.workbench_title
    override val enabled = false

    @Composable
    override fun Content(context: FeatureContext) = WorkbenchContent(context)
}

object WorkbenchTags {
    const val TABS = "workbench_tabs"
    const val MODEL_CHIP = "workbench_model_chip"
}

/** The fixed system-prompt presets; the RiskMandate base block is added from llm/prompts when it has loaded. */
val WORKBENCH_PRESETS = listOf(
    SystemPreset(R.string.workbench_system_none, ""),
    SystemPreset(R.string.workbench_system_brief, "You are a helpful assistant. Answer briefly and plainly."),
    SystemPreset(
        R.string.workbench_system_tools,
        "You can call the tools offered when one fits the request. If none fits, answer in text or ask a question. Never invent a tool.",
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkbenchContent(
    context: FeatureContext,
    io: CoroutineDispatcher = Dispatchers.IO,
    /** Device readings and the clock, injectable for tests (QA); the app reads the real ones. */
    probe: (() -> DeviceSample?)? = null,
    guard: DeviceGuard? = null,
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
    wallClock: () -> Long = { System.currentTimeMillis() },
) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val service = context.llm
    val read: () -> DeviceSample? = remember(probe) { probe ?: { DeviceProbe.sample(app) } }
    // The caps decide on experiments/'s guard (shared with the Lab and the tool tests); the probe records readings.
    val capsGuard: DeviceGuard = remember(guard) { guard ?: AndroidDeviceGuard(app) }
    val runner = remember(service) { service?.let { LlmRunner(it, probe = read, clock = clock, wallClock = wallClock) } }
    val state by (service?.state ?: remember { MutableStateFlow<LlmState>(LlmState.NoModel) }).collectAsState()
    val activeModel = when (val s = state) {
        is LlmState.Ready -> "${s.modelId} (${s.backend})"
        is LlmState.Idle -> s.modelId
        is LlmState.Loading -> s.modelId
        is LlmState.Failed -> s.modelId
        LlmState.NoModel -> null
    }
    var presets by remember { mutableStateOf(WORKBENCH_PRESETS) }
    LaunchedEffect(Unit) {
        withContext(io) { runCatching { Prompts.loadBlocking(app.assets, Prompts.RISKMANDATE_BASE) }.getOrNull() }?.let { base ->
            presets = WORKBENCH_PRESETS.take(1) + SystemPreset(R.string.workbench_system_riskmandate, base) + WORKBENCH_PRESETS.drop(1)
        }
    }
    val store = remember { RunLogStore(File(app.noBackupFilesDir, RunLogStore.DIR)) }
    var log by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    LaunchedEffect(Unit) { log = withContext(io) { store.load() } }
    fun record(label: String, r: RunRecord) {
        val e = LogEntry.of(label, r)
        log = (listOf(e) + log).take(RunLogStore.MAX_ENTRIES)
        scope.launch { withContext(io) { store.append(e) } }
    }

    // The Run tab's form and last run live here, so switching tabs keeps them; the form survives rotation.
    var savedForm by rememberSaveable { mutableStateOf(RunForm.save(RunForm())) }
    val form = RunForm.restore(savedForm)
    var last by remember { mutableStateOf<RunRecord?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    // A run holds stand mode's idle reset off and keeps the screen on, and releases both when it ends.
    context.HoldIdleWhile(running)
    val view = LocalView.current
    DisposableEffect(running) {
        // Restore what was there before, not false: another screen may hold it on (security R-wb-7).
        val previous = view.keepScreenOn
        if (running) view.keepScreenOn = true
        onDispose { if (running) view.keepScreenOn = previous }
    }

    FeatureContent(scrollable = false, maxContentWidth = androidx.compose.ui.unit.Dp.Unspecified) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Provenance: where it runs (the service says), and the model and backend actually in use.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(
                    stringResource(if (service?.location == LlmLocation.CLOUD) R.string.workbench_where_cloud else R.string.workbench_where_device),
                    ChipKind.Neutral,
                )
                activeModel?.let { StatusChip(it, ChipKind.Neutral, modifier = Modifier.testTag(WorkbenchTags.MODEL_CHIP)) }
            }
            SegmentedControl(
                labels = listOf(stringResource(R.string.workbench_tab_run), stringResource(R.string.workbench_tab_experiments), stringResource(R.string.workbench_tab_tools)),
                selected = tab,
                onSelect = { if (!running) tab = it },
                modifier = Modifier.widthIn(max = 420.dp).testTag(WorkbenchTags.TABS),
            )
            when (tab) {
                0 -> RunTab(
                    runner = runner, activeModel = activeModel, onOpenModels = { context.openFeature("models") },
                    presets = presets, toolSets = emptyList(),
                    form = form, onForm = { savedForm = RunForm.save(it) }, last = last, onLast = { last = it },
                    onRunning = { running = it }, onRecord = { record("run", it) },
                )
                1 -> ExperimentsTab(
                    runner = runner, base = form.settings(emptyList()), probe = read, guard = capsGuard, log = log,
                    onRecord = ::record,
                    onClearLog = { log = emptyList(); last = null; scope.launch { withContext(io) { store.clear() } } },
                    onRunning = { running = it }, clock = clock,
                )
                else -> {
                    // dev.lab's tab (features/workbench/tooltests): a plain column; the Workbench owns scrolling.
                    val s = form.settings(emptyList())
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ToolTestsTab(
                            llm = service, openFeature = context.openFeature, onBusy = { running = it },
                            settings = if (s == null) ToolRunSettings() else ToolRunSettings(sampling = s.sampling, maxTokens = s.maxTokens),
                        )
                    }
                }
            }
        }
    }
}
