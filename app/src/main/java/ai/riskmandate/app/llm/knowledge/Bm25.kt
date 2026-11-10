package ai.riskmandate.app.llm.knowledge

import java.text.Normalizer
import kotlin.math.ln

/** One passage found for a query. */
data class Hit(val item: KnowledgeItem, val passage: Int, val score: Double) {
    val text: String get() = item.passages[passage]
}

/**
 * Words for search: lower case, accents folded (Portuguese and English), split on anything but letters and digits,
 * stop words and one-letter words dropped, a plural "s" trimmed. The same for passages and questions.
 */
object Words {
    private val STOP = setOf(
        "the", "and", "for", "are", "was", "were", "with", "that", "this", "from", "what", "who", "which", "when", "where",
        "how", "does", "did", "its", "has", "have", "had", "into", "about", "their", "they", "them", "there", "than", "then",
        "but", "not", "can", "will", "would", "you", "your", "our", "out", "all", "any", "one", "his", "her", "she", "him",
        "is", "of", "to", "in", "on", "at", "by", "or", "an", "as", "be", "it", "we", "do", "so", "if", "no", "me", "my", "us",
        "tell", "say", "says", "said", "give", "about", "please",
        "o", "os", "as", "um", "uma", "de", "da", "do", "das", "dos", "em", "no", "na", "nos", "nas", "que", "para", "por",
        "com", "se", "ao", "aos", "mais", "como", "sobre", "qual", "quais", "quem", "onde", "quando", "e", "é",
    )

    fun of(text: String): List<String> {
        val folded = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return folded.split(Regex("[^\\p{L}\\p{N}]+")).asSequence()
            .filter { it.length > 1 && it !in STOP }
            .map { if (it.length > 3 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }
            .toList()
    }
}

/**
 * Okapi BM25 over every passage of [knowledge], built once in memory (about 550 passages: milliseconds). Each
 * passage is indexed with its item's title, so a company's or article's name finds it. No network, no model.
 */
class Bm25Index(val knowledge: Knowledge, private val k1: Double = 1.2, private val b: Double = 0.75) {
    private class Doc(val item: KnowledgeItem, val passage: Int, val tf: Map<String, Int>, val len: Int)

    private val docs: List<Doc>
    private val df = HashMap<String, Int>()
    private val avgLen: Double

    init {
        docs = knowledge.items.flatMap { item ->
            item.passages.indices.map { p ->
                val words = Words.of(item.title) + Words.of(item.passages[p])
                Doc(item, p, words.groupingBy { it }.eachCount(), words.size)
            }
        }
        docs.forEach { d -> d.tf.keys.forEach { w -> df[w] = (df[w] ?: 0) + 1 } }
        avgLen = docs.map { it.len }.average().takeIf { it > 0 } ?: 1.0
    }

    val size: Int get() = docs.size

    /**
     * The best [k] passages for [query], at most [perItem] from one item so the sources stay varied. Empty when no
     * word of the query is in the data.
     */
    fun search(query: String, k: Int = 4, perItem: Int = 1): List<Hit> {
        val q = Words.of(query).distinct()
        if (q.isEmpty()) return emptyList()
        val n = docs.size.toDouble()
        val scored = docs.mapNotNull { d ->
            var s = 0.0
            for (w in q) {
                val f = d.tf[w] ?: continue
                val dfw = df[w] ?: continue
                val idf = ln(1 + (n - dfw + 0.5) / (dfw + 0.5))
                s += idf * f * (k1 + 1) / (f + k1 * (1 - b + b * d.len / avgLen))
            }
            if (s > 0) Hit(d.item, d.passage, s) else null
        }.sortedWith(compareByDescending<Hit> { it.score }.thenBy { it.item.id }.thenBy { it.passage })
        val perCount = HashMap<String, Int>()
        val out = ArrayList<Hit>(k)
        for (h in scored) {
            if (out.size >= k) break
            val c = perCount[h.item.id] ?: 0
            if (c >= perItem) continue
            perCount[h.item.id] = c + 1
            out += h
        }
        return out
    }
}
