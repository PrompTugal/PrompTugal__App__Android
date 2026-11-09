package ai.riskmandate.app.features.releases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReleaseStoreTest {
    private class Fake(val files: Map<String, String>) : ArticleSource {
        override fun dirs() = files.keys.map { it.substringBefore('/') }.distinct().shuffled()
        override fun read(dir: String, file: String) = files["$dir/$file"]
    }

    private fun article(title: String, date: String? = null, extra: String = "") =
        "---\ntitle: $title\n${if (date != null) "date: $date\n" else ""}$extra---\n\n# $title\n\nBody."

    @Test fun parserReadsFrontMatter() {
        val a = ArticleParser.parse(
            "2026-11-09-x",
            "---\ntitle: \"Hello: world\"\ndate: 2026-11-09\nsummary: 'Short.'\nfeatures: [site, abpdemo]\nsample: true\nunknown: ignored\n---\n\n## Body\n\ntext",
        )!!
        assertEquals("Hello: world", a.title)
        assertEquals(LocalDate.of(2026, 11, 9), a.date)
        assertEquals("Short.", a.summary)
        assertEquals(listOf("site", "abpdemo"), a.features)
        assertTrue(a.sample)
        assertEquals(2, a.blocks.size)
    }

    @Test fun dateFallsBackToFolderPrefixAndBadArticlesAreRefused() {
        assertEquals(LocalDate.of(2026, 11, 8), ArticleParser.parse("2026-11-08-a", article("A"))!!.date)
        assertNull("no date anywhere", ArticleParser.parse("nodate", article("A")))
        assertNull("no title", ArticleParser.parse("2026-11-08-a", "---\ndate: 2026-11-08\n---\nBody"))
        assertNull("no front matter", ArticleParser.parse("2026-11-08-a", "# Just a heading"))
        assertNull("impossible date", ArticleParser.parse("x", article("A", "2026-13-40")))
    }

    @Test fun listIsNewestFirstWhateverTheListingOrder() {
        val source = Fake(
            mapOf(
                "2026-11-09-b/article.md" to article("B"),
                "2026-11-20-c/article.md" to article("C"),
                "2026-11-09-a/article.md" to article("A"),
                "2026-10-01-old/article.md" to article("Old"),
                "2026-12-25-moved/article.md" to article("Moved", "2026-01-01"),
            ),
        )
        repeat(20) { // dirs() shuffles: the order must not depend on it
            assertEquals(listOf("C", "B", "A", "Old", "Moved"), ReleaseStore.load(source, "en").map { it.title })
        }
    }

    @Test fun brokenArticlesAreSkippedNotFatal() {
        val source = Fake(mapOf("2026-11-09-ok/article.md" to article("Ok"), "2026-11-10-bad/article.md" to "garbage", "2026-11-11-empty/readme.txt" to "x"))
        assertEquals(listOf("Ok"), ReleaseStore.load(source, "en").map { it.title })
    }

    @Test fun portugueseUsedOnPortugueseDevicesWithFallback() {
        val source = Fake(
            mapOf(
                "2026-11-09-a/article.md" to article("English A"),
                "2026-11-09-a/article.pt-PT.md" to article("Português A"),
                "2026-11-10-b/article.md" to article("English B only"),
            ),
        )
        assertEquals(listOf("English B only", "Português A"), ReleaseStore.load(source, "pt").map { it.title })
        assertEquals(listOf("English B only", "English A"), ReleaseStore.load(source, "en").map { it.title })
        assertEquals(listOf("English B only", "English A"), ReleaseStore.load(source, "fr").map { it.title })
    }

    @Test fun emptySourceGivesEmptyList() = assertEquals(emptyList<Article>(), ReleaseStore.load(Fake(emptyMap()), "en"))

    @Test fun firstImageIsTheThumbnail() {
        val a = ArticleParser.parse("2026-11-09-x", article("T") + "\n\n![one](screenshots/a.png)\n\n![two](screenshots/b.png)")!!
        assertEquals("screenshots/a.png", a.firstImage!!.path)
    }
}
