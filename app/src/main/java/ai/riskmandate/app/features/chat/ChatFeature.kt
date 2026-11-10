package ai.riskmandate.app.features.chat

import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.features.HoldIdleWhile
import ai.riskmandate.app.llm.prompts.Prompts
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineDispatcher
import ai.riskmandate.app.llm.LlmLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    const val BADGE_CLOUD = "chat_badge_cloud"
    const val NETWORK = "chat_network"
    const val OPEN_MODELS = "chat_open_models"
    const val ASK_DATA = "chat_ask_data"
    const val ASK_DATA_BANNER = "chat_ask_data_banner"
    const val ASK_DATA_CANCEL = "chat_ask_data_cancel"
    fun sources(index: Int) = "chat_sources_$index"
    const val SOURCE_CLOUD = "chat_source_cloud"
    const val SOURCE_DEVICE = "chat_source_device"
    const val INCLUDE_HISTORY = "chat_include_history"
    const val SHOW_SENT = "chat_show_sent"
    const val SENT_DIALOG = "chat_sent_dialog"
    const val FIRST_SEND_DIALOG = "chat_first_send_dialog"
    const val FIRST_SEND_CONFIRM = "chat_first_send_confirm"
    const val FIRST_SEND_CANCEL = "chat_first_send_cancel"
    const val USE_DEVICE = "chat_use_device"
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

/**
 * The dispatcher for reading and writing history. The app uses the default (IO). Tests provide an immediate one, so
 * loading history does not hop to a real thread that the compose test clock cannot see (a slow CI runner made that
 * hop a flake).
 */
internal val LocalChatIo = compositionLocalOf<CoroutineDispatcher> { Dispatchers.IO }

/** Where history is kept. Null (the default) means the app-private file; tests provide a memory store. */
val LocalChatStore = compositionLocalOf<ChatHistoryStore?> { null }

/**
 * Feature H: chat with a language model, on this device or (when staff have switched the cloud on) through
 * OpenRouter. The screen always says which. Model output is drawn as plain text only; the one thing taken from it is
 * riskmandate.ai links that pass `Links.isAllowed`, offered as buttons the user taps.
 */
object ChatFeature : Feature {
    override val id = "chat"
    override val title = R.string.feature_chat_title
    override val enabled = true

    // The cloud route is built and tested, but chat does not ask the host for the cloud service until the review
    // records say chat was reviewed for it: the architect's FeatureContextTest and the host's CloudHostTest list the
    // "usesCloud" features, and security and the architect add "chat" there. Then this is the one line to flip to true
    // (the host still passes cloudLlm only with a key stored, the staff switch on and stand mode off, security M.4).
    override val usesCloud = CLOUD_REVIEWED
    override val kicker = R.string.chat_kicker
    override val summary = R.string.chat_summary

    @Composable
    override fun Content(context: FeatureContext) {
        // The host's service (null when no model is installed or the host has none): the no-model state then shows.
        val local = remember(context.llm) { context.llm?.let { LlmServiceChat(it) } ?: NoLlm }
        // The cloud one, only if the host gave it and it really says CLOUD; chat never mixes the two fields.
        val cloud = remember(context.cloudLlm) { context.cloudLlm?.takeIf { it.location == LlmLocation.CLOUD }?.let { LlmServiceChat(it) } }
        // "Ask our data" answers on this device only: it gets context.llm (never cloudLlm), and refuses anything not local.
        val assets = LocalContext.current.assets
        val data = remember(context.llm) { context.llm?.takeIf { it.location == LlmLocation.LOCAL }?.let { GroundedDataAsker(it, assets) } }
        ChatContent(context, local, cloud, data)
    }
}

private const val MODELS_ID = "models"

/** False until security and the architect have recorded a cloud review for chat (see [ChatFeature.usesCloud]). */
private const val CLOUD_REVIEWED = false

/** The chat for a given on-device [llm] and optional [cloud] service. Internal so tests can use [FakeChatLlm] directly. */
@Composable
internal fun ChatContent(context: FeatureContext, llm: ChatLlm, cloud: ChatLlm? = null, dataAsker: DataAsker? = null) {
    val android = LocalContext.current
    val provided = LocalChatStore.current
    val io = LocalChatIo.current
    val scope = rememberCoroutineScope()
    val controller = remember(llm, cloud, dataAsker, provided, io) {
        val store = provided ?: FileChatHistoryStore(File(android.applicationContext.noBackupFilesDir, "feature_chat"))
        ChatController(
            llm, store, scope, baseSystem = { Prompts.load(android.assets, ChatBasePrompt.ID) }, io = io,
            cloud = cloud, dataAsker = dataAsker,
            // The two bundled riskmandate.ai texts, read off the main thread, only used on the cloud route.
            cloudReference = {
                withContext(io) {
                    listOf(R.raw.feature_chat_riskmandate, R.raw.feature_chat_abp)
                        .joinToString("\n\n") { id -> android.resources.openRawResource(id).use { it.readBytes().toString(Charsets.UTF_8).trim() } }
                }
            },
        )
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
