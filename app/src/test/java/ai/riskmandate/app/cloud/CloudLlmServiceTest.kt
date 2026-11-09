package ai.riskmandate.app.cloud

import ai.riskmandate.app.llm.LlmError
import ai.riskmandate.app.llm.LlmEvent
import ai.riskmandate.app.llm.LlmLocation
import ai.riskmandate.app.llm.LlmMessage
import ai.riskmandate.app.llm.LlmRequest
import ai.riskmandate.app.llm.LlmStopReason
import ai.riskmandate.app.llm.LlmState
import ai.riskmandate.app.llm.ParamType
import ai.riskmandate.app.llm.Sampling
import ai.riskmandate.app.llm.ToolCheck
import ai.riskmandate.app.llm.ToolParam
import ai.riskmandate.app.llm.ToolSpec
import ai.riskmandate.app.llm.checkToolCall
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
    private val dir get() = File(tmp.root, "feature_cloud")
    private val key by lazy { CloudKey(dir, SoftwareCipher()).also { it.save(TEST_KEY) } }
    private fun newSpend(session: Long = 50_000_000, total: Long = 50_000_000, init: Boolean = true) =
        CloudSpend(dir, session, total).also { if (init) it.initialise() }
    private var spendOrNull: CloudSpend? = null
    private var spend: CloudSpend
        get() = spendOrNull ?: newSpend().also { spendOrNull = it }
        set(v) { spendOrNull = v }
    private val switch = CloudSwitch().also { it.turnOn() }
    private var now = 0L

    private fun service(
        model: CloudModel = CloudModels.SONNET,
        key: CloudKey = this.key,
        sw: CloudSwitch = switch,
        spend: CloudSpend = this.spend,
        transport: CloudTransport,
    ) = CloudLlmService(model, key, spend, sw, transport, Dispatchers.Unconfined, { now.also { now += 100 } }, gapMs = 0)

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
        assertEquals(LlmLocation.CLOUD, done.stats.location) // never "on this device"
        assertEquals(0.0004, done.stats.costUsd!!, 1e-12) // OpenRouter's usage.cost
        assertEquals(64, done.stats.maxTokens); assertEquals(LlmStopReason.DONE, done.stats.stopReason)
        assertEquals(400L, spend.snapshot().totalSpentMicros) // usage.cost, to the micro-dollar
        assertEquals(400L, spend.snapshot().sessionSpentMicros)
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
        // Only what the caller put in the request: no device, app or account field of any kind.
        assertEquals(setOf("model", "messages", "stream", "usage", "provider", "max_tokens", "temperature", "top_p", "top_k", "seed"), b.keys().asSequence().toSet())
    }

    @Test fun theHeadersAreFixedAndNameNeitherTheDeviceNorTheAndroidVersion() {
        val t = ok(content("x"), FINISH, usage(1, 1, 0.0))
        run(service(transport = t))
        val h = t.calls.single().headers
        assertEquals(CloudEndpoint.USER_AGENT, h["User-Agent"])
        assertEquals(CloudEndpoint.X_TITLE, h["X-Title"])
        assertEquals("identity", h["Accept-Encoding"])
        assertEquals(setOf("Authorization", "Content-Type", "Accept", "Accept-Encoding", "User-Agent", "X-Title"), h.keys)
        assertFalse(h.values.any { it.contains("Android 1", ignoreCase = true) || it.contains("Build/") || it.contains("Dalvik") })
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

    @Test fun theServiceSaysItRunsInTheCloud() {
        assertEquals(LlmLocation.CLOUD, service(transport = ok()).location)
    }

    @Test fun stateFollowsTheKey() {
        val k = CloudKey(File(tmp.root, "k"), SoftwareCipher())
        val s = service(key = k, transport = ok())
        s.refresh(); assertEquals(LlmState.NoModel, s.state.value)
        k.save(TEST_KEY); s.refresh()
        assertEquals(LlmState.Ready(CloudModels.SONNET.id, CloudLlmService.BACKEND), s.state.value)
    }

    @Test fun theSwitchIsOffByDefaultAndNothingIsSentWhileItIs() {
        val t = ok(content("x"), FINISH)
        val e = run(service(sw = CloudSwitch(), transport = t))
        assertEquals(LlmError.NO_MODEL, err(e).reason)
        assertTrue(err(e).message.contains("switched off"))
        assertTrue(t.calls.isEmpty()); assertEquals(1, terminal(e))
    }

    @Test fun standModeRefusesTheCloudWhateverWasSetBefore() {
        var stand = false
        val sw = CloudSwitch(standMode = { stand }).also { it.turnOn() }
        val t = ok(content("x"), FINISH, usage(1, 1, 0.0))
        val s = service(sw = sw, transport = t)
        assertTrue(run(s).last() is LlmEvent.Done)
        stand = true
        val e = run(s)
        assertTrue(e.last() is LlmEvent.Error)
        stand = false
        assertTrue("stand mode turned it off for good", run(s).last() is LlmEvent.Error)
        assertEquals(1, t.calls.size)
    }

    @Test fun theSpendingCapStopsTheCallBeforeAnyRequest() {
        key; spend = newSpend(session = 1_000, total = 50_000_000); spend.record(900)
        val t = ok(content("x"), FINISH)
        val e = run(service(transport = t))
        val error = err(e)
        assertEquals(LlmError.CAP_REACHED, error.reason)
        assertTrue(error.message.contains("cap", ignoreCase = true))
        assertFalse(allText(e).contains(TEST_KEY))
        assertTrue("nothing may be sent once the cap would be passed", t.calls.isEmpty())
        assertEquals(1, terminal(e))
    }

    @Test fun eachCapStopsACallOnItsOwn() {
        key
        for ((session, total) in listOf(500L to 50_000_000L, 50_000_000L to 500L)) {
            val t = ok(content("x"), FINISH)
            run(service(spend = newSpend(session, total), transport = t))
            assertTrue("session=$session total=$total", t.calls.isEmpty())
        }
    }

    @Test fun capIsCheckedAgainstTheWorstCaseOfThisCallNotJustWhatWasSpent() {
        key; spend = newSpend(session = 5_000, total = 5_000) // US$0.005: less than 2,048 output tokens of Sonnet at list price
        val t = ok(content("x"), FINISH)
        run(service(transport = t), LlmRequest(listOf(LlmMessage.user("hi")), maxTokens = 2048))
        assertTrue(t.calls.isEmpty())
    }

    @Test fun aMissingOrDamagedLedgerRefusesToSpend() {
        key
        val t = ok(content("x"), FINISH)
        val missing = run(service(spend = newSpend(init = false), transport = t))
        assertTrue(err(missing).message.contains("ledger")); assertTrue(t.calls.isEmpty())
        assertEquals(LlmError.CAP_REACHED, err(missing).reason)
        newSpend().record(1)
        File(dir, CloudSpend.FILE).writeText("{ not a ledger")
        val damaged = run(service(spend = newSpend(init = false), transport = t))
        assertTrue(err(damaged).message.contains("ledger")); assertTrue(t.calls.isEmpty())
        assertEquals(LlmError.CAP_REACHED, err(damaged).reason)
        assertEquals(1, terminal(missing)); assertEquals(1, terminal(damaged))
    }

    @Test fun anAnswerWithNoReportedCostIsChargedAtLeastTheReserve() {
        val s = service(transport = ok(content("hi"), FINISH, usage(1, 1, null)))
        run(s)
        val info = s.lastRun.value!!
        assertTrue(info.costIsEstimate)
        assertTrue("cost ${info.costMicros}", info.costMicros >= CloudLimits.RESERVE_MICROS)
        assertEquals(info.costMicros, spend.snapshot().totalSpentMicros)
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
            assertEquals("a refused call costs nothing", 0L, spend.snapshot().totalSpentMicros)
        }
    }

    @Test fun anErrorChunkMidStreamEndsWithOneErrorAndNeverEchoesTheServer() {
        val t = ok(content("par"), """{"error":{"code":500,"message":"boom $TEST_KEY"}}""", done = false)
        val e = run(service(transport = t))
        assertEquals(listOf("par"), e.filterIsInstance<LlmEvent.Token>().map { it.text })
        assertEquals(1, terminal(e))
        assertFalse(allText(e).contains(TEST_KEY)); assertFalse(allText(e).contains("boom"))
        assertTrue("the partial answer is billed", spend.snapshot().totalSpentMicros > 0)
    }

    @Test fun aReplyThatEndsEarlyOrIsNotJsonIsAnErrorNotADone() {
        val early = run(service(transport = ok(content("a"), done = false)))
        assertEquals(1, terminal(early)); assertTrue(early.last() is LlmEvent.Error)
        val junk = run(service(transport = FakeTransport { _, _, _ -> FakeResponse(200, listOf("data: {not json", "")) }))
        assertEquals(1, terminal(junk)); assertTrue(junk.last() is LlmEvent.Error)
    }

    @Test fun aReplyBeyondTheSizeCapIsRefused() {
        val e = run(service(transport = ok(content("x".repeat(CloudLimits.MAX_RESPONSE_CHARS + 1)), FINISH)))
        assertEquals(1, terminal(e)); assertTrue(e.last() is LlmEvent.Error)
        assertTrue(e.filterIsInstance<LlmEvent.Token>().isEmpty())
    }

    @Test fun aRequestBeyondTheSizeCapIsRefusedBeforeSending() {
        val t = ok(content("x"), FINISH)
        val e = run(service(transport = t), LlmRequest(listOf(LlmMessage.user("y".repeat(CloudLimits.MAX_REQUEST_BYTES + 1)))))
        assertEquals(LlmError.TOO_LONG, err(e).reason); assertTrue(t.calls.isEmpty())
    }

    @Test fun anAnswerWithNoReportedCostStillCarriesAnEstimatedCostUsd() {
        val e = run(service(transport = ok(content("hi"), FINISH, usage(1, 1, null))))
        val cost = (e.last() as LlmEvent.Done).stats.costUsd!!
        assertTrue("estimate is at least the reserve: $cost", cost >= CloudLimits.RESERVE_MICROS / 1_000_000.0)
    }

    @Test fun aReplyCutByTheTokenLimitSaysSo() {
        val cut = """{"choices":[{"delta":{},"finish_reason":"length"}]}"""
        val e = run(service(transport = ok(content("long answ"), cut, usage(3, 64, 0.001))))
        assertEquals(LlmStopReason.MAX_TOKENS, (e.last() as LlmEvent.Done).stats.stopReason)
    }

    @Test fun lastRunReportsCostAndBothRunningTotals() {
        val s = service(transport = ok(content("x"), FINISH, usage(3, 2, 0.001)))
        run(s)
        val info = s.lastRun.value!!
        assertFalse(info.costIsEstimate)
        assertEquals(1_000L, info.costMicros)
        assertEquals(1_000L, info.sessionSpentMicros); assertEquals(1_000L, info.totalSpentMicros)
        assertEquals(50_000_000L, info.sessionCapMicros); assertEquals(50_000_000L, info.totalCapMicros)
    }

    @Test fun worstCaseGrowsWithTheOutputLimitAndIsHugeForARequestThatCannotBeSent() {
        val s = service(transport = ok())
        val small = s.worstCaseMicros(LlmRequest(listOf(LlmMessage.user("hi")), maxTokens = 10))
        val big = s.worstCaseMicros(LlmRequest(listOf(LlmMessage.user("hi")), maxTokens = 2000))
        assertTrue(big > small && small > 0)
        assertTrue(s.worstCaseMicros(LlmRequest(listOf(LlmMessage.user("y".repeat(CloudLlmService.MAX_PROMPT_CHARS + 1))))) > 1_000_000_000L)
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
        assertEquals(0L, spend.snapshot().totalSpentMicros)
    }

    @Test fun stoppingTheCollectorClosesTheConnectionAndStillBillsWhatWasUsed() {
        val endless = generateSequence(0) { it + 1 }.map { content("t$it") }.flatMap { sequenceOf("data: $it", "") }
        val resp = FakeResponse(200, endless)
        val t = FakeTransport { _, _, _ -> resp }
        val first = runBlocking { service(transport = t).stream(ask).take(3).toList() }
        assertEquals(3, first.size)
        assertTrue("the connection must be closed on stop", resp.closed)
        assertTrue("a stopped answer is billed by estimate", spend.snapshot().totalSpentMicros > 0)
    }

    @Test fun thereIsAMinimumGapBetweenCalls() {
        val starts = mutableListOf<Long>()
        val t = FakeTransport { _, _, _ -> starts += System.nanoTime(); FakeResponse(200, sse(content("x"), FINISH, usage(1, 1, 0.0))) }
        val s = CloudLlmService(CloudModels.SONNET, key, spend, switch, t, Dispatchers.Unconfined, System::currentTimeMillis, gapMs = 60)
        run(s); run(s)
        assertEquals(2, starts.size)
        assertTrue("gap was ${(starts[1] - starts[0]) / 1_000_000} ms", (starts[1] - starts[0]) / 1_000_000 >= 55)
    }

    @Test fun twoServicesOnOneLedgerNeverRunAtTheSameTime() {
        val inFlight = java.util.concurrent.atomic.AtomicInteger()
        var most = 0
        val slow = FakeTransport { _, _, _ ->
            val now = inFlight.incrementAndGet(); synchronized(this) { most = maxOf(most, now) }
            Thread.sleep(40)
            inFlight.decrementAndGet()
            FakeResponse(200, sse(content("x"), FINISH, usage(1, 1, 0.0)))
        }
        val a = CloudLlmService(CloudModels.SONNET, key, spend, switch, slow, Dispatchers.IO, System::currentTimeMillis, gapMs = 0)
        val b = CloudLlmService(CloudModels.GEMMA4, key, spend, switch, slow, Dispatchers.IO, System::currentTimeMillis, gapMs = 0)
        runBlocking {
            val jobs = (1..4).map { i -> async { (if (i % 2 == 0) a else b).stream(ask).toList() } }
            jobs.forEach { it.await() }
        }
        assertEquals("one request at a time across services", 1, most)
        assertEquals(4, slow.calls.size)
    }

    @Test fun onlyPinnedModelsCanBeUsed() {
        assertThrows(IllegalArgumentException::class.java) {
            CloudLlmService(CloudModel("evil/model", "x", 0.0, 0.0, true), key, spend, switch, ok(), Dispatchers.Unconfined)
        }
        assertThrows(IllegalArgumentException::class.java) { // a pinned id with changed prices is not the pinned model
            CloudLlmService(CloudModels.SONNET.copy(inputUsdPerMtok = 0.0), key, spend, switch, ok(), Dispatchers.Unconfined)
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
