package ai.riskmandate.app.features.releases

import ai.riskmandate.app.R
import ai.riskmandate.app.RiskMandateTheme
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReleasesScreenTest {
    @get:Rule val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun fake(dir: String, title: String, date: String, extra: String = "") =
        ArticleParser.parse(dir, "---\ntitle: $title\ndate: $date\nsummary: About $title\n---\n\n# $title\n\nBody of $title. $extra")!!

    private fun show(articles: List<Article>?, links: MutableList<String> = mutableListOf(), selected: String? = null) {
        compose.setContent {
            var sel by mutableStateOf(selected)
            RiskMandateTheme { ReleasesScreen(articles, sel, onSelect = { sel = it }, onLink = { links += it }, images = Fixture.images) }
        }
    }

    private fun waitForImage() = compose.waitUntil(10_000) { compose.onAllNodesWithTag(ReleasesTags.IMAGE, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun everyBundledArticleParsesInBothLanguages() {
        // Whatever editor bundles must parse: a broken article would silently vanish from the list.
        val source = AssetArticleSource(app.assets)
        for (dir in source.dirs()) {
            assertTrue("$dir/article.md must parse", source.read(dir, ReleaseStore.ARTICLE)?.let { ArticleParser.parse(dir, it) } != null)
            source.read(dir, ReleaseStore.ARTICLE_PT)?.let { assertTrue("$dir/article.pt-PT.md must parse", ArticleParser.parse(dir, it) != null) }
        }
    }

    @Test fun listShowsTitleSummaryAndOpensTheReaderWithAnImage() {
        val sample = Fixture.sample
        show(listOf(sample))
        compose.onNodeWithText(sample.title).assertIsDisplayed()
        compose.onNodeWithText(sample.summary).assertIsDisplayed()
        waitForImage() // the thumbnail
        compose.onNodeWithTag(ReleasesTags.item(sample.dir)).performClick()
        compose.onNodeWithTag(ReleasesTags.READER).assertIsDisplayed()
        compose.onNodeWithTag(ReleasesTags.SAMPLE).assertIsDisplayed()
        compose.onNodeWithTag(ReleasesTags.ALL).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w360dp-h2400dp-port")
    fun readerRendersTheImageOfTheArticle() {
        val sample = Fixture.sample
        show(listOf(sample), selected = sample.dir)
        waitForImage()
        compose.onAllNodesWithTag(ReleasesTags.IMAGE)[0].assertIsDisplayed()
    }

    @Test fun backToListFromReader() {
        val articles = listOf(fake("2026-11-10-b", "Bee", "2026-11-10"), fake("2026-11-09-a", "Ay", "2026-11-09"))
        show(articles)
        compose.onNodeWithTag(ReleasesTags.item("2026-11-10-b")).performClick()
        compose.onNodeWithText("Body of Bee.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(ReleasesTags.ALL).performClick()
        compose.onNodeWithTag(ReleasesTags.LIST).assertIsDisplayed()
    }

    @Test fun emptyAndLoadingStates() {
        show(emptyList())
        compose.onNodeWithTag(ReleasesTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.feature_releases_empty)).assertIsDisplayed()
    }

    @Test fun loadingDrawsNothing() {
        show(null)
        assertTrue(compose.onAllNodesWithTag(ReleasesTags.LIST).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithTag(ReleasesTags.EMPTY).fetchSemanticsNodes().isEmpty())
    }

    @Test @Config(qualifiers = "w900dp-h600dp-land")
    fun tabletLandscapeShowsListAndReaderSideBySide() {
        val articles = listOf(fake("2026-11-10-b", "Bee", "2026-11-10"), fake("2026-11-09-a", "Ay", "2026-11-09"))
        show(articles)
        compose.onNodeWithTag(ReleasesTags.LIST).assertIsDisplayed()
        compose.onNodeWithTag(ReleasesTags.READER).assertIsDisplayed()
        compose.onNodeWithText("Body of Bee.", substring = true).assertIsDisplayed() // the newest is preselected
        assertTrue("no Back-to-list button in two panes", compose.onAllNodesWithTag(ReleasesTags.ALL).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag(ReleasesTags.item("2026-11-09-a")).performClick()
        compose.onNodeWithText("Body of Ay.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(ReleasesTags.LIST).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w360dp-h800dp-port")
    fun phonePortraitShowsOnePaneAtATime() {
        show(listOf(fake("2026-11-10-b", "Bee", "2026-11-10")))
        compose.onNodeWithTag(ReleasesTags.LIST).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag(ReleasesTags.READER).fetchSemanticsNodes().isEmpty())
    }

    @Test fun onlyAllowListedLinksAreClickable() {
        val a = fake("2026-11-10-b", "Bee", "2026-11-10", "[ok](https://riskmandate.ai/x) and [bad](https://evil.example/x).")
        val links = mutableListOf<String>()
        show(listOf(a), links, selected = a.dir)
        // The allowed link is annotated, the other is plain text: it carries no link annotation.
        val text = compose.onNodeWithText("Body of Bee.", substring = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].first()
        assertEquals(listOf("https://riskmandate.ai/x"), text.getLinkAnnotations(0, text.length).map { (it.item as androidx.compose.ui.text.LinkAnnotation.Clickable).tag })
        assertTrue(links.isEmpty())
    }

    @Test fun imageDecodesScaledAndMissingImageIsNull() {
        val full = ReleaseStore.image(Fixture.images, Fixture.IMAGE_DIR, "screenshots/fixture-home.png", 2048)!!
        val small = ReleaseStore.image(Fixture.images, Fixture.IMAGE_DIR, "screenshots/fixture-home.png", 240)!!
        assertTrue(small.width < full.width)
        assertEquals(null, ReleaseStore.image({ _, _ -> null }, Fixture.IMAGE_DIR, "screenshots/x.png", 240))
        assertEquals(null, ReleaseStore.image(Fixture.images, Fixture.IMAGE_DIR, "../x.png", 240))
    }

    @Test fun dateIsFormatted() {
        val a = fake("2026-11-10-b", "Bee", "2026-11-10")
        assertEquals(LocalDate.of(2026, 11, 10), a.date)
        assertFalse(a.sample)
    }
}
