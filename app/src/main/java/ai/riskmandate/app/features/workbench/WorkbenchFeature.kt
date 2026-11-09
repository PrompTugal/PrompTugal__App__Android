package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.features.HoldIdleWhile
import ai.riskmandate.app.llm.DeviceProbe
import ai.riskmandate.app.llm.DeviceSample
import ai.riskmandate.app.llm.RunRecord
import ai.riskmandate.app.llm.LlmState
import ai.riskmandate.app.llm.prompts.Prompts
import ai.riskmandate.app.llm.LlmRunner
import ai.riskmandate.app.ui.FeatureContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
    fun tab(i: Int) = "workbench_tab_$i"
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

@Composable
internal fun WorkbenchContent(
    context: FeatureContext,
    io: CoroutineDispatcher = Dispatchers.IO,
    /** Device readings and the clock, injectable for tests (QA); the app reads the real ones. */
    probe: (() -> DeviceSample?)? = null,
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
    wallClock: () -> Long = { System.currentTimeMillis() },
) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val service = context.llm
    val read: () -> DeviceSample? = remember(probe) { probe ?: { DeviceProbe.sample(app) } }
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
        runCatching { Prompts.load(app.assets, Prompts.RISKMANDATE_BASE) }.getOrNull()?.let { base ->
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

    FeatureContent {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TabRow(selectedTabIndex = tab) {
                listOf(R.string.workbench_tab_run, R.string.workbench_tab_experiments, R.string.workbench_tab_tools).forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { if (!running) tab = i }, text = { Text(stringResource(label)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag(WorkbenchTags.tab(i)))
                }
            }
            when (tab) {
                0 -> RunTab(
                    runner = runner, activeModel = activeModel, onOpenModels = { context.openFeature("models") },
                    presets = presets, toolSets = emptyList(),
                    form = form, onForm = { savedForm = RunForm.save(it) }, last = last, onLast = { last = it },
                    onRunning = { running = it }, onRecord = { record("run", it) },
                )
                1 -> ExperimentsTab(
                    runner = runner, base = form.settings(emptyList()), probe = read, log = log,
                    onRecord = ::record,
                    onClearLog = { log = emptyList(); last = null; scope.launch { withContext(io) { store.clear() } } },
                    onRunning = { running = it }, clock = clock,
                )
                else -> Text(stringResource(R.string.workbench_tools_soon))
            }
        }
    }
}
