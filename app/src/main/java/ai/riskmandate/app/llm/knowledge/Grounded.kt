package ai.riskmandate.app.llm.knowledge

import ai.riskmandate.app.llm.BackendPreference
import ai.riskmandate.app.llm.LlmEvent
import ai.riskmandate.app.llm.LlmMessage
import ai.riskmandate.app.llm.LlmRequest
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.LlmStats
import ai.riskmandate.app.llm.LlmStopReason
import ai.riskmandate.app.llm.RunSettings
import ai.riskmandate.app.llm.Sampling
import kotlin.coroutines.cancellation.CancellationException

/** The numbered sources one question is answered from, in the order the model sees them ([1] is index 0). */
data class Sources(val hits: List<Hit>) {
    operator fun get(n: Int): Hit? = hits.getOrNull(n - 1)
    val size: Int get() = hits.size
}

/**
 * Builds the request for a grounded answer: the system block (the RiskMandate base without the device claim, then
 * the grounding rules from assets/prompts/grounded_answer) and one user message holding the numbered sources and
 * the question. Sources are cut to [MAX_SOURCE_CHARS] in all, so prefill stays short on the tablet.
 */
object GroundedPrompt {
    const val MAX_SOURCE_CHARS = 4_000

    fun request(system: String, question: String, sources: Sources, maxTokens: Int = 256, backend: BackendPreference = BackendPreference.AUTO): LlmRequest {
        require(question.isNotBlank()) { "the question is empty" }
        require(question.length <= RunSettings.MAX_TYPED_CHARS) { "the question is longer than ${RunSettings.MAX_TYPED_CHARS} characters" }
        val user = buildString {
            if (sources.size == 0) appendLine("Sources: none found.") else appendLine("Sources:")
            var left = MAX_SOURCE_CHARS
            sources.hits.forEachIndexed { i, h ->
                val head = "[${i + 1}] ${h.item.title}" + (h.item.date?.let { " ($it)" } ?: "")
                val body = h.text.take(maxOf(0, left - head.length))
                left -= head.length + body.length
                appendLine(head)
                appendLine(body)
                appendLine()
            }
            append("Question: ").append(question.trim())
        }
        return LlmRequest(
            messages = listOf(LlmMessage.system(system), LlmMessage.user(user)),
            // Low temperature: answers should follow the sources, not wander (the Workbench can still sweep it).
            settings = Sampling.DEFAULT.copy(temperature = 0.2),
            maxTokens = maxTokens,
            backend = backend,
        )
    }
}

/** What an answer cited: the source numbers that exist, and any that do not (shown, never mapped to an item). */
data class Citations(val valid: List<Int>, val invalid: List<Int>) {
    companion object {
        private val REF = Regex("\\[(\\d{1,3}(?:\\s*[,;]\\s*\\d{1,3})*)\\]")

        fun of(answer: String, sources: Int): Citations {
            val all = REF.findAll(answer).flatMap { m -> m.groupValues[1].split(',', ';').mapNotNull { it.trim().toIntOrNull() } }.toList()
            val (ok, bad) = all.distinct().partition { it in 1..sources }
            return Citations(ok, bad)
        }

        /** True when the answer says the data does not hold it (the rule's own wording, or a near form). */
        fun saysUnknown(answer: String): Boolean = Regex("(do not|don't|cannot|can't) (know|find|tell)", RegexOption.IGNORE_CASE).containsMatchIn(answer)
    }
}

/** One finished grounded answer, whatever the ending, with the timings the Workbench reports. */
data class GroundedAnswer(
    val question: String,
    val sources: Sources,
    val text: String,
    val citations: Citations,
    val stats: LlmStats?,
    val stopReason: LlmStopReason,
    val error: LlmEvent.Error?,
    /** Search time, measured. */
    val retrievalMs: Long,
    /** From the start (search included) to the first piece of the answer, measured; null if none came. */
    val firstTokenMs: Long?,
    /** From the start to the end, search and any model load included, measured. */
    val totalMs: Long,
) {
    /** The cited items, in citation order, each once. */
    val cited: List<KnowledgeItem> get() = citations.valid.mapNotNull { sources[it]?.item }.distinctBy { it.id }
}

/**
 * Answers a question over the bundled data on [service]: searches [index] (no network), builds the grounded
 * request, streams the answer and checks its citations against the sources given. [system] is the system block
 * (load it with [systemBlock]). [clock] is monotonic milliseconds, injectable for tests.
 */
class GroundedAnswerer(
    private val service: LlmService,
    private val index: Bm25Index,
    private val system: String,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    suspend fun ask(
        question: String,
        k: Int = 4,
        maxTokens: Int = 256,
        backend: BackendPreference = BackendPreference.AUTO,
        onText: (text: String, firstTokenMs: Long?) -> Unit = { _, _ -> },
    ): GroundedAnswer {
        val start = clock()
        val sources = Sources(index.search(question, k.coerceIn(1, 8)))
        val retrievalMs = clock() - start
        val text = StringBuilder()
        var first: Long? = null
        var stats: LlmStats? = null
        var error: LlmEvent.Error? = null
        fun done(stop: LlmStopReason) = GroundedAnswer(
            question, sources, text.toString(), Citations.of(text.toString(), sources.size), stats?.copy(stopReason = stop), stop, error,
            retrievalMs, first, clock() - start,
        )
        val request = try {
            GroundedPrompt.request(system, question, sources, maxTokens, backend)
        } catch (e: IllegalArgumentException) {
            error = LlmEvent.Error(ai.riskmandate.app.llm.LlmError.INFERENCE_FAILED, e.message ?: "bad question")
            return done(LlmStopReason.ERROR)
        }
        try {
            service.stream(request).collect { e ->
                when (e) {
                    is LlmEvent.Token -> {
                        if (first == null) first = clock() - start
                        text.append(e.text)
                        onText(text.toString(), first)
                    }
                    is LlmEvent.Done -> stats = e.stats
                    is LlmEvent.Error -> error = e
                    is LlmEvent.ToolCallProposed -> Unit // no tools are offered
                }
            }
        } catch (e: CancellationException) {
            done(LlmStopReason.CANCELLED)
            throw e
        }
        return done(if (error != null) LlmStopReason.ERROR else stats?.stopReason ?: LlmStopReason.DONE)
    }

    companion object {
        val GROUNDED = ai.riskmandate.app.llm.prompts.PromptId("grounded_answer", 1)

        /** The base block without the device claim (safe on either service), then the grounding rules. */
        suspend fun systemBlock(assets: android.content.res.AssetManager): String {
            val p = ai.riskmandate.app.llm.prompts.Prompts
            return p.load(assets, p.RISKMANDATE_BASE_ANYWHERE) + "\n\n" + p.load(assets, GROUNDED)
        }
    }
}
