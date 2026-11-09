package ai.riskmandate.app.features.releases

/** Inline pieces of a line of text. Pure Kotlin: no Android types, so it runs on the JVM. */
sealed interface Span {
    data class Text(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false) : Span
    data class Link(val text: String, val url: String) : Span
}

/** Block level pieces of an article body. */
sealed interface Block {
    data class Heading(val level: Int, val spans: List<Span>) : Block
    data class Paragraph(val spans: List<Span>) : Block
    data class Bullets(val items: List<List<Span>>, val ordered: Boolean) : Block

    /** [path] is relative to the article folder and already checked by [safeImagePath]. */
    data class Image(val path: String, val caption: String) : Block
}

/**
 * The small Markdown subset the article format allows (no library, see README.md):
 * `#` to `######` headings; paragraphs (consecutive lines join); `-`/`*` and `1.` lists, one line per item;
 * an image alone on a line, `![caption](screenshots/name.png)`; inline `**bold**`, `*italic*`, `` `code` ``
 * and `[text](https://...)`. Anything else (tables, HTML, nested lists, block quotes) is shown as plain text.
 */
object Markdown {
    private val heading = Regex("""^(#{1,6})\s+(.+?)\s*#*\s*$""")
    private val bullet = Regex("""^\s*[-*]\s+(.+)$""")
    private val numbered = Regex("""^\s*\d+[.)]\s+(.+)$""")
    private val image = Regex("""^!\[([^\]]*)]\(([^)\s]+)\)\s*$""")

    fun parse(body: String): List<Block> {
        val blocks = ArrayList<Block>()
        val paragraph = ArrayList<String>()
        var list: ArrayList<List<Span>>? = null
        var listOrdered = false

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) blocks += Block.Paragraph(inline(paragraph.joinToString(" ")))
            paragraph.clear()
        }
        fun flushList() {
            list?.let { blocks += Block.Bullets(it.toList(), listOrdered) }
            list = null
        }

        for (raw in body.lines()) {
            val line = raw.trimEnd()
            if (line.isBlank()) { flushParagraph(); flushList(); continue }
            val h = heading.matchEntire(line.trim())
            if (h != null) {
                flushParagraph(); flushList()
                blocks += Block.Heading(h.groupValues[1].length, inline(h.groupValues[2]))
                continue
            }
            val img = image.matchEntire(line.trim())
            if (img != null && safeImagePath(img.groupValues[2]) != null) {
                flushParagraph(); flushList()
                blocks += Block.Image(img.groupValues[2], img.groupValues[1].trim())
                continue
            }
            val b = bullet.matchEntire(line)
            val n = numbered.matchEntire(line)
            if (b != null || n != null) {
                val ordered = n != null && b == null
                if (list != null && listOrdered != ordered) flushList()
                flushParagraph()
                if (list == null) { list = ArrayList(); listOrdered = ordered }
                list!!.add(inline((b ?: n)!!.groupValues[1]))
                continue
            }
            flushList()
            paragraph += line.trim()
        }
        flushParagraph(); flushList()
        return blocks
    }

    /**
     * An image path from an article, or null when it is not allowed: only `screenshots/<name>` with
     * a name of letters, digits, dot, dash and underscore and a png, jpg, jpeg or webp extension. No `..`,
     * no URL, no absolute path, so an article can only read its own folder's images.
     */
    fun safeImagePath(path: String): String? {
        val ok = Regex("""^screenshots/[A-Za-z0-9][A-Za-z0-9._-]*\.(png|jpg|jpeg|webp)$""", RegexOption.IGNORE_CASE)
        return if (ok.matches(path) && !path.contains("..")) path else null
    }

    private val token = Regex("""(?<!!)\[([^\]]+)]\(([^)\s]+)\)|\*\*(?=\S)(.+?)(?<=\S)\*\*|\*(?=[^\s*])(.+?)(?<=[^\s*])\*|`([^`]+)`""")

    /** Inline spans of one line or paragraph. Unmatched markers stay as text. */
    fun inline(text: String): List<Span> {
        val spans = ArrayList<Span>()
        var pos = 0
        for (m in token.findAll(text)) {
            if (m.range.first > pos) spans += Span.Text(text.substring(pos, m.range.first))
            val g = m.groupValues
            spans += when {
                m.groups[1] != null -> Span.Link(g[1], g[2])
                m.groups[3] != null -> Span.Text(g[3], bold = true)
                m.groups[4] != null -> Span.Text(g[4], italic = true)
                else -> Span.Text(g[5], code = true)
            }
            pos = m.range.last + 1
        }
        if (pos < text.length) spans += Span.Text(text.substring(pos))
        return spans
    }
}
