package ai.riskmandate.app.features.chat

import ai.riskmandate.app.Browser
import ai.riskmandate.app.R
import ai.riskmandate.app.ui.ChipKind
import ai.riskmandate.app.ui.FeatureContent
import ai.riskmandate.app.ui.NoticeBanner
import ai.riskmandate.app.ui.StatusChip
import ai.riskmandate.app.ui.UiTokens
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** The chat screen. Content only: the host's FeatureFrame draws the title and Back. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(controller: ChatController) {
    val state = controller.state
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    // Back steps out of the history list before the host takes the user home.
    BackHandler(enabled = showHistory) { showHistory = false }

    fun runPreset(p: Preset) {
        if (!p.enabled) return
        val prompt = androidContext.getString(p.prompt)
        when (p.mode) {
            PresetMode.FILL_INPUT -> input = prompt
            PresetMode.SEND -> {
                val raw = p.groundingRaw
                scope.launch {
                    val grounding = raw?.let { r ->
                        withContext(Dispatchers.IO) { androidContext.resources.openRawResource(r).use { it.readBytes().toString(Charsets.UTF_8) } }
                    }
                    controller.send(prompt, grounding)
                }
            }
        }
    }

    FeatureContent(scrollable = false, maxContentWidth = 840.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiTokens.Gap), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            StatusChip(stringResource(R.string.chat_badge), ChipKind.Allowed, Modifier.testTag(ChatTags.BADGE))
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { controller.newChat(); showHistory = false }, modifier = Modifier.heightIn(min = UiTokens.MinTouch).testTag(ChatTags.NEW)) {
                Text(stringResource(R.string.chat_new))
            }
            OutlinedButton(onClick = { showHistory = !showHistory }, modifier = Modifier.heightIn(min = UiTokens.MinTouch).testTag(ChatTags.HISTORY)) {
                Text(stringResource(if (showHistory) R.string.chat_history_close else R.string.chat_history))
            }
            TextButton(onClick = { confirmClear = true }, modifier = Modifier.heightIn(min = UiTokens.MinTouch).testTag(ChatTags.CLEAR)) {
                Text(stringResource(R.string.chat_clear))
            }
        }
        if (state.models.size > 1) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.chat_model_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 4.dp))
                for (m in state.models) {
                    FilterChip(selected = m.id == state.modelId, onClick = { controller.selectModel(m.id) }, label = { Text(m.name) },
                        enabled = !state.generating, modifier = Modifier.testTag(ChatTags.model(m.id)))
                }
            }
        }
        if (state.error == ChatError.SAVE_FAILED) {
            Text(stringResource(R.string.chat_save_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp).testTag(ChatTags.SAVE_ERROR))
        }

        when {
            showHistory -> HistoryList(state, onPick = { controller.select(it); showHistory = false }, modifier = Modifier.weight(1f))
            state.modelsLoaded && state.models.isEmpty() -> NoModel(Modifier.weight(1f))
            else -> {
                // Presets only on an empty chat: once talking, the messages get the room.
                if (state.modelsLoaded && state.current.isEmpty) {
                    Text(stringResource(R.string.chat_presets), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (p in ChatPresets.ALL) {
                            AssistChip(
                                onClick = { runPreset(p) },
                                enabled = p.enabled && (p.mode == PresetMode.FILL_INPUT || state.canSend),
                                label = { Text(stringResource(p.title)) },
                                modifier = Modifier.heightIn(min = UiTokens.MinTouch).testTag(ChatTags.preset(p.id)),
                            )
                        }
                    }
                    ChatPresets.ALL.firstOrNull { !it.enabled }?.disabledNote?.let {
                        Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (!state.modelsLoaded) {
                    Text(stringResource(R.string.chat_loading), modifier = Modifier.padding(top = 8.dp))
                }
                Messages(state.current, state.generating, Modifier.weight(1f))
            }
        }

        if (!showHistory && state.models.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it.take(ChatLimits.MAX_INPUT_CHARS) },
                    label = { Text(stringResource(R.string.chat_input_label)) },
                    modifier = Modifier.weight(1f).testTag(ChatTags.INPUT), maxLines = 4,
                )
                if (state.generating) {
                    Button(onClick = controller::stop, modifier = Modifier.heightIn(min = UiTokens.MinTouch).testTag(ChatTags.STOP)) { Text(stringResource(R.string.chat_stop)) }
                } else {
                    Button(
                        onClick = { controller.send(input); input = "" },
                        enabled = state.canSend && input.isNotBlank(),
                        modifier = Modifier.heightIn(min = UiTokens.MinTouch).testTag(ChatTags.SEND),
                    ) { Text(stringResource(R.string.chat_send)) }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.chat_clear_title)) },
            text = { Text(stringResource(R.string.chat_clear_body)) },
            confirmButton = {
                TextButton(onClick = { controller.clearHistory(); confirmClear = false; showHistory = false }, modifier = Modifier.testTag(ChatTags.CLEAR_CONFIRM)) {
                    Text(stringResource(R.string.chat_clear_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.chat_cancel)) } },
        )
    }
}

@Composable
private fun NoModel(modifier: Modifier) {
    Column(modifier.padding(top = UiTokens.Gap).testTag(ChatTags.NO_MODEL), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NoticeBanner(stringResource(R.string.chat_no_model_title))
        Text(stringResource(R.string.chat_no_model_body), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HistoryList(state: ChatState, onPick: (String) -> Unit, modifier: Modifier) {
    val saved = state.conversations.filter { !it.isEmpty }
    Column(modifier.padding(top = UiTokens.Gap), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.chat_history_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (saved.isEmpty()) Text(stringResource(R.string.chat_history_empty))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(saved, key = { _, c -> c.id }) { i, c ->
                OutlinedCard(onClick = { onPick(c.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = UiTokens.MinTouch).testTag(ChatTags.conversation(i))) {
                    // Plain text, one line. The title is the user's own first message.
                    Text(c.title().ifEmpty { stringResource(R.string.chat_history_untitled) }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun Messages(conversation: Conversation, generating: Boolean, modifier: Modifier) {
    val list = rememberLazyListState()
    val last = conversation.messages.lastOrNull()
    LaunchedEffect(conversation.id, conversation.messages.size, last?.text?.length) {
        if (conversation.messages.isNotEmpty()) list.scrollToItem(conversation.messages.lastIndex)
    }
    if (conversation.isEmpty) {
        Text(stringResource(R.string.chat_empty_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(vertical = UiTokens.Gap))
        return
    }
    Column(modifier) {
        LazyColumn(Modifier.weight(1f).testTag(ChatTags.MESSAGES), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(conversation.messages) { i, m ->
                MessageBubble(m, running = generating && i == conversation.messages.lastIndex, index = i)
            }
        }
        val answer = conversation.messages.lastOrNull()?.takeIf { it.role == ChatRole.ASSISTANT && !generating && it.tokens > 0 }
        if (answer != null) {
            val tps = String.format(Locale.US, "%.1f", answer.tokensPerSecond)
            val secs = String.format(Locale.US, "%.1f", answer.millis / 1000.0)
            Text(stringResource(R.string.chat_stats, tps, secs, answer.tokens.toString()), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp).testTag(ChatTags.STATS))
        }
    }
}

@Composable
private fun MessageBubble(m: ChatMessage, running: Boolean, index: Int) {
    val mine = m.role == ChatRole.USER
    val android = LocalContext.current
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().testTag(ChatTags.message(index)),
        shape = RoundedCornerShape(UiTokens.CardRadius),
        border = BorderStroke(1.dp, if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(if (mine) R.string.chat_you else R.string.chat_model), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Plain Text: the model's output is never parsed as markup, links or code.
            Text(if (m.text.isEmpty() && running) stringResource(R.string.chat_thinking) else m.text, style = MaterialTheme.typography.bodyLarge)
            if (m.stopped) Text(stringResource(R.string.chat_stopped), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (m.failed) Text(stringResource(R.string.chat_failed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            if (!mine && !running) {
                // The only thing taken from the output: riskmandate.ai links, checked by Links.isAllowed, opened only on a tap.
                ChatLinks.allowed(m.text).forEachIndexed { i, url ->
                    TextButton(onClick = { Browser.open(android, url) }, modifier = Modifier.heightIn(min = UiTokens.MinTouch).testTag(ChatTags.link(i))) {
                        Text(stringResource(R.string.chat_open_link, url), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
