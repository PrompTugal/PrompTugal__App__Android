package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.R
import ai.riskmandate.app.copyToClipboard
import ai.riskmandate.app.llm.DeviceSample
import ai.riskmandate.app.llm.LlmRunner
import ai.riskmandate.app.llm.RunRecord
import ai.riskmandate.app.llm.RunSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object ExperimentTags {
    const val MODE_SWEEP = "workbench_mode_sweep"
    const val MODE_AB = "workbench_mode_ab"
    const val VALUES = "workbench_values"
    const val VALUE_A = "workbench_value_a"
    const val VALUE_B = "workbench_value_b"
    const val REPEATS = "workbench_repeats"
    const val START = "workbench_start"
    const val STOP = "workbench_experiment_stop"
    const val PROGRESS = "workbench_progress"
    const val PLAN_ERROR = "workbench_plan_error"
    const val NO_BASE = "workbench_no_base"
    const val RESULTS = "workbench_results"
    const val COPY = "workbench_results_copy"
    const val LOG = "workbench_log"
    const val CLEAR = "workbench_clear"
    const val CONFIRM_CLEAR = "workbench_confirm_clear"
    fun variable(v: SweepVariable) = "workbench_variable_${v.name.lowercase(Locale.ROOT)}"
}

/**
 * Tab 2 of the Workbench: the run log (newest first, with a clear button), and experiments built on the Run tab's
 * settings: a sweep over one variable, or an A/B of two values, each repeated N times, within the Lab's caps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExperimentsTab(
    runner: LlmRunner?,
    /** The Run tab's current settings, or null when they are not valid. */
    base: RunSettings?,
    models: List<String>,
    probe: () -> DeviceSample?,
    log: List<LogEntry>,
    onRecord: (String, RunRecord) -> Unit,
    onClearLog: () -> Unit,
    onRunning: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ab by rememberSaveable { mutableStateOf(false) }
    var variable by rememberSaveable { mutableStateOf(SweepVariable.TEMPERATURE) }
    var values by rememberSaveable { mutableStateOf("0.2, 0.7, 1.0") }
    var valueA by rememberSaveable { mutableStateOf("0.2") }
    var valueB by rememberSaveable { mutableStateOf("1.0") }
    var repeats by rememberSaveable { mutableStateOf("3") }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf<ExperimentProgress?>(null) }
    var lastPlan by remember { mutableStateOf<ExperimentPlan?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf<Boolean?>(null) }
    val running = job != null

    val chosen = when (variable) {
        SweepVariable.SYSTEM_PROMPT -> listOf("on", "off")
        SweepVariable.MODEL -> if (ab) listOf(valueA, valueB) else models
        else -> if (ab) listOf(valueA.trim(), valueB.trim()) else ExperimentPlan.parseValues(values)
    }
    val plan = ExperimentPlan(variable, chosen, repeats.toIntOrNull() ?: 0)
    val expanded = base?.let { plan.expand(it, models) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.workbench_experiment), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        if (base == null) Text(stringResource(R.string.workbench_no_base), modifier = Modifier.testTag(ExperimentTags.NO_BASE))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !ab, enabled = !running, onClick = { ab = false }, label = { Text(stringResource(R.string.workbench_mode_sweep)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag(ExperimentTags.MODE_SWEEP))
            FilterChip(selected = ab, enabled = !running, onClick = { ab = true }, label = { Text(stringResource(R.string.workbench_mode_ab)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag(ExperimentTags.MODE_AB))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SweepVariable.entries.forEach { v ->
                FilterChip(selected = variable == v, enabled = !running, onClick = { variable = v }, label = { Text(stringResource(variableLabel(v))) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(ExperimentTags.variable(v)))
            }
        }
        when {
            variable == SweepVariable.SYSTEM_PROMPT -> Text(stringResource(R.string.workbench_values_system))
            variable == SweepVariable.MODEL && !ab -> Text(stringResource(R.string.workbench_values_models, models.joinToString(", ").ifEmpty { "-" }))
            ab -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallField(valueA, R.string.workbench_value_a, ExperimentTags.VALUE_A, !running, KeyboardType.Text) { valueA = it }
                SmallField(valueB, R.string.workbench_value_b, ExperimentTags.VALUE_B, !running, KeyboardType.Text) { valueB = it }
            }
            else -> OutlinedTextField(
                value = values, onValueChange = { values = it.take(200) }, enabled = !running, singleLine = true,
                label = { Text(stringResource(R.string.workbench_values)) }, modifier = Modifier.fillMaxWidth().testTag(ExperimentTags.VALUES),
            )
        }
        SmallField(repeats, R.string.workbench_repeats, ExperimentTags.REPEATS, !running, KeyboardType.Number) { repeats = it.filter(Char::isDigit).take(3) }
        Text(stringResource(R.string.workbench_caps), style = MaterialTheme.typography.bodySmall)
        expanded?.exceptionOrNull()?.let { Text(it.message ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(ExperimentTags.PLAN_ERROR)) }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) {
                Button(onClick = { job?.cancel() }, modifier = Modifier.heightIn(min = 48.dp).testTag(ExperimentTags.STOP)) { Text(stringResource(R.string.workbench_stop)) }
            } else {
                val runs = expanded?.getOrNull()
                Button(
                    onClick = {
                        val r = runner ?: return@Button
                        val list = runs ?: return@Button
                        copied = null
                        lastPlan = plan
                        progress = ExperimentProgress(0, list.size, null, emptyList(), null, false)
                        onRunning(true)
                        val j = scope.launch { runExperiment(list, r, probe, onRecord = onRecord) { progress = it } }
                        job = j
                        j.invokeOnCompletion { if (job === j) job = null; onRunning(false) }
                    },
                    enabled = runner != null && runs != null,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(ExperimentTags.START),
                ) { Text(stringResource(R.string.workbench_start, runs?.size ?: 0)) }
            }
        }
        progress?.let { p ->
            Text(
                if (p.finished) stringResource(R.string.workbench_progress_done, p.done, p.total) + (p.stop?.let { " " + stringResource(stopLabel(it)) } ?: "")
                else stringResource(R.string.workbench_progress, p.done + 1, p.total, p.current ?: ""),
                modifier = Modifier.testTag(ExperimentTags.PROGRESS).semantics { liveRegion = LiveRegionMode.Polite },
            )
            val planNow = lastPlan
            if (planNow != null && p.results.isNotEmpty()) {
                val text = ExperimentSummary.text(planNow, ExperimentSummary.of(planNow.values, p.results), p.stop)
                SelectionContainer {
                    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(ExperimentTags.RESULTS))
                }
                if (p.finished) {
                    OutlinedButton(onClick = { copied = copyToClipboard(context, "RiskMandate Workbench experiment", text) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag(ExperimentTags.COPY)) { Text(stringResource(R.string.workbench_results_copy)) }
                }
            }
        }
        copied?.let { Text(stringResource(if (it) R.string.workbench_copied else R.string.workbench_copy_failed)) }

        Text(stringResource(R.string.workbench_log, log.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.workbench_log_note), style = MaterialTheme.typography.bodySmall)
        if (log.isNotEmpty()) {
            TextButton(onClick = { confirmClear = true }, enabled = !running, modifier = Modifier.heightIn(min = 48.dp).testTag(ExperimentTags.CLEAR)) {
                Text(stringResource(R.string.workbench_clear))
            }
            SelectionContainer {
                Text(
                    log.take(SHOWN).joinToString("\n") { logLine(it) },
                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(ExperimentTags.LOG),
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.workbench_clear_title)) },
            text = { Text(stringResource(R.string.workbench_clear_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearLog() }, modifier = Modifier.testTag(ExperimentTags.CONFIRM_CLEAR)) {
                    Text(stringResource(R.string.workbench_clear))
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.workbench_cancel)) } },
        )
    }
}

/** One log row: when, which run, the settings, how it ended, and the key figures. */
fun logLine(e: LogEntry): String {
    val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(e.startedAtMs))
    val dec = e.decodeTps?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "-"
    return "$time ${e.label} | ${e.model} ${e.backend}${if (e.fellBack) "(fell back)" else ""} t=${e.temperature} p=${e.topP} max=${e.maxTokens}" +
        "${if (e.systemChars > 0) " sys" else ""}${if (e.tools > 0) " tools=${e.tools}" else ""} | ${e.stop.lowercase(Locale.ROOT)}" +
        " | ${if (e.cold) "cold" else "warm"} ttft=${e.firstTokenMs ?: "-"}ms dec=$dec tok/s out=${e.outputTokens ?: "-"}" +
        if (e.toolCalls > 0) " calls=${e.toolCalls} ok=${e.accepted}" else ""
}

private const val SHOWN = 100

private fun variableLabel(v: SweepVariable) = when (v) {
    SweepVariable.TEMPERATURE -> R.string.workbench_temperature
    SweepVariable.TOP_P -> R.string.workbench_top_p
    SweepVariable.MAX_TOKENS -> R.string.workbench_max_tokens
    SweepVariable.BACKEND -> R.string.workbench_backend
    SweepVariable.SYSTEM_PROMPT -> R.string.workbench_system
    SweepVariable.MODEL -> R.string.workbench_model
}

private fun stopLabel(s: ExperimentStop) = when (s) {
    ExperimentStop.RUN_CAP -> R.string.workbench_stop_runs
    ExperimentStop.TIME_CAP -> R.string.workbench_stop_time
    ExperimentStop.BATTERY -> R.string.workbench_stop_battery
    ExperimentStop.HOT -> R.string.workbench_stop_hot
    ExperimentStop.FAILURES -> R.string.workbench_stop_failures
    ExperimentStop.STOPPED -> R.string.workbench_stop_stopped
}

@Composable
private fun SmallField(value: String, label: Int, tag: String, enabled: Boolean, type: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.take(40)) }, enabled = enabled, singleLine = true,
        label = { Text(stringResource(label)) }, keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.widthIn(min = 120.dp, max = 200.dp).testTag(tag),
    )
}
