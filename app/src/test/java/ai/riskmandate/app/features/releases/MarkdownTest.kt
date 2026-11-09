package ai.riskmandate.app.features.releases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownTest {
    private fun t(s: String, bold: Boolean = false, italic: Boolean = false, code: Boolean = false) = Span.Text(s, bold, italic, code)

    @Test fun headingsAndParagraphs() {
        val blocks = Markdown.parse("# One\n\nfirst line\nsecond line\n\n### Three ###\nafter")
        assertEquals(
            listOf(
                Block.Heading(1, listOf(t("One"))),
                Block.Paragraph(listOf(t("first line second line"))),
                Block.Heading(3, listOf(t("Three"))),
                Block.Paragraph(listOf(t("after"))),
            ),
            blocks,
        )
    }

    @Test fun listsBulletedAndNumbered() {
        val blocks = Markdown.parse("- a\n- **b**\n\n1. x\n2) y\n* z")
        assertEquals(
            listOf(
                Block.Bullets(listOf(listOf(t("a")), listOf(t("b", bold = true))), ordered = false),
                Block.Bullets(listOf(listOf(t("x")), listOf(t("y"))), ordered = true),
                Block.Bullets(listOf(listOf(t("z"))), ordered = false),
            ),
            blocks,
        )
    }

    @Test fun imageAloneOnALineIsABlockWithCaption() {
        val blocks = Markdown.parse("text\n![The home screen](screenshots/home-phone-portrait-light.png)\nmore")
        assertEquals(
            listOf(
                Block.Paragraph(listOf(t("text"))),
                Block.Image("screenshots/home-phone-portrait-light.png", "The home screen"),
                Block.Paragraph(listOf(t("more"))),
            ),
            blocks,
        )
    }

    @Test fun imagePathsThatLeaveTheFolderAreNotImages() {
        for (bad in listOf("../x.png", "screenshots/../x.png", "https://evil.example/x.png", "/etc/x.png", "x.png", "screenshots/a/b.png", "screenshots/x.svg", "screenshots/.png")) {
            assertNull(bad, Markdown.safeImagePath(bad))
            assertEquals(listOf<Block>(Block.Paragraph(listOf(t("![c]($bad)")))), Markdown.parse("![c]($bad)"))
        }
        assertEquals("screenshots/a_b-1.PNG", Markdown.safeImagePath("screenshots/a_b-1.PNG"))
    }

    @Test fun inlineBoldItalicCodeAndLinks() {
        assertEquals(
            listOf(t("a "), t("b", bold = true), t(" "), t("c", italic = true), t(" "), t("d", code = true), t(" "), Span.Link("e", "https://riskmandate.ai/")),
            Markdown.inline("a **b** *c* `d` [e](https://riskmandate.ai/)"),
        )
    }

    @Test fun unmatchedMarkersStayText() {
        assertEquals(listOf(t("2 * 3 and **open")), Markdown.inline("2 * 3 and **open"))
    }

    @Test fun htmlAndTablesAreJustText() {
        assertEquals(listOf<Block>(Block.Paragraph(listOf(t("<b>x</b> | a | b |")))), Markdown.parse("<b>x</b> | a | b |"))
    }
}
