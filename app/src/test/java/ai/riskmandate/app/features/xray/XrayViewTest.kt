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
}
