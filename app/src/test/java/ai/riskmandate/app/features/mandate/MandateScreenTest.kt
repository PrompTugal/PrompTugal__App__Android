package ai.riskmandate.app.features.mandate

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.Links
import ai.riskmandate.app.ManagedConfig
import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.features.FeatureContext
import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(AndroidJUnit4::class)
class MandateScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val pkg = "ai.riskmandate.app"
    private val mandate = Mandate.current(pkg, listOf("start_url", "device_label"))

    private val cleanReach = listOf(
        ReachItem(ReachKind.PERMISSION, "android.permission.INTERNET"),
        ReachItem(ReachKind.PERMISSION, "android.permission.ACCESS_NETWORK_STATE"),
        ReachItem(ReachKind.PERMISSION, "$pkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"),
        ReachItem(ReachKind.COMPONENT, "$pkg.MainActivity", componentType = ComponentType.ACTIVITY),
    )
    private val dump = ReachItem(
        ReachKind.COMPONENT, "androidx.profileinstaller.ProfileInstallReceiver",
        componentType = ComponentType.RECEIVER, guardedBy = "android.permission.DUMP",
    )

    private fun state(reach: List<ReachItem>, config: ManagedConfig = ManagedConfig.DEFAULT) = MandateState(
        MandateReport(reach, mandate), HostProbe.run(Links::isAllowed), config, DeviceStatus.from(false, false, null),
    )

    private fun show(s: MandateState, onBack: () -> Unit = {}, fontScale: Float = 1f) = compose.setContent {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
            RiskMandateTheme { MandateScreen(s, onBack) }
        }
    }

    @Test fun noGapIsSaidPlainly() {
        show(state(cleanReach))
        compose.onNodeWithTag(MandateTags.SUMMARY).assertTextEquals("Reach 4, mandate 4, gap 0")
        compose.onNodeWithTag(MandateTags.GAP_STATUS)
            .assertTextEquals("Gap: none. Everything this app can reach, its mandate needs.")
    }

    @Test fun aGapIsShownPlainlyWithItsItems() {
        show(state(cleanReach + dump))
        compose.onNodeWithTag(MandateTags.SUMMARY).assertTextEquals("Reach 5, mandate 4, gap 1")
        compose.onNodeWithTag(MandateTags.GAP_STATUS)
            .assertTextEquals("Gap: 1. This app can reach things its mandate does not need:")
        compose.onNodeWithTag(MandateTags.gapItem(dump.key)).performScrollTo().assertIsDisplayed()
            .assertTextContains("androidx.profileinstaller.ProfileInstallReceiver", substring = true)
            .assertTextContains("callers must hold android.permission.DUMP", substring = true)
    }

    @Test fun showsQuotedDefinitionsAndTheAllowListProbes() {
        show(state(cleanReach))
        compose.onNodeWithText("Everything the agent can actually access", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("https://riskmandate.ai/: allowed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("http://riskmandate.ai/: refused").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Data collected: none", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Storage used: none", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun showsRefusedManagedStartUrl() {
        show(state(cleanReach, ManagedConfig.fromMap(mapOf("start_url" to "https://example.com/"))))
        compose.onNodeWithText("start_url was set but refused", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun backButtonWorks() {
        var back = 0
        show(state(cleanReach), onBack = { back++ })
        compose.onNodeWithTag(MandateTags.BACK).performClick()
        assertEquals(1, back)
    }

    @Test @Config(qualifiers = "pt-rPT")
    fun portugueseOnAPortugueseDevice() {
        show(state(cleanReach + dump))
        compose.onNodeWithTag(MandateTags.SUMMARY).assertTextEquals("Alcance 5, mandato 4, lacuna 1")
        compose.onNodeWithTag(MandateTags.GAP_STATUS)
            .assertTextEquals("Lacuna: 1. Esta app consegue alcançar coisas de que o seu mandato não precisa:")
        compose.onNodeWithText("Dados recolhidos: nenhuns", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun layoutSurvives200PercentFontScale() {
        show(state(cleanReach + dump), fontScale = 2f)
        compose.onNodeWithTag(MandateTags.BACK).assertIsDisplayed()
        compose.onNodeWithTag(MandateTags.gapItem(dump.key)).performScrollTo().assertIsDisplayed()
    }

    @Test fun backTouchTargetIsAtLeast48dp() {
        show(state(cleanReach))
        val dp = app.resources.displayMetrics.density
        assertTrue(compose.onNodeWithTag(MandateTags.BACK).fetchSemanticsNode().boundsInRoot.height >= 48 * dp - 1)
    }

    /** Real collection on this (Robolectric) device: the facts that must hold for the app as built. */
    @Test fun collectorReadsTheRealManifest() {
        val reach = ReachCollector.collect(app)
        val keys = reach.map { it.key }
        assertTrue(keys.contains("permission:android.permission.INTERNET"))
        assertTrue(keys.contains("permission:android.permission.ACCESS_NETWORK_STATE"))
        assertTrue(keys.contains("component:$pkg.MainActivity"))
        // Cleartext is off, backup is off, nothing stored: none of these are reach.
        assertFalse(keys.contains("network:cleartext"))
        assertFalse(keys.contains("storage:backup"))
        assertFalse(keys.contains("storage:files"))
        // No permission beyond the mandated ones is requested.
        val perms = reach.filter { it.kind == ReachKind.PERMISSION }.map { it.id }.toSet()
        assertEquals(
            setOf(
                "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
                "$pkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            ),
            perms,
        )
        val main = reach.first { it.id == "$pkg.MainActivity" }
        assertEquals(ComponentType.ACTIVITY, main.componentType)
    }

    @Test fun collectorSeesFilesTheAppKeeps() {
        assertEquals(0, ReachCollector.storageFileCount(app))
        java.io.File(app.filesDir, "x.txt").writeText("x")
        try {
            assertEquals(1, ReachCollector.storageFileCount(app))
            assertTrue(ReachCollector.collect(app).any { it.key == "storage:files" && it.count == 1 })
        } finally {
            java.io.File(app.filesDir, "x.txt").delete()
        }
    }

    @Test fun featureIsEnabledAndRendersEndToEnd() {
        assertTrue(MandateFeature.enabled)
        assertEquals("mandate", MandateFeature.id)
        val activity = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
        compose.setContent { RiskMandateTheme { MandateFeature.Content(FeatureContext(activity, onBack = {})) } }
        compose.onNodeWithTag(MandateTags.SUMMARY).assertIsDisplayed()
        compose.onNodeWithTag(MandateTags.GAP_STATUS).assertIsDisplayed()
        compose.onNodeWithTag(MandateTags.REACH).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(MandateTags.MANDATE).performScrollTo().assertIsDisplayed()
    }

    @Test fun everyMandateStringIsTranslated() {
        fun keys(path: String): Set<String> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.io.File(path))
            val nodes = doc.getElementsByTagName("string")
            return (0 until nodes.length).map { nodes.item(it) as Element }.map { it.getAttribute("name") }.toSet()
        }
        val en = keys("src/main/res/values/feature_mandate.xml")
        val pt = keys("src/main/res/values-pt-rPT/feature_mandate.xml")
        assertEquals(emptySet<String>(), en - pt)
        assertEquals(emptySet<String>(), pt - en)
    }
}
