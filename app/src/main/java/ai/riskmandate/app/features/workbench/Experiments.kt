package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.llm.BackendPreference
import ai.riskmandate.app.experiments.DeviceGuard
import ai.riskmandate.app.experiments.NoGuard
import ai.riskmandate.app.experiments.RunCaps
import ai.riskmandate.app.experiments.StopReason
import ai.riskmandate.app.llm.LlmRunner
import ai.riskmandate.app.llm.LlmStopReason
import ai.riskmandate.app.llm.RunRecord
import ai.riskmandate.app.llm.RunSettings
import ai.riskmandate.app.llm.RunUpdate
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.sqrt

/** What a sweep varies. No model sweep (architect): compare models by switching in Model setup; the log keeps both. */
enum class SweepVariable { TEMPERATURE, TOP_P, MAX_TOKENS, BACKEND, SYSTEM_PROMPT }

/**
 * One experiment: a variable, its values (labels as typed), and repeats. A sweep has any number of values; an
 * A/B has two. Runs go round by round (every value once, then again), so slow drift such as heat spreads over
 * all values instead of landing on the last one.
 */
data class ExperimentPlan(val variable: SweepVariable, val values: List<String>, val repeats: Int) {
    val total: Int get() = values.size * repeats

    /**
     * The runs in order, each with its value's label, built from [base]; or the reason the plan cannot run.
     */
    fun expand(base: RunSettings): Result<List<Pair<String, RunSettings>>> {
        if (values.isEmpty()) return fail("give at least one value")
        if (repeats !in 1..RunCaps.MAX_RUNS) return fail("repeats must be 1 to ${RunCaps.MAX_RUNS}")
        if (total > RunCaps.MAX_RUNS) return fail("at most ${RunCaps.MAX_RUNS} runs per experiment")
        val settings = values.map { v -> v to (apply(base, v) ?: return fail("not a valid value for ${variable.name.lowercase(Locale.ROOT)}: ${v.take(20)}")) }
        return Result.success((1..repeats).flatMap { settings })
    }

    private fun apply(base: RunSettings, v: String): RunSettings? = when (variable) {
        SweepVariable.TEMPERATURE -> v.toDoubleOrNull()?.takeIf { it in 0.0..2.0 }?.let { base.copy(sampling = base.sampling.copy(temperature = it)) }
        SweepVariable.TOP_P -> v.toDoubleOrNull()?.takeIf { it > 0.0 && it <= 1.0 }?.let { base.copy(sampling = base.sampling.copy(topP = it)) }
        SweepVariable.MAX_TOKENS -> v.toIntOrNull()?.takeIf { it in 1..ai.riskmandate.app.llm.LlmRequest.MAX_OUTPUT_TOKENS }?.let { base.copy(maxTokens = it) }
        SweepVariable.BACKEND -> BackendPreference.entries.firstOrNull { it.name.equals(v, true) }?.let { base.copy(backend = it) }
        SweepVariable.SYSTEM_PROMPT -> when (v.lowercase(Locale.ROOT)) {
            "on" -> base.takeIf { it.systemPrompt.isNotBlank() }
            "off" -> base.copy(systemPrompt = "")
            else -> null
        }
    }

    private fun fail(why: String) = Result.failure<List<Pair<String, RunSettings>>>(IllegalArgumentException(why))

    companion object {
        /** "0.2, 0.7 1.0" to ["0.2", "0.7", "1.0"]: commas or spaces, empty parts dropped, at most 20 values. */
        fun parseValues(text: String): List<String> = text.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.take(20)
    }
}

/** Why an experiment ended early. */
enum class ExperimentStop { RUN_CAP, TIME_CAP, BATTERY, HOT, FAILURES, STOPPED; companion object }

/**
 * The caps live in experiments/RunCaps (dev.lab), shared with the Lab's loop and the tool tests: 100 runs,
 * 30 minutes, 15% battery when not charging, severe heat, 3 failed runs in a row. This maps its reasons to the
 * Workbench's [ExperimentStop].
 */
internal fun ExperimentStop.Companion.of(r: StopReason): ExperimentStop? = when (r) {
    StopReason.DONE -> null
    StopReason.STOPPED, StopReason.INTERRUPTED -> ExperimentStop.STOPPED
    StopReason.LOW_BATTERY -> ExperimentStop.BATTERY
    StopReason.HOT -> ExperimentStop.HOT
    StopReason.TIME_CAP -> ExperimentStop.TIME_CAP
    StopReason.ERRORS -> ExperimentStop.FAILURES
}

/** Mean, spread and count of one figure over a value's runs (runs without the figure are left out). */
data class Spread(val n: Int, val mean: Double, val min: Double, val max: Double, val sd: Double) {
    companion object {
        fun of(xs: List<Double>): Spread? {
            if (xs.isEmpty()) return null
            val mean = xs.average()
            val sd = if (xs.size < 2) 0.0 else sqrt(xs.sumOf { (it - mean) * (it - mean) } / (xs.size - 1))
            return Spread(xs.size, mean, xs.min(), xs.max(), sd)
        }
    }
}

/** One row of the results table: a value and its figures. */
data class ValueSummary(
    val label: String,
    val runs: Int,
    val failed: Int,
    val firstTokenMs: Spread?,
    val decodeTps: Spread?,
    val outputTokens: Spread?,
    /** Pass rate when a check is attached (tool tests); null otherwise. */
    val passed: Int?,
)

object ExperimentSummary {
    /** Rows in the plan's value order. A run counts as failed when it ended in ERROR. */
    fun of(values: List<String>, results: List<Pair<String, RunRecord>>, pass: ((RunRecord) -> Boolean)? = null): List<ValueSummary> = values.map { v ->
        val rs = results.filter { it.first == v }.map { it.second }
        val ok = rs.filter { it.stopReason != LlmStopReason.ERROR }
        ValueSummary(
            label = v, runs = rs.size, failed = rs.size - ok.size,
            firstTokenMs = Spread.of(ok.mapNotNull { it.stats?.timeToFirstTokenMs?.toDouble() }),
            decodeTps = Spread.of(ok.mapNotNull { it.stats?.decodeTokensPerSecond }),
            outputTokens = Spread.of(ok.mapNotNull { it.stats?.outputTokens?.toDouble() }),
            passed = pass?.let { p -> rs.count(p) },
        )
    }

    /** The table as plain text (monospace on screen; "Copy results"). */
    fun text(plan: ExperimentPlan, rows: List<ValueSummary>, stop: ExperimentStop?): String = buildString {
        appendLine("RiskMandate Workbench experiment: ${plan.variable.name.lowercase(Locale.ROOT)} over ${plan.values.joinToString(", ")}, ${plan.repeats} each")
        if (stop != null) appendLine("Ended early: ${stop.name.lowercase(Locale.ROOT).replace('_', ' ')}")
        appendLine("value | runs | failed | first token ms (mean ± sd, min to max) | decode tok/s | output tokens" + if (rows.any { it.passed != null }) " | passed" else "")
        rows.forEach { r ->
            append("${r.label} | ${r.runs} | ${r.failed} | ${fmt(r.firstTokenMs, 0)} | ${fmt(r.decodeTps, 1)} | ${fmt(r.outputTokens, 0)}")
            if (r.passed != null) append(" | ${r.passed}/${r.runs}")
            appendLine()
        }
    }.trimEnd()

    private fun fmt(s: Spread?, d: Int): String = if (s == null) "-" else {
        val f = "%.${d}f"
        "${String.format(Locale.ROOT, f, s.mean)} ± ${String.format(Locale.ROOT, f, s.sd)} (${String.format(Locale.ROOT, f, s.min)} to ${String.format(Locale.ROOT, f, s.max)})"
    }
}

/** Progress for the screen. */
data class ExperimentProgress(val done: Int, val total: Int, val current: String?, val results: List<Pair<String, RunRecord>>, val stop: ExperimentStop?, val finished: Boolean)

/**
 * Runs a plan's runs one after another through [runner], checking the caps before each run. Every finished run
 * goes to [onRecord] (the run log). Cancelling stops at once with [ExperimentStop.STOPPED]; the progress
 * callback still gets the final state.
 */
suspend fun runExperiment(
    plan: List<Pair<String, RunSettings>>,
    runner: LlmRunner,
    /** Battery and heat for the caps (experiments/AndroidDeviceGuard in the app, a fake in tests). */
    guard: DeviceGuard = NoGuard,
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
    onRecord: (String, RunRecord) -> Unit = { _, _ -> },
    onProgress: (ExperimentProgress) -> Unit,
): ExperimentProgress {
    val start = clock()
    val results = mutableListOf<Pair<String, RunRecord>>()
    var failuresInARow = 0
    var stop: ExperimentStop? = null
    try {
        for ((label, settings) in plan) {
            stop = when {
                results.size >= RunCaps.MAX_RUNS -> ExperimentStop.RUN_CAP
                failuresInARow >= RunCaps.MAX_CONSECUTIVE_ERRORS -> ExperimentStop.FAILURES
                else -> ExperimentStop.of(RunCaps.nextStop(guard, clock, start) { false })
            }
            if (stop != null) break
            onProgress(ExperimentProgress(results.size, plan.size, label, results.toList(), null, false))
            // A run stopped by cancelling still reaches the log through its Finished update.
            val r = runner.run(settings) { u -> if (u is RunUpdate.Finished && u.record.stopReason == LlmStopReason.CANCELLED) onRecord(label, u.record) }
            results += label to r
            onRecord(label, r)
            failuresInARow = if (r.stopReason == LlmStopReason.ERROR) failuresInARow + 1 else 0
        }
        if (stop == null && failuresInARow >= RunCaps.MAX_CONSECUTIVE_ERRORS && results.size < plan.size) stop = ExperimentStop.FAILURES
    } catch (e: CancellationException) {
        val p = ExperimentProgress(results.size, plan.size, null, results.toList(), ExperimentStop.STOPPED, true)
        onProgress(p)
        throw e
    }
    val p = ExperimentProgress(results.size, plan.size, null, results.toList(), stop, true)
    onProgress(p)
    return p
}

/**
 * Keeps an experiment's results table across a rotation (the Activity is recreated and a running experiment is
 * cancelled with it). Only validated counters are saved (security L): the variable, the values (short labels),
 * repeats, the stop, and per value the counts and the three spreads. No prompt, answer or setting text.
 */
object SummaryCodec {
    private const val V = "s1"

    fun encode(plan: ExperimentPlan, rows: List<ValueSummary>, stop: ExperimentStop?): String =
        listOf(V, plan.variable.name, plan.repeats.toString(), stop?.name ?: "", rows.joinToString(";") { r ->
            listOf(r.label, r.runs, r.failed, sp(r.firstTokenMs), sp(r.decodeTps), sp(r.outputTokens), r.passed ?: -1).joinToString(",")
        }).joinToString("|")

    fun decode(text: String): Triple<ExperimentPlan, List<ValueSummary>, ExperimentStop?>? = try {
        val f = text.split("|")
        require(f.size == 5 && f[0] == V)
        val variable = SweepVariable.valueOf(f[1])
        val repeats = f[2].toInt().also { require(it in 1..RunCaps.MAX_RUNS) }
        val stop = if (f[3].isEmpty()) null else ExperimentStop.valueOf(f[3])
        val rows = if (f[4].isEmpty()) emptyList() else f[4].split(";").map { part ->
            val c = part.split(",")
            require(c.size == 4 + 3 * 5 && c[0].length in 1..20 && c[0].none { it == '|' || it == ';' })
            val runs = c[1].toInt().also { require(it in 0..RunCaps.MAX_RUNS) }
            val failed = c[2].toInt().also { require(it in 0..runs) }
            fun spread(i: Int): Spread? {
                val n = c[i].toInt()
                if (n == 0) return null
                require(n in 1..runs)
                val xs = (1..4).map { c[i + it].toDouble().also { d -> require(d.isFinite() && d >= 0) } }
                return Spread(n, xs[0], xs[1], xs[2], xs[3])
            }
            val passed = c[18].toInt().let { if (it < 0) null else it.also { p -> require(p <= runs) } }
            ValueSummary(c[0], runs, failed, spread(3), spread(8), spread(13), passed)
        }
        require(rows.size <= 20 && rows.sumOf { it.runs } <= RunCaps.MAX_RUNS)
        Triple(ExperimentPlan(variable, rows.map { it.label }, repeats), rows, stop)
    } catch (e: RuntimeException) {
        null
    }

    private fun sp(s: Spread?) = if (s == null) "0,0,0,0,0" else "${s.n},${s.mean},${s.min},${s.max},${s.sd}"
}
