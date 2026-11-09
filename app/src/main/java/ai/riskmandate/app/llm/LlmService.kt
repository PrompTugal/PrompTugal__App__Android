package ai.riskmandate.app.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The on-device model, as every feature sees it (architect: docs/architecture/README.md, "On-device LLM").
 *
 * - One per process, owned by the host ([LlmHost]); features get it through `FeatureContext.llm` and never
 *   load a model themselves.
 * - Main-safe: inference runs on the service's own dispatcher. One request at a time; others wait their turn.
 * - Cancellation is coroutine cancellation: stop collecting [stream] (Stop button, screen left) and the native
 *   call is cancelled. There is no separate cancel().
 * - The service never executes a tool. It reports [LlmEvent.ToolCallProposed]; the feature checks the proposal
 *   against its own [ToolSpec] list ([ToolSpec.check]) and decides.
 */
interface LlmService {
    val state: StateFlow<LlmState>

    /** Streams one answer. Cold: nothing runs until collected. Ends with exactly one [LlmEvent.Done] or [LlmEvent.Error]. */
    fun stream(request: LlmRequest): Flow<LlmEvent>

    /** Collects [stream] into one result. */
    suspend fun generate(request: LlmRequest): LlmResult {
        val text = StringBuilder()
        val calls = mutableListOf<LlmEvent.ToolCallProposed>()
        var stats: LlmStats? = null
        var error: LlmEvent.Error? = null
        stream(request).collect { e ->
            when (e) {
                is LlmEvent.Token -> text.append(e.text)
                is LlmEvent.ToolCallProposed -> calls += e
                is LlmEvent.Done -> stats = e.stats
                is LlmEvent.Error -> error = e
            }
        }
        return LlmResult(text.toString(), calls, stats, error)
    }
}

sealed interface LlmState {
    /** No verified model file is installed: show "set up a model" and a way to feature G (through the host). */
    data object NoModel : LlmState
    /** A model is installed but not in memory (never loaded, or unloaded to save memory). The next request loads it. */
    data class Idle(val modelId: String) : LlmState
    data class Loading(val modelId: String) : LlmState
    data class Ready(val modelId: String, val backend: String) : LlmState
    data class Failed(val modelId: String?, val reason: String) : LlmState
}

enum class LlmRole { SYSTEM, USER, ASSISTANT, TOOL }

/**
 * One turn. A [LlmRole.TOOL] message carries a tool's result back to the model; [toolName] names the tool.
 * An [LlmRole.ASSISTANT] message may carry the tool calls it proposed, so a conversation can be replayed.
 */
data class LlmMessage(
    val role: LlmRole,
    val text: String,
    val toolName: String? = null,
    val toolCalls: List<LlmEvent.ToolCallProposed> = emptyList(),
) {
    companion object {
        fun system(text: String) = LlmMessage(LlmRole.SYSTEM, text)
        fun user(text: String) = LlmMessage(LlmRole.USER, text)
        fun assistant(text: String, toolCalls: List<LlmEvent.ToolCallProposed> = emptyList()) =
            LlmMessage(LlmRole.ASSISTANT, text, toolCalls = toolCalls)
        fun toolResult(toolName: String, resultJson: String) = LlmMessage(LlmRole.TOOL, resultJson, toolName = toolName)
    }
}

/** Sampling settings. [seed] is honoured where the runtime supports it. */
data class Sampling(val temperature: Double, val topK: Int, val topP: Double, val seed: Int? = null) {
    companion object {
        /** Gemma's recommended chat settings (as in Google AI Edge Gallery). */
        val DEFAULT = Sampling(temperature = 1.0, topK = 64, topP = 0.95)
        /** Always the most likely token: the same answer every time, for tests, benches and scenarios. */
        val GREEDY = Sampling(temperature = 1.0, topK = 1, topP = 1.0)
    }
}

/**
 * One request. The service is stateless between requests: send the whole conversation each time (it starts a
 * fresh runtime conversation per request, so one feature's history never leaks into another's).
 * Requests whose messages are longer than the model's context are refused with [LlmError.TOO_LONG].
 */
data class LlmRequest(
    val messages: List<LlmMessage>,
    val tools: List<ToolSpec> = emptyList(),
    val settings: Sampling = Sampling.DEFAULT,
    val maxTokens: Int = 512,
) {
    init {
        require(messages.isNotEmpty()) { "a request needs at least one message" }
        require(maxTokens in 1..MAX_OUTPUT_TOKENS) { "maxTokens must be 1..$MAX_OUTPUT_TOKENS" }
        require(tools.map { it.name }.toSet().size == tools.size) { "tool names must be unique" }
    }

    companion object {
        const val MAX_OUTPUT_TOKENS = 2048
    }
}

sealed interface LlmEvent {
    /** A piece of the answer, in order. Plain text: show it as text, never as HTML, links or code. */
    data class Token(val text: String) : LlmEvent
    /**
     * The model proposes a call. [argsJson] is a JSON object as the model produced it, unchecked: pass it to
     * [ToolSpec.check] before anything runs.
     */
    data class ToolCallProposed(val name: String, val argsJson: String) : LlmEvent
    data class Done(val stats: LlmStats) : LlmEvent
    data class Error(val reason: LlmError, val message: String) : LlmEvent
}

enum class LlmError {
    /** No verified model installed. */
    NO_MODEL,
    /** The model could not be loaded (file, memory, GPU). */
    LOAD_FAILED,
    /** The prompt does not fit the context. */
    TOO_LONG,
    /** The runtime failed while answering. */
    INFERENCE_FAILED,
}

/** Speeds and counts for one answer. Counts are null when the runtime did not report them. */
data class LlmStats(
    val modelId: String,
    val backend: String,
    val promptTokens: Int?,
    val outputTokens: Int?,
    val prefillTokensPerSecond: Double?,
    val decodeTokensPerSecond: Double?,
    val timeToFirstTokenMs: Long?,
    val totalMs: Long,
    /** True if this request had to load the model first (its time is not in [totalMs]). */
    val loadedModel: Boolean = false,
    val loadMs: Long? = null,
)

data class LlmResult(
    val text: String,
    val toolCalls: List<LlmEvent.ToolCallProposed>,
    val stats: LlmStats?,
    val error: LlmEvent.Error?,
)
