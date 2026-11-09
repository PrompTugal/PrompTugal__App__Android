package ai.riskmandate.app.features.lab

/**
 * One path (raw or ABP) of one run, fed one model turn at a time. The model call itself is outside: live
 * runs pass each model text in and give the returned tool results back to the model; a test passes
 * scripted texts. Raw: every valid call runs on the fakes. ABP: every valid call is checked first and a
 * blocked or held call does not run. Invalid calls (unparseable, unknown, bad arguments, over a cap) are
 * recorded refusals on both paths.
 */
class PathRun(private val scenario: Scenario, private val abp: Boolean) {
    private val events = ArrayList<LabEvent>()
    private val calls = ArrayList<CallTrace>()
    private var finalText: String? = null
    private var steps = 0

    val done: Boolean get() = finalText != null

    /**
     * Feeds one model turn at [tMs]. Returns the text to give back to the model as tool results, or null
     * when the model answered (this turn is the final answer) or the step limit was reached.
     */
    fun step(text: String, tMs: Long): String? {
        check(!done) { "run already finished" }
        steps++
        if (text.isNotBlank()) events += LabEvent.ModelText(tMs, text)
        val raws = ToolProtocol.extractCalls(text)
        if (raws.isEmpty()) { finalText = text; return null }
        val back = ArrayList<String>()
        raws.forEachIndexed { i, raw ->
            val args = (raw.arguments as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
            val problem = ToolProtocol.validate(raw, scenario.tools, i)
            events += LabEvent.CallProposed(tMs, raw.name, args, problem == null)
            if (problem != null) {
                val msg = "refused: $problem"
                events += LabEvent.ToolResult(tMs, raw.name, false, msg)
                calls += CallTrace(raw.name, args, false, null, false, msg)
                back += "${raw.name}: $msg"
                return@forEachIndexed
            }
            val call = ToolProtocol.toCall(raw)
            val verdict = if (abp) AbpChecks.checkCall(scenario, call) else null
            if (verdict != null) {
                val rule = (verdict as? Verdict.Block)?.rule ?: (verdict as? Verdict.Hold)?.rule
                val reason = (verdict as? Verdict.Block)?.reason ?: (verdict as? Verdict.Hold)?.reason
                events += LabEvent.VerdictEvent(tMs, verdict.action, rule, reason)
            }
            if (verdict is Verdict.Block || verdict is Verdict.Hold) {
                val msg = if (verdict is Verdict.Block) "blocked: ${verdict.reason}" else "held for approval: ${(verdict as Verdict.Hold).reason}"
                events += LabEvent.ToolResult(tMs, call.name, false, msg)
                calls += CallTrace(call.name, args, true, verdict, false, msg)
                back += "${call.name}: $msg"
            } else {
                val result = FakeTools.asText(FakeTools.execute(scenario, call.name, call.args))
                events += LabEvent.ToolResult(tMs, call.name, true, result)
                calls += CallTrace(call.name, args, true, verdict, true, result)
                back += "${call.name}: $result"
            }
        }
        if (steps >= scenario.maxSteps) { finalText = ""; return null }
        return back.joinToString("\n")
    }

    /** Ends the run (adding the ABP's answer flags and the final answer) and scores it. */
    fun finish(tMs: Long): PathRecord {
        val text = finalText ?: ""
        val flags = if (abp) AbpChecks.checkFinal(scenario, text, Oracle.fetchedTriplets(scenario, calls)) else emptyList()
        for (f in flags) events += LabEvent.Flag(tMs, f.rule, f.claim, f.reason, f.text)
        events += LabEvent.Final(tMs, text)
        return PathRecord(events.toList(), Oracle.outcome(scenario, RunTrace(calls.toList(), text, flags), abp))
    }

    /** The trace so far, for scoring or export. */
    fun trace(): RunTrace {
        val text = finalText ?: ""
        val flags = if (abp) AbpChecks.checkFinal(scenario, text, Oracle.fetchedTriplets(scenario, calls)) else emptyList()
        return RunTrace(calls.toList(), text, flags)
    }
}
