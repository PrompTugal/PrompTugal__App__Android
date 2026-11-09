package ai.riskmandate.app.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Robolectric runner only because tool checks go through ToolSpec (no org.json, but kept uniform with ToolSpecTest). */
@RunWith(AndroidJUnit4::class)
class LlmRunnerTest {
    private val device = DeviceSample(MemorySample(900, 1000, 6000, 11000), 80, false, Thermal.NONE)
    private val share = ToolSpec("share_document", "Share", listOf(ToolParam("to", ParamType.STRING, "Recipient")))

    private fun runner(fake: LlmService) = LlmRunner(fake, probe = { device })

    @Test fun aRunStreamsAndEndsWithARecord() = runBlocking {
        val updates = mutableListOf<RunUpdate>()
        val fake = FakeLlmService { FakeReply("One two three.") }
        val r = runner(fake).run(RunSettings(backend = BackendPreference.CPU, systemPrompt = "Be brief.", userPrompt = "Hi")) { updates += it }
        assertEquals(BackendPreference.CPU, fake.requests.single().backend)
        assertEquals(0, fake.unloads)
        assertEquals("One two three.", r.text)
        assertEquals(LlmStopReason.DONE, r.stopReason)
        assertEquals(device, r.before)
        assertEquals(device, r.after)
        assertTrue(updates.last() is RunUpdate.Finished)
        assertEquals("One two three.", (updates.filterIsInstance<RunUpdate.Progress>().last()).text)
        assertEquals(3, r.stats?.outputTokens)
    }

    @Test fun aColdStartUnloadsFirstAndLongPromptsAreRefused() = runBlocking {
        val fake = FakeLlmService { FakeReply("ok") }
        runner(fake).run(RunSettings(userPrompt = "x", coldStart = true)) {}
        assertEquals(1, fake.unloads)
        val long = runner(fake).run(RunSettings(userPrompt = "x".repeat(RunSettings.MAX_TYPED_CHARS + 1))) {}
        assertEquals(LlmStopReason.ERROR, long.stopReason)
        assertEquals(1, fake.requests.size)
    }

    @Test fun scriptedStatsReachTheRecordExactly() = runBlocking {
        val scripted = LlmStats("gemma4e2b", "CPU", null, 7, null, null, null, 1234, loadedModel = true, loadMs = 9000,
            countsFrom = StatSource.RUNTIME, requestedBackend = "GPU", fellBackToCpu = true, stopReason = LlmStopReason.MAX_TOKENS, maxTokens = 7)
        val r = runner(FakeLlmService { FakeReply("a b c d e f g", stats = scripted) }).run(RunSettings(userPrompt = "x", maxTokens = 7)) {}
        assertEquals(LlmStopReason.MAX_TOKENS, r.stopReason)
        assertEquals(scripted, r.stats)
        assertTrue(RunReport.text(r).contains("GPU failed, fell back to CPU"))
    }

    @Test fun theSystemPromptIsSentOnlyWhenSet() = runBlocking {
        val fake = FakeLlmService { FakeReply("ok") }
        runner(fake).run(RunSettings(userPrompt = "a")) {}
        runner(fake).run(RunSettings(systemPrompt = "S", userPrompt = "b")) {}
        assertEquals(listOf(LlmRole.USER), fake.requests[0].messages.map { it.role })
        assertEquals(listOf(LlmRole.SYSTEM, LlmRole.USER), fake.requests[1].messages.map { it.role })
    }

    @Test fun maxTokensIsTheStopReason() = runBlocking {
        val r = runner(FakeLlmService { FakeReply("a b c d e") }).run(RunSettings(userPrompt = "x", maxTokens = 5)) {}
        assertEquals(LlmStopReason.MAX_TOKENS, r.stopReason)
    }

    @Test fun stoppingGivesAStoppedRecordWithWhatArrived() = runBlocking {
        var finished: RunRecord? = null
        val job = async {
            runner(FakeLlmService(tokenDelayMs = 20) { FakeReply("w ".repeat(500)) })
                .run(RunSettings(userPrompt = "long")) { if (it is RunUpdate.Finished) finished = it.record }
        }
        delay(120)
        job.cancel()
        runCatching { job.await() }
        val r = finished!!
        assertEquals(LlmStopReason.CANCELLED, r.stopReason)
        assertTrue(r.text.isNotEmpty())
        assertNull("stopped before the runtime reported", r.stats)
    }

    @Test fun errorsAreAnErrorStopWithTheirCode() = runBlocking {
        val r = runner(FakeLlmService(initial = LlmState.NoModel)).run(RunSettings(userPrompt = "x")) {}
        assertEquals(LlmStopReason.ERROR, r.stopReason)
        assertEquals(LlmError.NO_MODEL, r.error?.reason)
        val bad = runner(FakeLlmService()).run(RunSettings(userPrompt = "x", maxTokens = 0)) {}
        assertEquals(LlmStopReason.ERROR, bad.stopReason)
    }

    @Test fun proposedToolCallsAreCheckedNotRun() = runBlocking {
        val r = runner(FakeLlmService {
            FakeReply("", listOf(LlmEvent.ToolCallProposed("share_document", """{"to":"x@example.org"}"""), LlmEvent.ToolCallProposed("delete_all", "{}")))
        }).run(RunSettings(userPrompt = "share it", tools = listOf(share))) {}
        assertEquals(2, r.toolCalls.size)
        assertTrue(r.toolCalls[0].verdict is ToolCheck.Accepted)
        assertEquals(ToolCheck.Rejected("delete_all", "not an offered tool"), r.toolCalls[1].verdict)
    }

    @Test fun theReportLabelsEveryFigure() = runBlocking {
        val r = runner(FakeLlmService { FakeReply("Hello there", listOf(LlmEvent.ToolCallProposed("share_document", """{"to":"a"}"""))) })
            .run(RunSettings(userPrompt = "Say hello", tools = listOf(share), sampling = Sampling.GREEDY)) {}
        val t = RunReport.text(r)
        listOf(
            "RiskMandate Workbench run", "Stop: done", "(measured)", "output tokens: 2 (measured)", "decode: - tok/s (measured)",
            "Tool calls proposed (none ran):", "share_document {\"to\":\"a\"}  -> accepted", "battery 80%", "thermal none",
            "top-k 1", "Prompt: Say hello", "Answer: Hello there",
        ).forEach { assertTrue("missing: $it\n$t", it in t) }
        assertFalse(t.contains("—"))
    }
}
