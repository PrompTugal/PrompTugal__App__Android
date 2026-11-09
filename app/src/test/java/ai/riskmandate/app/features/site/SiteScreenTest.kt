package ai.riskmandate.app.features.site

import ai.riskmandate.app.RiskMandateTheme
import android.app.Application
import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun request(url: String, main: Boolean = true, method: String = "GET", gesture: Boolean = true, redirect: Boolean = false) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = main
        override fun isRedirect() = redirect
        override fun hasGesture() = gesture
        override fun getMethod() = method
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private fun client(switch: OfflineSwitch, external: MutableList<String> = mutableListOf()) =
        SiteWebViewClient(app, switch, onExternal = { external += it }, onMainFrameError = {}, onRenderGone = {}, notInCopyHtml = { "<p>not in copy</p>" })

    @Test fun theScreenRendersItsTabsOnline() {
        compose.setContent {
            RiskMandateTheme { SiteScreen(OfflineSwitch(false), onBack = {}, isSystemOffline = { false }, openExternal = { _, _ -> null }) }
        }
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

    @Test fun theSelectedTabAndTheOfflineModeSurviveRotation() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        val switch = OfflineSwitch(false)
        restoration.setContent {
            RiskMandateTheme { SiteScreen(switch, onBack = {}, isSystemOffline = { false }, openExternal = { _, _ -> null }) }
        }
        compose.onNodeWithTag(SiteTags.tab("pricing")).performClick()
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(SiteTags.tab("pricing")).assertIsSelected()
        compose.onNodeWithTag(SiteTags.tab("home")).assertIsNotSelected()
    }

    @Test fun theFeatureDescribesItselfForTheHomeCard() {
        assertNull("design ruling: the host's icon applies", SiteFeature.icon)
        assertEquals("riskmandate.ai", app.getString(SiteFeature.kicker!!))
        assertTrue(app.getString(SiteFeature.summary!!).isNotBlank())
        assertFalse("no em-dash", app.getString(SiteFeature.summary!!).contains('\u2014'))
    }

    @Test fun onlyARealTapOpensTheSystemBrowser() {
        val external = mutableListOf<String>()
        val c = client(OfflineSwitch(false), external)
        val wv = WebView(app)
        assertTrue(c.shouldOverrideUrlLoading(wv, request("https://github.com/x", gesture = false)))
        assertTrue(c.shouldOverrideUrlLoading(wv, request("https://github.com/x", redirect = true)))
        assertTrue(c.shouldOverrideUrlLoading(wv, request("https://github.com/x", main = false)))
        assertTrue("script- or redirect-initiated: nothing opened", external.isEmpty())
        assertTrue(c.shouldOverrideUrlLoading(wv, request("https://github.com/x")))
        assertEquals(listOf("https://github.com/x"), external)
    }

    @Test fun onlineRequestsPassThroughAndForeignSubresourcesAreBlocked() {
        val c = client(OfflineSwitch(false))
        val wv = WebView(app)
        assertNull(c.shouldInterceptRequest(wv, request("https://riskmandate.ai/abp.html")))
        assertEquals(403, c.shouldInterceptRequest(wv, request("https://tracker.example/p.js", main = false))!!.statusCode)
    }

    @Test fun theOfflineCopyIsServedFromTheAssetOriginOnly() {
        val c = client(OfflineSwitch(true))
        val wv = WebView(app)
        val base = OfflineCopy.ASSET_BASE
        val home = c.shouldInterceptRequest(wv, request(base + "index.html"))!!
        assertTrue("loader responses carry no explicit status (0 = default 200)", home.statusCode == 0 || home.statusCode == 200)
        assertEquals("text/html", home.mimeType)
        assertTrue(home.data.readBytes().toString(Charsets.UTF_8).contains("Give agents access. Not free rein."))
        assertNotNull(c.shouldInterceptRequest(wv, request(base + "pricing.html#x", main = false))!!.data)
        val missing = c.shouldInterceptRequest(wv, request(base + "contact.html"))!!
        assertEquals(404, missing.statusCode)
        assertTrue(missing.data.readBytes().toString(Charsets.UTF_8).contains("not in copy"))
        assertEquals(404, c.shouldInterceptRequest(wv, request(base + "assets/fonts/geist-latin.woff2", main = false))!!.statusCode)
        assertEquals(405, c.shouldInterceptRequest(wv, request(base + "index.html", method = "POST"))!!.statusCode)
        // Nothing is fetched from riskmandate.ai or anywhere else while offline.
        assertEquals(404, c.shouldInterceptRequest(wv, request("https://riskmandate.ai/assets/fonts/geist-latin.woff2", main = false))!!.statusCode)
        assertEquals(403, c.shouldInterceptRequest(wv, request("https://evil.example/", main = false))!!.statusCode)
    }

    @Test fun assetPathsCannotEscapeTheFolder() {
        for (bad in listOf("../AndroidManifest.xml", "a/../../b", "%2e%2e/x", "a//b", "/etc/passwd", "a\\b", ".hidden/..", "", "a b")) {
            assertFalse(bad, SiteAssets.safe(bad))
        }
        for (ok in listOf("index.html", "assets/anim/embed.css", "how-it-works.html")) assertTrue(ok, SiteAssets.safe(ok))
        val c = client(OfflineSwitch(true))
        assertEquals(404, c.shouldInterceptRequest(WebView(app), request(OfflineCopy.ASSET_BASE + "../licences.txt"))!!.statusCode)
        assertEquals(404, c.shouldInterceptRequest(WebView(app), request(OfflineCopy.ASSET_BASE + "%2e%2e/licences.txt"))!!.statusCode)
    }

    @Test fun offlineLinksToRiskmandateOpenTheirCopyAndTheAssetOriginIsAllowed() {
        val c = client(OfflineSwitch(true))
        val wv = WebView(app)
        assertFalse(c.shouldOverrideUrlLoading(wv, request(OfflineCopy.ASSET_BASE + "abp.html")))
        // A riskmandate.ai link while offline is handled here (it loads the copy), so the WebView never goes to the network.
        assertTrue(c.shouldOverrideUrlLoading(wv, request("https://riskmandate.ai/pricing.html")))
        assertEquals(Decision.OFFLINE_COPY, SitePolicy.decide(OfflineCopy.ASSET_BASE + "abp.html"))
        assertEquals(Decision.EXTERNAL, SitePolicy.decide("https://appassets.androidplatform.net/assets/other/x.html"))
        assertEquals(Decision.EXTERNAL, SitePolicy.decide("https://appassets.androidplatform.net/res/x.png"))
    }

    @Test fun certificateErrorsAreNeverProceededPast() {
        val client = client(OfflineSwitch(false))
        val handler = android.webkit.RecordingSslErrorHandler()
        client.onReceivedSslError(WebView(app), handler, android.net.http.SslError(android.net.http.SslError.SSL_UNTRUSTED, android.net.http.SslCertificate("CN=test", "CN=test", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z")))
        assertTrue(handler.cancelled)
        assertFalse(handler.proceeded)
    }

    @Test fun theRendererCrashDropsTheWebViewInsteadOfTheApp() {
        var gone = 0
        val c = SiteWebViewClient(app, OfflineSwitch(false), onExternal = {}, onMainFrameError = {}, onRenderGone = { gone++ }, notInCopyHtml = { "" })
        val detail = object : android.webkit.RenderProcessGoneDetail() {
            override fun didCrash() = true
            override fun rendererPriorityAtExit() = 0
        }
        assertTrue(c.onRenderProcessGone(WebView(app), detail))
        assertEquals(1, gone)
    }

    @Test fun theChromeClientDeniesEverythingAPageCanAsk() {
        val chrome = SiteChromeClient()
        var geo: Boolean? = null
        chrome.onGeolocationPermissionsShowPrompt("https://riskmandate.ai", GeolocationPermissions.Callback { _, allow, _ -> geo = allow })
        assertEquals(false, geo)
        var chosen: Array<Uri>? = arrayOf(Uri.EMPTY)
        assertTrue(chrome.onShowFileChooser(null, ValueCallback { chosen = it }, null))
        assertNull(chosen)
        assertFalse(chrome.onCreateWindow(WebView(app), false, true, null))
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
