package ai.riskmandate.app.features.releases

/** Articles and an image source for tests, independent of whatever articles the app bundles. */
object Fixture {
    const val IMAGE_DIR = "2026-11-09-fixture"

    val sample: Article = ArticleParser.parse(
        IMAGE_DIR,
        """
        ---
        title: "Fixture: what a release article looks like"
        date: 2026-11-09
        summary: A fixture article with headings, lists, bold, links and a screenshot.
        features: [releases]
        sample: true
        ---

        # Fixture article

        This is a **fixture**. It is not a real release.

        ## What an article can hold

        - Headings, paragraphs and lists.
        - **Bold**, *italic* and `code`.
        - A link to [RiskMandate](https://riskmandate.ai/).

        ## A screenshot

        ![The home screen, as the screenshot tests draw it.](screenshots/fixture-home.png)
        """.trimIndent(),
    )!!

    /** Serves `fixture-home.png` (src/test/resources/releases-fixture/) for every image path. */
    val images = ArticleImages { _, _ -> Fixture::class.java.getResourceAsStream("/releases-fixture/fixture-home.png") }
}
