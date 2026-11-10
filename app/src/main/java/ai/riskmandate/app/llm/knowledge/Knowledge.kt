package ai.riskmandate.app.llm.knowledge

import ai.riskmandate.app.llm.StrictJson
import android.content.res.AssetManager
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a bundled item comes from. RiskMandate items are only the sentences quoted verbatim from riskmandate.ai. */
enum class KnowledgeKind { RISKMANDATE, ARTICLE, COMPANY }

/**
 * One bundled item: a pt.news article, a pt.news company card or a RiskMandate quote. [source] is what a citation
 * shows (a pt.news address or "riskmandate.ai"); the app never opens it. [passages] are what the index searches.
 */
data class KnowledgeItem(
    val id: String,
    val kind: KnowledgeKind,
    val title: String,
    val source: String,
    val date: String?,
    val passages: List<String>,
)

/**
 * The bundled data the on-device model answers over (assets/knowledge, built by scripts/knowledge from a pinned
 * pt.news commit; see the manifest). Read-only, validated on load: a bad line is a load error, never a partial set.
 */
class Knowledge(val items: List<KnowledgeItem>) {
    private val byId = items.associateBy { it.id }
    fun item(id: String): KnowledgeItem? = byId[id]
    val passageCount: Int get() = items.sumOf { it.passages.size }

    companion object {
        const val PATH = "knowledge/knowledge.v1.jsonl"
        const val MAX_ITEMS = 2_000
        const val MAX_PASSAGES = 60
        const val MAX_PASSAGE_CHARS = 2_000
        const val MAX_LINE_CHARS = 40_000
        private val ID = Regex("^(rm|a|c):[a-z0-9][a-z0-9-]{0,80}$")
        private val SOURCE = Regex("^(riskmandate\\.ai|https://promptugal\\.news/[a-z0-9/._-]{1,200})$")

        /** Parses the JSON-lines text. Throws [IllegalArgumentException] naming the first bad line. */
        fun parse(text: String): Knowledge {
            val items = mutableListOf<KnowledgeItem>()
            text.lineSequence().filter { it.isNotBlank() }.forEachIndexed { n, line ->
                require(items.size < MAX_ITEMS) { "more than $MAX_ITEMS items" }
                require(line.length <= MAX_LINE_CHARS) { "line ${n + 1} is too long" }
                val m = StrictJson.parse(line, MAX_LINE_CHARS) as? Map<*, *> ?: throw IllegalArgumentException("line ${n + 1} is not an object")
                fun str(k: String, max: Int): String = (m[k] as? String)?.takeIf { it.isNotBlank() && it.length <= max }
                    ?: throw IllegalArgumentException("line ${n + 1}: bad $k")
                val id = str("id", 90).also { require(ID.matches(it)) { "line ${n + 1}: bad id" } }
                val kind = when (str("kind", 20)) {
                    "riskmandate" -> KnowledgeKind.RISKMANDATE
                    "article" -> KnowledgeKind.ARTICLE
                    "company" -> KnowledgeKind.COMPANY
                    else -> throw IllegalArgumentException("line ${n + 1}: bad kind")
                }
                require(id.startsWith(when (kind) { KnowledgeKind.RISKMANDATE -> "rm:"; KnowledgeKind.ARTICLE -> "a:"; KnowledgeKind.COMPANY -> "c:" })) { "line ${n + 1}: id does not match kind" }
                val source = str("source", 240).also { require(SOURCE.matches(it)) { "line ${n + 1}: bad source" } }
                require((kind == KnowledgeKind.RISKMANDATE) == (source == "riskmandate.ai")) { "line ${n + 1}: RiskMandate text comes only from riskmandate.ai" }
                val passages = (m["passages"] as? List<*>)?.map { p ->
                    (p as? String)?.takeIf { it.isNotBlank() && it.length <= MAX_PASSAGE_CHARS } ?: throw IllegalArgumentException("line ${n + 1}: bad passage")
                } ?: throw IllegalArgumentException("line ${n + 1}: no passages")
                require(passages.size in 1..MAX_PASSAGES) { "line ${n + 1}: passage count" }
                items += KnowledgeItem(id, kind, str("title", 200), source, (m["date"] as? String)?.takeIf { it.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) }, passages)
            }
            require(items.map { it.id }.toSet().size == items.size) { "duplicate ids" }
            require(items.isNotEmpty()) { "no items" }
            return Knowledge(items)
        }

        private val cache = AtomicReference<Knowledge?>(null)

        /** Loads and caches the bundled set off the main thread. [IOException] when it is missing or invalid. */
        suspend fun load(assets: AssetManager): Knowledge = cache.get() ?: withContext(Dispatchers.IO) {
            val k = try {
                parse(assets.open(PATH).bufferedReader().use { it.readText() })
            } catch (e: IllegalArgumentException) {
                throw IOException("bundled knowledge is invalid: ${e.message}")
            }
            cache.compareAndSet(null, k)
            cache.get()!!
        }
    }
}
