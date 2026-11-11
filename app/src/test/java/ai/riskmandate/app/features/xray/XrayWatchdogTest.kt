package ai.riskmandate.app.features.xray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayWatchdogTest {
    private val dog = XrayWatchdog(stallMs = 45_000, firstRetryMs = 10_000, maxRetryMs = 60_000)

    @Test fun aPageThatLoadsIsLeftAlone() {
        dog.onLoadStarted(0)
        dog.onLoadFinished(3_000)
        assertEquals(XrayWatchdog.State.SHOWING, dog.state)
        assertFalse(dog.coverShown)
        assertEquals(XrayAction.NONE, dog.tick(10_000_000))
        assertEquals("the network callback fires on registration: no reload", XrayAction.NONE, dog.onNetworkAvailable(10_000_001))
    }

    @Test fun aStalledLoadIsReloaded() {
        dog.onLoadStarted(0)
        assertEquals(XrayAction.NONE, dog.tick(44_999))
        assertEquals(XrayAction.RELOAD, dog.tick(45_000))
        assertTrue("the cover shows while it retries", dog.coverShown)
    }

    @Test fun anErrorShowsTheCoverAndRetriesWithBackoff() {
        dog.onLoadStarted(0)
        dog.onMainFrameError(1_000)
        dog.onLoadFinished(1_100, failedMeanwhile = true) // WebView still reports the end of a failed load
        assertTrue(dog.coverShown)
        assertEquals(XrayAction.NONE, dog.tick(10_999))
        assertEquals(XrayAction.RELOAD, dog.tick(11_000))
        // The retry fails too: 20 s, then 40 s, then at most 60 s.
        dog.onLoadStarted(11_000); dog.onMainFrameError(11_500)
        assertEquals(20_000, dog.retryDelayMs())
        dog.onLoadStarted(31_500); dog.onMainFrameError(32_000)
        assertEquals(40_000, dog.retryDelayMs())
        repeat(5) { dog.onMainFrameError(40_000L + it) }
        assertEquals(60_000, dog.retryDelayMs())
    }

    @Test fun theCoverStaysWhileARetryLoadsAndGoesWhenItSucceeds() {
        dog.onMainFrameError(0)
        dog.onLoadStarted(10_000)
        assertTrue("no flash of WebView's error page", dog.coverShown)
        dog.onLoadFinished(12_000)
        assertFalse(dog.coverShown)
        assertEquals(XrayWatchdog.State.SHOWING, dog.state)
        // And a later failure starts the backoff from 10 s again.
        dog.onMainFrameError(100_000)
        assertEquals(10_000, dog.retryDelayMs())
    }

    @Test fun theNetworkComingBackReloadsAFailedPageAtOnce() {
        dog.onMainFrameError(0)
        assertEquals(XrayAction.RELOAD, dog.onNetworkAvailable(1_000))
        assertEquals("the retry clock restarts", XrayAction.NONE, dog.tick(10_999))
    }

    @Test fun aLoadingPageIsNotReloadedByTheNetworkCallback() {
        dog.onLoadStarted(0)
        assertEquals(XrayAction.NONE, dog.onNetworkAvailable(10))
    }
}
