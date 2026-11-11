package ai.riskmandate.app.features.xray

import ai.riskmandate.app.RiskMandateTheme
import android.app.Application
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
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

    private fun request(url: String, main: Boolean = true) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = main
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private fun kiosk(onExit: () -> Unit = {}, startFailed: Boolean = false, showWebView: Boolean = false) = compose.setContent {
        RiskMandateTheme {
            XrayKiosk(onExit = onExit, startUrl = XrayPolicy.START_URL, exitHoldMs = 3_000, clock = { 0L }, showWebView = showWebView, startFailed = startFailed)
        }
    }

    @Test fun theKioskShowsTheWebViewAndNoCover() {
        kiosk(showWebView = true)
        compose.onNodeWithTag(XrayTags.WEBVIEW).assertExists()
        compose.onNodeWithTag(XrayTags.COVER).assertDoesNotExist()
        compose.onNodeWithTag(XrayTags.STAFF_CORNER).assertExists()
    }

    @Test fun aFailedPageShowsTheCalmCover() {
        kiosk(startFailed = true)
        compose.onNodeWithTag(XrayTags.COVER).assertIsDisplayed()
    }

    @Test fun aShortTapOnTheCornerDoesNothing() {
        kiosk()
        compose.onNodeWithTag(XrayTags.STAFF_CORNER).performTouchInput { down(center); advanceEventTime(500); up() }
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithTag(XrayTags.EXIT_DIALOG).assertDoesNotExist()
    }

    @Test fun holdingTheCornerThreeSecondsAsksAndLeaveExits() {
        var exited = 0
        kiosk(onExit = { exited++ })
        compose.onNodeWithTag(XrayTags.STAFF_CORNER).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(3_100)
        compose.onNodeWithTag(XrayTags.EXIT_DIALOG).assertIsDisplayed()
        assertEquals("asking is not leaving", 0, exited)
        compose.onNodeWithTag(XrayTags.EXIT_LEAVE).performClick()
        assertEquals(1, exited)
    }

    @Test fun stayKeepsTheKiosk() {
        var exited = 0
        kiosk(onExit = { exited++ })
        compose.onNodeWithTag(XrayTags.STAFF_CORNER).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(3_100)
        compose.onNodeWithTag(XrayTags.EXIT_STAY).performClick()
        compose.onNodeWithTag(XrayTags.EXIT_DIALOG).assertDoesNotExist()
        assertEquals(0, exited)
    }

    @Test fun theClientIgnoresPagesOffTheSiteAndBlocksForeignFetches() {
        var errors = 0
        val client = XrayWebViewClient(onStarted = {}, onFinished = {}, onMainFrameError = { errors++ }, onRenderGone = {})
        val view = WebView(app)
        assertFalse(client.shouldOverrideUrlLoading(view, request(XrayPolicy.START_URL)))
        assertTrue(client.shouldOverrideUrlLoading(view, request("https://evil.example/")))
        assertTrue(client.shouldOverrideUrlLoading(view, request("https://riskmandate.ai/")))
        assertTrue("a frame from a foreign host", client.shouldOverrideUrlLoading(view, request("https://evil.example/f", main = false)))
        assertNotNull(client.shouldInterceptRequest(view, request("https://tracker.example/p.js", main = false)))
        assertEquals(403, client.shouldInterceptRequest(view, request("https://tracker.example/p.js", main = false))!!.statusCode)
        assertNull(client.shouldInterceptRequest(view, request("https://cdn.jsdelivr.net/npm/three.js", main = false)))
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
