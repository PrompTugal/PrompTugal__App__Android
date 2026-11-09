package ai.riskmandate.app.features.stand

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun defaultsAreOffAndThreeMinutes() {
        val s = StandSettings.of(context)
        assertFalse(s.enabled)
        assertEquals(3, s.idleMinutes)
        assertFalse(s.hasPin)
    }

    @Test fun settingsSurviveANewInstance() {
        StandSettings.of(context).apply { enabled = true; idleMinutes = 7 }
        val again = StandSettings.of(context)
        assertTrue(again.enabled)
        assertEquals(7, again.idleMinutes)
    }

    @Test fun minutesAreClamped() {
        val s = StandSettings.of(context)
        s.idleMinutes = 0
        assertEquals(1, s.idleMinutes)
        s.idleMinutes = 999
        assertEquals(30, s.idleMinutes)
    }

    @Test fun pinIsCheckedAndNeverStoredInTheClear() {
        val s = StandSettings.of(context)
        assertTrue(s.setPin("4821"))
        assertTrue(s.hasPin)
        assertTrue(s.checkPin("4821"))
        assertFalse(s.checkPin("4822"))
        val raw = context.getSharedPreferences("stand_mode", Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(raw.contains("4821"))
        s.clearPin()
        assertFalse(s.hasPin)
        assertTrue(s.checkPin("anything"))
    }

    @Test fun invalidPinsAreRefusedAndChangeNothing() {
        val s = StandSettings.of(context)
        for (bad in listOf("", "123", "123456789", "12a4", "12 4")) assertFalse(bad, s.setPin(bad))
        assertFalse(s.hasPin)
    }
}
