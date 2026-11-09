package ai.riskmandate.app.features.chat

import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.features.HoldIdleWhile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import java.io.File

object ChatTags {
    const val BADGE = "chat_badge"
    const val NETWORK = "chat_network"
    const val OPEN_MODELS = "chat_open_models"
    const val MODEL_LABEL = "chat_model_label"
    const val INPUT = "chat_input"
    const val SEND = "chat_send"
    const val STOP = "chat_stop"
    const val NEW = "chat_new"
    const val HISTORY = "chat_history"
    const val CLEAR = "chat_clear"
    const val CLEAR_CONFIRM = "chat_clear_confirm"
    const val MESSAGES = "chat_messages"
    const val STATS = "chat_stats"
    const val NO_MODEL = "chat_no_model"
    const val SAVE_ERROR = "chat_save_error"
    fun message(index: Int) = "chat_message_$index"
    fun preset(id: String) = "chat_preset_$id"
    fun model(id: String) = "chat_model_$id"
    fun conversation(index: Int) = "chat_conversation_$index"
    fun link(index: Int) = "chat_link_$index"
}

/** Where history is kept. Null (the default) means the app-private file; tests provide a memory store. */
val LocalChatStore = compositionLocalOf<ChatHistoryStore?> { null }

/**
 * Feature H: chat with the on-device model. Off until it works end to end on the tablet (Dinis checks).
 * Model output is drawn as plain text only; the one thing taken from it is riskmandate.ai links that pass
 * `Links.isAllowed`, offered as buttons the user taps.
 */
object ChatFeature : Feature {
    override val id = "chat"
    override val title = R.string.feature_chat_title
    override val enabled = false
    override val kicker = R.string.chat_kicker
    override val summary = R.string.chat_summary

    @Composable
    override fun Content(context: FeatureContext) {
        // The host's service (null when no model is installed or the host has none): the no-model state then shows.
        val llm = remember(context.llm) { context.llm?.let { LlmServiceChat(it) } ?: NoLlm }
        ChatContent(context, llm)
    }
}

private const val MODELS_ID = "models"

/** The chat for a given [ChatLlm]. Internal so tests can use [FakeChatLlm] directly. */
@Composable
internal fun ChatContent(context: FeatureContext, llm: ChatLlm) {
    val android = LocalContext.current
    val provided = LocalChatStore.current
    val scope = rememberCoroutineScope()
    val controller = remember(llm, provided) {
        val store = provided ?: FileChatHistoryStore(File(android.applicationContext.noBackupFilesDir, "feature_chat"))
        ChatController(llm, store, scope)
    }
    // The open chat survives a rotation (the Activity is recreated): its id is saved, small, and reselected after loading.
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val reopen = remember { openId }
    LaunchedEffect(controller) { controller.start(reopen = reopen) }
    LaunchedEffect(controller.state.currentId) { openId = controller.state.currentId }
    if (ChatPolicy.WIPE_ON_LEAVE) DisposableEffect(controller) { onDispose { controller.wipeNow() } }
    // Generation can take a while: do not let stand mode's idle reset wipe the chat mid-answer.
    context.HoldIdleWhile(controller.state.generating)
    // Features name each other by id only; the host decides (and refuses it in stand mode).
    ChatScreen(controller, onOpenModels = { context.openFeature(MODELS_ID) })
}
