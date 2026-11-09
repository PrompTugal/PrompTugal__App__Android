package ai.riskmandate.app.features.lab

/** Pass counts for a group of runs. Runs that did not finish are counted apart and are in no rate. */
data class PassStat(val passed: Int, val finished: Int, val notFinished: Int, val meanMs: Long?) {
    val rate: Double? get() = if (finished == 0) null else passed.toDouble() / finished
}

/**
 * Pass rates per case, per kind and overall, per mode or both, and the failures with their raw output.
 * Counted by code from the runs, never from text. Both modes are always reported separately, so the report
 * says which way of asking for a tool call works on this model.
 */
class ToolTestReport(val runs: List<CaseRun>) {
    private fun stat(rs: List<CaseRun>): PassStat {
        val done = rs.filter { it.finished }
        return PassStat(done.count { it.passed }, done.size, rs.size - done.size, if (done.isEmpty()) null else done.sumOf { it.ms } / done.size)
    }

    fun overall(mode: ToolMode? = null) = stat(runs.filter { mode == null || it.mode == mode })
    fun byKind(kind: String, mode: ToolMode? = null) = stat(runs.filter { it.kind == kind && (mode == null || it.mode == mode) })
    fun byCase(caseId: String, mode: ToolMode? = null) = stat(runs.filter { it.caseId == caseId && (mode == null || it.mode == mode) })

    /** Finished runs that failed at least one check, newest first: shown with the raw output. */
    fun failures(): List<CaseRun> = runs.filter { it.finished && !it.passed }.asReversed()

    /** The modes present in the runs, in declaration order. */
    fun modes(): List<ToolMode> = ToolMode.entries.filter { m -> runs.any { it.mode == m } }
}
