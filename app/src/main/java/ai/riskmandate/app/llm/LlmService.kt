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

    /** Where answers are computed. Every "on this device" badge reads this (architect, cloud ruling). */
    val location: LlmLocation get() = LlmLocation.LOCAL

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

    /**
     * Drops the loaded model, so the next request loads it again: an honest cold-load measurement. Only the
     * Workbench and the bench call it (security R-wb-1). The default does nothing (services without a model).
     */
    suspend fun unload() {}
}

/** Where a service runs the model. llm/ never imports the cloud package; a cloud service says CLOUD itself. */
enum class LlmLocation { LOCAL, CLOUD }

/** Which backend a request wants (architect: on the request, not a host setting). */
enum class BackendPreference {
    /** GPU first, CPU if the GPU cannot load (the default for every feature). */
    AUTO,
    /** GPU; still falls back to CPU if the GPU cannot load, and the stats say so. */
    GPU,
    /** CPU only. */
    CPU,
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
    /** The host reloads the model when the loaded backend does not match (a cold start, reported). */
    val backend: BackendPreference = BackendPreference.AUTO,
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

/** Where a figure in [LlmStats] comes from. Shown next to the figure in the Workbench. */
enum class StatSource {
    /** LiteRT-LM's own benchmark counters. */
    RUNTIME,
    /** Measured in Kotlin: wall clock, or pieces of streamed text counted as tokens. */
    MEASURED,
}

/** Why an answer ended. */
enum class LlmStopReason {
    /** The model finished on its own. */
    DONE,
    /** It reached the request's maxTokens. */
    MAX_TOKENS,
    /** The caller stopped it (cancelled the collector); set by the caller, since a cancelled stream emits nothing. */
    CANCELLED,
    /** It failed; the [LlmEvent.Error] carries the reason code. */
    ERROR,
}

/**
 * Speeds and counts for one answer.
 * - [timeToFirstTokenMs], [totalMs] and [loadMs] are always MEASURED (wall clock in Kotlin).
 * - [promptTokens] (input tokens) and [outputTokens] come from [countsFrom]; [prefillTokensPerSecond] and
 *   [decodeTokensPerSecond] from [speedsFrom]. RUNTIME when LiteRT-LM reported them, MEASURED otherwise
 *   (output tokens then = streamed pieces, decode speed = pieces after the first over the time after the first).
 *   Null when neither is available (prefill speed and input tokens cannot be measured from outside).
 * - [backend] is the backend actually used; [requestedBackend] what was asked for; [fellBackToCpu] when the
 *   GPU could not load and the CPU was used instead.
 */
data class LlmStats(
    val modelId: String,
    val backend: String,
    val promptTokens: Int?,
    val outputTokens: Int?,
    val prefillTokensPerSecond: Double?,
    val decodeTokensPerSecond: Double?,
    val timeToFirstTokenMs: Long?,
    val totalMs: Long,
    /** True if this request had to load the model first, a cold start (its time is not in [totalMs]). */
    val loadedModel: Boolean = false,
    val loadMs: Long? = null,
    val countsFrom: StatSource = StatSource.MEASURED,
    val speedsFrom: StatSource = StatSource.MEASURED,
    val requestedBackend: String? = null,
    val fellBackToCpu: Boolean = false,
    val stopReason: LlmStopReason = LlmStopReason.DONE,
    /** The request's maxTokens, so a reader can see why [stopReason] is MAX_TOKENS. */
    val maxTokens: Int? = null,
    /** Where this answer was computed. */
    val location: LlmLocation = LlmLocation.LOCAL,
) {
    /** Architect's names for the same values. */
    val backendUsed: String get() = backend
    val fellBack: Boolean get() = fellBackToCpu
    val coldLoad: Boolean get() = loadedModel

    companion object {
        /**
         * Builds stats from what the runtime reported ([runtime*], null or non-positive when it did not) and what
         * Kotlin measured ([pieces] streamed, wall-clock times). Pure, so the labelling is tested on the JVM.
         */
        fun from(
            modelId: String, backend: String, maxTokens: Int,
            runtimePromptTokens: Int?, runtimeOutputTokens: Int?, runtimePrefillTps: Double?, runtimeDecodeTps: Double?,
            pieces: Int, timeToFirstTokenMs: Long?, totalMs: Long,
        ): LlmStats {
            val countsRuntime = (runtimeOutputTokens ?: 0) > 0
            val speedsRuntime = (runtimeDecodeTps ?: 0.0) > 0.0
            val output = if (countsRuntime) runtimeOutputTokens else pieces
            val measuredDecode = timeToFirstTokenMs?.let { t -> (totalMs - t).takeIf { it > 0 && pieces > 1 }?.let { (pieces - 1) * 1000.0 / it } }
            return LlmStats(
                modelId = modelId, backend = backend,
                promptTokens = runtimePromptTokens?.takeIf { countsRuntime && it > 0 },
                outputTokens = output,
                prefillTokensPerSecond = runtimePrefillTps?.takeIf { speedsRuntime && it > 0.0 },
                decodeTokensPerSecond = if (speedsRuntime) runtimeDecodeTps else measuredDecode,
                timeToFirstTokenMs = timeToFirstTokenMs, totalMs = totalMs,
                countsFrom = if (countsRuntime) StatSource.RUNTIME else StatSource.MEASURED,
                speedsFrom = if (speedsRuntime) StatSource.RUNTIME else StatSource.MEASURED,
                stopReason = if ((output ?: 0) >= maxTokens) LlmStopReason.MAX_TOKENS else LlmStopReason.DONE,
                maxTokens = maxTokens,
            )
        }
    }
}

data class LlmResult(
    val text: String,
    val toolCalls: List<LlmEvent.ToolCallProposed>,
    val stats: LlmStats?,
    val error: LlmEvent.Error?,
)
