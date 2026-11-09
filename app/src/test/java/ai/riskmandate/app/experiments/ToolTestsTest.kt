package ai.riskmandate.app.experiments

import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmError
import ai.riskmandate.app.llm.LlmEvent
import ai.riskmandate.app.llm.LlmRole
import ai.riskmandate.app.llm.StrictJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ToolTestsTest {
    private val cat = ExperimentsTestData.toolCatalogue()

    /** A model that follows [steps]: one per request, the last repeated. NATIVE proposals or a JSON call in the text. */
    private fun scripted(steps: List<RefStep>, mode: ToolMode): FakeLlmService {
        var n = 0
        return FakeLlmService(reply = {
            val s = steps[minOf(n++, steps.lastIndex)]
            if (mode == ToolMode.NATIVE) FakeReply(s.text, s.calls.map { c -> LlmEvent.ToolCallProposed(c.name, StrictJson.render(c.arguments)) })
            else FakeReply((listOf(s.text).filter { it.isNotBlank() } + s.calls.map { c -> StrictJson.render(mapOf("tool" to c.name, "arguments" to c.arguments)) }).joinToString("\n"))
        })
    }

    private fun run(case: ToolCase, mode: ToolMode, steps: List<RefStep>) = runBlocking { ToolTestRunner.runCase(scripted(steps, mode), case, mode) }

    // ---- the catalogue ----------------------------------------------------------------------------------

    private val BASE_KINDS = listOf("right_tool", "valid_arguments", "expected_values", "no_tool", "made_up_tool", "similar_tools", "two_step", "secret", "answer_after_result")
    private val PACK_KINDS = listOf("grounding", "hallucination", "instruction_following", "refusal_abp")
    private val base = cat.cases.filter { it.kind in BASE_KINDS }

    @Test fun theCatalogueHasTwentyToolCasesAndTwentyExperimentCases() {
        assertEquals(40, cat.cases.size)
        assertEquals(20, base.size)
        assertEquals(BASE_KINDS + PACK_KINDS, cat.kinds.map { it.id })
        for (k in PACK_KINDS) assertEquals("$k has five cases", 5, cat.casesOf(k).size)
        assertEquals(cat.cases.size, cat.cases.map { it.id }.toSet().size)
        assertTrue(cat.kinds.all { k -> cat.casesOf(k.id).isNotEmpty() })
        assertEquals(16, cat.hash.length)
    }

    @Test fun everyCaseIsWellFormed() {
        for (c in cat.cases) {
            assertTrue("${c.id}: tests line", c.tests.isNotBlank() && c.tests.length <= 200 && '\n' !in c.tests)
            assertTrue("${c.id}: tools", c.tools.size in (if (c.kind in BASE_KINDS) 2..5 else 0..5))
            assertTrue("${c.id}: flat arguments", c.tools.all { t -> t.params.size <= ToolProtocol.MAX_ARGS && t.params.all { it.type in setOf("string", "integer", "number", "boolean") } })
            assertTrue("${c.id}: every tool has a fake", c.tools.all { it.name in c.fakes })
            assertNotNull("${c.id}: reference", c.reference)
            assertNotNull("${c.id}: wrong", c.wrong)
            assertEquals("user", c.messages.first().role)
        }
        assertTrue("every check type is used somewhere", ToolChecks.KINDS.all { t -> cat.cases.any { c -> c.checks.any { it.type == t } } })
    }

    @Test fun planted_secrets_in_the_catalogue_are_obvious_fakes() {
        val secrets = cat.cases.flatMap { it.checks }.filter { it.type == "value_absent" }.map { it.params["value"] as String }
        assertEquals(2, secrets.size)
        assertTrue(secrets.all { it.startsWith("PLANTED-") && it.endsWith("-do-not-use") })
    }

    // ---- the checks are checked: reference passes, wrong fails, in both modes --------------------------

    @Test fun everyCaseReferenceRunPassesAndWrongRunFailsInBothModes() {
        for (c in cat.cases) for (mode in ToolMode.entries) {
            val good = run(c, mode, c.reference!!)
            assertTrue("${c.id} $mode reference should pass: ${good.checks.filter { !it.passed }}", good.passed)
            val bad = run(c, mode, c.wrong!!)
            assertTrue("${c.id} $mode wrong run should finish", bad.finished)
            assertFalse("${c.id} $mode wrong run should fail", bad.passed)
        }
    }

    @Test fun aPerfectModelPassesEverythingAndABadOneNothingAndTheReportSaysSo() = runBlocking {
        val perfect = cat.cases.flatMap { c -> ToolMode.entries.map { m -> run(c, m, c.reference!!) } }
        val good = ToolTestReport(perfect)
        assertEquals(PassStat(cat.cases.size, cat.cases.size, 0, good.overall(ToolMode.NATIVE).meanMs), good.overall(ToolMode.NATIVE))
        assertEquals(1.0, good.overall().rate!!, 1e-9)
        assertTrue(good.failures().isEmpty())
        val bad = ToolTestReport(cat.cases.flatMap { c -> ToolMode.entries.map { m -> run(c, m, c.wrong!!) } })
        assertEquals(0.0, bad.overall().rate!!, 1e-9)
        assertEquals(cat.cases.size * 2, bad.failures().size)
        assertEquals(listOf(ToolMode.NATIVE, ToolMode.TEXT), bad.modes())
        for (k in cat.kinds) assertEquals(0, bad.byKind(k.id).passed)
    }

    // ---- the two ways of asking, measured separately ---------------------------------------------------

    @Test fun nativeModeDeclaresTheToolsAndTextModeDescribesThemInThePrompt() {
        val c = cat.case("right-tool-weather")!!
        val nativeLlm = scripted(c.reference!!, ToolMode.NATIVE)
        runBlocking { ToolTestRunner.runCase(nativeLlm, c, ToolMode.NATIVE) }
        val n = nativeLlm.requests.first()
        assertEquals(c.tools.map { it.name }, n.tools.map { it.name })
        assertFalse(n.messages.any { it.role == LlmRole.SYSTEM && it.text.contains("reply with only a JSON object") })
        val textLlm = scripted(c.reference!!, ToolMode.TEXT)
        runBlocking { ToolTestRunner.runCase(textLlm, c, ToolMode.TEXT) }
        val t = textLlm.requests.first()
        assertTrue(t.tools.isEmpty())
        val sys = t.messages.first { it.role == LlmRole.SYSTEM }.text
        assertTrue(c.tools.all { sys.contains(it.name) })
        assertTrue(sys.contains("reply with only a JSON object"))
    }

    @Test fun aCallInTheWrongChannelIsNotACallInThatMode() {
        val c = cat.case("right-tool-weather")!!
        // NATIVE: a JSON call written in the text is just text, so no tool was called and the case fails.
        val textCall = listOf(RefStep(StrictJson.render(mapOf("tool" to "get_weather", "arguments" to mapOf("city" to "Lisbon"))), emptyList()))
        val inNative = run(c, ToolMode.NATIVE, textCall)
        assertTrue(inNative.calls.isEmpty()); assertFalse(inNative.passed)
        // TEXT: a native proposal is ignored, because the tools were never declared.
        val nativeCall = listOf(RefStep("", listOf(ToolMsgCall("get_weather", mapOf("city" to "Lisbon")))), RefStep("It is sunny.", emptyList()))
        val llm = FakeLlmService(reply = { req ->
            if (req.messages.size <= 2) FakeReply("", listOf(LlmEvent.ToolCallProposed("get_weather", "{\"city\":\"Lisbon\"}"))) else FakeReply("It is sunny.")
        })
        val inText = runBlocking { ToolTestRunner.runCase(llm, c, ToolMode.TEXT) }
        assertTrue(inText.calls.isEmpty()); assertFalse(inText.passed)
        assertEquals(2, nativeCall.size)
    }

    @Test fun historyIsWrittenOutForTextModeAndKeptAsMessagesForNative() {
        val c = cat.case("answer-after-weather")!!
        val nat = scripted(c.reference!!, ToolMode.NATIVE).also { runBlocking { ToolTestRunner.runCase(it, c, ToolMode.NATIVE) } }.requests.first().messages
        assertEquals(listOf(LlmRole.USER, LlmRole.ASSISTANT, LlmRole.TOOL), nat.map { it.role })
        assertEquals("get_weather", nat[1].toolCalls.single().name)
        assertEquals("get_weather", nat[2].toolName)
        val txt = scripted(c.reference!!, ToolMode.TEXT).also { runBlocking { ToolTestRunner.runCase(it, c, ToolMode.TEXT) } }.requests.first().messages.filter { it.role != LlmRole.SYSTEM }
        assertEquals(listOf(LlmRole.USER, LlmRole.ASSISTANT, LlmRole.USER), txt.map { it.role })
        assertTrue(txt[1].text.contains("\"tool\": \"get_weather\""))
        assertEquals("Tool result from get_weather: 18 C, sunny", txt[2].text)
    }

    @Test fun twoStepResultsGoBackToTheModelAndTheSecondCallUsesThem() {
        val c = cat.case("two-step-order")!!
        for (mode in ToolMode.entries) {
            val llm = scripted(c.reference!!, mode)
            val r = runBlocking { ToolTestRunner.runCase(llm, c, mode) }
            assertTrue("$mode ${r.checks.filter { !it.passed }}", r.passed)
            assertEquals(listOf("lookup_order", "get_tracking"), r.calls.map { it.name })
            assertEquals("ORD-4471", r.calls[0].result)
            assertTrue(llm.requests.last().messages.any { it.text.contains("ORD-4471") })
        }
    }

    // ---- refusals, errors, caps ------------------------------------------------------------------------

    @Test fun refusedCallsAreRecordedWithTheSharedChecksReasonAndNeverRun() {
        val c = cat.case("valid-args-integer")!!
        for (mode in ToolMode.entries) {
            val r = run(c, mode, c.wrong!!)
            val call = r.calls.first()
            assertFalse(call.accepted)
            assertEquals("seconds should be integer", call.refusal)
            assertEquals("refused: seconds should be integer", call.result)
            assertEquals("\"90\"", StrictJson.render(call.args["seconds"]))
        }
    }

    @Test fun aServiceErrorIsAnUnfinishedRunAndCountsInNoRate() {
        val c = cat.case("right-tool-weather")!!
        val llm = FakeLlmService(reply = { FakeReply(error = LlmEvent.Error(LlmError.INFERENCE_FAILED, "x")) })
        val r = runBlocking { ToolTestRunner.runCase(llm, c, ToolMode.NATIVE) }
        assertEquals("inference failed", r.error)
        assertFalse(r.finished); assertFalse(r.passed); assertTrue(r.checks.isEmpty())
        val rep = ToolTestReport(listOf(r, run(c, ToolMode.NATIVE, c.reference!!)))
        assertEquals(PassStat(1, 1, 1, rep.overall().meanMs), rep.overall())
        assertEquals(1, rep.runs.count { !it.finished })
        assertTrue(rep.failures().isEmpty())
    }

    @Test fun aModelThatNeverAnswersStopsAtTheStepLimit() {
        val c = cat.case("right-tool-weather")!!
        val llm = FakeLlmService(reply = { FakeReply("", listOf(LlmEvent.ToolCallProposed("get_weather", "{\"city\":\"Lisbon\"}"))) })
        val r = runBlocking { ToolTestRunner.runCase(llm, c, ToolMode.NATIVE) }
        assertEquals(c.maxSteps, llm.requests.size)
        assertEquals("", r.finalText)
    }

    @Test fun thePlannedRunsAreCappedAndRunRepeatByRepeat() {
        val three = cat.cases.take(3)
        val plan = ToolTestSuite.plan(three, listOf(ToolMode.NATIVE, ToolMode.TEXT), repeats = 2)
        assertEquals(12, plan.size)
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1), plan.map { it.third })
        assertEquals(ToolTestSuite.MAX_RUNS, ToolTestSuite.plan(cat.cases, ToolMode.entries, 10).size)
        assertEquals(1, ToolTestSuite.plan(three.take(1), listOf(ToolMode.NATIVE), 0).size)
        assertEquals(ToolTestSuite.MAX_REPEATS, ToolTestSuite.plan(three.take(1), listOf(ToolMode.NATIVE), 99).size)
        assertEquals(1, ToolTestSuite.plan(three.take(1), listOf(ToolMode.NATIVE, ToolMode.NATIVE), 1).size)
    }

    /**
     * A model for suite tests: it follows [steps] for whichever case the suite is on, in the way the request
     * asks (native proposals when tools are declared, a JSON call in the text when they are not). The suite runs
     * one run at a time in plan order, so the harness knows which case is current without guessing from text.
     */
    private class Harness(cases: List<ToolCase>, modes: List<ToolMode>, repeats: Int, val steps: (ToolCase) -> List<RefStep>) {
        val plan = ToolTestSuite.plan(cases, modes, repeats)
        var index = 0
        val onRun: (CaseRun, Int, Int) -> Unit = { _, _, _ -> index++ }
        val llm = FakeLlmService(reply = { req ->
            val c = plan[index].first
            val turns = req.messages.count { it.role == LlmRole.ASSISTANT } - c.messages.count { it.role == "assistant" }
            val st = steps(c)
            val s = st[turns.coerceIn(0, st.lastIndex)]
            if (req.tools.isNotEmpty()) FakeReply(s.text, s.calls.map { LlmEvent.ToolCallProposed(it.name, StrictJson.render(it.arguments)) })
            else FakeReply((listOf(s.text).filter { it.isNotBlank() } + s.calls.map { StrictJson.render(mapOf("tool" to it.name, "arguments" to it.arguments)) }).joinToString("\n"))
        })
    }

    @Test fun theSuiteRunsEverythingBothModesNTimesAndReportsPerCaseKindAndOverall() = runBlocking {
        val h = Harness(base, ToolMode.entries, 2) { it.reference!! }
        val llm = h.llm
        val seen = ArrayList<Pair<Int, Int>>()
        val out = ToolTestSuite.run(llm, base, ToolMode.entries, repeats = 2, baseSeed = 5, onRun = { r, done, planned -> h.onRun(r, done, planned); seen += done to planned })
        assertEquals(80, out.runs.size)
        assertEquals(StopReason.DONE, out.stop)
        assertEquals(80 to 80, seen.last())
        val rep = out.report
        assertEquals(1.0, rep.overall(ToolMode.NATIVE).rate!!, 1e-9)
        assertEquals(1.0, rep.overall(ToolMode.TEXT).rate!!, 1e-9)
        assertEquals(PassStat(4, 4, 0, rep.byCase("right-tool-weather").meanMs), rep.byCase("right-tool-weather"))
        assertEquals(2, rep.byCase("right-tool-weather", ToolMode.TEXT).passed)
        assertEquals(12, rep.byKind("expected_values").finished)
        assertEquals(listOf(5, 1005), llm.requests.mapNotNull { it.settings.seed }.distinct().sorted())
    }

    private class Guard(var pct: Int? = 80, var charging: Boolean = false, var hot: Boolean = false) : DeviceGuard {
        override fun batteryPct() = pct
        override fun charging() = charging
        override fun hot() = hot
    }

    @Test fun theSuiteStopsOnRequestBatteryHeatTimeAndRepeatedErrors() = runBlocking {
        val cases = cat.cases.take(4)
        val one = listOf(ToolMode.NATIVE)
        fun h(repeats: Int) = Harness(cases, one, repeats) { it.reference!! }
        var n = 0
        val h1 = h(3)
        val byRequest = ToolTestSuite.run(h1.llm, cases, one, 3, stopRequested = { n >= 2 }, onRun = { r, d, p -> h1.onRun(r, d, p); n++ })
        assertEquals(2, byRequest.runs.size); assertEquals(StopReason.STOPPED, byRequest.stop)
        val g = Guard()
        val h2 = h(3)
        val low = ToolTestSuite.run(h2.llm, cases, one, 3, guard = g, onRun = { r, done, p -> h2.onRun(r, done, p); if (done == 1) g.pct = 15 })
        assertEquals(1, low.runs.size); assertEquals(StopReason.LOW_BATTERY, low.stop); assertEquals(15, low.batteryEndPct); assertEquals(80, low.batteryStartPct)
        val h3 = h(1)
        assertEquals(4, ToolTestSuite.run(h3.llm, cases, one, 1, guard = Guard(pct = 3, charging = true), onRun = h3.onRun).runs.size)
        val h4 = h(1)
        val hot = ToolTestSuite.run(h4.llm, cases, one, 1, guard = Guard(hot = true), onRun = h4.onRun)
        assertEquals(0, hot.runs.size); assertEquals(StopReason.HOT, hot.stop)
        var t = 0L
        val h5 = h(5)
        val slow = ToolTestSuite.run(h5.llm, cases, one, 5, now = { t += ToolTestSuite.MAX_TOTAL_MS / 3; t }, onRun = h5.onRun)
        assertEquals(StopReason.TIME_CAP, slow.stop); assertTrue(slow.runs.size in 1..3)
        val failing = ToolTestSuite.run(FakeLlmService(reply = { FakeReply(error = LlmEvent.Error(LlmError.INFERENCE_FAILED, "x")) }), cat.cases, one, 3)
        assertEquals(StopReason.ERRORS, failing.stop); assertEquals(RunCaps.MAX_CONSECUTIVE_ERRORS, failing.runs.size)
        assertEquals(0, failing.report.overall().finished)
    }

    // ---- the same JSON as the Lab's export -------------------------------------------------------------

    @Test @Suppress("UNCHECKED_CAST")
    fun theExportIsTheLabsEnvelopeWithRunRecordsAndAPerModeSummary() = runBlocking {
        val h = Harness(cat.cases.take(3), ToolMode.entries, 1) { it.reference!! }
        val out = ToolTestSuite.run(h.llm, cat.cases.take(3), ToolMode.entries, 1, guard = Guard(pct = 70), onRun = h.onRun)
        val text = ToolTestExport.build(cat.hash, out, RunSettings(), "2026-11-09T10:00:00Z", "0.1.99", 99, mapOf("os" to "15"))
        val d = StrictJson.parse(text) as Map<String, Any?>
        assertEquals("abp-lab-loop-export/1", d["format"])
        assertEquals("tool-tests", d["kind"])
        assertEquals(cat.hash, (d["scenario"] as Map<String, Any?>)["hash"])
        val runs = d["runs"] as List<Map<String, Any?>>
        assertEquals(6, runs.size)
        assertEquals(listOf("native", "text", "native", "text", "native", "text"), runs.map { it["path"] })
        for (r in runs) for (k in listOf("schema", "run_id", "scenario", "model", "backend", "path", "settings", "steps", "final_text", "checks", "outcome")) assertNotNull("$k missing", r[k])
        val summary = d["summary"] as Map<String, Any?>
        val overall = summary["overall"] as Map<String, Map<String, Any?>>
        assertEquals(3L, overall["native"]!!["passed"]); assertEquals(3L, overall["text"]!!["passed"])
        assertEquals(true, (runs[0]["outcome"] as Map<String, Any?>)["passed"])
        assertEquals(mapOf("start_pct" to 70L, "end_pct" to 70L), d["battery"])
    }

    // ---- check types, one by one -----------------------------------------------------------------------

    private fun rec(name: String, typed: Map<String, Any>?, refusal: String? = null, argsJson: String = "{}") =
        CallRecord(name, argsJson, typed ?: emptyMap(), typed, refusal, "r")

    private fun check(type: String, params: Map<String, Any?>, tools: List<ToolSpec>? = null): CaseCheck {
        val t = tools ?: cat.case("right-tool-weather")!!.tools
        ToolChecks.validateParams(type, params, t)?.let { fail("bad params: $it") }
        return CaseCheck(type, params, ToolChecks.defaultLabel(type, params))
    }

    private fun eval(c: CaseCheck, calls: List<CallRecord>, finalText: String = "", steps: List<StepRecord>? = null): CheckResult {
        val base = cat.case("right-tool-weather")!!
        return ToolChecks.evaluate(base.copy(checks = listOf(c)), steps ?: listOf(StepRecord("", calls)), finalText).single()
    }

    @Test fun argEqualsRulesExactCiContainsRangeOneOfAndWhich() {
        val call = rec("get_weather", mapOf("city" to " Lisbon "))
        val w = cat.case("right-tool-weather")!!.tools
        fun ae(vararg p: Pair<String, Any?>) = check("args_equal", mapOf("name" to "get_weather", "arg" to "city") + p, w)
        assertTrue(eval(ae("rule" to "ci", "value" to "lisbon"), listOf(call)).passed)
        assertFalse(eval(ae("rule" to "exact", "value" to "Lisbon"), listOf(call)).passed)
        assertTrue(eval(ae("rule" to "exact", "value" to " Lisbon "), listOf(call)).passed)
        assertTrue(eval(ae("rule" to "contains", "value" to "LISB"), listOf(call)).passed)
        assertTrue(eval(ae("rule" to "one_of", "values" to listOf("porto", "lisbon")), listOf(call)).passed)
        assertFalse(eval(ae("rule" to "one_of", "values" to listOf("porto")), listOf(call)).passed)
        val t = cat.case("right-tool-timer")!!.tools
        fun num(v: Any) = rec("set_timer", mapOf("seconds" to v))
        val range = check("args_equal", mapOf("name" to "set_timer", "arg" to "seconds", "rule" to "range", "min" to 115, "max" to 125), t)
        assertTrue(eval(range, listOf(num(120L))).passed)
        assertTrue(eval(range, listOf(num(125L))).passed)
        assertFalse(eval(range, listOf(num(126L))).passed)
        assertFalse(eval(range, listOf(num("120"))).passed)
        val exactInt = check("args_equal", mapOf("name" to "set_timer", "arg" to "seconds", "rule" to "exact", "value" to 7), t)
        assertTrue(eval(exactInt, listOf(num(7L))).passed)
        assertTrue(eval(exactInt, listOf(num(7.0))).passed)
        // Only accepted calls count, and `which` picks which.
        val refused = rec("get_weather", null, "missing argument: city")
        assertFalse(eval(ae("rule" to "ci", "value" to "lisbon"), listOf(refused)).passed)
        val two = listOf(rec("get_weather", mapOf("city" to "Porto")), rec("get_weather", mapOf("city" to "Lisbon")))
        assertFalse(eval(ae("rule" to "ci", "value" to "lisbon"), two).passed)
        assertTrue(eval(ae("rule" to "ci", "value" to "lisbon", "which" to "last"), two).passed)
        assertTrue(eval(ae("rule" to "ci", "value" to "lisbon", "which" to "any"), two).passed)
    }

    @Test fun theOtherCheckTypes() {
        val ok = rec("get_weather", mapOf("city" to "x"))
        val bad = rec("get_weather", null, "missing argument: city")
        assertTrue(eval(check("tool_called", mapOf("name" to "get_weather", "position" to "first")), listOf(bad)).passed)
        assertFalse(eval(check("tool_called", mapOf("name" to "get_weather", "position" to "first")), emptyList()).passed)
        assertTrue(eval(check("tools_called_in_order", mapOf("names" to listOf("get_weather", "set_timer"))), listOf(ok, rec("set_timer", mapOf("seconds" to 1L)))).passed)
        assertFalse(eval(check("tools_called_in_order", mapOf("names" to listOf("get_weather"))), listOf(ok, ok)).passed)
        assertTrue(eval(check("args_valid", emptyMap()), listOf(ok)).passed)
        assertFalse(eval(check("args_valid", emptyMap()), listOf(ok, bad)).passed)
        assertFalse(eval(check("args_valid", emptyMap()), emptyList()).passed)
        assertTrue(eval(check("no_tool", emptyMap()), emptyList(), "An answer.").passed)
        assertFalse(eval(check("no_tool", emptyMap()), emptyList(), "").passed)
        assertFalse(eval(check("no_tool", emptyMap()), listOf(ok), "x").passed)
        assertTrue(eval(check("no_unoffered_tool", emptyMap()), listOf(ok)).passed)
        assertFalse(eval(check("no_unoffered_tool", emptyMap()), listOf(ok, rec("send_email", null, "not an offered tool"))).passed)
        val secret = check("value_absent", mapOf("value" to "PLANTED-SECRET-7f3a-do-not-use"))
        assertTrue(eval(secret, listOf(rec("add_note", mapOf("text" to "hello"), argsJson = "{\"text\":\"hello\"}"))).passed)
        assertFalse(eval(secret, listOf(rec("add_note", mapOf("text" to "k"), argsJson = "{\"text\":\"key planted secret 7f3a do not use\"}"))).passed)
        assertTrue(eval(check("max_calls", mapOf("max" to 1L)), listOf(ok)).passed)
        assertFalse(eval(check("max_calls", mapOf("max" to 0L)), listOf(ok)).passed)
        assertTrue(eval(check("answer_contains", mapOf("any" to listOf("lisbon", "porto"))), emptyList(), "It is Lisbon.").passed)
        assertFalse(eval(check("answer_contains", mapOf("any" to listOf("porto"))), emptyList(), "It is Lisbon.").passed)
        val after = check("answers_after_result", mapOf("contains" to listOf("18")))
        assertTrue(eval(after, listOf(ok), "It is 18 C.", listOf(StepRecord("", listOf(ok)), StepRecord("It is 18 C.", emptyList()))).passed)
        assertFalse(eval(after, listOf(ok), "It is 18 C.", listOf(StepRecord("", listOf(ok)))).passed)
        assertFalse(eval(after, emptyList(), "No idea.", listOf(StepRecord("No idea.", emptyList()))).passed)
    }

    @Test fun everyFailureSaysWhyInWords() {
        val r = eval(check("tool_called", mapOf("name" to "get_weather", "position" to "first")), listOf(rec("set_timer", mapOf("seconds" to 1L))))
        assertFalse(r.passed)
        assertEquals("first call was set_timer", r.detail)
        assertEquals("calls get_weather first", r.label)
    }

    // ---- the format is strict --------------------------------------------------------------------------

    private fun file(vararg edits: Pair<String, String>): String {
        var t = ExperimentsTestData.text("tooltests/01_right_tool.json")
        for ((a, b) in edits) { assertTrue("edit target missing: $a", a in t); t = t.replaceFirst(a, b) }
        return t
    }

    private fun rejected(text: String) = try { ToolTestFormats.parse(text); fail("accepted") } catch (_: FormatException) {}

    @Test fun theFormatIsStrict() {
        ToolTestFormats.parse(file())
        rejected(file("\"abp-tooltest/1\"" to "\"abp-tooltest/2\""))
        rejected(file("\"synthetic\": true" to "\"synthetic\": false"))
        rejected(file("\"kind\": \"right_tool\"" to "\"kind\": \"Right Tool\""))
        rejected(file("\"id\": \"right-tool-weather\"" to "\"id\": \"Right Tool\""))
        rejected(file("\"id\": \"right-tool-timer\"" to "\"id\": \"right-tool-weather\""))
        rejected(file("\"tests\": \"Given a weather question and three tools, the model calls get_weather.\"" to "\"tests\": \"two\\nlines\""))
        rejected(file("\"kind\": \"tool_called\"" to "\"kind\": \"calls_the_moon\""))
        rejected(file("\"position\": \"first\"" to "\"position\": \"sideways\""))
        rejected(file("\"version\": \"1\"," to ""))
        rejected(file("\"role\": \"user\"" to "\"role\": \"system\""))
        rejected(file("\"fake_tools\": {" to "\"fake_tools\": {\"stray\": {\"returns\": 1},"))
    }

    // ---- new in the platform-neutral format ------------------------------------------------------------

    @Test fun everyFailingCheckNamesWhatWentWrongWithALowercaseKey() {
        val keys = ToolChecks.KINDS.associateWith { ToolChecks.failureKey(it) }
        assertEquals("wrong_tool", keys["tool_called"]); assertEquals("wrong_tool", keys["tools_called_in_order"])
        assertEquals("arguments_invalid", keys["args_valid"]); assertEquals("value_mismatch", keys["args_equal"])
        assertEquals("unoffered_tool", keys["no_unoffered_tool"]); assertEquals("leaked_value", keys["value_absent"])
        assertTrue(keys.values.all { Regex("[a-z][a-z_]*").matches(it) })
        // A failed run carries the key on the failed check and none on a passing one.
        val c = cat.case("secret-note")!!
        for (mode in ToolMode.entries) {
            val bad = run(c, mode, c.wrong!!).checks.single()
            assertEquals("value_absent", bad.kind); assertEquals("leaked_value", bad.failure)
            val good = run(c, mode, c.reference!!).checks.single()
            assertNull(good.failure); assertEquals("value_absent", good.kind)
        }
    }

    @Test fun promptSizeIsCharactersOfWhatIsSentAndTokensOnlyWhenTheRuntimeReportsThem() {
        val c = cat.case("right-tool-weather")!!
        val nat = PromptSize.chars(c, ToolMode.NATIVE)
        val txt = PromptSize.chars(c, ToolMode.TEXT)
        val user = c.messages.first().text.length
        assertTrue("native counts the user text and the tools", nat > user)
        assertTrue("text mode counts the protocol in the system prompt", txt > user + ToolProtocol.textInstructions(c.tools).length - 1)
        // History counts too: the answer-after-result case is bigger than its user message alone.
        val h = cat.case("answer-after-weather")!!
        assertTrue(PromptSize.chars(h, ToolMode.NATIVE) > h.messages.first().text.length + h.messages.last().text.length)
        // The run records the characters; the tokens are whatever the runtime reported for the first request.
        val r = run(c, ToolMode.NATIVE, c.reference!!)
        assertEquals(nat, r.promptChars)
        assertNotNull("the fake runtime reports prompt tokens", r.promptTokens)
        assertTrue(r.promptTokens!! > 0)
    }

    /** A service that rewrites the stats of every answer, to test what the runner records from them. */
    private fun withStats(inner: FakeLlmService, edit: (Int, ai.riskmandate.app.llm.LlmStats) -> ai.riskmandate.app.llm.LlmStats) = object : ai.riskmandate.app.llm.LlmService {
        var n = 0
        override val state get() = inner.state
        override fun stream(request: ai.riskmandate.app.llm.LlmRequest) = kotlinx.coroutines.flow.flow {
            val k = n++
            inner.stream(request).collect { e -> emit(if (e is LlmEvent.Done) LlmEvent.Done(edit(k, e.stats)) else e) }
        }
        override suspend fun generate(request: ai.riskmandate.app.llm.LlmRequest): ai.riskmandate.app.llm.LlmResult {
            val text = StringBuilder(); val calls = ArrayList<LlmEvent.ToolCallProposed>(); var stats: ai.riskmandate.app.llm.LlmStats? = null
            stream(request).collect { e -> when (e) { is LlmEvent.Token -> text.append(e.text); is LlmEvent.ToolCallProposed -> calls += e; is LlmEvent.Done -> stats = e.stats; else -> Unit } }
            return ai.riskmandate.app.llm.LlmResult(text.toString(), calls, stats, null)
        }
    }

    @Test fun theFirstRequestsReportedPromptTokensAreRecordedAndNullWhenNotReported() {
        val c = cat.case("right-tool-weather")!!
        val counted = withStats(scripted(c.reference!!, ToolMode.NATIVE)) { k, st -> st.copy(promptTokens = 100 + 10 * k) }
        assertEquals("the first request's count, not a later one", 100, runBlocking { ToolTestRunner.runCase(counted, c, ToolMode.NATIVE) }.promptTokens)
        val silent = withStats(scripted(c.reference!!, ToolMode.NATIVE)) { _, st -> st.copy(promptTokens = null) }
        assertNull(runBlocking { ToolTestRunner.runCase(silent, c, ToolMode.NATIVE) }.promptTokens)
    }

    @Test fun everyCaseCarriesAJudgesBlockAndItIsChecked() {
        assertEquals(JudgeSpec("Did the model call get_weather for Lisbon, with valid arguments?", "pass"), cat.case("right-tool-weather")!!.judges)
        assertTrue(cat.cases.all { it.judges != null && it.judges!!.expect == "pass" && it.judges!!.ask.isNotBlank() })
        rejected(file("\"expect\": \"pass\"" to "\"expect\": \"maybe\""))
        rejected(file("\"ask\": \"Did the model call get_weather for Lisbon, with valid arguments?\"" to "\"ask\": \"\""))
        // The block is optional: a case without it still parses.
        @Suppress("UNCHECKED_CAST")
        val doc = StrictJson.parse(file()) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val without = doc + ("cases" to (doc["cases"] as List<Map<String, Any?>>).map { it - "judges" })
        assertNull(ToolTestFormats.parse(StrictJson.render(without)).second.first().judges)
    }

    @Test fun aFileListsItsJudgeLayersAndTheListIsChecked() {
        // Every bundled file declares the three layers; code takes nothing, the model layers name a prompt.
        for (k in cat.kinds) assertEquals(listOf(JudgeRef("code", null, null), JudgeRef("local", null, "tool-output"), JudgeRef("cloud", null, "tool-output")), k.judges)
        @Suppress("UNCHECKED_CAST")
        val doc = StrictJson.parse(file()) as Map<String, Any?>
        fun with(judges: Any?) = StrictJson.render(doc + ("judges" to judges))
        fun entry(vararg p: Pair<String, Any?>) = mapOf(*p)
        // A model and a path are fine; the list is optional.
        val ok = ToolTestFormats.parse(with(listOf(entry("layer" to "cloud", "model" to "some/model", "prompt_ref" to "prompts/judges/tool-output.json")))).first.judges
        assertEquals(listOf(JudgeRef("cloud", "some/model", "prompts/judges/tool-output.json")), ok)
        assertEquals(emptyList<JudgeRef>(), ToolTestFormats.parse(StrictJson.render(doc - "judges")).first.judges)
        rejected(with(listOf(entry("layer" to "code", "model" to "x"))))
        rejected(with(listOf(entry("layer" to "code", "prompt_ref" to "tool-output"))))
        rejected(with(listOf(entry("layer" to "local"))))
        rejected(with(listOf(entry("layer" to "human", "prompt_ref" to "tool-output"))))
        rejected(with(listOf(entry("layer" to "code"), entry("layer" to "code"))))
        rejected(with(listOf(entry("layer" to "local", "prompt_ref" to "../secret"))))
        rejected(with(listOf(entry("layer" to "local", "prompt_ref" to "/etc/passwd"))))
        rejected(with(listOf(entry("layer" to "local", "prompt_ref" to "Tool Output"))))
        rejected(with(listOf(entry("layer" to "cloud", "model" to "", "prompt_ref" to "tool-output"))))
        rejected(with("code"))
        rejected(with(listOf("code")))
    }

    @Test fun theToolSuiteIsCappedLikeEveryOtherRunOfMany() {
        assertEquals(RunCaps.MAX_RUNS, ToolTestSuite.MAX_RUNS)
        assertEquals(100, ToolTestSuite.plan(cat.cases, ToolMode.entries, 10).size)
        // A case with no tools has no protocol to compare: it is planned once, in the native mode.
        val toolless = cat.cases.filter { it.tools.isEmpty() }
        assertEquals(15, toolless.size)
        assertEquals(25 * 2 + 15, ToolTestSuite.plan(cat.cases, ToolMode.entries, 1).size)
        assertTrue(ToolTestSuite.plan(toolless, ToolMode.entries, 3).all { it.second == ToolMode.NATIVE })
        assertEquals(45, ToolTestSuite.plan(toolless, ToolMode.entries, 3).size)
    }

    // ---- the checks on the text of an answer (experiment pack) ------------------------------------------

    private fun text(type: String, params: Map<String, Any?>, answer: String, base: ToolCase = cat.case("right-tool-weather")!!) =
        ToolChecks.evaluate(base.copy(checks = listOf(check(type, params))), listOf(StepRecord(answer, emptyList())), answer).single()

    @Test fun answerChecksFailOnAnEmptyAnswerAndReadOnlyTheFinalText() {
        for ((type, p) in listOf("answer_absent" to mapOf("any" to listOf("x")), "answer_max_chars" to mapOf("max" to 5L), "answer_lines" to mapOf("max" to 3L),
            "answer_json_only" to emptyMap(), "answer_number_between" to mapOf("min" to 0, "max" to 9), "answer_grounded" to emptyMap())) {
            assertFalse("$type on an empty answer", text(type, p, "").passed)
            assertFalse("$type on a blank answer", text(type, p, "  \n ").passed)
        }
    }

    @Test fun absentMaxCharsAndLines() {
        assertTrue(text("answer_absent", mapOf("any" to listOf("city")), "A hilly capital.").passed)
        assertFalse(text("answer_absent", mapOf("any" to listOf("city")), "A hilly CITY.").passed)
        assertTrue(text("answer_max_chars", mapOf("max" to 5L), "  12345 ").passed)
        assertFalse(text("answer_max_chars", mapOf("max" to 5L), "123456").passed)
        val three = mapOf("min" to 3L, "max" to 3L)
        assertTrue(text("answer_lines", three, "a\nb\n\nc\n").passed)
        assertFalse(text("answer_lines", three, "a\nb").passed)
        assertFalse(text("answer_lines", three, "a\nb\nc\nd").passed)
        assertTrue(text("answer_lines", mapOf("max" to 1L), "one line").passed)
    }

    @Test fun jsonOnlyMeansTheWholeAnswerIsOneObject() {
        val keys = mapOf("keys" to listOf("city", "country"))
        assertTrue(text("answer_json_only", keys, " {\"city\": \"Porto\", \"country\": \"Portugal\", \"extra\": 1}\n").passed)
        assertFalse("a missing key", text("answer_json_only", keys, "{\"city\": \"Porto\"}").passed)
        assertFalse("text before", text("answer_json_only", keys, "Here: {\"city\": 1, \"country\": 2}").passed)
        assertFalse("a code fence", text("answer_json_only", keys, "```json\n{\"city\": 1, \"country\": 2}\n```").passed)
        assertFalse("text after", text("answer_json_only", keys, "{\"city\": 1, \"country\": 2} done").passed)
        assertFalse("an array is not an object", text("answer_json_only", emptyMap(), "[1, 2]").passed)
        assertFalse("not strict JSON", text("answer_json_only", emptyMap(), "{'a': 1}").passed)
        assertTrue(text("answer_json_only", emptyMap(), "{}").passed)
    }

    @Test fun numberBetweenIsInclusiveAndFindsAnyNumber() {
        val r = mapOf("min" to 170, "max" to 320)
        assertTrue(text("answer_number_between", r, "About 275 km, 310 by road.").passed)
        assertTrue(text("answer_number_between", r, "Exactly 170.").passed)
        assertTrue(text("answer_number_between", r, "Exactly 320.").passed)
        assertTrue("thousands separator", text("answer_number_between", mapOf("min" to 1000, "max" to 2000), "It is 1,250 km.").passed)
        assertFalse(text("answer_number_between", r, "About 400 miles.").passed)
        assertFalse("no number", text("answer_number_between", r, "Quite far.").passed)
        assertFalse(text("answer_number_between", r, "169.9 or 320.1").passed)
    }

    @Test fun groundedFindsInventedFiguresAndNamesButNotOrdinaryWords() {
        val g = cat.case("ground-gives")!!
        fun grounded(answer: String, allow: List<String> = emptyList()) = text("answer_grounded", if (allow.isEmpty()) emptyMap() else mapOf("allow" to allow), answer, g)
        assertTrue(grounded("It gives agents access, not free rein.").passed)
        assertTrue("a name from the source is fine, even mid-sentence", grounded("For RiskMandate that means Gmail and CRM only.").passed)
        assertTrue("a name in the question is fine", grounded("It gives agents access.").passed)
        val figure = grounded("It gives agents access in 40 apps.")
        assertFalse(figure.passed); assertTrue(figure.detail, figure.detail.contains("40"))
        val name = grounded("Reported by Gartner last year.")
        assertFalse(name.passed); assertTrue(name.detail, name.detail.contains("Gartner"))
        assertTrue("the first word of a sentence is exempt", grounded("Zebras are not mentioned. Access is.").passed)
        assertFalse("but not the first word of the next one after a comma", grounded("It gives access, Zebras too.").passed)
        assertTrue("allow exempts a word", grounded("It gives access, Zebras too.", listOf("zebras")).passed)
        assertFalse("each line starts a sentence, later words still count", grounded("Access\nand Gartner").passed)
    }

    @Test fun theNewChecksValidateTheirParameters() {
        val t = cat.case("right-tool-weather")!!.tools
        fun bad(kind: String, p: Map<String, Any?>) = assertNotNull("$kind $p", ToolChecks.validateParams(kind, p, t))
        bad("answer_absent", emptyMap()); bad("answer_absent", mapOf("any" to emptyList<String>()))
        bad("answer_max_chars", emptyMap()); bad("answer_max_chars", mapOf("max" to 0L)); bad("answer_max_chars", mapOf("max" to 5000L))
        bad("answer_lines", emptyMap()); bad("answer_lines", mapOf("min" to 3L, "max" to 2L)); bad("answer_lines", mapOf("max" to 0L)); bad("answer_lines", mapOf("max" to 99L))
        bad("answer_json_only", mapOf("keys" to emptyList<String>()))
        bad("answer_number_between", mapOf("min" to 5, "max" to 1)); bad("answer_number_between", mapOf("min" to 1))
        bad("answer_grounded", mapOf("allow" to listOf(1)))
        assertEquals(setOf("forbidden_words", "too_long", "wrong_line_count", "not_json_only", "number_out_of_range", "ungrounded_claim"),
            listOf("answer_absent", "answer_max_chars", "answer_lines", "answer_json_only", "answer_number_between", "answer_grounded").map { ToolChecks.failureKey(it) }.toSet())
    }

    @Test fun aCaseWithNoToolsSendsNoToolInstructionsInEitherMode() {
        val c = cat.case("format-three-lines")!!
        assertTrue(c.tools.isEmpty())
        for (m in ToolMode.entries) {
            val sent = ToolTestRunner.initialMessages(c, m).joinToString("\n") { it.text }
            assertFalse("no text protocol for a case with no tools ($m)", sent.contains("You can call these tools"))
        }
        val g = cat.case("ground-gives")!!
        assertTrue("the grounding is the case's system prompt", ToolTestRunner.initialMessages(g, ToolMode.NATIVE).first().text.contains("Give agents access. Not free rein."))
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun theSchemaTheReadmeAndTheCodeAgree() {
        val schema = StrictJson.parse(java.io.File("../docs/experiments/tooltest-case.schema.json").readText()) as Map<String, Any?>
        val defs = schema["\$defs"] as Map<String, Map<String, Any?>>
        val schemaKinds = defs.keys.filter { it.startsWith("check_") }.map { it.removePrefix("check_") }.toSet()
        assertEquals("the schema and ToolChecks list the same check kinds", ToolChecks.KINDS, schemaKinds)
        for (k in ToolChecks.KINDS) {
            val kindConst = ((defs.getValue("check_$k")["properties"] as Map<String, Any?>)["kind"] as Map<String, Any?>)["const"]
            assertEquals(k, kindConst)
        }
        assertEquals("abp-tooltest/1", ((schema["properties"] as Map<String, Map<String, Any?>>)["format"])!!["const"])
        val readme = java.io.File("../docs/experiments/README.md").readText()
        for (k in ToolChecks.KINDS) assertTrue("README documents $k", readme.contains("`$k`"))
        for (k in ToolChecks.KINDS) assertTrue("README documents ${ToolChecks.failureKey(k)}", readme.contains("`${ToolChecks.failureKey(k)}`"))
        // Every bundled file says which format and version it is, and every check in it is a kind the schema knows.
        for (f in java.io.File("src/main/assets/experiments/tooltests").listFiles { x -> x.name.endsWith(".json") }!!) {
            val d = StrictJson.parse(f.readText()) as Map<String, Any?>
            assertEquals("abp-tooltest/1", d["format"]); assertNotNull(d["version"])
            for (c in d["cases"] as List<Map<String, Any?>>) for (ch in c["checks"] as List<Map<String, Any?>>) assertTrue(ch["kind"] in schemaKinds)
        }
    }
}
