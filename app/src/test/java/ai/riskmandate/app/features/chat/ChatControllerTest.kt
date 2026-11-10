package ai.riskmandate.app.features.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure logic: a fake LlmService, an in-memory store, Unconfined dispatchers so everything runs in line. */
class ChatControllerTest {
    private var now = 0L

    /** The shared prompt as bundled (assets/prompts/riskmandate_base.v2.txt), read the way the app's loader trims it. */
    private val BASE = java.io.File("src/main/assets/prompts/riskmandate_base.v2.txt").readText().trimEnd('\n', '\r')
    private var ids = 0
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun controller(llm: ChatLlm, store: ChatHistoryStore = MemoryChatHistoryStore()) =
        ChatController(llm, store, scope, baseSystem = { BASE }, io = Dispatchers.Unconfined, clock = { now }, newId = { "c${ids++}" })

    private fun started(llm: ChatLlm, store: ChatHistoryStore = MemoryChatHistoryStore()) =
        controller(llm, store).also { runBlocking { it.start() } }

    @Test fun streamsTokensIntoOneAssistantMessageWithStats() {
        val llm = FakeChatLlm(pieces = listOf("a", "b", "c", "d", "e"))
        val c = started(llm)
        c.send("hi")
        val msgs = c.state.current.messages
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT), msgs.map { it.role })
        assertEquals("abcde", msgs[1].text)
        assertEquals(5, msgs[1].tokens)
        assertFalse(msgs[1].stopped || msgs[1].failed)
        assertFalse(c.state.generating)
    }

    @Test fun serviceReportedStatsWinOverOurOwnCount() {
        val llm = FakeChatLlm(pieces = listOf("a", "b", "c")).apply { reportedStats = ChatAnswerStats(outputTokens = 42, tokensPerSecond = 17.5, totalMs = 2400) }
        val c = started(llm)
        c.send("hi")
        val a = c.state.current.messages.last()
        assertEquals(42, a.tokens)
        assertEquals(17.5, a.tokensPerSecond, 1e-9)
        assertEquals(2400L, a.millis)
    }

    @Test fun statsUseTheClock() {
        val ch = Channel<String>(Channel.UNLIMITED)
        val c = started(channelLlm(ch))
        c.send("hi")
        assertTrue(c.state.generating)
        now = 1000; ch.trySend("x")          // first piece at 1000
        now = 3000; ch.trySend("y"); ch.trySend("z") // three pieces, 2 s after the first
        now = 4000; ch.close()
        val a = c.state.current.messages.last()
        assertEquals(3, a.tokens)
        assertEquals(4000L, a.millis)                 // asked at 0, ended at 4000
        assertEquals(2 * 1000.0 / 3000, a.tokensPerSecond, 1e-9) // (tokens - 1) over first piece to end
    }

    @Test fun stopKeepsWhatArrivedMarksItStoppedCancelsTheModelAndSaves() {
        val ch = Channel<String>(Channel.UNLIMITED)
        val store = MemoryChatHistoryStore()
        var cancelled = false
        val c = started(object : ChatLlm {
            override suspend fun installedModels() = listOf(ChatModelInfo("m", "M"))
            override fun stream(modelId: String, system: String?, turns: List<ChatTurn>, onStats: (ChatAnswerStats) -> Unit): Flow<String> = flow {
                try { ch.consumeAsFlow().collect { emit(it) } } finally { cancelled = true }
            }
        }, store)
        c.send("question")
        ch.trySend("par"); ch.trySend("tial")
        c.stop()
        val a = c.state.current.messages.last()
        assertEquals("partial", a.text)
        assertTrue(a.stopped)
        assertFalse(c.state.generating)
        assertTrue("the model's flow must be cancelled", cancelled)
        assertEquals("partial", store.saved.single().messages.last().text) // stopped answers are saved too
        c.send("again") // usable after a stop
        assertEquals(4, c.state.current.messages.size)
    }

    @Test fun failureIsMarkedAndKeepsPartialText() {
        val c = started(FakeChatLlm(pieces = listOf("a", "b", "c"), failAfter = 2))
        c.send("hi")
        val a = c.state.current.messages.last()
        assertTrue(a.failed)
        assertEquals("ab", a.text)
        assertFalse(c.state.generating)
    }

    @Test fun noModelMeansNothingIsSent() {
        val llm = FakeChatLlm(models = emptyList())
        val c = started(llm)
        assertTrue(c.state.modelsLoaded)
        assertNull(c.state.modelId)
        c.send("hi")
        assertTrue(c.state.current.isEmpty)
        assertTrue(llm.calls.isEmpty())
    }

    @Test fun blankAndBusyAreIgnored() {
        val ch = Channel<String>(Channel.UNLIMITED)
        val c = started(channelLlm(ch))
        c.send("   ")
        assertTrue(c.state.current.isEmpty)
        c.send("one"); c.send("two") // second ignored while the first answers
        assertEquals(2, c.state.current.messages.size)
        ch.close()
    }

    @Test fun historyIsSavedAndComesBackOnTheNextStart() {
        val store = MemoryChatHistoryStore()
        val first = started(FakeChatLlm(), store)
        first.send("remember me")
        assertEquals(1, store.saved.size)
        val second = started(FakeChatLlm(), store) // a new screen, same tablet
        assertEquals(2, second.state.conversations.size)   // a fresh empty chat plus the saved one
        assertTrue(second.state.current.isEmpty)           // opens on the empty chat
        second.select(second.state.conversations[1].id)
        assertEquals("remember me", second.state.current.messages.first().text)
        assertEquals("remember me", second.state.conversations[1].title())
    }

    @Test fun startReopensTheChatThatWasOpenBeforeARotation() {
        val store = MemoryChatHistoryStore()
        val first = started(FakeChatLlm(), store)
        first.send("one"); first.newChat(); first.send("two")
        val openId = first.state.currentId
        val second = controller(FakeChatLlm(), store).also { runBlocking { it.start(reopen = openId) } }
        assertEquals(openId, second.state.currentId)
        assertEquals("two", second.state.current.messages.first().text)
        val unknown = controller(FakeChatLlm(), store).also { runBlocking { it.start(reopen = "gone") } }
        assertTrue(unknown.state.current.isEmpty) // an id that was never saved opens a new chat
    }

    @Test fun theVisitorsMessageIsSavedBeforeTheAnswerFinishes() {
        val ch = Channel<String>(Channel.UNLIMITED)
        val store = MemoryChatHistoryStore()
        val c = started(channelLlm(ch), store)
        c.send("still typing the answer")
        assertTrue(c.state.generating)
        assertEquals(listOf("still typing the answer"), store.saved.single().messages.map { it.text }) // no empty answer saved
        ch.close()
    }

    @Test fun aPartialAnswerIsSavedEvenWhenTheScopeEndsMidStream() {
        // A rotation cancels the composition's scope: the partial answer must still reach the disk.
        val job = kotlinx.coroutines.Job()
        val ch = Channel<String>(Channel.UNLIMITED)
        val store = MemoryChatHistoryStore()
        val c = ChatController(channelLlm(ch), store, CoroutineScope(Dispatchers.Unconfined + job), { BASE }, Dispatchers.Unconfined, { now }, { "c${ids++}" })
        runBlocking { c.start() }
        c.send("question")
        ch.trySend("half an "); ch.trySend("answer")
        job.cancel()
        val saved = store.saved.single().messages
        assertEquals("half an answer", saved.last().text)
        assertTrue(saved.last().stopped)
    }

    @Test fun emptyChatsAreNeverSaved() {
        val store = MemoryChatHistoryStore()
        val c = started(FakeChatLlm(), store)
        c.newChat(); c.newChat()
        assertEquals(1, c.state.conversations.size) // an empty current chat is reused
        c.send("x")
        c.newChat()
        assertEquals(2, c.state.conversations.size)
        assertEquals(1, store.saved.size)
    }

    @Test fun newChatStartsEmptyAndKeepsTheOldOne() {
        val c = started(FakeChatLlm())
        c.send("first")
        val old = c.state.currentId
        c.newChat()
        assertTrue(c.state.current.isEmpty)
        assertEquals("first", c.state.conversations.first { it.id == old }.messages.first().text)
    }

    @Test fun newChatWhileAnsweringStopsIt() {
        val ch = Channel<String>(Channel.UNLIMITED)
        val c = started(channelLlm(ch))
        c.send("q"); ch.trySend("par")
        c.newChat()
        assertFalse(c.state.generating)
        assertTrue(c.state.current.isEmpty)
    }

    @Test fun clearHistoryDeletesEverythingHereAndOnDisk() {
        val store = MemoryChatHistoryStore()
        val c = started(FakeChatLlm(), store)
        c.send("secret"); c.newChat(); c.send("more")
        assertEquals(2, store.saved.size)
        c.clearHistory()
        assertEquals(1, c.state.conversations.size)
        assertTrue(c.state.current.isEmpty)
        assertTrue(store.saved.isEmpty())
        assertEquals(1, store.clears)
    }

    @Test fun clearHistoryWhileAnsweringStopsAndLeavesNothingBehind() {
        val ch = Channel<String>(Channel.UNLIMITED)
        val store = MemoryChatHistoryStore()
        val c = started(channelLlm(ch), store)
        c.send("q"); ch.trySend("par")
        c.clearHistory()
        // The cancelled answer's own finalisation runs after the clear: nothing from it may reappear.
        assertTrue(c.state.current.isEmpty || c.state.conversations.none { it.messages.any { m -> m.text == "q" } })
        assertTrue(store.saved.none { it.messages.any { m -> m.text == "q" } })
    }

    @Test fun wipeNowDeletesSavedHistoryWithoutNeedingTheScope() {
        val store = MemoryChatHistoryStore()
        val c = started(FakeChatLlm(), store)
        c.send("visitor text")
        c.wipeNow()
        assertTrue(store.saved.isEmpty())
    }

    @Test fun neverLogsContentAndKeepsTheStandWipeOffUntilDinisDecides() {
        assertFalse(ChatPolicy.WIPE_ON_LEAVE)
        val bad = Regex("""\bLog\.|println\(|printStackTrace|Timber""")
        val offenders = java.io.File("src/main/java/ai/riskmandate/app/features/chat").listFiles().orEmpty()
            .filter { it.extension == "kt" && bad.containsMatchIn(it.readText()) }.map { it.name }
        assertEquals("chat must not log (prompts, answers, history)", emptyList<String>(), offenders)
    }

    @Test fun modelPickerChoosesWhichModelIsAsked() {
        val llm = FakeChatLlm(models = listOf(ChatModelInfo("a", "A"), ChatModelInfo("b", "B")))
        val c = started(llm)
        assertEquals("a", c.state.modelId)
        c.selectModel("b")
        c.selectModel("zzz") // unknown: ignored
        c.send("hi")
        assertEquals("b", llm.calls.single().modelId)
    }

    @Test fun groundingGoesToTheModelAsSystemTextAndNotIntoTheVisibleChat() {
        val llm = FakeChatLlm()
        val c = started(llm)
        c.send("What is RiskMandate?", groundingText = "RiskMandate gives agents access, not free rein.")
        val call = llm.calls.single()
        assertTrue(call.system!!.contains("RiskMandate gives agents access, not free rein."))
        assertEquals("What is RiskMandate?", c.state.current.messages.first().text)
        assertFalse(c.state.current.messages.any { it.text.contains("free rein") && it.role == ChatRole.USER })
    }

    @Test fun everyRequestWithoutAPresetCarriesTheShortBaseBlockInThePagesOwnWords() {
        val llm = FakeChatLlm()
        val c = started(llm)
        c.send("what is risk mandate?")
        val system = llm.calls.single().system!!
        assertEquals(BASE, system)
        assertTrue(system.contains("riskmandate.ai"))
        // The two quoted sentences are verbatim from the bundled riskmandate.ai text.
        val page = java.io.File("src/main/res/raw/feature_chat_riskmandate.txt").readText()
        for (quote in Regex("\"([^\"]+)\"").findAll(system).map { it.groupValues[1] }.filter { it.length > 20 || it.startsWith("Give") }) {
            assertTrue("not verbatim from the page: $quote", page.contains(quote))
        }
        assertTrue("keep prefill small: about 4 characters a token", system.length < 600)
    }

    @Test fun chatAlwaysSendsBasePromptV2WhichMakesNoOnThisDeviceClaim() {
        assertEquals(ai.riskmandate.app.llm.prompts.Prompts.RISKMANDATE_BASE_ANYWHERE, ChatBasePrompt.ID)
        val v2 = java.io.File("src/main/assets/prompts/riskmandate_base.v2.txt").readText()
        assertFalse(v2.contains("on this device"))
    }

    @Test fun aPresetGroundingReplacesTheBaseBlock() {
        val llm = FakeChatLlm()
        val c = started(llm)
        c.send("What is RiskMandate?", groundingText = "PAGE TEXT")
        assertTrue(llm.calls.single().system!!.contains("PAGE TEXT"))
    }

    @Test fun plainTextDropsBoldMarkersAndNothingElse() {
        assertEquals("Portugal is nice", PlainText.clean("**Portugal** is __nice__"))
        assertEquals("", PlainText.clean("**"))
        assertEquals("a snake_case_name and __init", PlainText.clean("a snake_case_name and __init"))
        assertEquals("<b>x</b> [l](https://evil.example)", PlainText.clean("<b>x</b> [l](https://evil.example)")) // not rendered, not stripped
        assertEquals("half bold", PlainText.clean("half **bold"))
        assertEquals("a b", PlainText.clean("a __b__")) // pairs
        assertEquals("__ x __", PlainText.clean("__ x __")) // whitespace inside the markers: left alone
        assertEquals("x y z", PlainText.clean("__x__ __y__ __z__"))
    }

    @Test fun plainTextIsLinearOnHostileInput() {
        val hostile = listOf("__a".repeat(40_000), "__ ".repeat(40_000), "__".repeat(60_000), "a__".repeat(40_000) + " b", "_".repeat(100_000))
        for (h in hostile) {
            val t0 = System.nanoTime()
            PlainText.clean(h)
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertTrue("clean took $ms ms on ${h.take(6)}...", ms < 200)
        }
        assertEquals("ab".repeat(1000), PlainText.clean("__a__b".repeat(1000)))
    }

    @Test fun laterTurnsCarryTheEarlierOnes() {
        val llm = FakeChatLlm()
        val c = started(llm)
        c.send("one"); c.send("two")
        val turns = llm.calls.last().turns
        assertEquals(listOf("one", "Hello from the device", "two"), turns.map { it.text })
        assertEquals(ChatRole.USER, turns.last().role)
    }

    @Test fun promptStaysInsideTheBudgetAndStartsWithTheUser() {
        val long = "x".repeat(5_000)
        val msgs = (1..6).flatMap { listOf(ChatMessage(ChatRole.USER, "u$it$long"), ChatMessage(ChatRole.ASSISTANT, "a$it$long")) }
        val turns = ChatPrompt.turns(msgs, budgetChars = 12_000)
        assertTrue(turns.sumOf { it.text.length } <= 12_000)
        assertEquals(ChatRole.USER, turns.first().role)
        assertTrue(turns.last().text.startsWith("a6"))
        // One giant last message is cut to fit rather than dropped.
        val huge = ChatPrompt.turns(listOf(ChatMessage(ChatRole.USER, "y".repeat(50_000))), 12_000)
        assertEquals(12_000, huge.single().text.length)
        // Failed and empty turns are not sent back to the model.
        val skip = ChatPrompt.turns(listOf(ChatMessage(ChatRole.USER, "q"), ChatMessage(ChatRole.ASSISTANT, "", failed = true), ChatMessage(ChatRole.USER, "q2")))
        assertEquals(listOf("q", "q2"), skip.map { it.text })
    }

    @Test fun onlyRiskmandateLinksSurviveFromModelOutput() {
        val text = """See https://riskmandate.ai/abp. Also http://riskmandate.ai/x, https://evil.example/riskmandate.ai,
            https://riskmandate.ai@evil.example/ and https://riskmandate.ai:8443/ and javascript:alert(1)
            and https://www.riskmandate.ai/ and [x](https://riskmandate.ai/abp)."""
        assertEquals(listOf("https://riskmandate.ai/abp", "https://www.riskmandate.ai/"), ChatLinks.allowed(text))
        assertTrue(ChatLinks.allowed("no links here <a href=\"https://evil.example\">x</a>").isEmpty())
    }

    private fun channelLlm(ch: Channel<String>) = object : ChatLlm {
        override suspend fun installedModels() = listOf(ChatModelInfo("m", "M"))
        override fun stream(modelId: String, system: String?, turns: List<ChatTurn>, onStats: (ChatAnswerStats) -> Unit) = ch.consumeAsFlow()
    }
}
