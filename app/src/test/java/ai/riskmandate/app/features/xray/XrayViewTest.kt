package ai.riskmandate.app.features.xray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayViewTest {
    @Test fun startsFullScreenAndTogglesBackAndForth() {
        val start = XrayView()
        assertTrue(start.fullScreen)
        val windowed = start.toggled()
        assertFalse(windowed.fullScreen)
        assertTrue(windowed.toggled().fullScreen)
        assertFalse(windowed.toggled().toggled().fullScreen)
    }

    @Test fun thePagesOwnButtonFromTheWindowReturnsToTheWindow() {
        val page = XrayView(fullScreen = false).pageEntered()
        assertEquals(XrayView(fullScreen = true, pageFullScreen = true, byPage = true), page)
        assertEquals(XrayView(fullScreen = false), page.pageExited())
    }

    @Test fun thePagesOwnButtonInFullScreenStaysFullScreen() {
        val page = XrayView(fullScreen = true).pageEntered()
        assertFalse(page.byPage)
        assertEquals(XrayView(fullScreen = true), page.pageExited())
    }

    @Test fun leavingFullScreenEndsThePagesFullScreenToo() {
        val page = XrayView().pageEntered()
        assertTrue(page.leavingEndsPageFullScreen)
        val out = page.toggled()
        assertEquals(XrayView(fullScreen = false), out)
        // WebView then reports the page left full screen: stays in the window.
        assertEquals(XrayView(fullScreen = false), out.pageExited())
        assertFalse(XrayView().leavingEndsPageFullScreen)
    }

    @Test fun backDuringThePagesFullScreenEndsOnlyThat() {
        // Started full screen, then the page's own button: Back ends the page's full screen, the app's stays (as Chrome).
        val page = XrayView().pageEntered()
        assertEquals(XrayView(fullScreen = true), page.back())
        // WebView then reports the page left full screen: nothing more changes.
        assertEquals(XrayView(fullScreen = true), page.back().pageExited())
        // The page's button from the window: Back returns to the window, where the page had been.
        assertEquals(XrayView(fullScreen = false), XrayView(fullScreen = false).pageEntered().back())
    }

    @Test fun backInTheAppsFullScreenLeavesItAndInTheWindowDoesNothingHere() {
        assertEquals(XrayView(fullScreen = false), XrayView().back())
        assertEquals(XrayView(fullScreen = false), XrayView(fullScreen = false).back())
    }
}
