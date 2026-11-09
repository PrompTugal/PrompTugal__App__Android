package ai.riskmandate.app.features.lab

import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmError
import ai.riskmandate.app.llm.LlmEvent
import ai.riskmandate.app.llm.LlmRequest
import ai.riskmandate.app.llm.LlmRole
import ai.riskmandate.app.llm.StrictJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopTest {
    private val data = LabTestData.load()
    private val planted = data.scenario("sample-planted")!!
    private fun call(name: String, args: String) = LlmEvent.ToolCallProposed(name, args)

    private fun obedient(req: LlmRequest): FakeReply {
        val last = req.messages.last()
        return when {
            last.role == LlmRole.USER -> FakeReply("", listOf(call("read_note", "{\"name\":\"quarterly\"}")))
            last.role == LlmRole.TOOL && last.toolName == "read_note" -> FakeReply("", listOf(call("share_document", "{\"to\":\"x@example.test\",\"text\":\"t\"}")))
            else -> FakeReply("Done.")
        }
    }
    private fun honest(req: LlmRequest): FakeReply =
        if (req.messages.last().role == LlmRole.USER) FakeReply("", listOf(call("read_note", "{\"name\":\"quarterly\"}"))) else FakeReply("Revenue was steady.")

    private class Guard(var pct: Int? = 80, var charging: Boolean = false, var hot: Boolean = false) : DeviceGuard {
        override fun batteryPct() = pct
        override fun charging() = charging
        override fun hot() = hot
    }

    private fun score(rawFailed: Boolean, attempted: Boolean, caught: Boolean, ms: Long = 1000) = RunScore(rawFailed, attempted, caught, false, ms)

    @Test fun countersCountOnlyFinishedRunsAndNeverRoundUpTheHeadline() {
        var c = LoopCounters(planned = 20)
        assertNull(c.oneInX); assertEquals(null, c.averageMs); assertTrue(c.smallSample)
        repeat(7) { c = c.withRun(score(true, true, true), 70) }
        repeat(13) { c = c.withRun(score(false, false, false), 69) }
        c = c.withFailedRun()
        assertEquals(20, c.finished); assertEquals(1, c.notFinished)
        assertEquals(7, c.rawFailures); assertEquals(7, c.attempts); assertEquals(7, c.caught); assertEquals(0, c.escaped)
        assertEquals(20.0 / 7, c.oneInX!!, 1e-9)
        assertFalse(c.smallSample)
        assertEquals(1000L, c.averageMs)
        assertEquals(69, c.batteryEndPct)
    }

    @Test fun neverFailingHasNoOneInX() {
        val c = LoopCounters(20).withRun(score(false, false, false), null).withRun(score(false, true, true), null)
        assertNull(c.oneInX)
        assertEquals(1, c.caught)
        assertEquals(1, c.attempts)
    }

    @Test fun batteryUsedIsNeverNegativeAndNeedsBothReadings() {
        assertEquals(5, LoopCounters(5, batteryStartPct = 80, batteryEndPct = 75).batteryUsedPct)
        assertEquals(0, LoopCounters(5, batteryStartPct = 80, batteryEndPct = 85).batteryUsedPct)
        assertNull(LoopCounters(5, batteryStartPct = null, batteryEndPct = 75).batteryUsedPct)
    }

    @Test fun aLoopCountsRawFailuresAndAbpCatchesAndNothingEscapes() = runBlocking {
        val llm = FakeLlmService(reply = ::obedient)
        val seen = mutableListOf<LoopCounters>()
        val out = LoopRunner.run(llm, planted, runs = 5, onRun = { _, c -> seen += c })
        assertEquals(StopReason.DONE, out.stop)
        assertEquals(5, out.counters.finished)
        assertEquals(5, out.counters.rawFailures)
        assertEquals(5, out.counters.caught)
        assertEquals(0, out.counters.escaped)
        assertEquals(1.0, out.counters.oneInX!!, 1e-9)
        assertEquals((1..5).toList(), seen.map { it.finished })
        assertEquals(30, llm.requests.size)
    }

    @Test fun anHonestModelNeverFailsAndHasNothingToCatch() = runBlocking {
        val out = LoopRunner.run(FakeLlmService(reply = ::honest), planted, runs = 5)
        assertEquals(0, out.counters.rawFailures); assertEquals(0, out.counters.attempts); assertNull(out.counters.oneInX)
    }

    @Test fun runsAreClampedAndSeedsStepPerRun() = runBlocking {
        val llm = FakeLlmService(reply = ::honest)
        LoopRunner.run(llm, planted, runs = 3, baseSeed = 7)
        val seeds = llm.requests.mapNotNull { it.settings.seed }.distinct().sorted()
        assertEquals(listOf(7, 1007, 2007), seeds)
        val many = FakeLlmService(reply = { FakeReply("ok") })
        val out = LoopRunner.run(many, planted, runs = 10_000, stopRequested = { many.requests.size >= 4 })
        assertTrue("capped at ${LoopRunner.MAX_RUNS}", out.counters.planned == LoopRunner.MAX_RUNS)
        assertEquals(StopReason.STOPPED, out.stop)
    }

    @Test fun aStopRequestEndsTheLoopAfterTheCurrentRun() = runBlocking {
        val llm = FakeLlmService(reply = ::honest)
        var runs = 0
        val out = LoopRunner.run(llm, planted, runs = 20, stopRequested = { runs >= 2 }, onRun = { _, _ -> runs++ })
        assertEquals(2, out.counters.finished)
        assertEquals(StopReason.STOPPED, out.stop)
    }

    @Test fun lowBatteryStopsUnlessChargingAndHeatStopsAlways() = runBlocking {
        val g = Guard(pct = 80)
        var runs = 0
        val out = LoopRunner.run(FakeLlmService(reply = ::honest), planted, runs = 10, guard = g, onRun = { _, _ -> runs++; if (runs == 2) g.pct = LoopRunner.LOW_BATTERY_PCT })
        assertEquals(2, out.counters.finished); assertEquals(StopReason.LOW_BATTERY, out.stop)
        assertEquals(15, out.counters.batteryEndPct); assertEquals(65, out.counters.batteryUsedPct)
        val charging = LoopRunner.run(FakeLlmService(reply = ::honest), planted, runs = 5, guard = Guard(pct = 5, charging = true))
        assertEquals(5, charging.counters.finished)
        val hot = LoopRunner.run(FakeLlmService(reply = ::honest), planted, runs = 5, guard = Guard(hot = true))
        assertEquals(0, hot.counters.finished); assertEquals(StopReason.HOT, hot.stop)
    }

    @Test fun theTotalTimeIsCapped() = runBlocking {
        var t = 0L
        val out = LoopRunner.run(FakeLlmService(reply = ::honest), planted, runs = 10, now = { t += LoopRunner.MAX_TOTAL_MS / 4; t })
        assertEquals(StopReason.TIME_CAP, out.stop)
        assertTrue(out.counters.finished in 1..3)
    }

    @Test fun runsThatDoNotFinishAreTalliedApartAndRepeatedErrorsStopTheLoop() = runBlocking {
        val llm = FakeLlmService(reply = { FakeReply(error = LlmEvent.Error(LlmError.INFERENCE_FAILED, "x")) })
        val out = LoopRunner.run(llm, planted, runs = 20)
        assertEquals(StopReason.ERRORS, out.stop)
        assertEquals(LoopRunner.MAX_CONSECUTIVE_ERRORS, out.counters.notFinished)
        assertEquals(0, out.counters.finished)
        assertNull(out.counters.oneInX)
    }

    @Test fun theExportIsEvalsLoopFormatWithRunRecords() = runBlocking {
        val out = LoopRunner.run(FakeLlmService(reply = ::obedient), planted, runs = 2, baseSeed = 3, guard = Guard(pct = 70))
        val text = LoopExport.build(planted, out, "2026-11-09T05:00:00Z", "0.1.99", 99, device = mapOf("os" to "15"))
        @Suppress("UNCHECKED_CAST")
        val d = StrictJson.parse(text) as Map<String, Any?>
        assertEquals("abp-lab-loop-export/1", d["format"])
        assertEquals(mapOf("id" to planted.id, "version" to planted.version, "hash" to planted.hash), d["scenario"])
        assertEquals(mapOf("os" to "15"), d["device"])
        assertEquals(mapOf("start_pct" to 70L, "end_pct" to 70L), d["battery"])
        @Suppress("UNCHECKED_CAST")
        val runs = d["runs"] as List<Map<String, Any?>>
        assertEquals(listOf("raw", "abp", "raw", "abp"), runs.map { it["path"] })
        for (r in runs) for (k in listOf("schema", "run_id", "scenario", "model", "backend", "path", "settings", "steps", "final_text")) assertNotNull("$k missing", r[k])
        @Suppress("UNCHECKED_CAST")
        val abpRun = runs[1]["outcome"] as Map<String, Any?>
        assertEquals(true, abpRun["caught"]); assertEquals(false, abpRun["failure"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(3L, (runs[0]["settings"] as Map<String, Any?>)["seed"])
        // The export holds the model's tool proposals and the scenario's fixed strings, and no device identifier.
        assertFalse(text.contains("android_id", ignoreCase = true))
    }

    @Test fun countersSurviveTheSavedStateCodecAndHostileTextIsDropped() {
        var c = LoopCounters(planned = 20, batteryStartPct = 80, batteryEndPct = 77)
        c = c.withRun(RunScore(true, true, true, false, 1234), 79).withRun(RunScore(false, false, false, false, 2000), 77).withFailedRun()
        val text = LoopCountersCodec.encode(c, StopReason.INTERRUPTED)
        val (back, stop) = LoopCountersCodec.decode(text)!!
        assertEquals(c, back)
        assertEquals(StopReason.INTERRUPTED, stop)
        assertEquals(LoopCounters(5) to null, LoopCountersCodec.decode(LoopCountersCodec.encode(LoopCounters(5), null)))
        assertNull(LoopCountersCodec.decode(LoopCountersCodec.encode(null, null)))
        for (bad in listOf("", "v2|5|0|-1|-1||", "v1|0|0|-1|-1||", "v1|101|0|-1|-1||", "v1|5|0|-1|-1||11:5", "v1|5|0|-1|-1||2111:5", "v1|5|0|-1|-1||1111:-5",
            "v1|5|0|-1|-1||1111:99999999999", "v1|5|0|-1|-1|NOPE|", "v1|1|0|-1|-1||1111:5;1111:5", "v1|x|0|-1|-1||", "v1|5|0|-1|-1", "garbage", "v1|5|999|-1|-1||")) {
            assertNull("accepted: $bad", LoopCountersCodec.decode(bad))
        }
        // Out-of-range battery readings are dropped rather than trusted.
        assertNull(LoopCountersCodec.decode("v1|5|0|500|-1||")!!.first.batteryStartPct)
    }
}
