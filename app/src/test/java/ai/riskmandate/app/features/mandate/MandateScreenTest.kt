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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

    private fun show(s: MandateState, fontScale: Float = 1f) = compose.setContent {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
            RiskMandateTheme { MandateScreen(s) }
        }
    }

    @Test fun noGapIsSaidPlainly() {
        show(state(cleanReach))
        compose.onNodeWithTag(MandateTags.SUMMARY).assertTextEquals("Reach 4, mandate 9, gap 0")
        compose.onNodeWithTag(MandateTags.GAP_STATUS)
            .assertTextEquals("Gap: none. Everything this app can reach, its mandate needs.")
    }

    @Test fun aGapIsShownPlainlyWithItsItems() {
        show(state(cleanReach + dump))
        compose.onNodeWithTag(MandateTags.SUMMARY).assertTextEquals("Reach 5, mandate 9, gap 1")
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
        compose.onNodeWithText("Data collected by this app: none", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Storage used: none", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun showsWhatTheAppConnectsTo() {
        show(state(cleanReach))
        compose.onNodeWithText("connects to nothing itself", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun showsTheInAppSiteViewConnectionsWhenSiteIsOn() {
        show(state(cleanReach + networkReach(setOf("site"))))
        compose.onNodeWithTag(MandateTags.reachItem("network:connects:riskmandate.ai")).performScrollTo().assertIsDisplayed()
            .assertTextContains("riskmandate.ai", substring = true)
        compose.onNodeWithTag(MandateTags.GAP_STATUS).assertTextEquals("Gap: none. Everything this app can reach, its mandate needs.")
    }

    @Test fun showsRefusedManagedStartUrl() {
        show(state(cleanReach, ManagedConfig.fromMap(mapOf("start_url" to "https://example.com/"))))
        compose.onNodeWithText("start_url was set but refused", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test @Config(qualifiers = "pt-rPT")
    fun portugueseOnAPortugueseDevice() {
        show(state(cleanReach + dump))
        compose.onNodeWithTag(MandateTags.SUMMARY).assertTextEquals("Alcance 5, mandato 9, lacuna 1")
        compose.onNodeWithTag(MandateTags.GAP_STATUS)
            .assertTextEquals("Lacuna: 1. Esta app consegue alcançar coisas de que o seu mandato não precisa:")
        compose.onNodeWithText("Dados recolhidos por esta app: nenhuns", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun layoutSurvives200PercentFontScale() {
        show(state(cleanReach + dump), fontScale = 2f)
        compose.onNodeWithTag(MandateTags.gapItem(dump.key)).performScrollTo().assertIsDisplayed()
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
        assertEquals(emptyList<String>(), keys.filter { it.startsWith("storage:") })
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

    @Test fun collectorSeesEveryFileAndDirectoryTheAppKeeps() {
        assertEquals(emptyList<ReachItem>(), ReachCollector.storage(app))
        val data = java.io.File(app.applicationInfo.dataDir)
        val made = listOf("files/x.txt", "shared_prefs/stand_mode.xml", "app_webview/Default/Cookies", "app_webview/Default/b").map {
            java.io.File(data, it).also { f -> f.parentFile!!.mkdirs(); f.writeText("x") }
        }
        java.io.File(app.cacheDir, "c.bin").writeText("x")
        try {
            val items = ReachCollector.storage(app).associateBy { it.id }
            assertEquals(setOf("files/x.txt", "shared_prefs/stand_mode.xml", "app_webview", "cache"), items.keys)
            assertEquals(2, items.getValue("app_webview").count)
            val report = MandateReport(ReachCollector.collect(app), mandate)
            // Stand's settings, the in-app view's storage and its cache are mandated; a stray file is not.
            assertEquals(listOf("files/x.txt"), report.gap.filter { it.kind == ReachKind.STORAGE }.map { it.id })
        } finally {
            made.forEach { it.delete() }
            java.io.File(app.cacheDir, "c.bin").delete()
            java.io.File(data, "app_webview").deleteRecursively()
        }
    }

    @Test fun featureIsEnabledAndRendersEndToEnd() {
        assertTrue(MandateFeature.enabled)
        assertEquals("mandate", MandateFeature.id)
        val config = ManagedConfig.fromMap(mapOf("device_label" to "Stand 1"))
        val context = FeatureContext(onBack = {}, config = config, device = DeviceStatus.from(false, false, config.deviceLabel))
        compose.setContent { RiskMandateTheme { MandateFeature.Content(context) } }
        // Reach is read off the main thread: wait for the report.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(MandateTags.SUMMARY).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(MandateTags.SUMMARY).assertIsDisplayed()
        compose.onNodeWithTag(MandateTags.GAP_STATUS).assertIsDisplayed()
        compose.onNodeWithTag(MandateTags.REACH).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(MandateTags.MANDATE).performScrollTo().assertIsDisplayed()
        // The managed value comes from the context, not from a second read.
        compose.onNodeWithText("device_label: Stand 1").performScrollTo().assertIsDisplayed()
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
