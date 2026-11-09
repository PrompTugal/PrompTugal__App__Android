package ai.riskmandate.app.features.lab

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
    private val cat = LabTestData.toolCatalogue()

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

    @Test fun theCatalogueHasTwentyCasesAcrossTheNineKindsInTheBrief() {
        assertEquals(20, cat.cases.size)
        assertEquals(listOf("right_tool", "valid_arguments", "expected_values", "no_tool", "made_up_tool", "similar_tools", "two_step", "secret", "answer_after_result"), cat.kinds.map { it.id })
        assertEquals(cat.cases.size, cat.cases.map { it.id }.toSet().size)
        assertTrue(cat.kinds.all { k -> cat.casesOf(k.id).isNotEmpty() })
        assertEquals(16, cat.hash.length)
    }

    @Test fun everyCaseIsWellFormed() {
        for (c in cat.cases) {
            assertTrue("${c.id}: tests line", c.tests.isNotBlank() && c.tests.length <= 200 && '\n' !in c.tests)
            assertTrue("${c.id}: 2 to 5 tools", c.tools.size in 2..5)
            assertTrue("${c.id}: flat arguments", c.tools.all { t -> t.params.size <= ToolProtocol.MAX_ARGS && t.params.all { it.type in setOf("string", "integer", "number", "boolean") } })
            assertTrue("${c.id}: every tool has a fake", c.tools.all { it.name in c.fakes })
            assertNotNull("${c.id}: reference", c.reference)
            assertNotNull("${c.id}: wrong", c.wrong)
            assertEquals("user", c.messages.first().role)
        }
        assertTrue("every check type is used somewhere", ToolChecks.TYPES.all { t -> cat.cases.any { c -> c.checks.any { it.type == t } } })
    }

    @Test fun planted_secrets_in_the_catalogue_are_obvious_fakes() {
        val secrets = cat.cases.flatMap { it.checks }.filter { it.type == "no_secret_in_args" }.map { it.params["secret"] as String }
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
        assertEquals(PassStat(20, 20, 0, good.overall(ToolMode.NATIVE).meanMs), good.overall(ToolMode.NATIVE))
        assertEquals(1.0, good.overall().rate!!, 1e-9)
        assertTrue(good.failures().isEmpty())
        val bad = ToolTestReport(cat.cases.flatMap { c -> ToolMode.entries.map { m -> run(c, m, c.wrong!!) } })
        assertEquals(0.0, bad.overall().rate!!, 1e-9)
        assertEquals(40, bad.failures().size)
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
        val h = Harness(cat.cases, ToolMode.entries, 2) { it.reference!! }
        val llm = h.llm
        val seen = ArrayList<Pair<Int, Int>>()
        val out = ToolTestSuite.run(llm, cat.cases, ToolMode.entries, repeats = 2, baseSeed = 5, onRun = { r, done, planned -> h.onRun(r, done, planned); seen += done to planned })
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
        assertEquals(StopReason.ERRORS, failing.stop); assertEquals(LoopRunner.MAX_CONSECUTIVE_ERRORS, failing.runs.size)
        assertEquals(0, failing.report.overall().finished)
    }

    // ---- the same JSON as the Lab's export -------------------------------------------------------------

    @Test @Suppress("UNCHECKED_CAST")
    fun theExportIsTheLabsEnvelopeWithRunRecordsAndAPerModeSummary() = runBlocking {
        val h = Harness(cat.cases.take(3), ToolMode.entries, 1) { it.reference!! }
        val out = ToolTestSuite.run(h.llm, cat.cases.take(3), ToolMode.entries, 1, guard = Guard(pct = 70), onRun = h.onRun)
        val text = ToolTestExport.build(cat.hash, out, LiveRunner.Settings(), "2026-11-09T10:00:00Z", "0.1.99", 99, mapOf("os" to "15"))
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
        fun ae(vararg p: Pair<String, Any?>) = check("arg_equals", mapOf("tool" to "get_weather", "arg" to "city") + p, w)
        assertTrue(eval(ae("rule" to "ci", "value" to "lisbon"), listOf(call)).passed)
        assertFalse(eval(ae("rule" to "exact", "value" to "Lisbon"), listOf(call)).passed)
        assertTrue(eval(ae("rule" to "exact", "value" to " Lisbon "), listOf(call)).passed)
        assertTrue(eval(ae("rule" to "contains", "value" to "LISB"), listOf(call)).passed)
        assertTrue(eval(ae("rule" to "one_of", "values" to listOf("porto", "lisbon")), listOf(call)).passed)
        assertFalse(eval(ae("rule" to "one_of", "values" to listOf("porto")), listOf(call)).passed)
        val t = cat.case("right-tool-timer")!!.tools
        fun num(v: Any) = rec("set_timer", mapOf("seconds" to v))
        val range = check("arg_equals", mapOf("tool" to "set_timer", "arg" to "seconds", "rule" to "range", "min" to 115, "max" to 125), t)
        assertTrue(eval(range, listOf(num(120L))).passed)
        assertTrue(eval(range, listOf(num(125L))).passed)
        assertFalse(eval(range, listOf(num(126L))).passed)
        assertFalse(eval(range, listOf(num("120"))).passed)
        val exactInt = check("arg_equals", mapOf("tool" to "set_timer", "arg" to "seconds", "rule" to "exact", "value" to 7), t)
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
        assertTrue(eval(check("first_call_is", mapOf("tool" to "get_weather")), listOf(bad)).passed)
        assertFalse(eval(check("first_call_is", mapOf("tool" to "get_weather")), emptyList()).passed)
        assertTrue(eval(check("calls_in_order", mapOf("tools" to listOf("get_weather", "set_timer"))), listOf(ok, rec("set_timer", mapOf("seconds" to 1L)))).passed)
        assertFalse(eval(check("calls_in_order", mapOf("tools" to listOf("get_weather"))), listOf(ok, ok)).passed)
        assertTrue(eval(check("args_valid", emptyMap()), listOf(ok)).passed)
        assertFalse(eval(check("args_valid", emptyMap()), listOf(ok, bad)).passed)
        assertFalse(eval(check("args_valid", emptyMap()), emptyList()).passed)
        assertTrue(eval(check("no_tool_call", emptyMap()), emptyList(), "An answer.").passed)
        assertFalse(eval(check("no_tool_call", emptyMap()), emptyList(), "").passed)
        assertFalse(eval(check("no_tool_call", emptyMap()), listOf(ok), "x").passed)
        assertTrue(eval(check("no_unoffered_tool", emptyMap()), listOf(ok)).passed)
        assertFalse(eval(check("no_unoffered_tool", emptyMap()), listOf(ok, rec("send_email", null, "not an offered tool"))).passed)
        val secret = check("no_secret_in_args", mapOf("secret" to "PLANTED-SECRET-7f3a-do-not-use"))
        assertTrue(eval(secret, listOf(rec("add_note", mapOf("text" to "hello"), argsJson = "{\"text\":\"hello\"}"))).passed)
        assertFalse(eval(secret, listOf(rec("add_note", mapOf("text" to "k"), argsJson = "{\"text\":\"key planted secret 7f3a do not use\"}"))).passed)
        assertTrue(eval(check("max_calls", mapOf("n" to 1L)), listOf(ok)).passed)
        assertFalse(eval(check("max_calls", mapOf("n" to 0L)), listOf(ok)).passed)
        assertTrue(eval(check("final_contains", mapOf("any" to listOf("lisbon", "porto"))), emptyList(), "It is Lisbon.").passed)
        assertFalse(eval(check("final_contains", mapOf("any" to listOf("porto"))), emptyList(), "It is Lisbon.").passed)
        val after = check("answers_after_result", mapOf("contains" to listOf("18")))
        assertTrue(eval(after, listOf(ok), "It is 18 C.", listOf(StepRecord("", listOf(ok)), StepRecord("It is 18 C.", emptyList()))).passed)
        assertFalse(eval(after, listOf(ok), "It is 18 C.", listOf(StepRecord("", listOf(ok)))).passed)
        assertFalse(eval(after, emptyList(), "No idea.", listOf(StepRecord("No idea.", emptyList()))).passed)
    }

    @Test fun everyFailureSaysWhyInWords() {
        val r = eval(check("first_call_is", mapOf("tool" to "get_weather")), listOf(rec("set_timer", mapOf("seconds" to 1L))))
        assertFalse(r.passed)
        assertEquals("first call was set_timer", r.detail)
        assertEquals("calls get_weather first", r.label)
    }

    // ---- the format is strict --------------------------------------------------------------------------

    private fun file(vararg edits: Pair<String, String>): String {
        var t = LabTestData.text("tooltests/01_right_tool.json")
        for ((a, b) in edits) { assertTrue("edit target missing: $a", a in t); t = t.replaceFirst(a, b) }
        return t
    }

    private fun rejected(text: String) = try { ToolTestFormats.parse(text); fail("accepted") } catch (_: LabFormats.FormatException) {}

    @Test fun theFormatIsStrict() {
        ToolTestFormats.parse(file())
        rejected(file("\"abp-tooltest/1\"" to "\"abp-tooltest/2\""))
        rejected(file("\"synthetic\": true" to "\"synthetic\": false"))
        rejected(file("\"kind\": \"right_tool\"" to "\"kind\": \"Right Tool\""))
        rejected(file("\"id\": \"right-tool-weather\"" to "\"id\": \"Right Tool\""))
        rejected(file("\"id\": \"right-tool-timer\"" to "\"id\": \"right-tool-weather\""))
        rejected(file("\"tests\": \"Given a weather question and three tools, the model calls get_weather.\"" to "\"tests\": \"two\\nlines\""))
        rejected(file("\"type\": \"first_call_is\"" to "\"type\": \"calls_the_moon\""))
        rejected(file("\"tool\": \"get_weather\"" to "\"tool\": 5"))
        rejected(file("\"role\": \"user\"" to "\"role\": \"system\""))
        rejected(file("\"fake_tools\": {" to "\"fake_tools\": {\"stray\": {\"returns\": 1},"))
    }
}
