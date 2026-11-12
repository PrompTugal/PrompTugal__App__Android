package ai.riskmandate.app.features.xray

import ai.riskmandate.app.RiskMandateTheme
import android.app.Application
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
class XrayScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun request(url: String, main: Boolean = true, gesture: Boolean = true) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = main
        override fun isRedirect() = false
        override fun hasGesture() = gesture
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private fun screen(startFailed: Boolean = false, fullScreen: Boolean = false, showWebView: Boolean = false) = compose.setContent {
        RiskMandateTheme {
            XrayScreen(startUrl = XrayPolicy.START_URL, clock = { 0L }, showWebView = showWebView, startFailed = startFailed, startFullScreen = fullScreen)
        }
    }

    @Test fun inTheAppTheBarIsAboveThePage() {
        screen(showWebView = true)
        compose.onNodeWithTag(XrayTags.WEBVIEW).assertExists()
        compose.onNodeWithTag(XrayTags.TOOLBAR).assertIsDisplayed()
        compose.onNodeWithTag(XrayTags.RELOAD).assertIsDisplayed()
        compose.onNodeWithTag(XrayTags.COVER).assertDoesNotExist()
        compose.onNodeWithTag(XrayTags.FULLSCREEN).assertDoesNotExist()
    }

    @Test fun aFailedPageShowsTheCalmCover() {
        screen(startFailed = true)
        compose.onNodeWithTag(XrayTags.COVER).assertIsDisplayed()
    }

    @Test fun fullScreenInAndOut() {
        screen()
        compose.onNodeWithTag(XrayTags.FULLSCREEN_ENTER).performClick()
        compose.onNodeWithTag(XrayTags.FULLSCREEN).assertExists()
        compose.onNodeWithTag(XrayTags.TOOLBAR).assertDoesNotExist()
        compose.onNodeWithTag(XrayTags.FULLSCREEN_EXIT).assertIsDisplayed().performClick()
        compose.onNodeWithTag(XrayTags.FULLSCREEN).assertDoesNotExist()
        compose.onNodeWithTag(XrayTags.TOOLBAR).assertIsDisplayed()
    }

    @Test fun theExitButtonFadesAndATapBringsItBack() {
        compose.mainClock.autoAdvance = false
        compose.setContent { RiskMandateTheme { FullScreenContent(onExit = {}) { Box(Modifier.fillMaxSize()) } } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(XrayTags.FULLSCREEN_EXIT).assertExists()
        compose.mainClock.advanceTimeBy(XRAY_TOGGLE_SHOWN_MS + 1_000)
        compose.onNodeWithTag(XrayTags.FULLSCREEN_EXIT).assertDoesNotExist()
        compose.mainClock.autoAdvance = true // the tap is delivered and recomposed; the 4 s fade is a real delay, not skipped
        compose.onNodeWithTag(XrayTags.FULLSCREEN).performTouchInput { click(center) }
        compose.waitForIdle()
        compose.onNodeWithTag(XrayTags.FULLSCREEN_EXIT).assertExists()
    }

    @Test fun startsFullScreenByDefault() {
        screen(fullScreen = true)
        compose.onNodeWithTag(XrayTags.FULLSCREEN).assertExists()
        compose.onNodeWithTag(XrayTags.TOOLBAR).assertDoesNotExist()
    }

    @Test fun theClientKeepsPagesOnTheSiteAndSendsTappedLinksOutside() {
        var errors = 0
        val outside = mutableListOf<String>()
        val client = XrayWebViewClient(onStarted = {}, onFinished = {}, onMainFrameError = { errors++ }, onRenderGone = {}, onOutside = { outside += it })
        val view = WebView(app)
        assertFalse(client.shouldOverrideUrlLoading(view, request(XrayPolicy.START_URL)))
        assertTrue(client.shouldOverrideUrlLoading(view, request("https://claude.ai/new")))
        assertEquals(listOf("https://claude.ai/new"), outside)
        assertTrue(client.shouldOverrideUrlLoading(view, request("https://evil.example/", gesture = false)))
        assertEquals("not tapped: not opened", 1, outside.size)
        assertFalse("a frame from another host over TLS", client.shouldOverrideUrlLoading(view, request("https://example.com/f", main = false)))
        assertTrue("a frame over plain http", client.shouldOverrideUrlLoading(view, request("http://example.com/f", main = false)))
        assertNull("any host over TLS", client.shouldInterceptRequest(view, request("https://api.github.com/repos/x", main = false)))
        assertEquals(403, client.shouldInterceptRequest(view, request("http://example.com/p.js", main = false))!!.statusCode)
        assertNotNull(client.shouldInterceptRequest(view, request("https://evil.example/", main = true)))
        assertEquals(0, errors)
    }

    @Test fun theSettingsKeepThePageStateAndStayLockedDown() {
        val view = WebView(app)
        view.applyXraySettings()
        with(view.settings) {
            assertTrue("DOM storage kept for the demo key", domStorageEnabled)
            assertTrue(javaScriptEnabled)
            assertFalse(allowFileAccess)
            assertFalse(allowContentAccess)
            assertFalse(javaScriptCanOpenWindowsAutomatically)
        }
    }
}
