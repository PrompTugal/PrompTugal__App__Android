package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.features.HoldIdleWhile
import ai.riskmandate.app.llm.DeviceProbe
import ai.riskmandate.app.llm.LlmHost
import ai.riskmandate.app.llm.LlmRunner
import ai.riskmandate.app.llm.ModelCatalog
import ai.riskmandate.app.llm.ModelFiles
import ai.riskmandate.app.llm.ModelSpec
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/** The system-prompt presets. The chat's RiskMandate block is dev.chat's and joins when it is shared in llm/. */
val WORKBENCH_PRESETS = listOf(
    SystemPreset(R.string.workbench_system_none, ""),
    SystemPreset(R.string.workbench_system_brief, "You are a helpful assistant. Answer briefly and plainly."),
    SystemPreset(
        R.string.workbench_system_tools,
        "You can call the tools offered when one fits the request. If none fits, answer in text or ask a question. Never invent a tool.",
    ),
)

@Composable
internal fun WorkbenchContent(context: FeatureContext, io: CoroutineDispatcher = Dispatchers.IO) {
    val app = LocalContext.current.applicationContext
    val service = context.llm
    val runner = remember(service) {
        service?.let { s ->
            LlmRunner(s, select = { m, b -> (s as? LlmHost)?.select(m, b) }, probe = { DeviceProbe.sample(app) })
        }
    }
    var models by remember { mutableStateOf<List<ModelSpec>>(emptyList()) }
    LaunchedEffect(Unit) {
        models = withContext(io) {
            val dir = ModelFiles.internalDir(app)
            ModelCatalog.ALL.filter { it.downloadable && File(dir, it.fileName).isFile }
        }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    // A run holds stand mode's idle reset off and keeps the screen on, and releases both when it ends.
    context.HoldIdleWhile(running)
    val view = LocalView.current
    DisposableEffect(running) {
        if (running) view.keepScreenOn = true
        onDispose { if (running) view.keepScreenOn = false }
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
                0 -> RunTab(runner = runner, models = models, presets = WORKBENCH_PRESETS, toolSets = emptyList(), onRunning = { running = it })
                1 -> Text(stringResource(R.string.workbench_experiments_soon))
                else -> Text(stringResource(R.string.workbench_tools_soon))
            }
        }
    }
}
