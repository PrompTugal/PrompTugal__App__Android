package ai.riskmandate.app.llm.knowledge

import ai.riskmandate.app.llm.LlmStopReason
import ai.riskmandate.app.llm.StrictJson
import android.content.res.AssetManager
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One eval question and the items that answer it (empty: the data does not hold the answer). */
data class EvalQuestion(val q: String, val expect: List<String>)

/** How one question went. */
data class EvalResult(val question: EvalQuestion, val answer: GroundedAnswer) {
    /** An expected item was among the sources (always true for a question the data cannot answer). */
    val found: Boolean get() = question.expect.isEmpty() || answer.sources.hits.any { it.item.id in question.expect }
    /**
     * The answer is right in the way we can check without a judge: it cites an expected item and no made-up
     * number, or, when the data cannot answer, it says it does not know and cites nothing.
     */
    val right: Boolean get() = answer.stopReason != LlmStopReason.ERROR && answer.citations.invalid.isEmpty() && if (question.expect.isEmpty()) {
        answer.citations.valid.isEmpty() && Citations.saysUnknown(answer.text)
    } else {
        answer.cited.any { it.id in question.expect }
    }
}

/**
 * The bundled eval (assets/knowledge/eval.v1.json): retrieval recall needs no model and runs in tests; the full
 * run (citations right, time to first token) runs on the tablet from the Workbench.
 */
object KnowledgeEval {
    const val PATH = "knowledge/eval.v1.json"
    const val MAX_QUESTIONS = 100

    fun parse(text: String, knowledge: Knowledge): List<EvalQuestion> {
        val m = StrictJson.parse(text, 200_000) as? Map<*, *> ?: throw IllegalArgumentException("not an object")
        val qs = (m["questions"] as? List<*>)?.map { e ->
            val o = e as? Map<*, *> ?: throw IllegalArgumentException("bad question")
            val q = (o["q"] as? String)?.takeIf { it.isNotBlank() && it.length <= 500 } ?: throw IllegalArgumentException("bad q")
            val expect = (o["expect"] as? List<*>)?.map { id -> (id as? String)?.takeIf { knowledge.item(it) != null } ?: throw IllegalArgumentException("unknown item $id") }
                ?: throw IllegalArgumentException("bad expect")
            EvalQuestion(q, expect)
        } ?: throw IllegalArgumentException("no questions")
        require(qs.size in 1..MAX_QUESTIONS) { "question count" }
        return qs
    }

    suspend fun load(assets: AssetManager, knowledge: Knowledge): List<EvalQuestion> = withContext(Dispatchers.IO) {
        try { parse(assets.open(PATH).bufferedReader().use { it.readText() }, knowledge) } catch (e: IllegalArgumentException) { throw IOException("bundled eval is invalid: ${e.message}") }
    }

    /** Share of answerable questions whose expected item is in the top [k] sources. No model involved. */
    fun recall(index: Bm25Index, questions: List<EvalQuestion>, k: Int): Double {
        val answerable = questions.filter { it.expect.isNotEmpty() }
        if (answerable.isEmpty()) return 0.0
        return answerable.count { q -> index.search(q.q, k).any { it.item.id in q.expect } }.toDouble() / answerable.size
    }

    /** Plain-text summary for "Copy" and the status page. English by design. */
    fun report(results: List<EvalResult>, k: Int, model: String?): String = buildString {
        val firsts = results.mapNotNull { it.answer.firstTokenMs }.sorted()
        appendLine("RiskMandate grounded answers over bundled data: ${results.size} questions, top $k sources, model ${model ?: "-"}")
        appendLine("Sources found: ${results.count { it.found }}/${results.size}  answers right: ${results.count { it.right }}/${results.size}  made-up citations: ${results.sumOf { it.answer.citations.invalid.size }}")
        appendLine("Time to first token (measured, search included): median ${med(firsts)} ms, slowest ${firsts.lastOrNull() ?: "-"} ms")
        results.forEachIndexed { i, r ->
            append("${i + 1}. ${if (r.right) "right" else "wrong"}  ${r.answer.firstTokenMs ?: "-"} ms  cited ${r.answer.cited.joinToString { it.id }.ifEmpty { "-" }}  | ${r.question.q.take(80)}")
            if (i < results.size - 1) appendLine()
        }
    }

    private fun med(xs: List<Long>): String = if (xs.isEmpty()) "-" else String.format(Locale.ROOT, "%d", xs[xs.size / 2])
}
