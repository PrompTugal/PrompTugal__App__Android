package ai.riskmandate.app.llm.cloud

import ai.riskmandate.app.llm.LlmError
import ai.riskmandate.app.llm.LlmEvent
import ai.riskmandate.app.llm.LlmMessage
import ai.riskmandate.app.llm.LlmRequest
import ai.riskmandate.app.llm.LlmState
import ai.riskmandate.app.llm.ParamType
import ai.riskmandate.app.llm.Sampling
import ai.riskmandate.app.llm.ToolCheck
import ai.riskmandate.app.llm.ToolParam
import ai.riskmandate.app.llm.ToolSpec
import ai.riskmandate.app.llm.checkToolCall
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** The cloud service against a fake HTTP layer: no network, no real key. */
@RunWith(AndroidJUnit4::class)
class CloudLlmServiceTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dir get() = File(tmp.root, "cloud")
    private val key by lazy { CloudKey(dir, SoftwareCipher()).also { it.save(TEST_KEY) } }
    private val spend by lazy { CloudSpend(dir) }
    private var now = 0L

    private fun service(model: CloudModel = CloudModels.SONNET, key: CloudKey = this.key, transport: CloudTransport) =
        CloudLlmService(model, key, spend, transport, Dispatchers.Unconfined) { now.also { now += 100 } }

    private fun ok(vararg data: String, done: Boolean = true) = FakeTransport { _, _, _ -> FakeResponse(200, sse(*data, done = done)) }
    private val ask = LlmRequest(listOf(LlmMessage.system("be brief"), LlmMessage.user("hello")), maxTokens = 64)
    private fun run(s: CloudLlmService, r: LlmRequest = ask) = runBlocking { s.stream(r).toList() }
    private fun terminal(e: List<LlmEvent>) = e.count { it is LlmEvent.Done || it is LlmEvent.Error }
    private fun err(e: List<LlmEvent>) = e.filterIsInstance<LlmEvent.Error>().single()
    private fun allText(e: List<LlmEvent>) = e.joinToString(" ") { it.toString() }

    @Test fun streamsTokensThenOneDoneWithRealCountsAndCost() {
        val t = ok(content("Hel"), content("lo"), FINISH, usage(12, 5, 0.0004))
        val e = run(service(transport = t))
        assertEquals(listOf("Hel", "lo"), e.filterIsInstance<LlmEvent.Token>().map { it.text })
        val done = e.last() as LlmEvent.Done
        assertEquals(1, terminal(e))
        assertEquals(CloudModels.SONNET.id, done.stats.modelId)
        assertEquals(12, done.stats.promptTokens); assertEquals(5, done.stats.outputTokens)
        assertTrue(done.stats.backend.contains("openrouter.ai"))
        assertEquals(400L, spend.snapshot().spentMicros) // usage.cost, to the micro-dollar
    }

    @Test fun theRequestGoesToThePinnedUrlWithTheKeyOnlyInTheAuthorizationHeader() {
        val t = ok(content("x"), FINISH, usage(1, 1, 0.0))
        run(service(transport = t), LlmRequest(listOf(LlmMessage.user("hi")), settings = Sampling(0.4, 64, 0.9, seed = 7), maxTokens = 50))
        val call = t.calls.single()
        assertEquals(CloudEndpoint.CHAT_URL, call.url)
        assertEquals("Bearer $TEST_KEY", call.headers["Authorization"])
        assertFalse("the key must not be in the body", call.body.contains(TEST_KEY))
        assertFalse(call.headers.filterKeys { it != "Authorization" }.values.any { it.contains(TEST_KEY) })
        val b = JSONObject(call.body)
        assertEquals(CloudModels.SONNET.id, b.getString("model"))
        assertTrue(b.getBoolean("stream")); assertTrue(b.getJSONObject("usage").getBoolean("include"))
        assertEquals("deny", b.getJSONObject("provider").getString("data_collection"))
        assertEquals(50, b.getInt("max_tokens")); assertEquals(0.4, b.getDouble("temperature"), 1e-9)
        assertEquals(0.9, b.getDouble("top_p"), 1e-9); assertEquals(64, b.getInt("top_k")); assertEquals(7, b.getInt("seed"))
        assertEquals("user", b.getJSONArray("messages").getJSONObject(0).getString("role"))
    }

    @Test fun withoutAKeyNothingIsSent() {
        val t = ok(content("x"), FINISH)
        val empty = CloudKey(File(tmp.root, "other"), SoftwareCipher())
        val e = run(service(key = empty, transport = t))
        assertEquals(LlmError.NO_MODEL, err(e).reason)
        assertTrue(t.calls.isEmpty())
        assertEquals(1, terminal(e))
        assertEquals(LlmState.NoModel, service(key = empty, transport = t).also { it.refresh() }.state.value)
    }

    @Test fun stateFollowsTheKey() {
        val k = CloudKey(File(tmp.root, "k"), SoftwareCipher())
        val s = service(key = k, transport = ok())
        s.refresh(); assertEquals(LlmState.NoModel, s.state.value)
        k.save(TEST_KEY); s.refresh()
        assertEquals(LlmState.Ready(CloudModels.SONNET.id, CloudLlmService.BACKEND), s.state.value)
    }

    @Test fun theSpendingCapStopsTheCallBeforeAnyRequest() {
        key; spend.setCap(1_000); spend.record(900)
        val t = ok(content("x"), FINISH)
        val e = run(service(transport = t))
        val error = err(e)
        assertEquals(LlmError.INFERENCE_FAILED, error.reason)
        assertTrue(error.message.contains("cap", ignoreCase = true))
        assertFalse(allText(e).contains(TEST_KEY))
        assertTrue("nothing may be sent once the cap would be passed", t.calls.isEmpty())
        assertEquals(1, terminal(e))
    }

    @Test fun capIsCheckedAgainstTheWorstCaseOfThisCallNotJustWhatWasSpent() {
        key; spend.setCap(5_000) // US$0.005: less than 64 output tokens of Sonnet at its list price plus the prompt
        val t = ok(content("x"), FINISH)
        run(service(transport = t), LlmRequest(listOf(LlmMessage.user("hi")), maxTokens = 2048))
        assertTrue(t.calls.isEmpty())
    }

    @Test fun httpErrorsBecomeFixedMessagesAndTheBodyIsNeverRead() {
        for ((status, reason) in listOf(401 to LlmError.LOAD_FAILED, 403 to LlmError.LOAD_FAILED, 413 to LlmError.TOO_LONG,
            402 to LlmError.INFERENCE_FAILED, 429 to LlmError.INFERENCE_FAILED, 302 to LlmError.INFERENCE_FAILED, 500 to LlmError.INFERENCE_FAILED)) {
            val t = FakeTransport { _, _, _ -> FakeResponse(status, listOf("""{"error":"echo $TEST_KEY"}""")) }
            val e = run(service(transport = t))
            assertEquals("status $status", reason, err(e).reason)
            assertTrue(err(e).message.contains("$status") || status == 402 || status == 413)
            assertFalse(allText(e).contains(TEST_KEY))
            assertFalse("error body must not be read", t.responses.single().linesCalled)
            assertTrue(t.responses.single().closed)
            assertEquals("a refused call costs nothing", 0L, spend.snapshot().spentMicros)
        }
    }

    @Test fun anErrorChunkMidStreamEndsWithOneErrorAndNeverEchoesTheServer() {
        val t = ok(content("par"), """{"error":{"code":500,"message":"boom $TEST_KEY"}}""", done = false)
        val e = run(service(transport = t))
        assertEquals(listOf("par"), e.filterIsInstance<LlmEvent.Token>().map { it.text })
        assertEquals(1, terminal(e))
        assertFalse(allText(e).contains(TEST_KEY)); assertFalse(allText(e).contains("boom"))
        assertTrue("the partial answer is billed", spend.snapshot().spentMicros > 0)
    }

    @Test fun aReplyThatEndsEarlyOrIsNotJsonIsAnErrorNotADone() {
        val early = run(service(transport = ok(content("a"), done = false)))
        assertEquals(1, terminal(early)); assertTrue(early.last() is LlmEvent.Error)
        val junk = run(service(transport = FakeTransport { _, _, _ -> FakeResponse(200, listOf("data: {not json", "")) }))
        assertEquals(1, terminal(junk)); assertTrue(junk.last() is LlmEvent.Error)
    }

    @Test fun costFallsBackToAnEstimateWhenOpenRouterSendsNone() {
        val s = service(transport = ok(content("some words here"), FINISH, usage(10, 4, null)))
        run(s)
        val info = s.lastRun.value!!
        assertTrue(info.costIsEstimate); assertTrue(info.costMicros > 0)
        assertEquals(info.costMicros, spend.snapshot().spentMicros)
    }

    @Test fun lastRunReportsCostAndTheRunningTotal() {
        val s = service(transport = ok(content("x"), FINISH, usage(3, 2, 0.001)))
        run(s)
        val info = s.lastRun.value!!
        assertFalse(info.costIsEstimate)
        assertEquals(1_000L, info.costMicros); assertEquals(1_000L, info.spentMicros); assertEquals(1_000_000L, info.capMicros)
    }

    @Test fun toolCallsAreAssembledFromDeltasAndOnlyProposed() {
        fun tc(index: Int, name: String?, args: String) =
            """{"choices":[{"delta":{"tool_calls":[{"index":$index,"function":{${if (name != null) "\"name\":\"$name\"," else ""}"arguments":${JSONObject.quote(args)}}}]}}]}"""
        val t = ok(tc(0, "get_weather", "{\"ci"), tc(0, null, "ty\":\"Lisbon\"}"), tc(1, "made_up", "{}"), FINISH, usage(5, 5, 0.0))
        val tools = listOf(ToolSpec("get_weather", "weather", listOf(ToolParam("city", ParamType.STRING, "city"))))
        val e = run(service(transport = t), LlmRequest(listOf(LlmMessage.user("weather?")), tools = tools, maxTokens = 64))
        val calls = e.filterIsInstance<LlmEvent.ToolCallProposed>()
        assertEquals(listOf("get_weather", "made_up"), calls.map { it.name })
        assertEquals("""{"city":"Lisbon"}""", calls[0].argsJson)
        // The feature decides, with its own allow-list, exactly as for the local model.
        assertTrue(checkToolCall(tools, calls[0]) is ToolCheck.Accepted)
        assertTrue(checkToolCall(tools, calls[1]) is ToolCheck.Rejected)
        assertTrue(e.last() is LlmEvent.Done)
        assertTrue(JSONObject(t.calls.single().body).getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name") == "get_weather")
    }

    @Test fun anOversizeToolCallIsAnError() {
        val huge = JSONObject.quote("x".repeat(20_000))
        val chunk = """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"t","arguments":$huge}}]}}]}"""
        val e = run(service(transport = ok(chunk, FINISH)))
        assertEquals(1, terminal(e)); assertTrue(e.last() is LlmEvent.Error)
    }

    @Test fun toolsOnAModelThatTakesNoneAreRefusedWithoutASend() {
        val t = ok(content("x"), FINISH)
        val tools = listOf(ToolSpec("t", "d"))
        val e = run(service(model = CloudModels.GEMMA3, transport = t), LlmRequest(listOf(LlmMessage.user("hi")), tools = tools))
        assertTrue(e.last() is LlmEvent.Error); assertTrue(t.calls.isEmpty())
    }

    @Test fun toolResultsCarryTheIdOfTheCallTheyAnswer() {
        val t = ok(content("done"), FINISH, usage(1, 1, 0.0))
        val call = LlmEvent.ToolCallProposed("get_weather", """{"city":"Lisbon"}""")
        run(service(transport = t), LlmRequest(listOf(
            LlmMessage.user("weather?"), LlmMessage.assistant("", listOf(call)), LlmMessage.toolResult("get_weather", """{"temp":18}"""),
        )))
        val msgs = JSONObject(t.calls.single().body).getJSONArray("messages")
        val id = msgs.getJSONObject(1).getJSONArray("tool_calls").getJSONObject(0).getString("id")
        assertEquals(id, msgs.getJSONObject(2).getString("tool_call_id"))
        assertEquals("tool", msgs.getJSONObject(2).getString("role"))
    }

    @Test fun aTooLongConversationIsRefusedBeforeSending() {
        val t = ok(content("x"), FINISH)
        val e = run(service(transport = t), LlmRequest(listOf(LlmMessage.user("y".repeat(CloudLlmService.MAX_PROMPT_CHARS + 1)))))
        assertEquals(LlmError.TOO_LONG, err(e).reason); assertTrue(t.calls.isEmpty())
    }

    @Test fun aNetworkFailureIsAFixedMessage() {
        val t = ok(content("x"), FINISH).apply { failWith = IOException("connect to https://openrouter.ai failed for $TEST_KEY") }
        val e = run(service(transport = t))
        assertEquals(1, terminal(e))
        assertFalse(allText(e).contains(TEST_KEY)); assertFalse(allText(e).contains("connect to"))
        assertEquals(0L, spend.snapshot().spentMicros)
    }

    @Test fun stoppingTheCollectorClosesTheConnectionAndStillBillsWhatWasUsed() {
        val endless = generateSequence(0) { it + 1 }.map { content("t$it") }.flatMap { sequenceOf("data: $it", "") }
        val resp = FakeResponse(200, endless)
        val t = FakeTransport { _, _, _ -> resp }
        val first = runBlocking { service(transport = t).stream(ask).take(3).toList() }
        assertEquals(3, first.size)
        assertTrue("the connection must be closed on stop", resp.closed)
        assertTrue("a stopped answer is billed by estimate", spend.snapshot().spentMicros > 0)
    }

    @Test fun onlyPinnedModelsCanBeUsed() {
        assertThrows(IllegalArgumentException::class.java) {
            CloudLlmService(CloudModel("evil/model", "x", 0.0, 0.0, true), key, spend, ok(), Dispatchers.Unconfined)
        }
        assertThrows(IllegalArgumentException::class.java) { // a pinned id with changed prices is not the pinned model
            CloudLlmService(CloudModels.SONNET.copy(inputUsdPerMtok = 0.0), key, spend, ok(), Dispatchers.Unconfined)
        }
    }

    @Test fun theKeyNeverAppearsInAnyEventForAnyOutcome() {
        val outcomes = listOf(
            ok(content("fine"), FINISH, usage(1, 1, 0.0)),
            ok(content("fine"), done = false),
            FakeTransport { _, _, _ -> FakeResponse(401, listOf(TEST_KEY)) },
            ok("""{"error":{"message":"$TEST_KEY"}}""", done = false),
        )
        for (t in outcomes) assertFalse(allText(run(service(transport = t))).contains(TEST_KEY))
    }
}
