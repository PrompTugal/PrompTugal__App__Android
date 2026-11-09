package ai.riskmandate.app.features.lab

import ai.riskmandate.app.llm.LlmService

/**
 * Runs many cases: one case, a kind or all, in one or both modes, N times each. Same kind of caps as the Lab's
 * loop mode (security I.5): at most [MAX_RUNS] runs and [MAX_TOTAL_MS] in all, a stop on low battery (not
 * charging) or severe thermal status, a stop after [LoopRunner.MAX_CONSECUTIVE_ERRORS] runs in a row that did
 * not finish, and a stop request that ends it after the current run. The order is repeat by repeat, so a stop
 * leaves every case with about the same number of runs.
 */
object ToolTestSuite {
    const val MAX_RUNS = 300
    const val MAX_REPEATS = 10
    const val MAX_TOTAL_MS = LoopRunner.MAX_TOTAL_MS

    data class Outcome(val runs: List<CaseRun>, val planned: Int, val stop: StopReason, val batteryStartPct: Int?, val batteryEndPct: Int?) {
        val report: ToolTestReport get() = ToolTestReport(runs)
    }

    /** The runs a request would make, capped: (case, mode, rep) in the order they run. */
    fun plan(cases: List<ToolCase>, modes: List<ToolMode>, repeats: Int): List<Triple<ToolCase, ToolMode, Int>> {
        val n = repeats.coerceIn(1, MAX_REPEATS)
        return (0 until n).flatMap { rep -> cases.flatMap { c -> modes.distinct().map { m -> Triple(c, m, rep) } } }.take(MAX_RUNS)
    }

    suspend fun run(
        llm: LlmService,
        cases: List<ToolCase>,
        modes: List<ToolMode>,
        repeats: Int,
        settings: LiveRunner.Settings = LiveRunner.Settings(),
        baseSeed: Int? = null,
        guard: DeviceGuard = NoGuard,
        now: () -> Long = System::currentTimeMillis,
        stopRequested: () -> Boolean = { false },
        onRun: (CaseRun, Int, Int) -> Unit = { _, _, _ -> },
    ): Outcome {
        val plan = plan(cases, modes, repeats)
        val t0 = now()
        val done = ArrayList<CaseRun>()
        val startPct = guard.batteryPct()
        var errorsInARow = 0
        var stop = StopReason.DONE
        for ((case, mode, rep) in plan) {
            stop = when {
                stopRequested() -> StopReason.STOPPED
                now() - t0 >= MAX_TOTAL_MS -> StopReason.TIME_CAP
                guard.hot() -> StopReason.HOT
                guard.batteryPct()?.let { it <= LoopRunner.LOW_BATTERY_PCT } == true && !guard.charging() -> StopReason.LOW_BATTERY
                else -> StopReason.DONE
            }
            if (stop != StopReason.DONE) break
            val s = settings.copy(sampling = settings.sampling.copy(seed = baseSeed?.plus(LoopRunner.SEED_STEP * rep) ?: settings.sampling.seed))
            val run = ToolTestRunner.runCase(llm, case, mode, s, rep, now)
            done += run
            onRun(run, done.size, plan.size)
            if (run.finished) errorsInARow = 0 else if (++errorsInARow >= LoopRunner.MAX_CONSECUTIVE_ERRORS) { stop = StopReason.ERRORS; break }
        }
        return Outcome(done, plan.size, stop, startPct, guard.batteryPct() ?: startPct)
    }
}
