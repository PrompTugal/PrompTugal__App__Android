package ai.riskmandate.app.features.chat

import ai.riskmandate.app.Links
import ai.riskmandate.app.R
import androidx.annotation.RawRes
import androidx.annotation.StringRes

/** One message in a chat. Plain text only: it is never parsed as markup, links or code. */
data class ChatMessage(
    val role: ChatRole,
    val text: String,
    /** Pieces received for an answer (about one per token); 0 for the user's messages. */
    val tokens: Int = 0,
    /** Time from asking to the end of the answer, in milliseconds; 0 while it is still running. */
    val millis: Long = 0,
    /** Tokens per second while generating (first piece to last); 0 when it cannot be told. */
    val tokensPerSecond: Double = 0.0,
    val stopped: Boolean = false,
    val failed: Boolean = false,
    /** Why it failed, as the service's error reason name (NO_MODEL, LOAD_FAILED, TOO_LONG, INFERENCE_FAILED), or null. Never content. */
    val failure: String? = null,
)

data class Conversation(val id: String, val messages: List<ChatMessage> = emptyList()) {
    val isEmpty: Boolean get() = messages.isEmpty()

    /** A short label for the history list: the first thing the user said. */
    fun title(max: Int = 48): String {
        val first = messages.firstOrNull { it.role == ChatRole.USER }?.text?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (first.length <= max) first else first.take(max - 1).trimEnd() + "…"
    }
}

/** Limits that keep history small and a prompt inside the model's context budget. */
object ChatLimits {
    const val MAX_CONVERSATIONS = 30
    const val MAX_MESSAGES = 200
    const val MAX_MESSAGE_CHARS = 20_000
    const val MAX_INPUT_CHARS = 2_000

    /** About 3K tokens of history (4 characters a token): the brief aims for 4K to 8K per request, with room for the answer. */
    const val PROMPT_BUDGET_CHARS = 12_000
}

/** Turns a conversation into the model's input: newest turns that fit the budget, never an empty or failed turn. */
object ChatPrompt {
    fun turns(messages: List<ChatMessage>, budgetChars: Int = ChatLimits.PROMPT_BUDGET_CHARS): List<ChatTurn> {
        val usable = messages.filter { !it.failed && it.text.isNotBlank() }
        val picked = ArrayDeque<ChatTurn>()
        var used = 0
        for (m in usable.asReversed()) {
            val remaining = budgetChars - used
            if (remaining <= 0) break
            val text = if (m.text.length <= remaining) m.text else if (picked.isEmpty()) m.text.takeLast(remaining) else break
            picked.addFirst(ChatTurn(m.role, text))
            used += text.length
        }
        // A conversation must start with the user's turn.
        while (picked.isNotEmpty() && picked.first().role != ChatRole.USER) picked.removeFirst()
        return picked.toList()
    }
}

/** How a preset behaves when tapped. */
enum class PresetMode { SEND, FILL_INPUT }

/**
 * A preset prompt. [groundingRaw] is a bundled text (res/raw, from riskmandate.ai only) the model is told to
 * answer from. A preset that needs something not built yet is [enabled] = false and says so.
 */
data class Preset(
    val id: String,
    @StringRes val title: Int,
    @StringRes val prompt: Int,
    val mode: PresetMode,
    @RawRes val groundingRaw: Int? = null,
    val enabled: Boolean = true,
    @StringRes val disabledNote: Int? = null,
)

/** The model's settings for chat. Lower than Gemma's default (temperature 1.0, top-k 64, top-p 0.95): fewer glitches and invented facts. */
object ChatSampling {
    const val TEMPERATURE = 0.4
    const val TOP_K = 64
    const val TOP_P = 0.9
}

/**
 * Plain text for display: the markers a small model adds for bold are dropped, nothing is rendered.
 * One linear pass, no backtracking: a long run of "__" with no closing pair costs the same as ordinary text.
 */
object PlainText {
    fun clean(text: String): String {
        val s = text.replace("**", "")
        var i = s.indexOf("__")
        if (i < 0) return s
        val out = StringBuilder(s.length)
        var pos = 0
        while (i >= 0) {
            val j = s.indexOf("__", i + 2)
            if (j < 0) break // no closing pair anywhere after this: nothing more to strip
            val inner = s.substring(i + 2, j)
            if (inner.isNotEmpty() && !inner.first().isWhitespace() && !inner.last().isWhitespace() && '\n' !in inner) {
                out.append(s, pos, i).append(inner)
                pos = j + 2
                i = s.indexOf("__", pos)
            } else {
                i = s.indexOf("__", i + 2) // the would-be closer is tried as the next opener
            }
        }
        return out.append(s, pos, s.length).toString()
    }
}

object ChatPresets {
    val ALL: List<Preset> = listOf(
        Preset("riskmandate", R.string.chat_preset_rm_title, R.string.chat_preset_rm_prompt, PresetMode.SEND, R.raw.feature_chat_riskmandate),
        Preset("abp", R.string.chat_preset_abp_title, R.string.chat_preset_abp_prompt, PresetMode.SEND, R.raw.feature_chat_abp),
        Preset("risk", R.string.chat_preset_risk_title, R.string.chat_preset_risk_prompt, PresetMode.FILL_INPUT),
        // Needs the graph tools from the lab and eval roles. Stays off until they exist.
        Preset("triplets", R.string.chat_preset_triplets_title, R.string.chat_preset_triplets_prompt, PresetMode.SEND, null, false, R.string.chat_preset_triplets_note),
    )

    /** The instruction block for a grounded preset. Plain English: it is for the model, not shown. */
    fun system(groundingText: String): String =
        "Answer only from the text between the markers. If the text does not say, reply that you do not know. " +
            "Do not invent facts about RiskMandate. Keep the answer short.\n--- TEXT ---\n" + groundingText.trim() + "\n--- END ---"
}

/** Model output is text. The only thing taken from it is a short list of links the app may open. */
object ChatLinks {
    private val URL = Regex("""https?://[^\s<>"'`\])}]+""", RegexOption.IGNORE_CASE)
    private const val MAX_LINKS = 3

    /** URLs in [text] that pass [Links.isAllowed] (riskmandate.ai over https), de-duplicated, in order, trailing punctuation dropped. */
    fun allowed(text: String): List<String> =
        URL.findAll(text).map { it.value.trimEnd('.', ',', ';', ':', '!', '?') }
            .filter { Links.isAllowed(it) }.distinct().take(MAX_LINKS).toList()
}

/** Display numbers for an answer. */
object ChatStats {
    fun tokensPerSecond(tokens: Int, firstPieceAt: Long, endAt: Long): Double {
        val ms = endAt - firstPieceAt
        return if (tokens < 2 || ms <= 0) 0.0 else (tokens - 1) * 1000.0 / ms
    }
}

/**
 * Decisions that wait for Dinis. Constants, not state.
 * [WIPE_ON_LEAVE]: security H.3, a shared stand tablet. When true, saved history is deleted whenever the chat
 * leaves the screen (Back, or stand mode's idle reset: the feature cannot tell them apart). Off until he decides.
 */
object ChatPolicy {
    const val WIPE_ON_LEAVE = false
}
