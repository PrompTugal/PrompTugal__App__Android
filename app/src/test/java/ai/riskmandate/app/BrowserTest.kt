package ai.riskmandate.app

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class BrowserTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun refusesOtherHosts() =
        assertEquals(OpenResult.REFUSED, Browser.open(app, "https://evil.example/"))

    @Test fun noBrowserDoesNotCrash() {
        shadowOf(app).checkActivities(true)
        val result = Browser.open(app, Links.HOME)
        assertEquals(OpenResult.NO_BROWSER, result)
        assertNotNull(Browser.message(result))
    }

    @Test fun fallsBackToViewIntentWithoutCustomTabs() {
        val result = Browser.open(app, Links.HOME)
        assertEquals(OpenResult.BROWSER, result)
        assertNull(Browser.message(result))
        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(Links.HOME, started.dataString)
    }

    @Test fun everyResultHasTheRightMessage() {
        assertNull(Browser.message(OpenResult.CUSTOM_TAB))
        assertNotNull(Browser.message(OpenResult.FAILED))
        assertNotNull(Browser.message(OpenResult.REFUSED))
    }

    @Test fun offlineDecision() {
        org.junit.Assert.assertTrue(Browser.offline(hasActiveNetwork = false, hasInternet = false))
        org.junit.Assert.assertTrue(Browser.offline(hasActiveNetwork = true, hasInternet = false))
        org.junit.Assert.assertFalse(Browser.offline(hasActiveNetwork = true, hasInternet = true))
    }

    @Test fun askingTheSystemNeverThrows() {
        Browser.isOffline(app) // either answer is fine; it must not crash
    }

    @Test fun externalOpensHttpsInTheSystemBrowserWithViewIntent() {
        val result = Browser.openExternal(app, "https://example.org/page?x=1")
        assertEquals(OpenResult.BROWSER, result)
        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://example.org/page?x=1", started.dataString)
        assertNull("no explicit component: the system picks the browser, never this app", started.component)
        assertEquals(setOf(Intent.CATEGORY_BROWSABLE), started.categories)
    }

    @Test fun externalRefusesOtherSchemesWithoutStartingAnything() {
        for (u in listOf("http://example.org/", "javascript:alert(1)", "intent://x#Intent;end", "file:///x", "https://a@b.example/")) {
            assertEquals(u, OpenResult.REFUSED, Browser.openExternal(app, u))
        }
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun externalNeverOpensWhilePinned() {
        val am = app.getSystemService(android.app.ActivityManager::class.java)
        shadowOf(am).setLockTaskModeState(android.app.ActivityManager.LOCK_TASK_MODE_PINNED)
        assertEquals(OpenResult.PINNED, Browser.openExternal(app, "https://example.org/"))
        assertNull(shadowOf(app).nextStartedActivity)
        assertNotNull(Browser.message(OpenResult.PINNED))
        shadowOf(am).setLockTaskModeState(android.app.ActivityManager.LOCK_TASK_MODE_NONE)
        assertEquals(OpenResult.BROWSER, Browser.openExternal(app, "https://example.org/"))
    }

    @Test fun externalWithNoBrowserDoesNotCrash() {
        shadowOf(app).checkActivities(true)
        assertEquals(OpenResult.NO_BROWSER, Browser.openExternal(app, "https://example.org/"))
    }

    @Test fun riskmandateOpenKeepsItsAllowList() {
        assertEquals(OpenResult.REFUSED, Browser.open(app, "https://example.org/"))
    }
}
