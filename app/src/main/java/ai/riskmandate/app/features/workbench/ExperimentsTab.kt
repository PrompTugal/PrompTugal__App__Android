package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.R
import ai.riskmandate.app.copyToClipboard
import ai.riskmandate.app.experiments.DeviceGuard
import ai.riskmandate.app.experiments.RunCaps
import ai.riskmandate.app.llm.DeviceSample
import ai.riskmandate.app.llm.LlmRunner
import ai.riskmandate.app.llm.RunRecord
import ai.riskmandate.app.llm.RunSettings
import ai.riskmandate.app.ui.ChipKind
import ai.riskmandate.app.ui.DataTable
import ai.riskmandate.app.ui.LocalWideLayout
import ai.riskmandate.app.ui.NoticeBanner
import ai.riskmandate.app.ui.SegmentedControl
import ai.riskmandate.app.ui.StatRow
import ai.riskmandate.app.ui.StatusChip
import ai.riskmandate.app.ui.TableCell
import ai.riskmandate.app.ui.TableColumn
import ai.riskmandate.app.ui.TitledCard
import ai.riskmandate.app.ui.Tone
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    const val CAP_BANNER = "workbench_cap_banner"
    const val PLAN_ERROR = "workbench_plan_error"
    const val NO_BASE = "workbench_no_base"
    const val RESULTS = "workbench_results"
    const val COPY = "workbench_results_copy"
    const val VIEW = "workbench_results_view"
    const val LOG = "workbench_log"
    const val CLEAR = "workbench_clear"
    const val CONFIRM_CLEAR = "workbench_confirm_clear"
    fun variable(v: SweepVariable) = "workbench_variable_${v.name.lowercase(Locale.ROOT)}"
}

/**
 * Tab 2 of the Workbench (design spec docs/design/workbench.md): left, the builder (sweep or A/B, built on the Run
 * tab's settings) and its progress within the Lab's caps; right, the results with its own switcher: the sweep
 * table (mean ± sample standard deviation) and the run log (newest first, clear behind a confirmation).
 * One column below 840 dp.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExperimentsTab(
    runner: LlmRunner?,
    /** The Run tab's current settings, or null when they are not valid. */
    base: RunSettings?,
    /** Readings shown in the progress card (memory, battery, thermal level). */
    probe: () -> DeviceSample?,
    /** Battery and heat the caps decide on (experiments/RunCaps). */
    guard: DeviceGuard,
    log: List<LogEntry>,
    onRecord: (String, RunRecord) -> Unit,
    onClearLog: () -> Unit,
    onRunning: (Boolean) -> Unit = {},
    /** Monotonic milliseconds for the caps' 30-minute limit; injectable for tests (QA). */
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ab by rememberSaveable { mutableStateOf(false) }
    var variable by rememberSaveable { mutableStateOf(SweepVariable.TEMPERATURE) }
    var values by rememberSaveable { mutableStateOf("0.2, 0.7, 1.0") }
    var valueA by rememberSaveable { mutableStateOf("0.2") }
    var valueB by rememberSaveable { mutableStateOf("1.0") }
    var repeats by rememberSaveable { mutableStateOf("3") }
    var view by rememberSaveable { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf<ExperimentProgress?>(null) }
    var lastPlan by remember { mutableStateOf<ExperimentPlan?>(null) }
    // The last results table, as validated counters, survives a rotation (SummaryCodec).
    var savedSummary by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf<Boolean?>(null) }
    val running = job != null

    val chosen = when (variable) {
        SweepVariable.SYSTEM_PROMPT -> listOf("on", "off")
        else -> if (ab) listOf(valueA.trim(), valueB.trim()) else ExperimentPlan.parseValues(values)
    }
    val plan = ExperimentPlan(variable, chosen, repeats.toIntOrNull() ?: 0)
    val expanded = base?.let { plan.expand(it) }

    fun start(list: List<Pair<String, RunSettings>>) {
        val r = runner ?: return
        copied = null
        lastPlan = plan
        progress = ExperimentProgress(0, list.size, null, emptyList(), null, false)
        onRunning(true)
        val j = scope.launch {
            runExperiment(list, r, guard, clock = clock, onRecord = onRecord) { p ->
                progress = p
                if (p.finished) savedSummary = SummaryCodec.encode(plan, ExperimentSummary.of(plan.values, p.results), p.stop)
            }
        }
        job = j
        j.invokeOnCompletion { if (job === j) job = null; onRunning(false) }
    }

    val builder: @Composable ColumnScope.() -> Unit = {
        TitledCard(stringResource(if (ab) R.string.workbench_mode_ab else R.string.workbench_mode_sweep), kicker = stringResource(R.string.workbench_experiment)) {
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
                ab -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallField(valueA, R.string.workbench_value_a, ExperimentTags.VALUE_A, !running, KeyboardType.Text) { valueA = it }
                    SmallField(valueB, R.string.workbench_value_b, ExperimentTags.VALUE_B, !running, KeyboardType.Text) { valueB = it }
                }
                else -> OutlinedTextField(
                    value = values, onValueChange = { values = it.take(200) }, enabled = !running, singleLine = true,
                    label = { Text(stringResource(R.string.workbench_values)) }, modifier = Modifier.fillMaxWidth().testTag(ExperimentTags.VALUES),
                )
            }
            SmallField(repeats, R.string.workbench_repeats, ExperimentTags.REPEATS, !running, KeyboardType.Number) { repeats = it.filter(Char::isDigit).take(3) }
            expanded?.exceptionOrNull()?.let { Text(it.message ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(ExperimentTags.PLAN_ERROR)) }
            if (running) {
                OutlinedButton(onClick = { job?.cancel() }, modifier = Modifier.heightIn(min = 48.dp).testTag(ExperimentTags.STOP)) { Text(stringResource(R.string.workbench_stop)) }
            } else {
                val runs = expanded?.getOrNull()
                Button(onClick = { runs?.let(::start) }, enabled = runner != null && runs != null, modifier = Modifier.heightIn(min = 48.dp).testTag(ExperimentTags.START)) {
                    Text(stringResource(R.string.workbench_start, runs?.size ?: 0))
                }
            }
        }
        TitledCard(stringResource(R.string.workbench_progress_title), accessory = {
            val stop = progress?.stop
            StatusChip(
                stringResource(if (stop != null && stop != ExperimentStop.STOPPED) R.string.workbench_caps_held else R.string.workbench_caps_within),
                if (stop != null && stop != ExperimentStop.STOPPED) ChipKind.Held else ChipKind.Allowed,
            )
        }) {
            val p = progress
            val now = probe()
            StatRow(stringResource(R.string.workbench_progress_runs), p?.let { "${if (it.finished) it.done else it.done + 1} / ${it.total}" } ?: "-",
                modifier = Modifier.testTag(ExperimentTags.PROGRESS).semantics { liveRegion = LiveRegionMode.Polite })
            StatRow(stringResource(R.string.workbench_progress_battery), now?.batteryPct?.let { "$it%" } ?: stringResource(R.string.workbench_not_available), source = stringResource(R.string.workbench_src_app),
                tone = when {
                    (now?.batteryPct ?: 100) <= RunCaps.LOW_BATTERY_PCT -> Tone.Bad
                    (now?.batteryPct ?: 100) < 30 -> Tone.Warn
                    else -> Tone.Neutral
                })
            StatRow(stringResource(R.string.workbench_stat_thermal), now?.thermal?.name?.lowercase(Locale.ROOT) ?: stringResource(R.string.workbench_not_available), source = stringResource(R.string.workbench_src_app),
                tone = if (now?.thermal?.severeOrWorse == true) Tone.Bad else Tone.Neutral)
            Text(stringResource(R.string.workbench_caps), style = MaterialTheme.typography.bodySmall)
        }
    }

    val results: @Composable ColumnScope.() -> Unit = {
        progress?.stop?.takeIf { it != ExperimentStop.STOPPED }?.let { stop ->
            NoticeBanner(stringResource(stopLabel(stop)), modifier = Modifier.testTag(ExperimentTags.CAP_BANNER))
        }
        SegmentedControl(
            labels = listOf(stringResource(R.string.workbench_view_results), stringResource(R.string.workbench_log, log.size)),
            selected = view, onSelect = { view = it }, modifier = Modifier.testTag(ExperimentTags.VIEW),
        )
        if (view == 0) {
            val p = progress
            val planNow = lastPlan
            val live = if (p != null && planNow != null && p.results.isNotEmpty()) Triple(planNow, ExperimentSummary.of(planNow.values, p.results), p.stop) else null
            val shown = live ?: if (p == null) SummaryCodec.decode(savedSummary) else null
            if (shown == null) {
                Text(stringResource(R.string.workbench_no_results), style = MaterialTheme.typography.bodyMedium)
            } else {
                val (pl, rows, stop) = shown
                TitledCard(stringResource(R.string.workbench_view_results), kicker = stringResource(R.string.workbench_results_kicker, pl.repeats)) {
                    DataTable(
                        columns = listOf(
                            TableColumn(stringResource(variableLabel(pl.variable)), 1f),
                            TableColumn(stringResource(R.string.workbench_col_runs), 0.8f, numeric = true),
                            TableColumn(stringResource(R.string.workbench_col_ttft), 1.6f, numeric = true),
                            TableColumn(stringResource(R.string.workbench_col_decode), 1.4f, numeric = true),
                            TableColumn(stringResource(R.string.workbench_col_output), 1.4f, numeric = true),
                        ),
                        rows = rows.map { r ->
                            listOf(
                                TableCell(r.label),
                                TableCell(if (r.failed > 0) "${r.runs - r.failed} of ${r.runs}" else "${r.runs}"),
                                TableCell(spread(r.firstTokenMs, 0)),
                                TableCell(spread(r.decodeTps, 1)),
                                TableCell(spread(r.outputTokens, 0)),
                            )
                        },
                        modifier = Modifier.testTag(ExperimentTags.RESULTS),
                    )
                    if (p?.finished == true) {
                        val text = ExperimentSummary.text(pl, rows, stop)
                        OutlinedButton(onClick = { copied = copyToClipboard(context, "RiskMandate Workbench experiment", text) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag(ExperimentTags.COPY)) { Text(stringResource(R.string.workbench_results_copy)) }
                    }
                    copied?.let { Text(stringResource(if (it) R.string.workbench_copied else R.string.workbench_copy_failed)) }
                }
            }
        } else {
            TitledCard(stringResource(R.string.workbench_log, log.size), accessory = {
                if (log.isNotEmpty()) TextButton(onClick = { confirmClear = true }, enabled = !running, modifier = Modifier.heightIn(min = 48.dp).testTag(ExperimentTags.CLEAR)) {
                    Text(stringResource(R.string.workbench_clear))
                }
            }) {
                Text(stringResource(R.string.workbench_log_note), style = MaterialTheme.typography.bodySmall)
                if (log.isNotEmpty()) {
                    DataTable(
                        columns = listOf(
                            TableColumn(stringResource(R.string.workbench_col_time), 0.9f),
                            TableColumn(stringResource(R.string.workbench_col_run), 2.2f),
                            TableColumn(stringResource(R.string.workbench_col_ttft_short), 0.9f, numeric = true),
                            TableColumn(stringResource(R.string.workbench_col_decode_short), 0.9f, numeric = true),
                            TableColumn(stringResource(R.string.workbench_col_output_short), 0.8f, numeric = true),
                            TableColumn(stringResource(R.string.workbench_stat_stop), 1.5f),
                        ),
                        rows = log.take(SHOWN).map { e ->
                            listOf(
                                TableCell(SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(e.startedAtMs))),
                                TableCell(settingsLine(e)),
                                TableCell(e.firstTokenMs?.toString() ?: "-"),
                                TableCell(e.decodeTps?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "-"),
                                TableCell(e.outputTokens?.toString() ?: "-"),
                                TableCell(e.stop.lowercase(Locale.ROOT).replace('_', ' ')) {
                                    StatusChip(e.stop.lowercase(Locale.ROOT).replace('_', ' '), logKind(e.stop))
                                },
                            )
                        },
                        modifier = Modifier.testTag(ExperimentTags.LOG),
                    )
                }
            }
        }
    }

    if (LocalWideLayout.current) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(304.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = builder)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = results)
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            builder()
            results()
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

/** One log row as plain text (kept for "Copy" and tests): when, which run, the settings, how it ended, the key figures. */
fun logLine(e: LogEntry): String {
    val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(e.startedAtMs))
    val dec = e.decodeTps?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "-"
    return "$time ${settingsLine(e)} | ${e.stop.lowercase(Locale.ROOT)}" +
        " | ${if (e.cold) "cold" else "warm"} ttft=${e.firstTokenMs ?: "-"}ms dec=$dec tok/s out=${e.outputTokens ?: "-"}" +
        if (e.toolCalls > 0) " calls=${e.toolCalls} ok=${e.accepted}" else ""
}

/** The run's label, model, backend and the settings, on one line. */
fun settingsLine(e: LogEntry): String =
    "${e.label} | ${if (e.cloud) "Cloud \u00b7 ${e.model} via openrouter.ai" else "${e.model} ${e.backend}"}${if (e.fellBack) "(fell back)" else ""} t=${e.temperature} p=${e.topP} max=${e.maxTokens}" +
        "${if (e.systemChars > 0) " sys" else ""}${if (e.tools > 0) " tools=${e.tools}" else ""}"

private fun spread(s: Spread?, d: Int): String = if (s == null) "-" else {
    val f = "%.${d}f"
    "${String.format(Locale.ROOT, f, s.mean)} ± ${String.format(Locale.ROOT, f, s.sd)}"
}

private fun logKind(stop: String) = when (stop) {
    "DONE" -> ChipKind.Allowed
    "ERROR" -> ChipKind.Blocked
    else -> ChipKind.Held
}

private const val SHOWN = 100

private fun variableLabel(v: SweepVariable) = when (v) {
    SweepVariable.TEMPERATURE -> R.string.workbench_temperature
    SweepVariable.TOP_P -> R.string.workbench_top_p
    SweepVariable.MAX_TOKENS -> R.string.workbench_max_tokens
    SweepVariable.BACKEND -> R.string.workbench_backend
    SweepVariable.SYSTEM_PROMPT -> R.string.workbench_system
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
