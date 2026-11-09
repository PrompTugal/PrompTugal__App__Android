package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.experiments.DeviceGuard
import ai.riskmandate.app.experiments.RunCaps
import ai.riskmandate.app.experiments.StopReason
import ai.riskmandate.app.llm.BackendPreference
import ai.riskmandate.app.llm.DeviceSample
import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmRunner
import ai.riskmandate.app.llm.LlmState
import ai.riskmandate.app.llm.LlmStopReason
import ai.riskmandate.app.llm.MemorySample
import ai.riskmandate.app.llm.RunSettings
import ai.riskmandate.app.llm.Thermal
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Robolectric: the run log entries go through StrictJson only, but tool checks in the runner are kept uniform. */
@RunWith(AndroidJUnit4::class)
class ExperimentsTest {
    private val base = RunSettings(systemPrompt = "Be brief.", userPrompt = "Say hi", maxTokens = 64)
    private fun dev(pct: Int? = 80, charging: Boolean? = false, thermal: Thermal = Thermal.NONE) = DeviceSample(MemorySample(1, 1, 1, 1), pct, charging, thermal)

    @Test fun aSweepGoesRoundByRound() {
        val runs = ExperimentPlan(SweepVariable.TEMPERATURE, listOf("0.2", "1.0"), 3).expand(base).getOrThrow()
        assertEquals(listOf("0.2", "1.0", "0.2", "1.0", "0.2", "1.0"), runs.map { it.first })
        assertEquals(0.2, runs[0].second.sampling.temperature, 0.0)
        assertEquals("Say hi", runs[5].second.userPrompt)
    }

    @Test fun everyVariableAppliesAndBadValuesAreRefused() {
        assertEquals(0.5, ExperimentPlan(SweepVariable.TOP_P, listOf("0.5"), 1).expand(base).getOrThrow()[0].second.sampling.topP, 0.0)
        assertEquals(128, ExperimentPlan(SweepVariable.MAX_TOKENS, listOf("128"), 1).expand(base).getOrThrow()[0].second.maxTokens)
        assertEquals(BackendPreference.CPU, ExperimentPlan(SweepVariable.BACKEND, listOf("cpu"), 1).expand(base).getOrThrow()[0].second.backend)
        val sys = ExperimentPlan(SweepVariable.SYSTEM_PROMPT, listOf("on", "off"), 1).expand(base).getOrThrow()
        assertEquals(listOf("Be brief.", ""), sys.map { it.second.systemPrompt })
        listOf(
            ExperimentPlan(SweepVariable.TEMPERATURE, listOf("3"), 1), ExperimentPlan(SweepVariable.TOP_P, listOf("0"), 1),
            ExperimentPlan(SweepVariable.BACKEND, listOf("npu"), 1),
            ExperimentPlan(SweepVariable.TEMPERATURE, emptyList(), 1), ExperimentPlan(SweepVariable.TEMPERATURE, listOf("1"), 0),
            ExperimentPlan(SweepVariable.TEMPERATURE, (1..11).map { "0.1" }, 10),
        ).forEach { assertTrue(it.toString(), it.expand(base).isFailure) }
        assertTrue("system on needs a system prompt", ExperimentPlan(SweepVariable.SYSTEM_PROMPT, listOf("on"), 1).expand(base.copy(systemPrompt = "")).isFailure)
        assertEquals(listOf("0.2", "0.7", "1.0"), ExperimentPlan.parseValues("0.2, 0.7  1.0,"))
    }

    private class Guard(var pct: Int? = 80, var charging: Boolean = false, var hot: Boolean = false) : DeviceGuard {
        override fun batteryPct() = pct
        override fun charging() = charging
        override fun hot() = hot
    }

    @Test fun theCapsAreExperimentsRunCaps() {
        assertEquals(100, RunCaps.MAX_RUNS)
        assertEquals(30 * 60_000L, RunCaps.MAX_TOTAL_MS)
        assertEquals(15, RunCaps.LOW_BATTERY_PCT)
        assertEquals(3, RunCaps.MAX_CONSECUTIVE_ERRORS)
        assertEquals(ExperimentStop.BATTERY, ExperimentStop.of(StopReason.LOW_BATTERY))
        assertEquals(ExperimentStop.HOT, ExperimentStop.of(StopReason.HOT))
        assertEquals(ExperimentStop.TIME_CAP, ExperimentStop.of(StopReason.TIME_CAP))
        assertEquals(ExperimentStop.FAILURES, ExperimentStop.of(StopReason.ERRORS))
        assertNull(ExperimentStop.of(StopReason.DONE))
    }

    @Test fun lowBatteryOffChargeOrTheTimeCapStopBeforeTheNextRun() = runBlocking {
        val plan = ExperimentPlan(SweepVariable.TEMPERATURE, listOf("0.2"), 5).expand(base).getOrThrow()
        val runner = LlmRunner(FakeLlmService { FakeReply("ok") })
        assertEquals(ExperimentStop.BATTERY, runExperiment(plan, runner, Guard(pct = 15)) {}.stop)
        assertNull("charging is fine", runExperiment(plan, runner, Guard(pct = 10, charging = true)) {}.stop)
        var t = 0L
        val p = runExperiment(plan, runner, Guard(), clock = { t.also { t += 16 * 60_000L } }) {}
        assertEquals(ExperimentStop.TIME_CAP, p.stop)
    }

    @Test fun anExperimentRunsLogsAndSummarises() = runBlocking {
        val logged = mutableListOf<String>()
        val plan = ExperimentPlan(SweepVariable.TEMPERATURE, listOf("0.2", "1.0"), 2)
        val fake = FakeLlmService { FakeReply("one two three") }
        val p = runExperiment(plan.expand(base).getOrThrow(), LlmRunner(fake), Guard(), onRecord = { l, _ -> logged += l }) {}
        assertEquals(4, p.done)
        assertNull(p.stop)
        assertEquals(listOf("0.2", "1.0", "0.2", "1.0"), logged)
        assertEquals(listOf(0.2, 1.0, 0.2, 1.0), fake.requests.map { it.settings.temperature })
        val rows = ExperimentSummary.of(plan.values, p.results)
        assertEquals(2, rows[0].runs)
        assertEquals(3.0, rows[0].outputTokens!!.mean, 0.0)
        val text = ExperimentSummary.text(plan, rows, p.stop)
        assertTrue(text.contains("0.2 | 2 | 0 |"))
    }

    @Test fun heatOrLowBatteryStopsBeforeTheNextRun() = runBlocking {
        var n = 0
        val plan = ExperimentPlan(SweepVariable.TEMPERATURE, listOf("0.2"), 5).expand(base).getOrThrow()
        val guard = object : DeviceGuard {
            override fun batteryPct() = 80
            override fun charging() = false
            override fun hot() = ++n > 2
        }
        val p = runExperiment(plan, LlmRunner(FakeLlmService { FakeReply("ok") }), guard) {}
        assertEquals(ExperimentStop.HOT, p.stop)
        assertTrue(p.done in 1..2)
    }

    @Test fun threeFailuresInARowStop() = runBlocking {
        val plan = ExperimentPlan(SweepVariable.TEMPERATURE, listOf("0.2"), 10).expand(base).getOrThrow()
        val p = runExperiment(plan, LlmRunner(FakeLlmService(initial = LlmState.NoModel)), Guard()) {}
        assertEquals(ExperimentStop.FAILURES, p.stop)
        assertEquals(3, p.done)
        assertEquals(3, ExperimentSummary.of(listOf("0.2"), p.results)[0].failed)
    }

    @Test fun stoppingEndsAsStoppedAndLogsTheCutRun() = runBlocking {
        val logged = mutableListOf<LlmStopReason>()
        var last: ExperimentProgress? = null
        val plan = ExperimentPlan(SweepVariable.TEMPERATURE, listOf("0.2"), 5).expand(base).getOrThrow()
        val job = async {
            runExperiment(plan, LlmRunner(FakeLlmService(tokenDelayMs = 20) { FakeReply("w ".repeat(100)) }), Guard(), onRecord = { _, r -> logged += r.stopReason }) { last = it }
        }
        delay(150)
        job.cancel()
        runCatching { job.await() }
        assertEquals(ExperimentStop.STOPPED, last?.stop)
        assertEquals(LlmStopReason.CANCELLED, logged.last())
    }

    @Test fun spreadIsMeanSdMinMax() {
        val s = Spread.of(listOf(2.0, 4.0, 6.0))!!
        assertEquals(4.0, s.mean, 0.0)
        assertEquals(2.0, s.sd, 1e-9)
        assertEquals(2.0, s.min, 0.0)
        assertEquals(6.0, s.max, 0.0)
        assertNull(Spread.of(emptyList()))
        assertEquals(0.0, Spread.of(listOf(5.0))!!.sd, 0.0)
    }
}
