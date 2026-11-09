package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.R
import ai.riskmandate.app.copyToClipboard
import ai.riskmandate.app.llm.BackendPreference
import ai.riskmandate.app.llm.LlmRunner
import ai.riskmandate.app.llm.RunRecord
import ai.riskmandate.app.llm.RunReport
import ai.riskmandate.app.llm.RunSettings
import ai.riskmandate.app.llm.RunUpdate
import ai.riskmandate.app.llm.Sampling
import ai.riskmandate.app.llm.ToolSpec
import ai.riskmandate.app.ui.ChipKind
import ai.riskmandate.app.ui.CountPill
import ai.riskmandate.app.ui.Kicker
import ai.riskmandate.app.ui.LocalWideLayout
import ai.riskmandate.app.Pt
import ai.riskmandate.app.ui.StatRow
import ai.riskmandate.app.ui.StatusChip
import ai.riskmandate.app.ui.StepRow
import ai.riskmandate.app.ui.TitledCard
import ai.riskmandate.app.ui.Tone
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object RunTags {
    const val SYSTEM = "workbench_system"
    const val PROMPT = "workbench_prompt"
    const val TEMPERATURE = "workbench_temperature"
    const val TOP_K = "workbench_top_k"
    const val TOP_P = "workbench_top_p"
    const val SEED = "workbench_seed"
    const val MAX_TOKENS = "workbench_max_tokens"
    const val RUN = "workbench_run"
    const val STOP = "workbench_stop"
    const val RERUN = "workbench_rerun"
    const val COPY = "workbench_copy"
    const val LIVE = "workbench_live"
    const val ANSWER = "workbench_answer"
    const val INVALID = "workbench_invalid"
    const val NO_SERVICE = "workbench_no_service"
    const val MODEL = "workbench_model"
    const val OPEN_MODELS = "workbench_open_models"
    const val COLD = "workbench_cold"
    const val DONE = "workbench_done"
    const val RESET = "workbench_reset"
    const val STATS = "workbench_stats"
    const val STAT_LOAD = "workbench_stat_load"
    const val STAT_TTFT = "workbench_stat_ttft"
    const val STAT_DECODE = "workbench_stat_decode"
    const val STAT_OUTPUT = "workbench_stat_output"
    const val STAT_BACKEND = "workbench_stat_backend"
    const val STAT_STOP = "workbench_stat_stop"
    fun call(i: Int) = "workbench_call_$i"
    fun backend(name: String) = "workbench_backend_$name"
    fun systemPreset(i: Int) = "workbench_system_preset_$i"
    fun tools(i: Int) = "workbench_tools_$i"
}

/** The settings as typed: strings, so half-typed numbers do not fight the keyboard. Saved across rotation. */
data class RunForm(
    val backend: String = "AUTO",
    val coldStart: Boolean = false,
    val system: String = "",
    val prompt: String = "",
    val temperature: String = "1.0",
    val topK: String = "64",
    val topP: String = "0.95",
    val seed: String = "",
    val maxTokens: String = "256",
    val toolSet: Int = -1,
) {
    /** The settings, or null when a number is out of range or the prompt is empty (the screen says what to fix). */
    fun settings(toolSets: List<ToolSet>): RunSettings? {
        val t = temperature.toDoubleOrNull()?.takeIf { it in 0.0..2.0 } ?: return null
        val k = topK.toIntOrNull()?.takeIf { it in 1..1000 } ?: return null
        val p = topP.toDoubleOrNull()?.takeIf { it > 0.0 && it <= 1.0 } ?: return null
        val s = if (seed.isBlank()) null else seed.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val m = maxTokens.toIntOrNull()?.takeIf { it in 1..ai.riskmandate.app.llm.LlmRequest.MAX_OUTPUT_TOKENS } ?: return null
        if (prompt.isBlank() || prompt.length > RunSettings.MAX_TYPED_CHARS || system.length > RunSettings.MAX_TYPED_CHARS) return null
        return RunSettings(
            backend = BackendPreference.entries.firstOrNull { it.name == backend } ?: BackendPreference.AUTO,
            systemPrompt = system,
            userPrompt = prompt,
            sampling = Sampling(temperature = t, topK = k, topP = p, seed = s),
            maxTokens = m,
            tools = toolSets.getOrNull(toolSet)?.tools ?: emptyList(),
            coldStart = coldStart,
        )
    }

    companion object {
        /** Saved as a list of strings in a rememberSaveable. */
        fun save(f: RunForm): List<String> = listOf(f.backend, f.coldStart.toString(), f.system, f.prompt, f.temperature, f.topK, f.topP, f.seed, f.maxTokens, f.toolSet.toString())

        /** Checked on the way back: wrong shape gives the defaults, text is cut to the caps (security L). */
        fun restore(l: List<String>): RunForm = if (l.size != 10) RunForm() else RunForm(
            backend = l[0].takeIf { b -> BackendPreference.entries.any { it.name == b } } ?: "AUTO",
            coldStart = l[1] == "true",
            system = l[2].take(RunSettings.MAX_TYPED_CHARS), prompt = l[3].take(RunSettings.MAX_TYPED_CHARS),
            temperature = l[4].take(8), topK = l[5].take(8), topP = l[6].take(8), seed = l[7].take(10), maxTokens = l[8].take(8),
            toolSet = l[9].toIntOrNull()?.coerceIn(-1, 50) ?: -1,
        )
    }
}

/** A named set of tools offered to the model (fake tools only; the catalogue comes from dev.lab's tool tests). */
data class ToolSet(val name: String, val tools: List<ToolSpec>)

/** A system-prompt preset: a label resource and the text (empty for "none"). */
data class SystemPreset(val label: Int, val text: String)

/**
 * Tab 1 of the Workbench: one experiment at a time (design spec docs/design/workbench.md). On a wide screen three
 * panes side by side, each scrolling on its own (settings 256 dp, answer, stats 304 dp); below 840 dp one column
 * in the order actions, settings, answer, stats. The streamed answer is plain text; every figure is a [StatRow]
 * that says whether the runtime reported it or the app measured it.
 * [runner] is null when the app has no model service (then the tab says to set up a model).
 * [onRecord] receives every finished run (the run log, tab 2).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunTab(
    runner: LlmRunner?,
    /** The model in use, read from the service (Ready, Idle or Loading), or null. Chosen in Model setup. */
    activeModel: String?,
    onOpenModels: () -> Unit,
    presets: List<SystemPreset>,
    toolSets: List<ToolSet>,
    /** The form and the last run are held by the Workbench, so they survive switching tabs. */
    form: RunForm,
    onForm: (RunForm) -> Unit,
    last: RunRecord?,
    onLast: (RunRecord?) -> Unit,
    onRunning: (Boolean) -> Unit = {},
    onRecord: (RunRecord) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Edits always apply to the latest form: a callback held by a child may outlive the composition it came from.
    val currentForm by rememberUpdatedState(form)
    val currentOnForm by rememberUpdatedState(onForm)
    val edit: ((RunForm) -> RunForm) -> Unit = remember { { f -> currentOnForm(f(currentForm)) } }

    var job by remember { mutableStateOf<Job?>(null) }
    var live by remember { mutableStateOf<RunUpdate.Progress?>(null) }
    var copied by remember { mutableStateOf<Boolean?>(null) }
    val running = job != null
    val settings = form.settings(toolSets)

    fun start(s: RunSettings) {
        val r = runner ?: return
        copied = null
        onLast(null)
        live = null
        onRunning(true)
        val j = scope.launch {
            r.run(s) { u ->
                when (u) {
                    is RunUpdate.Progress -> live = u
                    is RunUpdate.Finished -> { onLast(u.record); onRecord(u.record) }
                }
            }
        }
        // Assign first, then listen: a run that already finished calls the handler at once, after the assignment.
        job = j
        j.invokeOnCompletion { if (job === j) job = null; onRunning(false) }
    }

    val actions: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) {
                OutlinedButton(onClick = { job?.cancel() }, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.STOP)) { Text(stringResource(R.string.workbench_stop)) }
            } else {
                last?.let { rec ->
                    OutlinedButton(
                        onClick = { copied = copyToClipboard(context, "RiskMandate Workbench run", RunReport.text(rec)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.COPY),
                    ) { Text(stringResource(R.string.workbench_copy)) }
                    OutlinedButton(onClick = { start(rec.settings) }, enabled = runner != null, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.RERUN)) {
                        Text(stringResource(R.string.workbench_rerun))
                    }
                }
                Button(onClick = { settings?.let(::start) }, enabled = runner != null && settings != null, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.RUN)) {
                    Text(stringResource(R.string.workbench_run))
                }
            }
        }
        copied?.let { Text(stringResource(if (it) R.string.workbench_copied else R.string.workbench_copy_failed), style = MaterialTheme.typography.bodyMedium) }
    }
    val settingsPane: @Composable ColumnScope.() -> Unit = {
        RunSettingsCard(runner != null, activeModel, onOpenModels, presets, toolSets, form, edit, running, settings != null)
    }
    val answerPane: @Composable ColumnScope.() -> Unit = { RunAnswerCards(live, last) }
    val statsPane: @Composable ColumnScope.() -> Unit = { RunStatsCard(live, last, running) }

    if (LocalWideLayout.current) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            actions()
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(256.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = settingsPane)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = answerPane)
                Column(Modifier.width(304.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = statsPane)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            actions()
            settingsPane()
            answerPane()
            statsPane()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunSettingsCard(
    hasService: Boolean,
    activeModel: String?,
    onOpenModels: () -> Unit,
    presets: List<SystemPreset>,
    toolSets: List<ToolSet>,
    form: RunForm,
    edit: ((RunForm) -> RunForm) -> Unit,
    running: Boolean,
    valid: Boolean,
) {
    TitledCard(stringResource(R.string.workbench_settings), kicker = stringResource(R.string.workbench_experiment)) {
        if (!hasService) {
            Text(stringResource(R.string.workbench_no_service), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(RunTags.NO_SERVICE))
        }
        Kicker(stringResource(R.string.workbench_model))
        Text(activeModel ?: stringResource(R.string.workbench_model_none), modifier = Modifier.testTag(RunTags.MODEL))
        TextButton(onClick = onOpenModels, enabled = !running, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.OPEN_MODELS)) {
            Text(stringResource(R.string.workbench_model_change))
        }
        Kicker(stringResource(R.string.workbench_backend))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("AUTO" to R.string.workbench_backend_auto, "GPU" to R.string.workbench_backend_gpu, "CPU" to R.string.workbench_backend_cpu).forEach { (b, label) ->
                FilterChip(selected = form.backend == b, enabled = !running, onClick = { edit { it.copy(backend = b) } },
                    label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.backend(b)))
            }
            FilterChip(selected = form.coldStart, enabled = !running, onClick = { edit { it.copy(coldStart = !form.coldStart) } },
                label = { Text(stringResource(R.string.workbench_cold_start)) }, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.COLD))
        }
        Kicker(stringResource(R.string.workbench_system))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEachIndexed { i, p ->
                FilterChip(selected = form.system == p.text, enabled = !running, onClick = { edit { it.copy(system = p.text) } },
                    label = { Text(stringResource(p.label)) }, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.systemPreset(i)))
            }
        }
        OutlinedTextField(
            value = form.system, onValueChange = { v -> edit { it.copy(system = v.take(RunSettings.MAX_TYPED_CHARS)) } }, enabled = !running,
            label = { Text(stringResource(R.string.workbench_system_label)) }, minLines = 2, maxLines = 6, textStyle = Pt.type.data,
            modifier = Modifier.fillMaxWidth().testTag(RunTags.SYSTEM),
        )
        Kicker(stringResource(R.string.workbench_sampling))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(form.temperature, R.string.workbench_temperature, RunTags.TEMPERATURE, !running, decimal = true) { v -> edit { it.copy(temperature = v) } }
            NumberField(form.topK, R.string.workbench_top_k, RunTags.TOP_K, !running) { v -> edit { it.copy(topK = v) } }
            NumberField(form.topP, R.string.workbench_top_p, RunTags.TOP_P, !running, decimal = true) { v -> edit { it.copy(topP = v) } }
            NumberField(form.seed, R.string.workbench_seed, RunTags.SEED, !running) { v -> edit { it.copy(seed = v) } }
            NumberField(form.maxTokens, R.string.workbench_max_tokens, RunTags.MAX_TOKENS, !running) { v -> edit { it.copy(maxTokens = v) } }
        }
        Kicker(stringResource(R.string.workbench_tools))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = form.toolSet < 0, enabled = !running, onClick = { edit { it.copy(toolSet = -1) } },
                label = { Text(stringResource(R.string.workbench_tools_none)) }, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.tools(-1)))
            toolSets.forEachIndexed { i, t ->
                FilterChip(selected = form.toolSet == i, enabled = !running, onClick = { edit { it.copy(toolSet = i) } },
                    label = { Text(t.name) }, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.tools(i)))
            }
        }
        OutlinedTextField(
            value = form.prompt, onValueChange = { v -> edit { it.copy(prompt = v.take(RunSettings.MAX_TYPED_CHARS)) } }, enabled = !running,
            label = { Text(stringResource(R.string.workbench_prompt)) }, minLines = 4, maxLines = 8, textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().testTag(RunTags.PROMPT),
        )
        if (!valid) Text(stringResource(R.string.workbench_invalid), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(RunTags.INVALID))
        TextButton(onClick = { edit { RunForm(prompt = it.prompt) } }, enabled = !running, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.RESET)) {
            Text(stringResource(R.string.workbench_reset))
        }
    }
}

@Composable
private fun RunAnswerCards(live: RunUpdate.Progress?, last: RunRecord?) {
    val answer = last?.text ?: live?.text ?: ""
    TitledCard(
        stringResource(R.string.workbench_answer), kicker = stringResource(R.string.workbench_answer_kicker),
        accessory = { last?.let { StatusChip(stopWord(it), stopKind(it)) } },
    ) {
        if (live != null && last == null) {
            // Not a live region: it changes with every token. The completion line below is announced once.
            Text(
                stringResource(R.string.workbench_live, seconds(live.elapsedMs), live.firstTokenMs?.let { seconds(it) } ?: "...", live.pieces),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(RunTags.LIVE),
            )
        }
        last?.let { rec ->
            Text(
                stringResource(
                    R.string.workbench_done, stopWord(rec), rec.stats?.outputTokens ?: 0,
                    rec.stats?.decodeTokensPerSecond?.let { String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "-",
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(RunTags.DONE).semantics { liveRegion = LiveRegionMode.Polite },
            )
            rec.error?.let { Text(stringResource(R.string.workbench_error_detail, it.reason.name, it.message.take(200)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
        // Model output is plain text only: never HTML, links or code.
        if (answer.isNotEmpty()) SelectionContainer { Text(answer, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(RunTags.ANSWER)) }
    }
    val calls = last?.toolCalls.orEmpty()
    if (calls.isNotEmpty()) {
        TitledCard(stringResource(R.string.workbench_tool_calls), accessory = { CountPill(calls.size, stringResource(R.string.workbench_tool_calls)) }) {
            calls.forEachIndexed { i, c ->
                val (verdict, kind) = when (val v = c.verdict) {
                    is ai.riskmandate.app.llm.ToolCheck.Accepted -> stringResource(R.string.workbench_call_ok) to ChipKind.Allowed
                    is ai.riskmandate.app.llm.ToolCheck.Rejected ->
                        stringResource(if (v.reason == "not an offered tool") R.string.workbench_call_not_offered else R.string.workbench_call_invalid) to ChipKind.Blocked
                }
                val reason = (c.verdict as? ai.riskmandate.app.llm.ToolCheck.Rejected)?.reason
                StepRow(i + 1, c.name.take(40), verdict, kind, detail = c.argsJson.take(300) + (reason?.let { "\n$it" } ?: ""), dense = true,
                    modifier = Modifier.testTag(RunTags.call(i)))
            }
        }
    }
}

@Composable
private fun RunStatsCard(live: RunUpdate.Progress?, last: RunRecord?, running: Boolean) {
    val st = last?.stats
    val runtime = stringResource(R.string.workbench_src_runtime)
    val app = stringResource(R.string.workbench_src_app)
    val na = stringResource(R.string.workbench_not_available)
    val pending = "..."
    fun src(s: ai.riskmandate.app.llm.StatSource?) = when (s) {
        ai.riskmandate.app.llm.StatSource.RUNTIME -> runtime
        else -> app
    }
    fun v(x: Any?) = x?.toString() ?: if (running) pending else na
    fun tps(x: Double?) = x?.let { String.format(java.util.Locale.ROOT, "%.1f", it) } ?: if (running) pending else na
    TitledCard(stringResource(R.string.workbench_stats), kicker = stringResource(R.string.workbench_stats_kicker), modifier = Modifier.testTag(RunTags.STATS)) {
        Kicker(stringResource(R.string.workbench_group_timing), color = Pt.colors.muted)
        StatRow(
            stringResource(if (st?.coldLoad == true) R.string.workbench_stat_load_cold else R.string.workbench_stat_load_warm),
            if (st == null) v(null) else if (st.coldLoad) "${st.loadMs ?: "-"} ms" else stringResource(R.string.workbench_warm),
            source = app, modifier = Modifier.testTag(RunTags.STAT_LOAD),
        )
        StatRow(stringResource(R.string.workbench_stat_ttft), (st?.timeToFirstTokenMs ?: live?.firstTokenMs)?.let { "$it ms" } ?: v(null), source = app,
            modifier = Modifier.testTag(RunTags.STAT_TTFT))
        StatRow(stringResource(R.string.workbench_stat_prefill), tps(st?.prefillTokensPerSecond), source = if (st?.prefillTokensPerSecond != null) runtime else null)
        StatRow(stringResource(R.string.workbench_stat_decode), tps(st?.decodeTokensPerSecond), source = st?.let { src(it.speedsFrom) },
            modifier = Modifier.testTag(RunTags.STAT_DECODE))
        StatRow(stringResource(R.string.workbench_stat_total), (st?.totalMs ?: live?.elapsedMs)?.let { "$it ms" } ?: v(null), source = app)
        Kicker(stringResource(R.string.workbench_group_tokens), color = Pt.colors.muted)
        StatRow(stringResource(R.string.workbench_stat_input), v(st?.promptTokens), source = if (st?.promptTokens != null) src(st.countsFrom) else null)
        StatRow(stringResource(R.string.workbench_stat_output), (st?.outputTokens ?: live?.pieces)?.toString() ?: v(null), source = st?.let { src(it.countsFrom) } ?: if (live != null) app else null,
            modifier = Modifier.testTag(RunTags.STAT_OUTPUT))
        Kicker(stringResource(R.string.workbench_group_engine), color = Pt.colors.muted)
        StatRow(
            stringResource(R.string.workbench_stat_backend),
            if (st == null) v(null) else if (st.fellBack) stringResource(R.string.workbench_fell_back, st.backendUsed) else st.backendUsed,
            source = runtime, tone = if (st?.fellBack == true) Tone.Warn else Tone.Neutral, modifier = Modifier.testTag(RunTags.STAT_BACKEND),
        )
        StatRow(stringResource(R.string.workbench_stat_stop), last?.let { stopWord(it) } ?: v(null), modifier = Modifier.testTag(RunTags.STAT_STOP))
        Kicker(stringResource(R.string.workbench_group_device), color = Pt.colors.muted)
        StatRow(stringResource(R.string.workbench_stat_memory), beforeAfter(last?.before?.memory?.rssMb, last?.after?.memory?.rssMb, " MB", na), source = app)
        StatRow(stringResource(R.string.workbench_stat_battery), beforeAfter(last?.before?.batteryPct, last?.after?.batteryPct, "%", na), source = app)
        StatRow(
            stringResource(R.string.workbench_stat_thermal),
            beforeAfter(last?.before?.thermal?.name?.lowercase(), last?.after?.thermal?.name?.lowercase(), "", na),
            source = app,
            tone = when {
                last?.after?.thermal?.severeOrWorse == true -> Tone.Bad
                last?.after?.thermal == ai.riskmandate.app.llm.Thermal.MODERATE -> Tone.Warn
                else -> Tone.Neutral
            },
        )
        Text(stringResource(R.string.workbench_legend), style = MaterialTheme.typography.bodySmall, color = Pt.colors.muted)
    }
}

private fun beforeAfter(a: Any?, b: Any?, unit: String, na: String): String =
    if (a == null && b == null) na else "${a ?: "-"}$unit \u2192 ${b ?: "-"}$unit"

private fun stopKind(r: RunRecord) = when (r.stopReason) {
    ai.riskmandate.app.llm.LlmStopReason.DONE -> ChipKind.Allowed
    ai.riskmandate.app.llm.LlmStopReason.MAX_TOKENS, ai.riskmandate.app.llm.LlmStopReason.CANCELLED -> ChipKind.Held
    ai.riskmandate.app.llm.LlmStopReason.ERROR -> ChipKind.Blocked
}

@Composable
private fun NumberField(value: String, label: Int, tag: String, enabled: Boolean, decimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && it == '.') || it == '-' }.take(8)) },
        enabled = enabled,
        singleLine = true,
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.widthIn(min = 120.dp, max = 160.dp).testTag(tag),
    )
}

@Composable
private fun stopWord(r: RunRecord): String = stringResource(
    when (r.stopReason) {
        ai.riskmandate.app.llm.LlmStopReason.DONE -> R.string.workbench_stop_done
        ai.riskmandate.app.llm.LlmStopReason.MAX_TOKENS -> R.string.workbench_stop_max
        ai.riskmandate.app.llm.LlmStopReason.CANCELLED -> R.string.workbench_stop_cancelled
        ai.riskmandate.app.llm.LlmStopReason.ERROR -> R.string.workbench_stop_error
    },
)

private fun seconds(ms: Long) = String.format(java.util.Locale.ROOT, "%.1f s", ms / 1000.0)
