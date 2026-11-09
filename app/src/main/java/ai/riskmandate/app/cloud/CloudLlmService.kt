package ai.riskmandate.app.cloud

import ai.riskmandate.app.llm.LlmError
import ai.riskmandate.app.llm.LlmEvent
import ai.riskmandate.app.llm.LlmMessage
import ai.riskmandate.app.llm.LlmRequest
import ai.riskmandate.app.llm.LlmRole
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.LlmState
import ai.riskmandate.app.llm.LlmStats
import ai.riskmandate.app.llm.ToolSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/** What the last cloud call cost, for the screen's counters. Amounts in micro-dollars (1,000,000 = US$1). */
data class CloudRunInfo(
    val modelId: String,
    val promptTokens: Int?,
    val outputTokens: Int?,
    /** What OpenRouter reported, or an estimate (at least the reserve) when it did not. */
    val costMicros: Long,
    val costIsEstimate: Boolean,
    val sessionSpentMicros: Long,
    val sessionCapMicros: Long,
    val totalSpentMicros: Long,
    val totalCapMicros: Long,
)

/**
 * An [LlmService] backed by OpenRouter, for one pinned [model] (make one service per model). Same contract as the
 * on-device service: a cold [stream], exactly one Done or Error, coroutine cancellation (the connection is closed),
 * one request at a time, main-safe. Tool calls are only proposed, never executed.
 *
 * Network: one pinned host through [CloudTransport]. The key goes only in the `Authorization` header of that request.
 * Every request is checked against the spending cap before it is sent, using a worst-case estimate; the real cost
 * from OpenRouter's `usage.cost` is recorded when the answer ends (also when it is stopped or cut off). Error texts
 * are fixed phrases: nothing from the server's reply and nothing from the request is ever put in them.
 */
class CloudLlmService(
    val model: CloudModel,
    private val key: CloudKey,
    private val spend: CloudSpend,
    private val switch: CloudSwitch,
    private val transport: CloudTransport = HttpUrlTransport(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val gapMs: Long = CloudLimits.MIN_GAP_MS,
) : LlmService {
    init {
        require(CloudModels.byId(model.id) == model) { "model is not in the pinned list" }
    }

    private val _state = MutableStateFlow<LlmState>(LlmState.NoModel)
    override val state: StateFlow<LlmState> = _state.asStateFlow()

    private val _lastRun = MutableStateFlow<CloudRunInfo?>(null)
    val lastRun: StateFlow<CloudRunInfo?> = _lastRun.asStateFlow()

    @Volatile private var lastEndedAt = 0L

    /** The most [request] could cost, for the cap check and for a sweep to show before it starts. Very large when it cannot be sent at all. */
    fun worstCaseMicros(request: LlmRequest): Long =
        try { worstCase(buildBody(request), request.maxTokens) } catch (e: TooLarge) { Long.MAX_VALUE / 4 }

    private fun worstCase(body: String, maxTokens: Int): Long =
        Math.ceil((body.length / 3 + 1) * model.inputUsdPerMtok + maxTokens * model.outputUsdPerMtok).toLong()

    /** Re-reads whether a key is stored. The host calls it after the key is saved or removed. */
    fun refresh() {
        _state.value = if (key.isSet()) LlmState.Ready(model.id, BACKEND) else LlmState.NoModel
    }

    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        spend.gate.withLock { runOne(request) { emit(it) } }
    }.flowOn(io)

    private suspend fun runOne(request: LlmRequest, emit: suspend (LlmEvent) -> Unit) {
        refresh()
        if (!switch.isOn) return emit(LlmEvent.Error(LlmError.NO_MODEL, "The cloud model is switched off."))
        if (_state.value == LlmState.NoModel) return emit(LlmEvent.Error(LlmError.NO_MODEL, "No OpenRouter key is stored on this tablet."))
        if (request.tools.isNotEmpty() && !model.supportsTools) {
            return emit(LlmEvent.Error(LlmError.INFERENCE_FAILED, "This cloud model does not take tools."))
        }
        val body = try {
            buildBody(request)
        } catch (e: TooLarge) {
            return emit(LlmEvent.Error(LlmError.TOO_LONG, "The conversation is too long to send."))
        }
        if (body.toByteArray(Charsets.UTF_8).size > CloudLimits.MAX_REQUEST_BYTES) {
            return emit(LlmEvent.Error(LlmError.TOO_LONG, "The request is larger than ${CloudLimits.MAX_REQUEST_BYTES / 1024} KB."))
        }
        val promptTokensEstimate = body.length / 3 + 1
        if (spend.wouldExceed(worstCase(body, request.maxTokens))) {
            val s = spend.snapshot()
            return emit(
                if (!s.ledgerOk) {
                    LlmEvent.Error(LlmError.INFERENCE_FAILED, "The cloud spending ledger is missing or damaged, so nothing is sent. Staff can reset it in Diagnostics.")
                } else {
                    LlmEvent.Error(
                        LlmError.INFERENCE_FAILED,
                        "Cloud spending cap reached: this session US$${CloudSpend.usd(s.sessionSpentMicros)} of US$${CloudSpend.usd(s.sessionCapMicros)}, " +
                            "this tablet US$${CloudSpend.usd(s.totalSpentMicros)} of US$${CloudSpend.usd(s.totalCapMicros)}.",
                    )
                },
            )
        }
        val wait = gapMs - (clock() - lastEndedAt)
        if (lastEndedAt > 0 && wait > 0) delay(wait) // a minimum gap between calls

        val started = clock()
        val response = try {
            key.withBearer { bearer ->
                transport.post(CloudEndpoint.CHAT_URL, mapOf(
                    "Authorization" to bearer, "Content-Type" to "application/json", "Accept" to "text/event-stream",
                    "Accept-Encoding" to "identity", "User-Agent" to CloudEndpoint.USER_AGENT, "X-Title" to CloudEndpoint.X_TITLE,
                ), body)
            }
        } catch (e: IOException) {
            return emit(LlmEvent.Error(LlmError.INFERENCE_FAILED, "Could not reach openrouter.ai."))
        } ?: return emit(LlmEvent.Error(LlmError.NO_MODEL, "No OpenRouter key is stored on this tablet."))

        val ctx = currentCoroutineContext()
        val abort = ctx[Job]?.invokeOnCompletion { runCatching { response.close() } } // a stopped answer closes the connection
        var firstAt = 0L
        var promptTokens: Int? = null
        var outputTokens: Int? = null
        var reportedCost: Long? = null
        var chars = 0
        var finished = false
        var failure: LlmEvent.Error? = null
        val calls = linkedMapOf<Int, ToolAcc>()
        try {
            if (response.status !in 200..299) return emit(errorFor(response.status))
            try {
                for (line in response.lines()) {
                    ctx.ensureActive()
                    if (!line.startsWith("data:")) continue // comments, blank lines, event names
                    val data = line.substring(5).trim()
                    if (data == "[DONE]") { finished = true; break }
                    val chunk = JSONObject(data)
                    if (chunk.has("error")) { failure = LlmEvent.Error(LlmError.INFERENCE_FAILED, "openrouter.ai reported an error while answering."); break }
                    chunk.optJSONObject("usage")?.let { u ->
                        if (u.has("prompt_tokens")) promptTokens = u.optInt("prompt_tokens")
                        if (u.has("completion_tokens")) outputTokens = u.optInt("completion_tokens")
                        val c = u.optDouble("cost", Double.NaN)
                        if (c.isFinite() && c >= 0) reportedCost = Math.round(c * 1_000_000.0)
                    }
                    val choice = chunk.optJSONArray("choices")?.optJSONObject(0) ?: continue
                    if (choice.optString("finish_reason", "").let { it.isNotEmpty() && it != "null" }) finished = true
                    val delta = choice.optJSONObject("delta") ?: continue
                    if (!delta.isNull("content")) {
                        val text = delta.optString("content", "")
                        if (text.isNotEmpty()) {
                            if (firstAt == 0L) firstAt = clock()
                            chars += text.length
                            if (chars > CloudLimits.MAX_RESPONSE_CHARS) { failure = LlmEvent.Error(LlmError.INFERENCE_FAILED, "The reply from openrouter.ai is too long."); break }
                            emit(LlmEvent.Token(text))
                        }
                    }
                    val toolDeltas = delta.optJSONArray("tool_calls")
                    if (toolDeltas != null) {
                        for (i in 0 until toolDeltas.length()) {
                            val tc = toolDeltas.optJSONObject(i) ?: continue
                            val acc = calls.getOrPut(tc.optInt("index", i)) { ToolAcc() }
                            if (calls.size > MAX_TOOL_CALLS) { failure = LlmEvent.Error(LlmError.INFERENCE_FAILED, "The model proposed too many tool calls."); break }
                            val fn = tc.optJSONObject("function") ?: continue
                            if (!fn.isNull("name")) acc.name.append(fn.optString("name", ""))
                            if (!fn.isNull("arguments")) acc.args.append(fn.optString("arguments", ""))
                            if (acc.args.length > ToolSpec.MAX_ARGS_CHARS * 2 || acc.name.length > 200) {
                                failure = LlmEvent.Error(LlmError.INFERENCE_FAILED, "A tool call from the model was too long."); break
                            }
                        }
                        if (failure != null) break
                    }
                }
            } catch (e: JSONException) {
                failure = LlmEvent.Error(LlmError.INFERENCE_FAILED, "Unreadable reply from openrouter.ai.")
            } catch (e: IOException) {
                ctx.ensureActive() // closed by a stop: this throws the cancellation, which is not an error
                failure = LlmEvent.Error(LlmError.INFERENCE_FAILED, "The connection to openrouter.ai dropped.")
            }
        } finally {
            abort?.dispose()
            runCatching { response.close() }
            lastEndedAt = clock()
            settle(request, promptTokensEstimate, promptTokens, outputTokens, reportedCost, chars, response.status in 200..299)
        }
        failure?.let { return emit(it) }
        if (!finished) return emit(LlmEvent.Error(LlmError.INFERENCE_FAILED, "The reply from openrouter.ai ended early."))
        for (acc in calls.values) {
            val name = acc.name.toString().trim().take(64)
            if (name.isNotEmpty()) emit(LlmEvent.ToolCallProposed(name, acc.args.toString().ifBlank { "{}" }))
        }
        val ended = clock()
        emit(LlmEvent.Done(LlmStats(
            modelId = model.id, backend = BACKEND, promptTokens = promptTokens, outputTokens = outputTokens,
            prefillTokensPerSecond = null, decodeTokensPerSecond = null,
            timeToFirstTokenMs = if (firstAt > 0) firstAt - started else null, totalMs = ended - started,
        )))
    }

    /** Records what the call cost, once, whatever way it ended. Not billed when the server refused it. */
    private fun settle(request: LlmRequest, promptEstimate: Int, promptTokens: Int?, outputTokens: Int?, reported: Long?, chars: Int, billable: Boolean) {
        if (!billable) { _lastRun.value = null; return }
        val estimate = ((promptTokens ?: promptEstimate) * model.inputUsdPerMtok + (outputTokens ?: (chars / 3 + 1).coerceAtMost(request.maxTokens)) * model.outputUsdPerMtok)
        val cost = reported ?: maxOf(Math.ceil(estimate).toLong(), CloudLimits.RESERVE_MICROS) // never less than the reserve when no cost is reported
        try { spend.record(cost) } catch (e: Exception) { /* the next request's cap check reads the file and fails closed if it is damaged */ }
        val s = spend.snapshot()
        _lastRun.value = CloudRunInfo(model.id, promptTokens, outputTokens, cost, reported == null, s.sessionSpentMicros, s.sessionCapMicros, s.totalSpentMicros, s.totalCapMicros)
    }

    private fun errorFor(status: Int): LlmEvent.Error = when (status) {
        401, 403 -> LlmEvent.Error(LlmError.LOAD_FAILED, "OpenRouter rejected the key (HTTP $status). Check it in Diagnostics.")
        402 -> LlmEvent.Error(LlmError.INFERENCE_FAILED, "OpenRouter says the account is out of credit (HTTP 402).")
        413 -> LlmEvent.Error(LlmError.TOO_LONG, "OpenRouter says the request is too large (HTTP 413).")
        429 -> LlmEvent.Error(LlmError.INFERENCE_FAILED, "OpenRouter rate limit (HTTP 429). Try again shortly.")
        in 300..399 -> LlmEvent.Error(LlmError.INFERENCE_FAILED, "openrouter.ai answered with a redirect, which is not followed (HTTP $status).")
        else -> LlmEvent.Error(LlmError.INFERENCE_FAILED, "OpenRouter error (HTTP $status).")
    }

    private class ToolAcc { val name = StringBuilder(); val args = StringBuilder() }
    private class TooLarge : Exception()

    private fun buildBody(request: LlmRequest): String {
        var total = 0
        val messages = JSONArray()
        var nextCallId = 0
        val pendingIds = ArrayDeque<String>()
        for (m in request.messages) {
            total += m.text.length
            if (total > MAX_PROMPT_CHARS) throw TooLarge()
            val o = JSONObject()
            when (m.role) {
                LlmRole.SYSTEM -> o.put("role", "system").put("content", m.text)
                LlmRole.USER -> o.put("role", "user").put("content", m.text)
                LlmRole.ASSISTANT -> {
                    o.put("role", "assistant").put("content", m.text)
                    if (m.toolCalls.isNotEmpty()) {
                        val arr = JSONArray()
                        for (c in m.toolCalls) {
                            val id = "call_${nextCallId++}".also { pendingIds.addLast(it) }
                            arr.put(JSONObject().put("id", id).put("type", "function")
                                .put("function", JSONObject().put("name", c.name).put("arguments", c.argsJson)))
                        }
                        o.put("tool_calls", arr)
                    }
                }
                LlmRole.TOOL -> o.put("role", "tool").put("tool_call_id", pendingIds.removeFirstOrNull() ?: "call_${nextCallId++}").put("content", m.text)
            }
            messages.put(o)
        }
        val s = request.settings
        val body = JSONObject()
            .put("model", model.id)
            .put("messages", messages)
            .put("stream", true)
            .put("usage", JSONObject().put("include", true))
            .put("provider", JSONObject().put("data_collection", "deny")) // ask providers not to keep or train on the prompt
            .put("max_tokens", request.maxTokens.coerceIn(1, LlmRequest.MAX_OUTPUT_TOKENS))
        if (s.temperature.isFinite()) body.put("temperature", s.temperature.coerceIn(0.0, 2.0))
        if (s.topP.isFinite()) body.put("top_p", s.topP.coerceIn(0.0, 1.0))
        if (s.topK >= 1) body.put("top_k", s.topK)
        s.seed?.let { body.put("seed", it) }
        if (request.tools.isNotEmpty()) {
            val tools = JSONArray()
            for (t in request.tools) tools.put(JSONObject().put("type", "function").put("function", JSONObject(t.schemaJson())))
            body.put("tools", tools)
        }
        return body.toString()
    }

    companion object {
        const val BACKEND = "openrouter.ai (cloud)"
        /** A prompt of more characters than the request may hold in bytes cannot fit, so it is refused before the body is built. */
        const val MAX_PROMPT_CHARS = CloudLimits.MAX_REQUEST_BYTES
        const val MAX_TOOL_CALLS = 8
    }
}
