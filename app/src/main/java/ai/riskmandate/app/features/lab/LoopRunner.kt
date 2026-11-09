package ai.riskmandate.app.features.lab

import ai.riskmandate.app.llm.LlmService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** What the loop asks of the device before each run. The Android implementation is in [AndroidDeviceGuard]. */
interface DeviceGuard {
    /** Battery percentage, or null when unknown. */
    fun batteryPct(): Int?
    fun charging(): Boolean
    /** True when the device reports severe thermal pressure. */
    fun hot(): Boolean
}

object NoGuard : DeviceGuard {
    override fun batteryPct(): Int? = null
    override fun charging(): Boolean = false
    override fun hot(): Boolean = false
}

/** [INTERRUPTED]: the screen was turned (the Activity was recreated) while the loop ran; the counts so far are kept. */
enum class StopReason { DONE, STOPPED, LOW_BATTERY, HOT, TIME_CAP, ERRORS, INTERRUPTED }

/**
 * Loop mode: run one scenario N times, both lanes each time, and count. Caps (security I.5): at most
 * [MAX_RUNS] runs and [MAX_TOTAL_MS] in all, a stop on low battery (not charging) or high temperature, and a
 * stop after [MAX_CONSECUTIVE_ERRORS] runs in a row that did not finish. A stop request ends the loop after the
 * current run. Live runs only: a replay is never counted.
 */
object LoopRunner {
    const val DEFAULT_RUNS = 20
    const val MIN_RUNS = 5
    const val MAX_RUNS = 100
    const val MAX_TOTAL_MS = 30 * 60_000L
    const val LOW_BATTERY_PCT = 15
    const val MAX_CONSECUTIVE_ERRORS = 3
    /** Seed of run i is the base seed plus this times i (the harness's convention), so runs differ but repeat. */
    const val SEED_STEP = 1000

    /** One finished run: both lanes, how long it took and which model and backend the service reported. */
    data class RunRecord(val index: Int, val raw: PathRecord, val abp: PathRecord, val ms: Long, val modelId: String?, val backend: String?, val settings: LiveRunner.Settings)

    data class Outcome(val records: List<RunRecord>, val counters: LoopCounters, val stop: StopReason)

    suspend fun run(
        llm: LlmService,
        scenario: Scenario,
        runs: Int,
        settings: LiveRunner.Settings = LiveRunner.Settings(),
        baseSeed: Int? = null,
        guard: DeviceGuard = NoGuard,
        now: () -> Long = System::currentTimeMillis,
        stopRequested: () -> Boolean = { false },
        onLane: (abp: Boolean, PathRecord) -> Unit = { _, _ -> },
        onRun: (RunRecord, LoopCounters) -> Unit = { _, _ -> },
    ): Outcome {
        val n = runs.coerceIn(1, MAX_RUNS)
        val t0 = now()
        val records = ArrayList<RunRecord>()
        var counters = LoopCounters(n, batteryStartPct = guard.batteryPct(), batteryEndPct = guard.batteryPct())
        var errorsInARow = 0
        var stop = StopReason.DONE
        for (i in 0 until n) {
            stop = when {
                stopRequested() -> StopReason.STOPPED
                now() - t0 >= MAX_TOTAL_MS -> StopReason.TIME_CAP
                guard.hot() -> StopReason.HOT
                guard.batteryPct()?.let { it <= LOW_BATTERY_PCT } == true && !guard.charging() -> StopReason.LOW_BATTERY
                else -> StopReason.DONE
            }
            if (stop != StopReason.DONE) break
            val s = settings.copy(sampling = settings.sampling.copy(seed = baseSeed?.plus(SEED_STEP * i) ?: settings.sampling.seed))
            val start = now()
            val (raw, abp) = coroutineScope {
                val a = async { LiveRunner.runPath(llm, scenario, abp = false, settings = s, now = now) { onLane(false, it) } }
                val b = async { LiveRunner.runPath(llm, scenario, abp = true, settings = s, now = now) { onLane(true, it) } }
                a.await() to b.await()
            }
            if (raw is LiveRunner.Result.Done && abp is LiveRunner.Result.Done) {
                errorsInARow = 0
                val ms = now() - start
                val rec = RunRecord(i, raw.record, abp.record, ms, raw.modelId ?: abp.modelId, raw.backend ?: abp.backend, s)
                records += rec
                counters = counters.withRun(RunScore.of(raw.record, abp.record, ms), guard.batteryPct())
                onRun(rec, counters)
            } else {
                counters = counters.withFailedRun()
                if (++errorsInARow >= MAX_CONSECUTIVE_ERRORS) { stop = StopReason.ERRORS; break }
            }
        }
        // One last reading, so a stop on low battery reports the level that stopped it.
        counters = counters.copy(batteryEndPct = guard.batteryPct() ?: counters.batteryEndPct)
        return Outcome(records, counters, stop)
    }
}
