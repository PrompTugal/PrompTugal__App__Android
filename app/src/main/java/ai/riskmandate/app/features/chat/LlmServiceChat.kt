package ai.riskmandate.app.features.chat

import ai.riskmandate.app.llm.LlmEvent
import ai.riskmandate.app.llm.LlmMessage
import ai.riskmandate.app.llm.LlmRequest
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.LlmState
import ai.riskmandate.app.llm.ModelCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** The model stopped with an error. The message is the service's own: no prompt or answer text. */
class ChatLlmException(message: String) : Exception(message)

/**
 * Chat's view of the shared [LlmService] (the host's, through `FeatureContext.llm`).
 *
 * - The model is the one Models (G) chose: this service has no list, so the "installed" models are the one in
 *   [LlmService.state] (a read-only label in the chat, no picker). The id is a label only; the service answers
 *   with its current model.
 * - The system block goes in as [LlmMessage.system], then the turns; the whole conversation every time (the
 *   service keeps nothing between requests).
 * - Tool proposals are ignored: chat offers no tools. Done ends the answer; an Error becomes an exception.
 * - Stopping is cancelling the collector: the service cancels the native call.
 */
class LlmServiceChat(private val service: LlmService, private val maxTokens: Int = DEFAULT_MAX_TOKENS) : ChatLlm {
    override suspend fun installedModels(): List<ChatModelInfo> = when (val s = service.state.value) {
        LlmState.NoModel -> emptyList()
        is LlmState.Idle -> listOf(info(s.modelId))
        is LlmState.Loading -> listOf(info(s.modelId))
        is LlmState.Ready -> listOf(info(s.modelId))
        is LlmState.Failed -> s.modelId?.let { listOf(info(it)) }.orEmpty()
    }

    override fun stream(modelId: String, system: String?, turns: List<ChatTurn>): Flow<String> = flow {
        val messages = buildList {
            if (system != null) add(LlmMessage.system(system))
            for (t in turns) add(if (t.role == ChatRole.USER) LlmMessage.user(t.text) else LlmMessage.assistant(t.text))
        }
        service.stream(LlmRequest(messages, maxTokens = maxTokens)).collect { e ->
            when (e) {
                is LlmEvent.Token -> emit(e.text)
                is LlmEvent.Error -> throw ChatLlmException(e.reason.name)
                is LlmEvent.Done, is LlmEvent.ToolCallProposed -> Unit
            }
        }
    }

    private fun info(id: String) = ChatModelInfo(id, ModelCatalog.ALL.firstOrNull { it.id == id }?.displayName ?: id)

    companion object {
        const val DEFAULT_MAX_TOKENS = 512
    }
}
