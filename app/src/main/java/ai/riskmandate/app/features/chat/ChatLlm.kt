package ai.riskmandate.app.features.chat

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** One installed model, as the chat picker shows it. */
data class ChatModelInfo(val id: String, val name: String)

enum class ChatRole { USER, ASSISTANT }

/** One turn handed to the model. */
data class ChatTurn(val role: ChatRole, val text: String)

/** What the service reports when an answer ends. Null where the runtime did not say; chat then counts pieces and times it itself. */
data class ChatAnswerStats(val outputTokens: Int?, val tokensPerSecond: Double?, val totalMs: Long?)

/**
 * What chat needs from the shared LLM service (dev.llm's `LlmService`). This is a stand-in inside the
 * chat package until that interface lands on `dev`; then a thin adapter replaces it and nothing else
 * in this package changes. Features never load models themselves.
 *
 * [stream] emits the answer piece by piece (one emission is about one token). Stopping is cancelling
 * the collector: the implementation must stop generating when its flow is cancelled.
 */
interface ChatLlm {
    /** Models installed on this device right now. Empty when none (the chat then explains how to set one up). */
    suspend fun installedModels(): List<ChatModelInfo>

    /** [system] is an instruction block, or null; [turns] ends with the user's last message. */
    fun stream(modelId: String, system: String?, turns: List<ChatTurn>, onStats: (ChatAnswerStats) -> Unit = {}): Flow<String>
}

/** Used until the shared service is wired: no model, so the chat shows its empty state. */
object NoLlm : ChatLlm {
    override suspend fun installedModels(): List<ChatModelInfo> = emptyList()
    override fun stream(modelId: String, system: String?, turns: List<ChatTurn>, onStats: (ChatAnswerStats) -> Unit): Flow<String> = flowOf()
}

/** Test double: replays [pieces] with an optional [delayMs] between them, and records what it was asked. */
class FakeChatLlm(
    var models: List<ChatModelInfo> = listOf(ChatModelInfo("fake", "Fake model")),
    var pieces: List<String> = listOf("Hello", " from", " the", " device"),
    var delayMs: Long = 0,
    var failAfter: Int = -1,
) : ChatLlm {
    data class Call(val modelId: String, val system: String?, val turns: List<ChatTurn>)

    val calls = mutableListOf<Call>()
    var cancelled = 0

    /** If set, reported when an answer ends, as the real service's Done event does. */
    var reportedStats: ChatAnswerStats? = null

    override suspend fun installedModels() = models

    override fun stream(modelId: String, system: String?, turns: List<ChatTurn>, onStats: (ChatAnswerStats) -> Unit): Flow<String> = flow {
        calls += Call(modelId, system, turns)
        try {
            pieces.forEachIndexed { i, p ->
                if (failAfter == i) throw IllegalStateException("fake failure")
                if (delayMs > 0) delay(delayMs)
                emit(p)
            }
            reportedStats?.let(onStats)
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelled++
            throw e
        }
    }
}
