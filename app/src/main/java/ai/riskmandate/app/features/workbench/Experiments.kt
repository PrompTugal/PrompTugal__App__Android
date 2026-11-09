package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.llm.BackendPreference
import ai.riskmandate.app.llm.DeviceSample
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
        if (repeats !in 1..ExperimentCaps.MAX_RUNS) return fail("repeats must be 1 to ${ExperimentCaps.MAX_RUNS}")
        if (total > ExperimentCaps.MAX_RUNS) return fail("at most ${ExperimentCaps.MAX_RUNS} runs per experiment")
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
enum class ExperimentStop { RUN_CAP, TIME_CAP, BATTERY, HOT, FAILURES, STOPPED }

/** The Lab's loop caps, the same numbers (brief): 100 runs, 30 minutes, 15% battery off charge, severe heat, 3 failures in a row. */
object ExperimentCaps {
    const val MAX_RUNS = 100
    const val MAX_MS = 30 * 60_000L
    const val MIN_BATTERY_PCT = 15
    const val MAX_FAILURES_IN_A_ROW = 3

    /** Checked before each run. Null: go on. */
    fun check(runsDone: Int, elapsedMs: Long, device: DeviceSample?, failuresInARow: Int): ExperimentStop? = when {
        runsDone >= MAX_RUNS -> ExperimentStop.RUN_CAP
        elapsedMs >= MAX_MS -> ExperimentStop.TIME_CAP
        failuresInARow >= MAX_FAILURES_IN_A_ROW -> ExperimentStop.FAILURES
        device != null && device.charging != true && (device.batteryPct ?: 100) <= MIN_BATTERY_PCT -> ExperimentStop.BATTERY
        device != null && device.thermal.severeOrWorse -> ExperimentStop.HOT
        else -> null
    }
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
    probe: () -> DeviceSample?,
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
            stop = ExperimentCaps.check(results.size, clock() - start, probe(), failuresInARow)
            if (stop != null) break
            onProgress(ExperimentProgress(results.size, plan.size, label, results.toList(), null, false))
            // A run stopped by cancelling still reaches the log through its Finished update.
            val r = runner.run(settings) { u -> if (u is RunUpdate.Finished && u.record.stopReason == LlmStopReason.CANCELLED) onRecord(label, u.record) }
            results += label to r
            onRecord(label, r)
            failuresInARow = if (r.stopReason == LlmStopReason.ERROR) failuresInARow + 1 else 0
        }
        if (stop == null && failuresInARow >= ExperimentCaps.MAX_FAILURES_IN_A_ROW && results.size < plan.size) stop = ExperimentStop.FAILURES
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
        val repeats = f[2].toInt().also { require(it in 1..ExperimentCaps.MAX_RUNS) }
        val stop = if (f[3].isEmpty()) null else ExperimentStop.valueOf(f[3])
        val rows = if (f[4].isEmpty()) emptyList() else f[4].split(";").map { part ->
            val c = part.split(",")
            require(c.size == 4 + 3 * 5 && c[0].length in 1..20 && c[0].none { it == '|' || it == ';' })
            val runs = c[1].toInt().also { require(it in 0..ExperimentCaps.MAX_RUNS) }
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
        require(rows.size <= 20 && rows.sumOf { it.runs } <= ExperimentCaps.MAX_RUNS)
        Triple(ExperimentPlan(variable, rows.map { it.label }, repeats), rows, stop)
    } catch (e: RuntimeException) {
        null
    }

    private fun sp(s: Spread?) = if (s == null) "0,0,0,0,0" else "${s.n},${s.mean},${s.min},${s.max},${s.sd}"
}
