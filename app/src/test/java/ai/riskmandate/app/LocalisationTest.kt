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
        compose.onNodeWithText("Abrir riskmandate.ai").assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.DEVICE_MANAGEMENT)
            .assertTextEquals("Gerido por uma organização (dispositivo totalmente gerido)")
    }

    @Test @Config(qualifiers = "en")
    fun englishIsTheDefault() = assertEquals("Open riskmandate.ai", app.getString(R.string.home_open))

    /** Every string has a pt-PT translation (lint's MissingTranslation is a warning; this makes it a failure). */
    @Test fun everyStringIsTranslated() {
        fun keys(path: String): Set<String> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.io.File(path))
            val nodes = doc.getElementsByTagName("string")
            return (0 until nodes.length).map { nodes.item(it) as Element }
                .filter { it.getAttribute("translatable") != "false" }
                .map { it.getAttribute("name") }.toSet()
        }
        val en = keys("src/main/res/values/strings.xml")
        val pt = keys("src/main/res/values-pt-rPT/strings.xml")
        assertEquals(emptySet<String>(), en - pt)
        assertEquals(emptySet<String>(), pt - en)
    }

    @Test fun noScreenHardcodesText() {
        val sources = java.io.File("src/main/java/ai/riskmandate/app").listFiles { f -> f.name.endsWith(".kt") }!!
        val literal = Regex("""\bText\(\s*"|contentDescription\s*=\s*"""")
        val offenders = sources.filter { literal.containsMatchIn(it.readText()) }.map { it.name }
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
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertNotEquals(0f, bounds.height)
            assert(bounds.height >= 48 * dp - 1) { "$tag is ${bounds.height / dp}dp high" }
        }
    }
}
