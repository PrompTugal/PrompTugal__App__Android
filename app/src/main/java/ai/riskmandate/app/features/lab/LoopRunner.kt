package ai.riskmandate.app.features.lab

import ai.riskmandate.app.experiments.DeviceGuard
import ai.riskmandate.app.experiments.NoGuard
import ai.riskmandate.app.experiments.RunCaps
import ai.riskmandate.app.experiments.RunSettings
import ai.riskmandate.app.experiments.StopReason
import ai.riskmandate.app.llm.LlmService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Loop mode: run one scenario N times, both lanes each time, and count. Caps (security I.5): at most
 * [MAX_RUNS] runs and [MAX_TOTAL_MS] in all, a stop on low battery (not charging) or high temperature, and a
 * stop after [MAX_CONSECUTIVE_ERRORS] runs in a row that did not finish. A stop request ends the loop after the
 * current run. Live runs only: a replay is never counted.
 */
object LoopRunner {
    const val DEFAULT_RUNS = 20
    const val MIN_RUNS = 5
    const val MAX_RUNS = RunCaps.MAX_RUNS
    const val MAX_TOTAL_MS = RunCaps.MAX_TOTAL_MS
    const val LOW_BATTERY_PCT = RunCaps.LOW_BATTERY_PCT
    const val MAX_CONSECUTIVE_ERRORS = RunCaps.MAX_CONSECUTIVE_ERRORS
    const val SEED_STEP = RunCaps.SEED_STEP

    /** One finished run: both lanes, how long it took and which model and backend the service reported. */
    data class RunRecord(val index: Int, val raw: PathRecord, val abp: PathRecord, val ms: Long, val modelId: String?, val backend: String?, val settings: RunSettings)

    data class Outcome(val records: List<RunRecord>, val counters: LoopCounters, val stop: StopReason)

    suspend fun run(
        llm: LlmService,
        scenario: Scenario,
        runs: Int,
        settings: RunSettings = RunSettings(),
        baseSeed: Int? = null,
        guard: DeviceGuard = NoGuard,
        now: () -> Long = System::currentTimeMillis,
        stopRequested: () -> Boolean = { false },
        onLane: (abp: Boolean, PathRecord) -> Unit = { _, _ -> },
        onRun: (RunRecord, LoopCounters) -> Unit = { _, _ -> },
    ): Outcome {
        val n = runs.coerceIn(1, MAX_RUNS)
        val records = ArrayList<RunRecord>()
        var counters = LoopCounters(n, batteryStartPct = guard.batteryPct(), batteryEndPct = guard.batteryPct())
        val stop = RunCaps.capped(n, guard, now, stopRequested) { i ->
            val s = settings.copy(sampling = settings.sampling.copy(seed = baseSeed?.plus(SEED_STEP * i) ?: settings.sampling.seed))
            val start = now()
            val (raw, abp) = coroutineScope {
                val a = async { LiveRunner.runPath(llm, scenario, abp = false, settings = s, now = now) { onLane(false, it) } }
                val b = async { LiveRunner.runPath(llm, scenario, abp = true, settings = s, now = now) { onLane(true, it) } }
                a.await() to b.await()
            }
            if (raw is LiveRunner.Result.Done && abp is LiveRunner.Result.Done) {
                val ms = now() - start
                val rec = RunRecord(i, raw.record, abp.record, ms, raw.modelId ?: abp.modelId, raw.backend ?: abp.backend, s)
                records += rec
                counters = counters.withRun(RunScore.of(raw.record, abp.record, ms), guard.batteryPct())
                onRun(rec, counters)
                true
            } else {
                counters = counters.withFailedRun()
                false
            }
        }
        // One last reading, so a stop on low battery reports the level that stopped it.
        counters = counters.copy(batteryEndPct = guard.batteryPct() ?: counters.batteryEndPct)
        return Outcome(records, counters, stop)
    }
}
