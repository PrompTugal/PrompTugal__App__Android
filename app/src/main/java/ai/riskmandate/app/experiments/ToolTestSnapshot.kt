package ai.riskmandate.app.experiments

import ai.riskmandate.app.llm.StrictJson

/**
 * What survives a rotation of the tool-test results (the Activity is recreated and the composition with it).
 * Small on purpose, since saved state has a hard size limit: every run's summary (case, kind, mode, repeat, did
 * it finish, did it pass, how long), the failed checks of the first [KEEP_FAILURES] failures with a short raw
 * excerpt, and nothing else. Rates come back whole; the raw output of later failures does not. Checked on the
 * way back: anything out of range is dropped and the tab starts fresh.
 */
object ToolTestSnapshot {
    const val KEEP_FAILURES = 5
    private const val EXCERPT = 600
    private const val DETAIL = 200
    private const val MAX_RUNS = ToolTestSuite.MAX_RUNS

    data class Restored(val runs: List<CaseRun>, val planned: Int, val stop: StopReason?)

    fun encode(runs: List<CaseRun>, planned: Int, stop: StopReason?): String {
        if (runs.isEmpty() && stop == null) return ""
        var kept = 0
        val list = runs.take(MAX_RUNS).map { r ->
            val failed = r.finished && !r.passed
            val detail = if (failed && kept < KEEP_FAILURES) {
                kept++
                mapOf(
                    "checks" to r.checks.filter { !it.passed }.take(3).map { mapOf("l" to it.label.take(DETAIL), "d" to it.detail.take(DETAIL), "k" to it.kind, "f" to it.failure) },
                    "raw" to rawExcerpt(r),
                )
            } else null
            linkedMapOf("c" to r.caseId, "k" to r.kind, "m" to r.mode.name, "r" to r.rep, "f" to (if (r.finished) 1 else 0), "p" to (if (r.passed) 1 else 0), "t" to r.ms, "n" to r.promptChars, "x" to detail)
        }
        return StrictJson.render(mapOf("v" to 1, "planned" to planned, "stop" to stop?.name, "runs" to list))
    }

    /** The raw output of a run in a few lines: what the model said and called, then its final text. */
    fun rawExcerpt(r: CaseRun): String = (r.steps.flatMap { s -> listOfNotNull(s.text.takeIf { it.isNotBlank() }) + s.calls.map { "call ${it.name} ${it.argsJson}" + (it.refusal?.let { x -> " (refused: $x)" } ?: "") } } +
        listOf("answer: ${r.finalText}")).joinToString("\n").take(EXCERPT)

    @Suppress("UNCHECKED_CAST")
    fun decode(text: String): Restored? = try {
        if (text.isEmpty()) null else {
            val d = StrictJson.parse(text, 200_000) as Map<String, Any?>
            require(d["v"] == 1L)
            val planned = (d["planned"] as Long).toInt()
            require(planned in 0..MAX_RUNS)
            val stop = (d["stop"] as? String)?.let { StopReason.valueOf(it) }
            val runs = (d["runs"] as List<Map<String, Any?>>).map { m ->
                val finished = m["f"] == 1L
                val passed = m["p"] == 1L
                require(!passed || finished)
                val x = m["x"] as? Map<String, Any?>
                val checks = when {
                    !finished -> emptyList()
                    passed -> listOf(CheckResult("passed", true, ""))
                    x != null -> (x["checks"] as List<Map<String, Any?>>).map { CheckResult(it["l"] as String, false, it["d"] as String, it["k"] as? String ?: "", it["f"] as? String) }
                    else -> listOf(CheckResult("failed", false, ""))
                }
                CaseRun(
                    m["c"] as String, m["k"] as String, ToolMode.valueOf(m["m"] as String), (m["r"] as Long).toInt(),
                    (x?.get("raw") as? String)?.let { listOf(StepRecord(it, emptyList())) } ?: emptyList(), "",
                    (m["t"] as Long).also { require(it in 0..24L * 3_600_000) }, null, null, null, null,
                    if (finished) null else "did not finish", checks, ((m["n"] as? Long) ?: 0L).toInt().also { require(it in 0..1_000_000) },
                )
            }
            require(runs.size <= planned.coerceAtLeast(runs.size) && runs.size <= MAX_RUNS)
            Restored(runs, planned, stop)
        }
    } catch (e: Exception) {
        null
    }
}
