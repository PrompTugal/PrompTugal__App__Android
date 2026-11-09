package ai.riskmandate.app.features.site

import ai.riskmandate.app.Browser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSwitchTest {
    @Test fun startsFromTheConnectivityHint() {
        assertTrue(OfflineSwitch(Browser.offline(hasActiveNetwork = false, hasInternet = false)).offline)
        assertTrue(OfflineSwitch(Browser.offline(hasActiveNetwork = true, hasInternet = false)).offline)
        assertFalse(OfflineSwitch(Browser.offline(hasActiveNetwork = true, hasInternet = true)).offline)
    }

    @Test fun aFailedMainPageSwitchesToTheCopyOnce() {
        val s = OfflineSwitch(false)
        assertTrue(s.onMainFrameError())
        assertTrue(s.offline)
        assertFalse("already offline: no change, so no reload loop", s.onMainFrameError())
    }

    @Test fun tryOnlineNeedsAConnection() {
        val s = OfflineSwitch(true)
        assertFalse(s.tryOnline(systemOffline = true))
        assertTrue(s.offline)
        assertTrue(s.tryOnline(systemOffline = false))
        assertFalse(s.offline)
        assertFalse("already online: no change", s.tryOnline(systemOffline = false))
        assertEquals(false, s.offline)
    }
}
