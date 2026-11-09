package ai.riskmandate.app.llm

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeLlmServiceTest {
    private val ask = LlmRequest(listOf(LlmMessage.system("Be brief."), LlmMessage.user("Hello there")))

    @Test fun anOnDeviceServiceHasNoCost() {
        assertEquals(null, FakeLlmService().maxCostMicros(RunSettings(userPrompt = "x").request()))
    }

    @Test fun streamsTokensThenDone() = runBlocking {
        val events = FakeLlmService { FakeReply("One two three.") }.stream(ask).toList()
        assertEquals(listOf("One ", "two ", "three."), events.filterIsInstance<LlmEvent.Token>().map { it.text })
        val done = events.last() as LlmEvent.Done
        assertEquals(3, done.stats.outputTokens)
        assertEquals(FakeLlmService.MODEL_ID, done.stats.modelId)
    }

    @Test fun generateCollectsTextAndProposalsWithoutRunningThem() = runBlocking {
        val call = LlmEvent.ToolCallProposed("share_document", """{"to":"outside@example.org"}""")
        val r = FakeLlmService { FakeReply("Sharing.", listOf(call)) }.generate(ask)
        assertEquals("Sharing.", r.text)
        assertEquals(listOf(call), r.toolCalls)
        assertEquals(null, r.error)
    }

    @Test fun noModelIsAnErrorNotACrash() = runBlocking {
        val r = FakeLlmService(initial = LlmState.NoModel).generate(ask)
        assertEquals(LlmError.NO_MODEL, r.error?.reason)
        assertEquals("", r.text)
    }

    @Test fun scriptedErrorsEndTheStream() = runBlocking {
        val e = LlmEvent.Error(LlmError.TOO_LONG, "too long")
        assertEquals(listOf(e), FakeLlmService { FakeReply(error = e) }.stream(ask).toList())
    }

    @Test fun recordsRequestsAndServesOneAtATime() = runBlocking {
        val fake = FakeLlmService(tokenDelayMs = 20) { FakeReply("a b c d") }
        val first = async { fake.stream(ask).toList() }
        delay(5)
        val second = async { fake.stream(ask).toList() }
        val a = first.await()
        val b = second.await()
        assertTrue(a.last() is LlmEvent.Done && b.last() is LlmEvent.Done)
        assertEquals(2, fake.requests.size)
    }

    @Test fun cancellingTheCollectorStopsTheAnswer() = runBlocking {
        val fake = FakeLlmService(tokenDelayMs = 10) { FakeReply("w ".repeat(1000)) }
        val got = withTimeout(2_000) { fake.stream(ask).take(3).toList() }
        assertEquals(3, got.size)
        // The lock was released: a second request still runs.
        assertTrue(FakeLlmService { FakeReply("x") }.let { fake.generate(LlmRequest(listOf(LlmMessage.user("again")), maxTokens = 5)) }.stats != null)
    }

    @Test fun tokensJoinBackToTheText() {
        val t = "  Hello,  world!\nNew line "
        assertEquals(t, FakeLlmService.tokens(t).joinToString(""))
    }
}
