package ai.riskmandate.app.features.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Ask our data" in the controller. Plain JVM (no Robolectric), like ChatControllerTest: the asker here is a fake. */
class ChatDataControllerTest {
    private var now = 0L
    private var ids = 0
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val device = FakeChatLlm(models = listOf(ChatModelInfo("dev-model", "Device model")), pieces = listOf("dev", " answer"))
    private val cloud = FakeChatLlm(models = listOf(ChatModelInfo("cloud/model", "Cloud model")), pieces = listOf("cloud", " answer"), local = false)

    private val grounding = ChatGrounding(
        sources = listOf(ChatSource(1, "Cerebras", "COMPANY", "https://promptugal.news/companies/cerebras/index.html")),
        attribution = "From pt.news: Cerebras (https://promptugal.news/companies/cerebras/index.html)",
        badRefs = listOf(7),
    )

    private class Asker(var answer: DataAnswer, val questions: MutableList<String> = mutableListOf()) : DataAsker {
        override suspend fun ask(question: String, onText: (String) -> Unit): DataAnswer {
            questions += question
            onText(answer.text.take(5))
            return answer
        }
    }

    private fun answer(text: String = "Cerebras makes chips [1].", g: ChatGrounding = grounding, error: String? = null) =
        DataAnswer(text, g, outputTokens = 12, tokensPerSecond = 5.5, totalMs = 2_000, errorReason = error)

    private fun controller(asker: DataAsker?, device: ChatLlm = this.device, cloud: ChatLlm? = null) =
        ChatController(device, MemoryChatHistoryStore(), scope, baseSystem = { "BASE" }, io = Dispatchers.Unconfined, clock = { now }, newId = { "c${ids++}" },
            cloud = cloud, dataAsker = asker).also { runBlocking { it.start() } }

    @Test fun itIsAvailableOnlyWithAnAskerAndAnInstalledDeviceModel() {
        assertTrue(controller(Asker(answer())).state.dataAvailable)
        assertFalse(controller(null).state.dataAvailable)
        assertFalse(controller(Asker(answer()), device = FakeChatLlm(models = emptyList())).state.dataAvailable)
    }

    @Test fun armingIsIgnoredWhenItIsNotAvailable() {
        val c = controller(null)
        c.armAskData(true)
        assertFalse(c.state.askData)
    }

    @Test fun anArmedQuestionGoesToTheAskerOnceNotToTheDeviceModelAndItKeepsItsSources() {
        val asker = Asker(answer())
        val c = controller(asker)
        c.armAskData(true)
        assertTrue(c.state.askData)
        c.send("what does Cerebras make?")
        assertEquals(listOf("what does Cerebras make?"), asker.questions)
        assertTrue("the plain chat service is not used for it", device.calls.isEmpty())
        val a = c.state.current.messages.last()
        assertEquals("Cerebras makes chips [1].", a.text)
        assertEquals(grounding, a.grounding)
        assertEquals(ChatVia.device("dev-model"), a.via)
        assertEquals(12, a.tokens); assertEquals(5.5, a.tokensPerSecond, 1e-9)
        assertFalse("armed for one question only", c.state.askData)
        c.send("and then a normal question")
        assertEquals(1, asker.questions.size); assertEquals(1, device.calls.size)
        assertNull(c.state.current.messages.last().grounding)
    }

    @Test fun whateverServiceIsChosenForNormalMessagesADataQuestionStaysOnTheDevice() {
        val asker = Asker(answer())
        val c = controller(asker, cloud = cloud)
        assertTrue(c.state.useCloud)
        c.armAskData(true)
        c.send("what does Cerebras make?")
        assertTrue("never the cloud", cloud.calls.isEmpty())
        val a = c.state.current.messages.last()
        assertEquals(ChatVia.device("dev-model"), a.via)
        assertFalse(ChatVia.isCloud(a.via))
        assertEquals("the screen's choice for normal messages is untouched", true, c.state.useCloud)
    }

    @Test fun anErrorFromTheAnswererIsAFailureWithItsReason() {
        val c = controller(Asker(answer(text = "", g = ChatGrounding(emptyList(), "", emptyList()), error = "LOAD_FAILED")))
        c.armAskData(true)
        c.send("q")
        val a = c.state.current.messages.last()
        assertTrue(a.failed); assertEquals("LOAD_FAILED", a.failure)
    }

    @Test fun stoppingKeepsTheTextSoFarAndNoSources() {
        val ch = Channel<Unit>()
        val stuck = DataAsker { _, onText -> onText("half an ans"); ch.receive(); error("never") }
        val c = controller(stuck)
        c.armAskData(true)
        c.send("q")
        assertTrue(c.state.generating)
        c.stop()
        val a = c.state.current.messages.last()
        assertEquals("half an ans", a.text); assertTrue(a.stopped); assertNull(a.grounding)
        assertFalse(c.state.generating)
    }

    @Test fun theQuestionIsSavedBeforeTheAnswerAndTheAnswerAfter() {
        val store = MemoryChatHistoryStore()
        val c = ChatController(device, store, scope, baseSystem = { "BASE" }, io = Dispatchers.Unconfined, clock = { now }, newId = { "c${ids++}" }, dataAsker = Asker(answer()))
        runBlocking { c.start() }
        c.armAskData(true)
        c.send("q")
        assertEquals(listOf("q", "Cerebras makes chips [1]."), store.saved.single().messages.map { it.text })
        assertEquals(grounding, store.saved.single().messages.last().grounding)
    }
}
