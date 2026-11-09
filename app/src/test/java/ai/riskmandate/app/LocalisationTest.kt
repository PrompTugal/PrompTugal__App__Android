package ai.riskmandate.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(AndroidJUnit4::class)
class LocalisationTest {
    @get:Rule val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test @Config(qualifiers = "pt-rPT")
    fun portugueseIsUsedOnAPortugueseDevice() {
        assertEquals("Abrir riskmandate.ai", app.getString(R.string.home_open))
        assertEquals("Etiqueta: Mesa 1", app.getString(R.string.home_device_label, "Mesa 1"))
        compose.setContent {
            RiskMandateTheme { HomeScreen("0.1.7 (7)", DeviceStatus.from(true, false, null), onOpen = {}) }
        }
        compose.onNodeWithText("Abrir riskmandate.ai").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.DEVICE_MANAGEMENT)
            .assertTextEquals("Gerido por uma organização (dispositivo totalmente gerido)")
    }

    @Test @Config(qualifiers = "en")
    fun englishIsTheDefault() = assertEquals("Open riskmandate.ai", app.getString(R.string.home_open))

    private val res = java.io.File("src/main/res")

    private fun keys(file: java.io.File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.getAttribute("translatable") != "false" }
            .map { it.getAttribute("name") }.toSet()
    }

    private fun stringFiles(dir: String) =
        java.io.File(res, dir).listFiles { f -> f.name.endsWith(".xml") }!!.associate { it.name to keys(it) }
            .filterValues { it.isNotEmpty() }

    /** Every string in every file of values (strings.xml, feature_ID.xml, and so on) has a pt-PT translation in the same file name, and no more. */
    @Test fun everyStringIsTranslated() {
        val en = stringFiles("values")
        val pt = stringFiles("values-pt-rPT")
        assertEquals("files differ between values and values-pt-rPT", en.keys, pt.keys)
        for ((file, enKeys) in en) {
            assertEquals("$file: missing in pt-PT", emptySet<String>(), enKeys - pt.getValue(file))
            assertEquals("$file: extra in pt-PT", emptySet<String>(), pt.getValue(file) - enKeys)
        }
        // Names are unique across files, so a feature cannot shadow a host string.
        val all = en.values.flatMap { it }
        assertEquals("duplicate string names across files", all.distinct().size, all.size)
    }

    /** No Kotlin source anywhere under app/src/main (host, features, ui) hardcodes visible text. */
    @Test fun noScreenHardcodesText() {
        val sources = java.io.File("src/main/java").walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()
        assertTrue("no sources found", sources.size > 5)
        val literal = Regex("""\bText\(\s*"|contentDescription\s*=\s*"|onClick\(label\s*=\s*"|\btitle\s*=\s*"|\bplaceholder\s*=\s*"""")
        val offenders = sources.filter { literal.containsMatchIn(it.readText()) }.map { it.path }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test fun layoutSurvives200PercentFontScale() {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                RiskMandateTheme {
                    HomeScreen("0.1.7 (7)", DeviceStatus.from(true, false, "Lisbon kiosk 3"), onOpen = {}, message = R.string.message_no_browser)
                }
            }
        }
        // Everything is reachable by scrolling, nothing is clipped away.
        compose.onNodeWithTag(HomeTags.OPEN).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.MESSAGE).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.LICENCES).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.VERSION).performScrollTo().assertIsDisplayed()
    }

    @Test fun touchTargetsAreAtLeast48dp() {
        compose.setContent {
            RiskMandateTheme { HomeScreen("0.1.7 (7)", DeviceStatus.from(false, false, null), onOpen = {}) }
        }
        val dp = app.resources.displayMetrics.density
        for (tag in listOf(HomeTags.OPEN, HomeTags.LICENCES)) {
            compose.onNodeWithTag(tag).performScrollTo()
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertNotEquals(0f, bounds.height)
            assert(bounds.height >= 48 * dp - 1) { "$tag is ${bounds.height / dp}dp high" }
        }
    }
}
