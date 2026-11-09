package ai.riskmandate.app.features.lab

import ai.riskmandate.app.experiments.RunSettings
import ai.riskmandate.app.experiments.StopReason
import ai.riskmandate.app.R
import ai.riskmandate.app.llm.Sampling
import ai.riskmandate.app.ui.ChipKind
import ai.riskmandate.app.ui.LocalWideLayout
import ai.riskmandate.app.ui.SectionCard
import ai.riskmandate.app.ui.StatTile
import ai.riskmandate.app.ui.StatusChip
import ai.riskmandate.app.ui.UiTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import java.util.Locale
import kotlin.math.roundToInt

/** The sampling the visitor has chosen. Seed text empty means random. */
data class SamplingChoice(val temperature: Double = Sampling.DEFAULT.temperature, val topP: Double = Sampling.DEFAULT.topP, val seedText: String = "") {
    val seed: Int? get() = seedText.trim().toIntOrNull()
    fun toSettings() = RunSettings(Sampling(temperature, Sampling.DEFAULT.topK, topP, seed))

    companion object {
        const val TEMP_MIN = 0.0; const val TEMP_MAX = 1.5; const val TEMP_STEP = 0.1
        const val TOP_P_MIN = 0.1; const val TOP_P_MAX = 1.0; const val TOP_P_STEP = 0.05

        /** Snaps to the step so repeated plus and minus never drift (0.30000000000000004). */
        fun snap(v: Double, min: Double, max: Double, step: Double): Double = ((v.coerceIn(min, max) / step).roundToInt() * step).let { Math.round(it * 100) / 100.0 }.coerceIn(min, max)
    }
}

/** The settings a recording was made with, read from its `source.settings`. Null fields are unknown. */
data class RecordedSettings(val temperature: Double?, val topP: Double?, val seed: Int?) {
    fun toChoice(current: SamplingChoice) = SamplingChoice(
        temperature = temperature?.let { SamplingChoice.snap(it, SamplingChoice.TEMP_MIN, SamplingChoice.TEMP_MAX, SamplingChoice.TEMP_STEP) } ?: current.temperature,
        topP = topP?.let { SamplingChoice.snap(it, SamplingChoice.TOP_P_MIN, SamplingChoice.TOP_P_MAX, SamplingChoice.TOP_P_STEP) } ?: current.topP,
        seedText = seed?.toString() ?: "",
    )

    /** True when the current choice is the recorded one (unknown recorded fields do not count against it). */
    fun matches(c: SamplingChoice) = (temperature == null || kotlin.math.abs(c.temperature - temperature) < 0.001) &&
        (topP == null || kotlin.math.abs(c.topP - topP) < 0.001) && (seed == null || c.seed == seed)

    companion object {
        fun of(settings: Map<String, Any?>): RecordedSettings? {
            val t = (settings["temperature"] as? Number)?.toDouble()
            val p = (settings["top_p"] as? Number)?.toDouble()
            val s = (settings["seed"] as? Number)?.toInt()
            return if (t == null && p == null && s == null) null else RecordedSettings(t, p, s)
        }
    }
}

internal fun one(d: Double): String = String.format(Locale.getDefault(), "%.1f", d)
internal fun two(d: Double): String = String.format(Locale.getDefault(), "%.2f", d)

/** "X" of "1 in X", to one decimal, without a trailing ".0" when it is whole. */
internal fun oneInX(x: Double): String {
    val r = Math.round(x * 10) / 10.0
    return if (r == Math.floor(r)) r.toInt().toString() else one(r)
}

@Composable
internal fun SettingsPanel(
    choice: SamplingChoice, recorded: RecordedSettings?, onChange: (SamplingChoice) -> Unit,
    onExport: (() -> Unit)?, canExport: Boolean,
) {
    SectionCard(stringResource(R.string.lab_settings), Modifier.padding(top = UiTokens.Gap).testTag(LabTags.SETTINGS)) {
        StepSlider(R.string.lab_temperature, one(choice.temperature), choice.temperature, SamplingChoice.TEMP_MIN, SamplingChoice.TEMP_MAX, SamplingChoice.TEMP_STEP, LabTags.TEMPERATURE) {
            onChange(choice.copy(temperature = it))
        }
        StepSlider(R.string.lab_top_p, two(choice.topP), choice.topP, SamplingChoice.TOP_P_MIN, SamplingChoice.TOP_P_MAX, SamplingChoice.TOP_P_STEP, LabTags.TOP_P) {
            onChange(choice.copy(topP = it))
        }
        OutlinedTextField(
            value = choice.seedText, onValueChange = { onChange(choice.copy(seedText = it.filter { c -> c.isDigit() }.take(9))) },
            label = { Text(stringResource(R.string.lab_seed)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(LabTags.SEED),
        )
        if (recorded != null) {
            Text(
                stringResource(R.string.lab_recorded_values, recorded.temperature?.let(::one) ?: "-", recorded.topP?.let(::two) ?: "-", recorded.seed?.toString() ?: "-"),
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton({ onChange(recorded.toChoice(choice)) }, Modifier.heightIn(min = 56.dp).testTag(LabTags.RECORDED)) {
                Text(stringResource(R.string.lab_recorded_settings), fontSize = 18.sp)
            }
        } else {
            Text(stringResource(R.string.lab_no_recorded), style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton({ onExport?.invoke() }, Modifier.heightIn(min = 56.dp).testTag(LabTags.EXPORT), enabled = canExport && onExport != null) {
            Text(stringResource(R.string.lab_export), fontSize = 18.sp)
        }
        if (onExport == null) Text(stringResource(R.string.lab_export_unavailable), style = MaterialTheme.typography.bodyMedium)
        else if (!canExport) Text(stringResource(R.string.lab_export_nothing), style = MaterialTheme.typography.bodyMedium)
    }
}

/** A slider with minus and plus buttons beside it, so a precise value never depends on dragging. */
@Composable
private fun StepSlider(label: Int, valueText: String, value: Double, min: Double, max: Double, step: Double, tag: String, onValue: (Double) -> Unit) {
    val name = stringResource(label)
    val less = stringResource(R.string.lab_decrease, name)
    val more = stringResource(R.string.lab_increase, name)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, Modifier.widthIn(min = 120.dp), style = MaterialTheme.typography.titleMedium)
        OutlinedButton({ onValue(SamplingChoice.snap(value - step, min, max, step)) }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = less }.testTag(tag + "_minus")) { Text(stringResource(R.string.lab_minus), fontSize = 22.sp) }
        Slider(
            value = value.toFloat(), onValueChange = { onValue(SamplingChoice.snap(it.toDouble(), min, max, step)) },
            valueRange = min.toFloat()..max.toFloat(), modifier = Modifier.weight(1f).testTag(tag),
        )
        OutlinedButton({ onValue(SamplingChoice.snap(value + step, min, max, step)) }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = more }.testTag(tag + "_plus")) { Text(stringResource(R.string.lab_plus), fontSize = 22.sp) }
        Text(valueText, Modifier.widthIn(min = 56.dp), style = MaterialTheme.typography.titleMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

@Composable
internal fun LoopControls(
    runs: Int, onRuns: (Int) -> Unit, running: Boolean, canRun: Boolean, onRun: () -> Unit, onStop: () -> Unit, stopping: Boolean,
    progressRun: Int, counters: LoopCounters?,
) {
    Column(Modifier.padding(top = UiTokens.Gap), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val runsLabel = stringResource(R.string.lab_loop_runs, runs)
            val fewer = stringResource(R.string.lab_decrease, runsLabel)
            val more = stringResource(R.string.lab_increase, runsLabel)
            OutlinedButton({ onRuns((runs - 5).coerceAtLeast(LoopRunner.MIN_RUNS)) }, Modifier.heightIn(min = 56.dp).semantics { contentDescription = fewer }.testTag(LabTags.LOOP_MINUS), enabled = !running) { Text(stringResource(R.string.lab_minus), fontSize = 22.sp) }
            Text(runsLabel, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag(LabTags.LOOP_RUNS))
            OutlinedButton({ onRuns((runs + 5).coerceAtMost(LoopRunner.MAX_RUNS)) }, Modifier.heightIn(min = 56.dp).semantics { contentDescription = more }.testTag(LabTags.LOOP_PLUS), enabled = !running) { Text(stringResource(R.string.lab_plus), fontSize = 22.sp) }
            if (running) OutlinedButton(onStop, Modifier.heightIn(min = 56.dp).testTag(LabTags.LOOP_STOP)) { Text(stringResource(R.string.lab_stop), fontSize = 20.sp) }
            else Button(onRun, Modifier.heightIn(min = 56.dp).testTag(LabTags.LOOP_RUN), enabled = canRun) { Text(stringResource(R.string.lab_run), fontSize = 20.sp) }
        }
        if (running && counters != null) {
            Text(
                stringResource(if (stopping) R.string.lab_loop_stopping else R.string.lab_loop_run_of, progressRun.coerceAtMost(counters.planned), counters.planned),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(LabTags.LOOP_PROGRESS),
            )
            LinearProgressIndicator(progress = { counters.finished.toFloat() / counters.planned }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Big counters, updated when each run finishes, never mid-run. The headline is a polite live region. */
@Composable
internal fun CountersStrip(c: LoopCounters, stop: StopReason?) {
    Column(Modifier.padding(top = UiTokens.Gap).testTag(LabTags.COUNTERS), verticalArrangement = Arrangement.spacedBy(UiTokens.Gap)) {
        val wide = LocalWideLayout.current
        val tiles: @Composable (Modifier) -> Unit = { m ->
            StatTile(stringResource(R.string.lab_stat_of, c.rawFailures, c.finished), stringResource(R.string.lab_stat_failed), m.testTag(LabTags.STAT_FAILED), large = true)
            StatTile(stringResource(R.string.lab_stat_of, c.caught, c.attempts), stringResource(R.string.lab_stat_caught), m.testTag(LabTags.STAT_CAUGHT), large = true)
        }
        val headline = c.oneInX?.let { stringResource(R.string.lab_one_in, oneInX(it)) } ?: stringResource(R.string.lab_never_failed, c.finished)
        if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiTokens.Gap)) { tiles(Modifier.weight(1f)) }
        else Column(verticalArrangement = Arrangement.spacedBy(UiTokens.Gap)) { tiles(Modifier.fillMaxWidth()) }
        StatTile(
            headline, stringResource(R.string.lab_stat_headline),
            Modifier.fillMaxWidth().testTag(LabTags.STAT_HEADLINE).semantics { liveRegion = LiveRegionMode.Polite }, large = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(UiTokens.Gap), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            StatTile(c.batteryUsedPct?.let { stringResource(R.string.lab_stat_percent, it) } ?: stringResource(R.string.lab_stat_na), stringResource(R.string.lab_stat_battery))
            StatTile(c.averageMs?.let { stringResource(R.string.lab_stat_seconds, one(it / 1000.0)) } ?: stringResource(R.string.lab_stat_na), stringResource(R.string.lab_stat_time))
            if (c.notFinished > 0) StatTile(c.notFinished.toString(), stringResource(R.string.lab_stat_unfinished), Modifier.testTag(LabTags.STAT_UNFINISHED))
            if (c.smallSample) StatusChip(stringResource(R.string.lab_small_sample), ChipKind.Held, Modifier.testTag(LabTags.SMALL_SAMPLE))
        }
        stopText(stop)?.let { Text(stringResource(it), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(LabTags.STOP_REASON)) }
    }
}

internal fun stopText(stop: StopReason?): Int? = when (stop) {
    StopReason.LOW_BATTERY -> R.string.lab_stop_low_battery
    StopReason.HOT -> R.string.lab_stop_hot
    StopReason.TIME_CAP -> R.string.lab_stop_time
    StopReason.ERRORS -> R.string.lab_stop_errors
    StopReason.STOPPED -> R.string.lab_stop_user
    StopReason.INTERRUPTED -> R.string.lab_stop_turned
    StopReason.DONE, null -> null
}
