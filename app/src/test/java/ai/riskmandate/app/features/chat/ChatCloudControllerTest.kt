package ai.riskmandate.app.features.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chat with two services: the device and (when the host gave one) the cloud. Both are fakes; nothing leaves the test. */
class ChatCloudControllerTest {
    private var now = 0L
    private var ids = 0
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val BASE = "BASE-V2"
    private val REFERENCE = "REFERENCE-TEXTS"

    private val device = FakeChatLlm(models = listOf(ChatModelInfo("dev-model", "Device model")), pieces = listOf("dev", " answer"))
    private val cloud = FakeChatLlm(models = listOf(ChatModelInfo("cloud/model", "Cloud model")), pieces = listOf("cloud", " answer"), local = false)

    private fun controller(device: ChatLlm = this.device, cloud: ChatLlm? = this.cloud, store: ChatHistoryStore = MemoryChatHistoryStore()) =
        ChatController(device, store, scope, baseSystem = { BASE }, io = Dispatchers.Unconfined, clock = { now }, newId = { "c${ids++}" },
            cloud = cloud, cloudReference = { REFERENCE })

    private fun started(device: ChatLlm = this.device, cloud: ChatLlm? = this.cloud, store: ChatHistoryStore = MemoryChatHistoryStore()) =
        controller(device, cloud, store).also { runBlocking { it.start() } }

    /** A send that has been confirmed, if the first cloud send of the session was held for it. */
    private fun ChatController.sendThrough(text: String, groundingText: String? = null) {
        send(text, groundingText)
        if (state.pendingSend != null) confirmPendingSend()
    }

    @Test fun theFirstCloudSendOfASessionIsHeldWithTheExactRequestAndNothingIsSentUntilConfirmed() {
        val c = started()
        c.send("hello")
        val p = c.state.pendingSend!!
        assertTrue(cloud.calls.isEmpty()); assertTrue(device.calls.isEmpty())
        assertTrue(p.preview.viaCloud); assertEquals(listOf("hello"), p.preview.turns.map { it.text })
        assertTrue(p.preview.system!!.contains(REFERENCE))
        assertTrue("the held message is not in the chat yet", c.state.current.messages.isEmpty())
        c.send("another while held")
        assertEquals("a second send while one is held is ignored", "hello", c.state.pendingSend!!.text)
        c.confirmPendingSend()
        assertNull(c.state.pendingSend)
        assertEquals(1, cloud.calls.size)
        assertEquals("what was shown is what was sent", p.preview.system, cloud.calls.single().system)
        c.send("second")
        assertNull("later cloud sends in the session do not ask again", c.state.pendingSend)
        assertEquals(2, cloud.calls.size)
    }

    @Test fun cancellingTheHeldSendSendsNothingAndGivesTheTextBack() {
        val c = started()
        c.send("secret-ish")
        assertEquals("secret-ish", c.cancelPendingSend())
        assertNull(c.state.pendingSend)
        assertTrue(cloud.calls.isEmpty()); assertTrue(c.state.current.messages.isEmpty())
        c.send("again")
        assertEquals("a cancel does not count as seen", "again", c.state.pendingSend!!.text)
    }

    @Test fun aSendOnTheDeviceIsNeverHeld() {
        val c = started()
        c.setUseCloud(false)
        c.send("on the device")
        assertNull(c.state.pendingSend)
        assertEquals(1, device.calls.size)
    }

    @Test fun theCloudAnswersByDefaultWhenItIsAvailableAndSaysSo() {
        val c = started()
        assertTrue(c.state.cloudAvailable); assertTrue(c.state.useCloud); assertFalse(c.state.local)
        assertEquals("cloud/model", c.state.modelId)
        c.sendThrough("hello")
        assertEquals(1, cloud.calls.size); assertTrue(device.calls.isEmpty())
        val a = c.state.current.messages.last()
        assertEquals("cloud answer", a.text)
        assertEquals(ChatVia.cloud("cloud/model"), a.via)
        assertTrue(ChatVia.isCloud(a.via))
    }

    @Test fun withoutACloudServiceChatIsOnTheDeviceOnly() {
        val c = started(cloud = null)
        assertFalse(c.state.cloudAvailable); assertFalse(c.state.useCloud); assertTrue(c.state.local)
        c.setUseCloud(true) // nothing to switch to
        assertFalse(c.state.useCloud)
        c.sendThrough("hello")
        assertEquals(ChatVia.device("dev-model"), c.state.current.messages.last().via)
        assertTrue(cloud.calls.isEmpty())
    }

    @Test fun aCloudServiceWithNoReadyModelIsNotOffered() {
        val c = started(cloud = FakeChatLlm(models = emptyList(), local = false))
        assertFalse(c.state.cloudAvailable); assertFalse(c.state.useCloud); assertEquals("dev-model", c.state.modelId)
    }

    @Test fun theChoiceSwitchesTheServiceAndTheModelAndIsNeverSaved() {
        val store = MemoryChatHistoryStore()
        val c = started(store = store)
        c.setUseCloud(false)
        assertFalse(c.state.useCloud); assertTrue(c.state.local); assertEquals("dev-model", c.state.modelId)
        c.sendThrough("on the device")
        assertEquals(ChatVia.device("dev-model"), c.state.current.messages.last().via)
        c.setUseCloud(true)
        assertEquals("cloud/model", c.state.modelId); assertFalse(c.state.local)
        // Saved history holds the answers, never the choice: the next start is on the default again.
        val next = started(store = store)
        assertTrue(next.state.useCloud)
    }

    @Test fun theChoiceCannotChangeWhileAnAnswerIsRunning() {
        val ch = Channel<String>(Channel.UNLIMITED)
        val slow = object : ChatLlm {
            override val isLocal = false
            override suspend fun installedModels() = listOf(ChatModelInfo("cloud/model", "Cloud model"))
            override fun stream(modelId: String, system: String?, turns: List<ChatTurn>, onStats: (ChatAnswerStats) -> Unit) = ch.consumeAsFlow()
        }
        val c = started(cloud = slow)
        c.sendThrough("q")
        assertTrue(c.state.generating)
        c.setUseCloud(false)
        assertTrue("still the cloud while it answers", c.state.useCloud)
        ch.close()
    }

    @Test fun theCloudGetsOnlyTheCurrentMessageUnlessEarlierOnesAreSwitchedOn() {
        val c = started()
        c.sendThrough("one"); c.sendThrough("two")
        assertEquals(listOf("two"), cloud.calls.last().turns.map { it.text }) // earlier messages are not sent
        c.setIncludeHistory(true)
        c.sendThrough("three")
        assertEquals(listOf("one", "cloud answer", "two", "cloud answer", "three"), cloud.calls.last().turns.map { it.text })
        c.newChat() // a new chat starts with the switch off again
        assertFalse(c.state.includeHistory)
    }

    @Test fun theDeviceAlwaysGetsTheWholeChat() {
        val c = started(cloud = null)
        c.sendThrough("one"); c.sendThrough("two")
        assertEquals(listOf("one", "dev answer", "two"), device.calls.last().turns.map { it.text })
    }

    @Test fun whatWillBeSentIsExactlyWhatIsSent() {
        val c = started()
        c.sendThrough("earlier")
        c.setIncludeHistory(true)
        val preview = runBlocking { c.previewSend("next question") }
        c.sendThrough("next question")
        val real = cloud.calls.last()
        assertEquals(real.system, preview.system)
        assertEquals(real.turns, preview.turns)
        assertTrue(preview.viaCloud)
        val noHistory = started()
        noHistory.sendThrough("earlier")
        val p2 = runBlocking { noHistory.previewSend("next question") }
        noHistory.sendThrough("next question")
        assertEquals(cloud.calls.last().turns, p2.turns)
        assertEquals(listOf("next question"), p2.turns.map { it.text })
    }

    @Test fun theCloudRouteCarriesTheBaseBlockAndTheReferenceTextsTheDeviceOnlyTheBase() {
        val c = started()
        c.sendThrough("what is RiskMandate?")
        val system = cloud.calls.single().system!!
        assertTrue(system.startsWith(BASE)); assertTrue(system.contains(REFERENCE))
        assertTrue(system.contains("Answer other questions normally")) // not forced to "I do not know" for everything else
        c.setUseCloud(false)
        c.sendThrough("what is RiskMandate?")
        assertEquals(BASE, device.calls.single().system)
    }

    @Test fun aPresetKeepsItsOwnGroundingOnEitherRoute() {
        val c = started()
        c.sendThrough("What is RiskMandate?", groundingText = "PRESET TEXT")
        val system = cloud.calls.single().system!!
        assertTrue(system.contains("PRESET TEXT")); assertFalse(system.contains(REFERENCE))
    }

    @Test fun theCostAndSourceOfACloudAnswerLandOnTheMessageAndInTheSavedChat() {
        val withCost = FakeChatLlm(models = listOf(ChatModelInfo("cloud/model", "Cloud model")), pieces = listOf("a", "b"), local = false)
            .apply { reportedStats = ChatAnswerStats(outputTokens = 9, tokensPerSecond = 40.0, totalMs = 700, costUsd = 0.0004) }
        val store = MemoryChatHistoryStore()
        val c = started(cloud = withCost, store = store)
        c.sendThrough("q")
        val a = c.state.current.messages.last()
        assertEquals(0.0004, a.costUsd!!, 1e-12); assertEquals(9, a.tokens)
        val saved = store.saved.single().messages
        assertEquals(a.via, saved.last().via); assertEquals(0.0004, saved.last().costUsd!!, 1e-12)
        assertNull("the user's own message has no source", saved.first().via)
    }

    @Test fun aCapStopIsAFailureWithItsReasonAndTheDeviceIsOneTapAway() {
        val capped = FakeChatLlm(models = listOf(ChatModelInfo("cloud/model", "Cloud model")), local = false, failWith = ChatLlmException("CAP_REACHED"))
        val c = started(cloud = capped)
        c.sendThrough("q")
        val a = c.state.current.messages.last()
        assertTrue(a.failed); assertEquals("CAP_REACHED", a.failure)
        assertEquals(ai.riskmandate.app.R.string.chat_failed_cap, failureText(a.failure))
        assertTrue(c.state.cloudAvailable)
        c.setUseCloud(false)
        c.sendThrough("q")
        assertEquals("dev answer", c.state.current.messages.last().text)
        assertNotEquals(a.via, c.state.current.messages.last().via)
        // A failed turn is not sent back to the model.
        assertEquals(listOf("q", "q"), device.calls.last().turns.map { it.text }) // the failed answer is dropped, both questions stay
    }
}
