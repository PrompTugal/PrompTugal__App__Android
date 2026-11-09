package ai.riskmandate.app.experiments

import ai.riskmandate.app.llm.LlmService

/**
 * Runs many cases: one case, a kind or all, in one or both modes, N times each. Same kind of caps as the Lab's
 * loop mode (security I.5): at most [MAX_RUNS] runs and [MAX_TOTAL_MS] in all, a stop on low battery (not
 * charging) or severe thermal status, a stop after [RunCaps.MAX_CONSECUTIVE_ERRORS] runs in a row that did
 * not finish, and a stop request that ends it after the current run. The order is repeat by repeat, so a stop
 * leaves every case with about the same number of runs.
 */
object ToolTestSuite {
    /** The same cap as every other run of many experiments (security): 100 runs. "All cases, both modes" is 40 runs per repeat. */
    const val MAX_RUNS = RunCaps.MAX_RUNS
    const val MAX_REPEATS = 10
    const val MAX_TOTAL_MS = RunCaps.MAX_TOTAL_MS

    data class Outcome(
        val runs: List<CaseRun>, val planned: Int, val stop: StopReason, val batteryStartPct: Int?, val batteryEndPct: Int?,
        /** What the judge panel made of each finished run, when a panel was given (empty otherwise). */
        val judged: List<PanelResult> = emptyList(),
    ) {
        val report: ToolTestReport get() = ToolTestReport(runs)
        val judgeReport: JudgeReport get() = JudgeReport(judged)
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
        settings: RunSettings = RunSettings(),
        baseSeed: Int? = null,
        guard: DeviceGuard = NoGuard,
        now: () -> Long = System::currentTimeMillis,
        stopRequested: () -> Boolean = { false },
        onRun: (CaseRun, Int, Int) -> Unit = { _, _, _ -> },
        panel: JudgePanel? = null,
        onJudged: (PanelResult) -> Unit = {},
    ): Outcome {
        val plan = plan(cases, modes, repeats)
        val done = ArrayList<CaseRun>()
        val judged = ArrayList<PanelResult>()
        val startPct = guard.batteryPct()
        val stop = RunCaps.capped(plan.size, guard, now, stopRequested) { i ->
            val (case, mode, rep) = plan[i]
            val s = settings.copy(sampling = settings.sampling.copy(seed = baseSeed?.plus(RunCaps.SEED_STEP * rep) ?: settings.sampling.seed))
            val run = ToolTestRunner.runCase(llm, case, mode, s, rep, now)
            done += run
            onRun(run, done.size, plan.size)
            if (panel != null && run.finished) panel.run(case, run).also { judged += it; onJudged(it) }
            run.finished
        }
        return Outcome(done, plan.size, stop, startPct, guard.batteryPct() ?: startPct, judged)
    }
}
