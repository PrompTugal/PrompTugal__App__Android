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
        val c = ChatController(LlmServiceChat(svc), MemoryChatHistoryStore(), CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
        runBlocking { c.start() }
        c.send("hello")
        assertEquals("one two three", c.state.current.messages.last().text)
        assertTrue(c.state.current.messages.last().tokens > 0)
    }
}
