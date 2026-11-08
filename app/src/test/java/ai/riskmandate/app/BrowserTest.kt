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
}
