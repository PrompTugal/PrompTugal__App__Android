package ai.riskmandate.app.features.releases

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** One release article, parsed. [dir] is the folder name under assets/releases/, e.g. `2026-11-09-sample`. */
data class Article(
    val dir: String,
    val title: String,
    val date: LocalDate,
    val summary: String,
    /** Ids of the features the release touched (`site`, `abpdemo`, ...). Informational. */
    val features: List<String>,
    /** True for the sample article: the screen marks it so nobody mistakes it for a real release. */
    val sample: Boolean,
    val blocks: List<Block>,
) {
    /** The first image of the body, used as the list thumbnail. */
    val firstImage: Block.Image? get() = blocks.firstOrNull { it is Block.Image } as Block.Image?
}

/** Front matter + body, split. [meta] keys are lowercase. */
data class RawArticle(val meta: Map<String, String>, val body: String)

object ArticleParser {
    /**
     * `---` on the first line, `key: value` lines, `---`, then the Markdown body. Values may be quoted with
     * `"` or `'`. A list is `[a, b]`. Unknown keys are ignored. A file without front matter has no meta.
     */
    fun split(text: String): RawArticle {
        val lines = text.trimStart(Char(0xFEFF)).lines()
        if (lines.firstOrNull()?.trim() != "---") return RawArticle(emptyMap(), text)
        val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" } ?: return RawArticle(emptyMap(), text)
        val meta = LinkedHashMap<String, String>()
        for (l in lines.subList(1, end)) {
            val i = l.indexOf(':')
            if (i <= 0 || l.startsWith("#")) continue
            meta[l.substring(0, i).trim().lowercase()] = unquote(l.substring(i + 1).trim())
        }
        return RawArticle(meta, lines.drop(end + 1).joinToString("\n"))
    }

    private fun unquote(v: String): String =
        if (v.length >= 2 && (v.first() == '"' && v.last() == '"' || v.first() == '\'' && v.last() == '\'')) v.substring(1, v.length - 1) else v

    fun list(v: String?): List<String> =
        v.orEmpty().trim().removePrefix("[").removeSuffix("]").split(',').map { unquote(it.trim()) }.filter { it.isNotEmpty() }

    /**
     * Parse an article. The date is the front matter `date`, else the folder name's `YYYY-MM-DD` prefix;
     * without either the article is refused (null), as is one without a title. The sort order depends on the date.
     */
    fun parse(dir: String, text: String): Article? {
        val raw = split(text)
        val date = parseDate(raw.meta["date"]) ?: parseDate(dir.take(10)) ?: return null
        val title = raw.meta["title"]?.takeIf { it.isNotBlank() } ?: return null
        return Article(
            dir = dir,
            title = title,
            date = date,
            summary = raw.meta["summary"].orEmpty(),
            features = list(raw.meta["features"]),
            sample = raw.meta["sample"].equals("true", ignoreCase = true),
            blocks = Markdown.parse(raw.body),
        )
    }

    private fun parseDate(s: String?): LocalDate? = try {
        s?.trim()?.let { LocalDate.parse(it) }
    } catch (e: DateTimeParseException) {
        null
    }

    /** Newest first; same day: folder name descending, so the order never depends on how the assets are listed. */
    val newestFirst: Comparator<Article> = compareByDescending<Article> { it.date }.thenByDescending { it.dir }
}
