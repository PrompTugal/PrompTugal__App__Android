package ai.riskmandate.app.features.chat

import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmError
import ai.riskmandate.app.llm.LlmEvent
import ai.riskmandate.app.llm.LlmRole
import ai.riskmandate.app.llm.LlmState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmServiceChatTest {
    private val turns = listOf(ChatTurn(ChatRole.USER, "one"), ChatTurn(ChatRole.ASSISTANT, "two"), ChatTurn(ChatRole.USER, "three"))

    @Test fun streamsTokensAndSendsSystemThenTurnsWithTheTokenCap() = runBlocking {
        val svc = FakeLlmService(reply = { FakeReply("alpha beta gamma") })
        val pieces = LlmServiceChat(svc).stream("m", "SYS", turns).toList()
        assertEquals("alpha beta gamma", pieces.joinToString(""))
        val req = svc.requests.single()
        assertEquals(listOf(LlmRole.SYSTEM, LlmRole.USER, LlmRole.ASSISTANT, LlmRole.USER), req.messages.map { it.role })
        assertEquals(listOf("SYS", "one", "two", "three"), req.messages.map { it.text })
        assertEquals(LlmServiceChat.DEFAULT_MAX_TOKENS, req.maxTokens)
        assertTrue(req.tools.isEmpty()) // chat offers no tools
    }

    @Test fun doneEventReportsRealCountsAndSpeed() = runBlocking {
        val svc = FakeLlmService(reply = { FakeReply("alpha beta") })
        var got: ChatAnswerStats? = null
        LlmServiceChat(svc).stream("m", null, turns.take(1)) { got = it }.toList()
        assertTrue("Done must report stats", got != null)
        assertEquals(got!!.outputTokens != null, true)
    }

    @Test fun chatAsksForLowerTemperatureThanGemmasDefault() = runBlocking {
        val svc = FakeLlmService()
        LlmServiceChat(svc).stream("m", null, turns.take(1)).toList()
        val s = svc.requests.single().settings
        assertTrue("temperature ${s.temperature}", s.temperature in 0.3..0.5)
        assertEquals(0.9, s.topP, 1e-9)
        assertEquals(64, s.topK)
    }

    @Test fun theAdapterReadsWhereTheServiceRuns() {
        val local = FakeLlmService()
        assertTrue(LlmServiceChat(local).isLocal)
        val cloud = object : ai.riskmandate.app.llm.LlmService by FakeLlmService() {
            override val location = ai.riskmandate.app.llm.LlmLocation.CLOUD
        }
        assertFalse(LlmServiceChat(cloud).isLocal)
        val c = ChatController(LlmServiceChat(cloud), MemoryChatHistoryStore(), CoroutineScope(Dispatchers.Unconfined), { null }, Dispatchers.Unconfined)
        runBlocking { c.start() }
        assertFalse(c.state.local)
    }

    @Test fun noSystemBlockMeansNoSystemMessage() = runBlocking {
        val svc = FakeLlmService()
        LlmServiceChat(svc).stream("m", null, turns.take(1)).toList()
        assertEquals(listOf(LlmRole.USER), svc.requests.single().messages.map { it.role })
    }

    @Test fun toolProposalsAreIgnored() = runBlocking {
        val svc = FakeLlmService(reply = { FakeReply("hi", toolCalls = listOf(LlmEvent.ToolCallProposed("share", "{}"))) })
        assertEquals("hi", LlmServiceChat(svc).stream("m", null, turns.take(1)).toList().joinToString(""))
    }

    @Test fun serviceErrorBecomesAnExceptionWithoutContent() = runBlocking {
        val svc = FakeLlmService(reply = { FakeReply("", error = LlmEvent.Error(LlmError.INFERENCE_FAILED, "secret prompt text")) })
        val err = runCatching { LlmServiceChat(svc).stream("m", null, turns.take(1)).toList() }.exceptionOrNull()
        assertTrue(err is ChatLlmException)
        assertFalse(err!!.message!!.contains("secret"))
    }

    @Test fun controllerKeepsTheFailureReasonAndStoreRoundTripsIt() {
        val svc = FakeLlmService(reply = { FakeReply("", error = LlmEvent.Error(LlmError.TOO_LONG, "x")) })
        val c = ChatController(LlmServiceChat(svc), MemoryChatHistoryStore(), CoroutineScope(Dispatchers.Unconfined), { null }, Dispatchers.Unconfined)
        runBlocking { c.start() }
        c.send("hello")
        val m = c.state.current.messages.last()
        assertTrue(m.failed)
        assertEquals("TOO_LONG", m.failure)
        assertEquals(ai.riskmandate.app.R.string.chat_failed_too_long, failureText(m.failure))
        assertEquals(ai.riskmandate.app.R.string.chat_failed, failureText(null))
    }

    @Test fun installedModelFollowsTheServiceState() = runBlocking {
        val svc = FakeLlmService(initial = LlmState.NoModel)
        val chat = LlmServiceChat(svc)
        assertTrue(chat.installedModels().isEmpty())
        svc.setState(LlmState.Idle("gemma4-e2b"))
        assertEquals(1, chat.installedModels().size)
        svc.setState(LlmState.Failed(null, "x"))
        assertTrue(chat.installedModels().isEmpty())
        svc.setState(LlmState.Ready("unknown-id", "cpu"))
        assertEquals("unknown-id", chat.installedModels().single().name)
    }

    @Test fun controllerWorksEndToEndOnTheFakeService() {
        val svc = FakeLlmService(reply = { FakeReply("one two three") })
        val c = ChatController(LlmServiceChat(svc), MemoryChatHistoryStore(), CoroutineScope(Dispatchers.Unconfined), { null }, Dispatchers.Unconfined)
        runBlocking { c.start() }
        c.send("hello")
        assertEquals("one two three", c.state.current.messages.last().text)
        assertTrue(c.state.current.messages.last().tokens > 0)
    }
}
