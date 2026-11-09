package ai.riskmandate.app.features.site

import ai.riskmandate.app.RiskMandateTheme
import android.app.Application
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun request(url: String, main: Boolean = true, method: String = "GET") = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = main
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = method
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private fun client(switch: OfflineSwitch, external: MutableList<String> = mutableListOf()) =
        SiteWebViewClient(app, switch, onExternal = { external += it }, onMainFrameError = {}, notInCopyHtml = { "<p>not in copy</p>" })

    @Test fun theScreenRendersItsTabsOnline() {
        compose.setContent {
            RiskMandateTheme { SiteScreen(OfflineSwitch(false), onBack = {}, isSystemOffline = { false }, openExternal = { _, _ -> null }) }
        }
        compose.onNodeWithTag(SiteTags.BACK).assertIsDisplayed()
        compose.onNodeWithTag(SiteTags.TABS).assertIsDisplayed()
        for (tab in SITE_TABS) compose.onNodeWithTag(SiteTags.tab(tab.id)).assertExists()
        compose.onNodeWithTag(SiteTags.WEBVIEW).assertExists()
        compose.onNodeWithTag(SiteTags.OFFLINE_BANNER).assertDoesNotExist()
    }

    @Test fun offlineShowsTheBannerWithTheDateAndTryOnlineSwitchesBack() {
        val switch = OfflineSwitch(true)
        compose.setContent {
            RiskMandateTheme { SiteScreen(switch, onBack = {}, isSystemOffline = { false }, openExternal = { _, _ -> null }) }
        }
        compose.onNodeWithTag(SiteTags.OFFLINE_BANNER).assertIsDisplayed()
        compose.onNodeWithText("Offline copy from 9 November 2026", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(SiteTags.TRY_ONLINE).performClick()
        compose.waitForIdle()
        assertFalse(switch.offline)
        compose.onNodeWithTag(SiteTags.OFFLINE_BANNER).assertDoesNotExist()
    }

    @Test fun tryOnlineStaysOfflineWithoutAConnection() {
        val switch = OfflineSwitch(true)
        compose.setContent {
            RiskMandateTheme { SiteScreen(switch, onBack = {}, isSystemOffline = { true }, openExternal = { _, _ -> null }) }
        }
        compose.onNodeWithTag(SiteTags.TRY_ONLINE).performClick()
        compose.waitForIdle()
        assertTrue(switch.offline)
        compose.onNodeWithTag(SiteTags.OFFLINE_BANNER).assertIsDisplayed()
    }

    @Test fun backButtonLeaves() {
        var left = false
        compose.setContent { RiskMandateTheme { SiteScreen(OfflineSwitch(false), onBack = { left = true }, isSystemOffline = { false }, openExternal = { _, _ -> null }) } }
        compose.onNodeWithTag(SiteTags.BACK).performClick()
        assertTrue(left)
    }

    @Test fun anAllowedLinkStaysInTheWebViewAndAnythingElseGoesOut() {
        val external = mutableListOf<String>()
        val c = client(OfflineSwitch(false), external)
        val wv = WebView(app)
        assertFalse(c.shouldOverrideUrlLoading(wv, request("https://riskmandate.ai/pricing.html")))
        assertTrue(external.isEmpty())
        assertTrue(c.shouldOverrideUrlLoading(wv, request("https://evil.example/")))
        assertTrue(c.shouldOverrideUrlLoading(wv, request("https://riskmandate.ai:x@evil.example/")))
        assertTrue(c.shouldOverrideUrlLoading(wv, request("mailto:agent@riskmandate.ai")))
        assertEquals(listOf("https://evil.example/", "https://riskmandate.ai:x@evil.example/", "mailto:agent@riskmandate.ai"), external)
        // A frame inside a page never takes the visitor out.
        assertTrue(c.shouldOverrideUrlLoading(wv, request("https://evil.example/frame", main = false)))
        assertEquals(3, external.size)
    }

    @Test fun onlineRequestsPassThroughAndForeignSubresourcesAreBlocked() {
        val c = client(OfflineSwitch(false))
        val wv = WebView(app)
        assertNull(c.shouldInterceptRequest(wv, request("https://riskmandate.ai/abp.html")))
        assertEquals(403, c.shouldInterceptRequest(wv, request("https://tracker.example/p.js", main = false))!!.statusCode)
    }

    @Test fun offlineServesTheBundledPagesAndNothingFromTheNetwork() {
        val c = client(OfflineSwitch(true))
        val wv = WebView(app)
        val home = c.shouldInterceptRequest(wv, request("https://riskmandate.ai/"))!!
        assertEquals(200, home.statusCode)
        assertTrue(home.data.readBytes().toString(Charsets.UTF_8).contains("Give agents access. Not free rein."))
        assertEquals(200, c.shouldInterceptRequest(wv, request("https://riskmandate.ai/pricing.html#x", main = false))!!.statusCode)
        val missing = c.shouldInterceptRequest(wv, request("https://riskmandate.ai/contact.html"))!!
        assertEquals(404, missing.statusCode)
        assertTrue(missing.data.readBytes().toString(Charsets.UTF_8).contains("not in copy"))
        assertEquals(404, c.shouldInterceptRequest(wv, request("https://riskmandate.ai/assets/fonts/geist-latin.woff2", main = false))!!.statusCode)
        assertEquals(403, c.shouldInterceptRequest(wv, request("https://evil.example/", main = false))!!.statusCode)
        assertEquals(405, c.shouldInterceptRequest(wv, request("https://riskmandate.ai/", method = "POST"))!!.statusCode)
    }

    @Test fun theWebViewIsLockedDown() {
        val wv = WebView(app)
        wv.applySiteSettings()
        with(wv.settings) {
            assertTrue(javaScriptEnabled)
            assertFalse(allowFileAccess)
            assertFalse(allowContentAccess)
            assertEquals(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW, mixedContentMode)
        }
        // Robolectric's WebSettings shadow does not keep the Safe Browsing flag, so check the source line instead.
        val source = java.io.File("src/main/java/ai/riskmandate/app/features/site/SiteWebView.kt").readText()
        assertTrue(source.contains("safeBrowsingEnabled = true"))
        assertTrue(source.contains("setAcceptThirdPartyCookies(this, false)"))
    }

    /** The site's own sources keep their text in string resources, as the rest of the app does. */
    @Test fun noHardcodedText() {
        val literal = Regex("""\bText\(\s*"|contentDescription\s*=\s*"""")
        val offenders = java.io.File("src/main/java/ai/riskmandate/app/features/site").listFiles { f -> f.name.endsWith(".kt") }!!
            .filter { literal.containsMatchIn(it.readText()) }.map { it.name }
        assertEquals(emptyList<String>(), offenders)
    }

    /** No JavaScript interface, no file access: the sources must not add them. */
    @Test fun noJavascriptInterfaceAnywhere() {
        val offenders = java.io.File("src/main/java/ai/riskmandate/app/features/site").listFiles { f -> f.name.endsWith(".kt") }!!
            .filter { it.readText().contains("addJavascriptInterface") }.map { it.name }
        assertEquals(emptyList<String>(), offenders)
    }
}
