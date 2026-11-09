package ai.riskmandate.app.features.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ChatError { SAVE_FAILED }

/** Everything the screen draws. */
data class ChatState(
    val conversations: List<Conversation> = listOf(Conversation("new")),
    val currentId: String = "new",
    val models: List<ChatModelInfo> = emptyList(),
    val modelId: String? = null,
    val modelsLoaded: Boolean = false,
    val generating: Boolean = false,
    val error: ChatError? = null,
) {
    val current: Conversation get() = conversations.firstOrNull { it.id == currentId } ?: Conversation(currentId)
    val canSend: Boolean get() = modelId != null && !generating
}

/**
 * The chat's logic, with no Android UI in it. Created by the screen in `remember`, with the composition's
 * coroutine scope, so everything stops when the user leaves the feature. Main thread only (the scope's
 * dispatcher); disk work is moved to [io].
 */
class ChatController(
    private val llm: ChatLlm,
    private val store: ChatHistoryStore,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    var state by mutableStateOf(ChatState())
        private set

    private var job: Job? = null
    private val saveLock = Mutex()

    /**
     * Reads saved history and the installed models. Call once. [reopen] is the id of the chat that was open before a
     * rotation (kept by the screen in `rememberSaveable`): it is reselected when it was saved, else a new empty chat opens.
     */
    suspend fun start(reopen: String? = null) {
        val saved = withContext(io) { store.load() }
        val models = llm.installedModels()
        val fresh = Conversation(newId())
        // Open on a new empty chat; old chats are one tap away in the history list.
        val conversations = (listOf(fresh) + saved.filter { !it.isEmpty }).take(ChatLimits.MAX_CONVERSATIONS)
        state = state.copy(
            conversations = conversations, currentId = reopen?.takeIf { id -> conversations.any { it.id == id } } ?: fresh.id, models = models,
            modelId = state.modelId?.takeIf { id -> models.any { it.id == id } } ?: models.firstOrNull()?.id,
            modelsLoaded = true,
        )
    }

    fun selectModel(id: String) {
        if (state.generating || state.models.none { it.id == id }) return
        state = state.copy(modelId = id)
    }

    fun select(id: String) {
        if (state.generating || state.conversations.none { it.id == id }) return
        state = state.copy(currentId = id)
    }

    /** Stops the answer in progress. What was received stays, marked as stopped. */
    fun stop() {
        job?.cancel()
    }

    fun newChat() {
        stop()
        if (state.current.isEmpty) return
        val fresh = Conversation(newId())
        state = state.copy(conversations = (listOf(fresh) + state.conversations).take(ChatLimits.MAX_CONVERSATIONS), currentId = fresh.id)
    }

    /** Deletes every saved chat, here and on disk, and opens a new empty one. */
    fun clearHistory() {
        job?.cancel()
        val fresh = Conversation(newId())
        state = state.copy(conversations = listOf(fresh), currentId = fresh.id, generating = false, error = null)
        scope.launch(io) { saveLock.withLock { try { store.clear() } catch (e: Exception) { /* nothing left to do */ } } }
    }

    /** Deletes saved history right now, on the calling thread, for use when the screen is leaving (the scope is ending). */
    fun wipeNow() {
        job?.cancel()
        try { store.clear() } catch (e: Exception) { /* nothing left to do */ }
    }

    /** Sends [text] (optionally grounded in [groundingText]). Ignored when blank, busy or no model is installed. */
    fun send(text: String, groundingText: String? = null) {
        val model = state.modelId
        val input = text.trim().take(ChatLimits.MAX_INPUT_CHARS)
        if (model == null || input.isEmpty() || state.generating) return
        val convId = state.currentId
        val history = state.current.messages
        update(convId) { it + ChatMessage(ChatRole.USER, input) + ChatMessage(ChatRole.ASSISTANT, "") }
        state = state.copy(generating = true, error = null)
        scope.launch { persistNow() } // the visitor's message is on disk before the answer starts
        val turns = ChatPrompt.turns(history + ChatMessage(ChatRole.USER, input))
        val system = groundingText?.let(ChatPresets::system)
        val started = clock()
        job = scope.launch {
            var tokens = 0
            var firstAt = 0L
            val sb = StringBuilder()
            var stopped = false
            var failed = false
            var failure: String? = null
            var reported: ChatAnswerStats? = null
            try {
                llm.stream(model, system, turns) { reported = it }.collect { piece ->
                    if (tokens == 0) firstAt = clock()
                    tokens++
                    if (sb.length < ChatLimits.MAX_MESSAGE_CHARS) sb.append(piece)
                    val now = clock()
                    replaceLast(convId, ChatMessage(ChatRole.ASSISTANT, sb.toString(), tokens, 0, ChatStats.tokensPerSecond(tokens, firstAt, now)))
                }
            } catch (e: CancellationException) {
                stopped = true
            } catch (e: Exception) {
                failed = true
                failure = (e as? ChatLlmException)?.reason
            } finally {
                val end = clock()
                // The service's own numbers when it gave them (real tokens, real decode speed, answer time without model load).
                val r = reported
                val shownTokens = r?.outputTokens ?: tokens
                val shownTps = r?.tokensPerSecond ?: if (tokens > 0) ChatStats.tokensPerSecond(tokens, firstAt, end) else 0.0
                replaceLast(convId, ChatMessage(
                    ChatRole.ASSISTANT, sb.toString(), shownTokens, r?.totalMs ?: (end - started), shownTps, stopped, failed, failure,
                ))
                state = state.copy(generating = false)
                // Also when the scope is ending (a rotation, leaving the screen): the partial answer is written anyway.
                withContext(NonCancellable) { persistNow() }
            }
        }
    }

    private fun update(id: String, f: (List<ChatMessage>) -> List<ChatMessage>) {
        state = state.copy(conversations = state.conversations.map { if (it.id == id) it.copy(messages = f(it.messages)) else it })
    }

    private fun replaceLast(id: String, m: ChatMessage) = update(id) { if (it.isEmpty()) it else it.dropLast(1) + m }

    /** Saves every chat that has something in it. A placeholder for an answer that has not started yet is not saved. */
    private suspend fun persistNow() {
        val snapshot = state.conversations.map { c ->
            c.copy(messages = c.messages.filterNot { it.role == ChatRole.ASSISTANT && it.text.isEmpty() && !it.failed && !it.stopped })
        }.filter { !it.isEmpty }
        val ok = withContext(io) { saveLock.withLock { runCatching { store.save(snapshot) }.isSuccess } }
        if (!ok) state = state.copy(error = ChatError.SAVE_FAILED)
    }
}
