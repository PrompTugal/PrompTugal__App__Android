package ai.riskmandate.app.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A scripted [LlmService] for previews, tests and qa. In main (not test) so any feature's tests and previews can
 * use it. Same rules as the real one: one request at a time, cancellation by coroutine, ends with Done or Error,
 * never runs a tool.
 *
 * [reply] decides what each request gets: the text to stream (split into word-sized tokens) and any tool calls.
 * [tokenDelayMs] slows the stream down, so a Stop button can be tested.
 */
class FakeLlmService(
    initial: LlmState = LlmState.Ready(MODEL_ID, "fake"),
    private val tokenDelayMs: Long = 0,
    private val reply: (LlmRequest) -> FakeReply = { FakeReply("This is a fake answer.") },
) : LlmService {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<LlmState> = _state.asStateFlow()
    private val lock = Mutex()

    /** Every request received, in order. */
    val requests: List<LlmRequest> get() = synchronized(received) { received.toList() }
    private val received = mutableListOf<LlmRequest>()

    fun setState(state: LlmState) {
        _state.value = state
    }

    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        lock.withLock {
            synchronized(received) { received += request }
            val s = _state.value
            if (s !is LlmState.Ready && s !is LlmState.Idle) {
                emit(LlmEvent.Error(LlmError.NO_MODEL, "no model is installed"))
                return@withLock
            }
            val r = reply(request)
            r.error?.let {
                emit(it)
                return@withLock
            }
            var count = 0
            tokens(r.text).forEach {
                if (tokenDelayMs > 0) delay(tokenDelayMs)
                emit(LlmEvent.Token(it))
                count++
            }
            r.toolCalls.forEach { emit(it) }
            emit(
                LlmEvent.Done(
                    LlmStats(
                        modelId = MODEL_ID, backend = "fake", promptTokens = request.messages.sumOf { tokens(it.text).size },
                        outputTokens = count, prefillTokensPerSecond = null, decodeTokensPerSecond = null,
                        timeToFirstTokenMs = 0, totalMs = count * tokenDelayMs,
                    ),
                ),
            )
        }
    }

    companion object {
        const val MODEL_ID = "fake"

        /** Word-sized pieces, spaces kept, so joining them gives the text back. */
        fun tokens(text: String): List<String> = Regex("\\S+\\s*|\\s+").findAll(text).map { it.value }.toList()
    }
}

data class FakeReply(
    val text: String = "",
    val toolCalls: List<LlmEvent.ToolCallProposed> = emptyList(),
    /** If set, the stream emits only this. */
    val error: LlmEvent.Error? = null,
)
