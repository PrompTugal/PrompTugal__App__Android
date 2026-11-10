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
    /** False when the answers come from the cloud: the badge says so. Follows the active service, never a constant. */
    val local: Boolean = true,
    /** True when the host handed chat a ready cloud service (a key is stored, the staff switch is on, stand mode is off). */
    val cloudAvailable: Boolean = false,
    /** True while the cloud service is the one answering. Never saved: it starts on the default after a rotation or restart. */
    val useCloud: Boolean = false,
    /** Cloud only: send the earlier messages of this chat too. Off by default, never saved. */
    val includeHistory: Boolean = false,
    /** True when "Ask our data" can be used: a data asker exists and a model is installed on this device. */
    val dataAvailable: Boolean = false,
    /** True while the next question is armed to be answered from our data (once; never saved). */
    val askData: Boolean = false,
    val generating: Boolean = false,
    val error: ChatError? = null,
) {
    val current: Conversation get() = conversations.firstOrNull { it.id == currentId } ?: Conversation(currentId)
    val canSend: Boolean get() = modelId != null && !generating
}

/** Exactly what the next send would hand to the service, for the "show what will be sent" view. Plain text. */
data class SendPreview(val system: String?, val turns: List<ChatTurn>, val viaCloud: Boolean, val modelId: String?)

/**
 * The chat's logic, with no Android UI in it. Created by the screen in `remember`, with the composition's
 * coroutine scope, so everything stops when the user leaves the feature. Main thread only (the scope's
 * dispatcher); disk work is moved to [io].
 *
 * Two services: [llm] is the on-device one; [cloud], when the host gave chat one, is the OpenRouter one. The cloud
 * answers by default while it is available and ready, and [setUseCloud] switches per chat session (never saved).
 * What is sent to the cloud is only the current message plus the system block unless [setIncludeHistory] is on.
 */
class ChatController(
    private val llm: ChatLlm,
    private val store: ChatHistoryStore,
    private val scope: CoroutineScope,
    /** The RiskMandate base block (the shared base prompt), or null if it cannot be read. Called off the main thread. */
    private val baseSystem: suspend () -> String?,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val cloud: ChatLlm? = null,
    /** The bundled riskmandate.ai texts the cloud route carries as its reference for RiskMandate questions, or null. */
    private val cloudReference: suspend () -> String? = { null },
    /** Answers questions from the bundled data on this device (never the cloud), or null when there is none. */
    private val dataAsker: DataAsker? = null,
) {
    var state by mutableStateOf(ChatState())
        private set

    private var job: Job? = null
    private val saveLock = Mutex()
    private var deviceModelId: String? = null

    /**
     * Reads saved history and the installed models. Call once. [reopen] is the id of the chat that was open before a
     * rotation (kept by the screen in `rememberSaveable`): it is reselected when it was saved, else a new empty chat opens.
     */
    suspend fun start(reopen: String? = null) {
        val saved = withContext(io) { store.load() }
        // The cloud is offered only when its service reports a model ready; otherwise chat is on the device only.
        val cloudModels = cloud?.installedModels().orEmpty()
        val useCloud = cloud != null && cloudModels.isNotEmpty()
        val deviceModels = llm.installedModels()
        deviceModelId = deviceModels.firstOrNull()?.id
        val models = if (useCloud) cloudModels else deviceModels
        val fresh = Conversation(newId())
        // Open on a new empty chat; old chats are one tap away in the history list.
        val conversations = (listOf(fresh) + saved.filter { !it.isEmpty }).take(ChatLimits.MAX_CONVERSATIONS)
        state = state.copy(
            conversations = conversations, currentId = reopen?.takeIf { id -> conversations.any { it.id == id } } ?: fresh.id, models = models,
            modelId = state.modelId?.takeIf { id -> models.any { it.id == id } } ?: models.firstOrNull()?.id,
            modelsLoaded = true, local = if (useCloud) cloud!!.isLocal else llm.isLocal,
            cloudAvailable = useCloud, useCloud = useCloud, includeHistory = false,
            dataAvailable = dataAsker != null && deviceModels.isNotEmpty(), askData = false,
        )
    }

    /** Chooses the cloud or the device for the next messages (only when both exist, and not while answering). Never saved. */
    fun setUseCloud(on: Boolean) {
        if (state.generating || !state.cloudAvailable || on == state.useCloud) return
        val target = if (on) cloud!! else llm
        scope.launch {
            val models = target.installedModels()
            state = state.copy(useCloud = on, includeHistory = false, models = models, modelId = models.firstOrNull()?.id, local = target.isLocal)
        }
    }

    /** Cloud only: whether the earlier messages of this chat go with the next message. Off by default, never saved. */
    fun setIncludeHistory(on: Boolean) {
        if (state.useCloud) state = state.copy(includeHistory = on)
    }

    /** Arms (or disarms) "Ask our data" for the next question only. Ignored when it is not available or while answering. */
    fun armAskData(on: Boolean) {
        if (state.generating || (on && !state.dataAvailable)) return
        state = state.copy(askData = on)
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
        state = state.copy(conversations = (listOf(fresh) + state.conversations).take(ChatLimits.MAX_CONVERSATIONS), currentId = fresh.id, includeHistory = false)
    }

    /** Deletes every saved chat, here and on disk, and opens a new empty one. */
    fun clearHistory() {
        job?.cancel()
        val fresh = Conversation(newId())
        state = state.copy(conversations = listOf(fresh), currentId = fresh.id, generating = false, error = null, includeHistory = false)
        scope.launch(io) { saveLock.withLock { try { store.clear() } catch (e: Exception) { /* nothing left to do */ } } }
    }

    /** Deletes saved history right now, on the calling thread, for use when the screen is leaving (the scope is ending). */
    fun wipeNow() {
        job?.cancel()
        try { store.clear() } catch (e: Exception) { /* nothing left to do */ }
    }

    /**
     * What a send of [text] would hand to the active service right now: the system block and the messages. The same
     * builder the real send uses, so the "show what will be sent" view cannot differ from what is sent.
     */
    suspend fun previewSend(text: String, groundingText: String? = null): SendPreview =
        buildRequest(text.trim().take(ChatLimits.MAX_INPUT_CHARS), groundingText?.let(ChatPresets::system), state.current.messages, state.useCloud && cloud != null, state.includeHistory)

    private suspend fun buildRequest(input: String, grounded: String?, history: List<ChatMessage>, viaCloud: Boolean, includeHistory: Boolean): SendPreview {
        val user = ChatMessage(ChatRole.USER, input)
        // The cloud gets only the current message unless the earlier ones were switched on; the device uses the whole chat.
        val turns = if (viaCloud && !includeHistory) ChatPrompt.turns(listOf(user)) else ChatPrompt.turns(history + user)
        val base = runCatching { baseSystem() }.getOrNull()
        val system = when {
            grounded != null -> grounded // a preset carries its own text, as before
            viaCloud -> listOfNotNull(base, runCatching { cloudReference() }.getOrNull()?.let(ChatPresets::systemWithReference)).joinToString("\n\n").ifBlank { null }
            else -> base
        }
        return SendPreview(system, turns, viaCloud, state.modelId)
    }

    /** Sends [text] (optionally grounded in [groundingText]). Ignored when blank, busy or no model is installed. */
    fun send(text: String, groundingText: String? = null) {
        val model = state.modelId
        val input = text.trim().take(ChatLimits.MAX_INPUT_CHARS)
        if (model == null || input.isEmpty() || state.generating) return
        if (state.askData && dataAsker != null && state.dataAvailable) return sendData(input, dataAsker)
        val convId = state.currentId
        val history = state.current.messages
        val viaCloud = state.useCloud && cloud != null
        val withHistory = state.includeHistory
        val backend = if (viaCloud) cloud!! else llm
        val via = if (viaCloud) ChatVia.cloud(model) else ChatVia.device(model)
        update(convId) { it + ChatMessage(ChatRole.USER, input) + ChatMessage(ChatRole.ASSISTANT, "", via = via) }
        state = state.copy(generating = true, error = null)
        scope.launch { persistNow() } // the visitor's message is on disk before the answer starts
        val grounded = groundingText?.let(ChatPresets::system)
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
                val request = buildRequest(input, grounded, history, viaCloud, withHistory)
                backend.stream(model, request.system, request.turns) { reported = it }.collect { piece ->
                    if (tokens == 0) firstAt = clock()
                    tokens++
                    if (sb.length < ChatLimits.MAX_MESSAGE_CHARS) sb.append(piece)
                    val now = clock()
                    replaceLast(convId, ChatMessage(ChatRole.ASSISTANT, sb.toString(), tokens, 0, ChatStats.tokensPerSecond(tokens, firstAt, now), via = via))
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
                    via = via, costUsd = r?.costUsd,
                ))
                state = state.copy(generating = false)
                // Also when the scope is ending (a rotation, leaving the screen): the partial answer is written anyway.
                withContext(NonCancellable) { persistNow() }
            }
        }
    }

    /**
     * "Ask our data": the question goes to the data asker, which searches the bundled data and has the on-device model
     * answer from it. Always the device (the asker only accepts a local service), whichever service is chosen for
     * normal messages. One question, no earlier messages. The answer keeps its sources, credit and made-up numbers.
     */
    private fun sendData(input: String, asker: DataAsker) {
        val convId = state.currentId
        val via = ChatVia.device(deviceModelId ?: "device")
        update(convId) { it + ChatMessage(ChatRole.USER, input) + ChatMessage(ChatRole.ASSISTANT, "", via = via) }
        state = state.copy(generating = true, error = null, askData = false)
        scope.launch { persistNow() }
        val started = clock()
        job = scope.launch {
            var partial = ""
            var result: DataAnswer? = null
            var stopped = false
            var failed = false
            var failure: String? = null
            try {
                result = asker.ask(input) { text ->
                    partial = text.take(ChatLimits.MAX_MESSAGE_CHARS)
                    replaceLast(convId, ChatMessage(ChatRole.ASSISTANT, partial, via = via))
                }
            } catch (e: CancellationException) {
                stopped = true
            } catch (e: Exception) {
                failed = true
                failure = (e as? ChatLlmException)?.reason
            } finally {
                val r = result
                val text = (r?.text ?: partial).take(ChatLimits.MAX_MESSAGE_CHARS)
                replaceLast(convId, ChatMessage(
                    ChatRole.ASSISTANT, text, r?.outputTokens ?: 0, r?.totalMs ?: (clock() - started), r?.tokensPerSecond ?: 0.0,
                    stopped, failed || r?.errorReason != null, failure ?: r?.errorReason, via = via, grounding = r?.grounding,
                ))
                state = state.copy(generating = false)
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
