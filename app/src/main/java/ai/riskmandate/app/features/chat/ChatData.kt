package ai.riskmandate.app.features.chat

import ai.riskmandate.app.llm.LlmLocation
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.knowledge.Bm25Index
import ai.riskmandate.app.llm.knowledge.GroundedAnswerer
import ai.riskmandate.app.llm.knowledge.Knowledge
import android.content.res.AssetManager

/** One source a data answer cited, as plain text: its number in the answer, title, kind name and where it comes from. Never a link. */
data class ChatSource(val n: Int, val title: String, val kind: String, val source: String)

/**
 * What goes under a data answer: the cited sources, the pt.news credit line (security R-know-2) and any citation
 * numbers the model made up (shown as such, never matched to an item).
 */
data class ChatGrounding(val sources: List<ChatSource>, val attribution: String, val badRefs: List<Int>)

/** A finished "Ask our data" answer, whatever the ending. [errorReason] is a service error reason name or null. */
data class DataAnswer(
    val text: String,
    val grounding: ChatGrounding,
    val outputTokens: Int?,
    val tokensPerSecond: Double?,
    val totalMs: Long,
    val errorReason: String?,
)

/**
 * Answers a question from the bundled data (RiskMandate quotes, pt.news articles and company cards), on this device.
 * [onText] gets the whole answer so far, as it streams. Cancellation is coroutine cancellation.
 */
fun interface DataAsker {
    suspend fun ask(question: String, onText: (String) -> Unit): DataAnswer
}

/**
 * [DataAsker] over dev.llm's [GroundedAnswerer]: a BM25 search of the bundled data (no network), then the on-device
 * model answers from the numbered passages. Built only for a service that runs on this device: it refuses anything
 * else, so a question over the data can never be sent to the cloud (the chat hands it `context.llm`, never `cloudLlm`).
 */
class GroundedDataAsker(private val service: LlmService, private val assets: AssetManager) : DataAsker {
    init {
        require(service.location == LlmLocation.LOCAL) { "data answers run on the device only" }
    }

    private var answerer: GroundedAnswerer? = null

    private suspend fun answerer(): GroundedAnswerer = answerer ?: run {
        val k = Knowledge.load(assets)
        GroundedAnswerer(service, Bm25Index(k), GroundedAnswerer.systemBlock(assets)).also { answerer = it }
    }

    override suspend fun ask(question: String, onText: (String) -> Unit): DataAnswer {
        val a = answerer().ask(question, k = 4, maxTokens = 256) { text, _ -> onText(text) }
        val sources = a.citations.valid.mapNotNull { n -> a.sources[n]?.let { ChatSource(n, it.item.title, it.item.kind.name, it.item.source) } }
        return DataAnswer(
            text = a.text,
            grounding = ChatGrounding(sources, a.attributionLine, a.citations.invalid),
            outputTokens = a.stats?.outputTokens,
            tokensPerSecond = a.stats?.decodeTokensPerSecond,
            totalMs = a.totalMs,
            errorReason = a.error?.reason?.name,
        )
    }
}
