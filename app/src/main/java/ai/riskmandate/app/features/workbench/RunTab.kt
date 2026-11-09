package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.R
import ai.riskmandate.app.copyToClipboard
import ai.riskmandate.app.llm.BenchBackend
import ai.riskmandate.app.llm.LlmRunner
import ai.riskmandate.app.llm.ModelSpec
import ai.riskmandate.app.llm.RunRecord
import ai.riskmandate.app.llm.RunReport
import ai.riskmandate.app.llm.RunSettings
import ai.riskmandate.app.llm.RunUpdate
import ai.riskmandate.app.llm.Sampling
import ai.riskmandate.app.llm.ToolSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    const val REPORT = "workbench_report"
    const val INVALID = "workbench_invalid"
    const val NO_SERVICE = "workbench_no_service"
    fun model(id: String) = "workbench_model_$id"
    fun backend(name: String) = "workbench_backend_$name"
    fun systemPreset(i: Int) = "workbench_system_preset_$i"
    fun tools(i: Int) = "workbench_tools_$i"
}

/** The settings as typed: strings, so half-typed numbers do not fight the keyboard. Saved across rotation. */
data class RunForm(
    val modelId: String? = null,
    val backend: String = "AUTO",
    val system: String = "",
    val prompt: String = "",
    val temperature: String = "1.0",
    val topK: String = "64",
    val topP: String = "0.95",
    val seed: String = "",
    val maxTokens: String = "256",
    val toolSet: Int = -1,
) {
    /** The settings, or null when a number is out of range (the screen says which fields to fix). */
    fun settings(toolSets: List<ToolSet>): RunSettings? {
        val t = temperature.toDoubleOrNull()?.takeIf { it in 0.0..2.0 } ?: return null
        val k = topK.toIntOrNull()?.takeIf { it in 1..1000 } ?: return null
        val p = topP.toDoubleOrNull()?.takeIf { it > 0.0 && it <= 1.0 } ?: return null
        val s = if (seed.isBlank()) null else seed.toIntOrNull() ?: return null
        val m = maxTokens.toIntOrNull()?.takeIf { it in 1..ai.riskmandate.app.llm.LlmRequest.MAX_OUTPUT_TOKENS } ?: return null
        if (prompt.isBlank()) return null
        return RunSettings(
            modelId = modelId,
            backend = BenchBackend.entries.firstOrNull { it.name == backend },
            systemPrompt = system,
            userPrompt = prompt,
            sampling = Sampling(temperature = t, topK = k, topP = p, seed = s),
            maxTokens = m,
            tools = toolSets.getOrNull(toolSet)?.tools ?: emptyList(),
        )
    }

    companion object {
        /** Saved as a list of strings in a rememberSaveable. */
        fun save(f: RunForm): List<String> = listOf(f.modelId ?: "", f.backend, f.system, f.prompt, f.temperature, f.topK, f.topP, f.seed, f.maxTokens, f.toolSet.toString())
        fun restore(l: List<String>): RunForm = if (l.size != 10) RunForm() else
            RunForm(l[0].ifEmpty { null }, l[1], l[2], l[3], l[4], l[5], l[6], l[7], l[8], l[9].toIntOrNull() ?: -1)
    }
}

/** A named set of tools offered to the model (fake tools only; the catalogue comes from dev.lab's tool tests). */
data class ToolSet(val name: String, val tools: List<ToolSpec>)

/** A system-prompt preset: a label resource and the text (empty for "none"). */
data class SystemPreset(val label: Int, val text: String)

/**
 * Tab 1 of the Workbench: one experiment at a time. Every setting editable, the streamed answer as plain text,
 * live figures while it runs, the full labelled stats when it ends, Stop, Re-run and Copy run.
 * [runner] is null when the app has no model service (then the tab says to set up a model).
 * [onRecord] receives every finished run (the run log, tab 2).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunTab(
    runner: LlmRunner?,
    models: List<ModelSpec>,
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
    fun edit(f: (RunForm) -> RunForm) = onForm(f(form))

    var job by remember { mutableStateOf<Job?>(null) }
    var live by remember { mutableStateOf<RunUpdate.Progress?>(null) }
    var copied by remember { mutableStateOf<Boolean?>(null) }
    val running = job != null

    fun start(settings: RunSettings) {
        val r = runner ?: return
        copied = null
        onLast(null)
        live = null
        onRunning(true)
        val j = scope.launch {
            r.run(settings) { u ->
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (runner == null) {
            Text(stringResource(R.string.workbench_no_service), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(RunTags.NO_SERVICE))
        }

        Text(stringResource(R.string.workbench_model), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = form.modelId == null, enabled = !running, onClick = { edit { it.copy(modelId = null) } },
                label = { Text(stringResource(R.string.workbench_model_current)) }, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.model("current")))
            models.forEach { m ->
                FilterChip(selected = form.modelId == m.id, enabled = !running, onClick = { edit { it.copy(modelId = m.id) } },
                    label = { Text(m.displayName) }, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.model(m.id)))
            }
        }
        Text(stringResource(R.string.workbench_backend), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("AUTO" to R.string.workbench_backend_auto, "GPU" to R.string.workbench_backend_gpu, "CPU" to R.string.workbench_backend_cpu).forEach { (b, label) ->
                FilterChip(selected = form.backend == b, enabled = !running, onClick = { edit { it.copy(backend = b) } },
                    label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.backend(b)))
            }
        }

        Text(stringResource(R.string.workbench_system), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEachIndexed { i, p ->
                FilterChip(selected = form.system == p.text, enabled = !running, onClick = { edit { it.copy(system = p.text) } },
                    label = { Text(stringResource(p.label)) }, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.systemPreset(i)))
            }
        }
        OutlinedTextField(
            value = form.system, onValueChange = { v -> edit { it.copy(system = v.take(MAX_PROMPT)) } }, enabled = !running,
            label = { Text(stringResource(R.string.workbench_system_label)) }, minLines = 2, maxLines = 6,
            modifier = Modifier.fillMaxWidth().testTag(RunTags.SYSTEM),
        )
        OutlinedTextField(
            value = form.prompt, onValueChange = { v -> edit { it.copy(prompt = v.take(MAX_PROMPT)) } }, enabled = !running,
            label = { Text(stringResource(R.string.workbench_prompt)) }, minLines = 2, maxLines = 8,
            modifier = Modifier.fillMaxWidth().testTag(RunTags.PROMPT),
        )

        Text(stringResource(R.string.workbench_sampling), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(form.temperature, R.string.workbench_temperature, RunTags.TEMPERATURE, !running, decimal = true) { v -> edit { it.copy(temperature = v) } }
            NumberField(form.topK, R.string.workbench_top_k, RunTags.TOP_K, !running) { v -> edit { it.copy(topK = v) } }
            NumberField(form.topP, R.string.workbench_top_p, RunTags.TOP_P, !running, decimal = true) { v -> edit { it.copy(topP = v) } }
            NumberField(form.seed, R.string.workbench_seed, RunTags.SEED, !running) { v -> edit { it.copy(seed = v) } }
            NumberField(form.maxTokens, R.string.workbench_max_tokens, RunTags.MAX_TOKENS, !running) { v -> edit { it.copy(maxTokens = v) } }
        }

        Text(stringResource(R.string.workbench_tools), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = form.toolSet < 0, enabled = !running, onClick = { edit { it.copy(toolSet = -1) } },
                label = { Text(stringResource(R.string.workbench_tools_none)) }, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.tools(-1)))
            toolSets.forEachIndexed { i, t ->
                FilterChip(selected = form.toolSet == i, enabled = !running, onClick = { edit { it.copy(toolSet = i) } },
                    label = { Text(t.name) }, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.tools(i)))
            }
        }

        val settings = form.settings(toolSets)
        if (settings == null) {
            Text(stringResource(R.string.workbench_invalid), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(RunTags.INVALID))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) {
                Button(onClick = { job?.cancel() }, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.STOP)) { Text(stringResource(R.string.workbench_stop)) }
            } else {
                Button(onClick = { settings?.let(::start) }, enabled = runner != null && settings != null, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.RUN)) {
                    Text(stringResource(R.string.workbench_run))
                }
                last?.let { rec ->
                    OutlinedButton(onClick = { start(rec.settings) }, enabled = runner != null, modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.RERUN)) {
                        Text(stringResource(R.string.workbench_rerun))
                    }
                    OutlinedButton(
                        onClick = { copied = copyToClipboard(context, "RiskMandate Workbench run", RunReport.text(rec)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag(RunTags.COPY),
                    ) { Text(stringResource(R.string.workbench_copy)) }
                }
            }
        }
        copied?.let { Text(stringResource(if (it) R.string.workbench_copied else R.string.workbench_copy_failed)) }

        live?.let { p ->
            Text(
                stringResource(
                    R.string.workbench_live, seconds(p.elapsedMs), p.firstTokenMs?.let { seconds(it) } ?: "-", p.pieces,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(RunTags.LIVE).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val answer = last?.text ?: live?.text ?: ""
        if (answer.isNotEmpty()) {
            Text(stringResource(R.string.workbench_answer), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            // Model output is plain text only: never HTML, links or code.
            SelectionContainer { Text(answer, modifier = Modifier.testTag(RunTags.ANSWER)) }
        }
        last?.let { rec ->
            Text(stringResource(R.string.workbench_stats), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            SelectionContainer {
                Text(RunReport.text(rec), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(RunTags.REPORT))
            }
        }
    }
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

private fun seconds(ms: Long) = String.format(java.util.Locale.ROOT, "%.1f s", ms / 1000.0)

/** Longest prompt or system prompt accepted in the form (the request's own TOO_LONG check comes after). */
const val MAX_PROMPT = 8_000
