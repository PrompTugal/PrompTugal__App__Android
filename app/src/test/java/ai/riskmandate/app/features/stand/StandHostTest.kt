package ai.riskmandate.app.features.stand

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

private class FakeLock(var refuse: Boolean = false, var throwing: Boolean = false) : ScreenLock {
    var locked = false
    var lockCalls = 0
    var unlockCalls = 0
    var awake = false
    override fun isLocked() = locked
    override fun lock(): Boolean {
        lockCalls++
        if (throwing) return false
        if (!refuse) locked = true
        return true
    }
    override fun unlock() { unlockCalls++; locked = false }
    override fun keepScreenOn(on: Boolean) { awake = on }
}

@RunWith(AndroidJUnit4::class)
class StandHostTest {
    @get:Rule val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var settings: StandSettings
    private var now = 0L
    private var homeCalls = 0
    private lateinit var lifecycle: LifecycleRegistry

    @Before fun setUp() {
        settings = StandSettings.of(app)
        settings.enabled = false
        settings.clearPin()
        settings.idleMinutes = 3
        val owner = object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = this@StandHostTest.lifecycle
        }
        lifecycle = LifecycleRegistry.createUnsafe(owner)
        lifecycle.currentState = Lifecycle.State.RESUMED
    }

    private fun show(lock: ScreenLock, atHome: Boolean = false) = compose.setContent {
        StandHostContent(settings, lock, lifecycle, { now }, atHome, { homeCalls++ }) { Text("app") }
    }

    private fun openSettings() {
        compose.onNodeWithTag(StandTags.CORNER).performTouchInput { longClick() }
    }

    @Test fun appShowsAndNothingIsPinnedWhileOff() {
        val lock = FakeLock()
        show(lock)
        compose.onNodeWithText("app").assertIsDisplayed()
        compose.waitForIdle()
        assertEquals(0, lock.lockCalls)
        assertEquals(0, lock.unlockCalls)
        assertFalse(lock.awake)
    }

    @Test fun aPlainTapOnTheCornerDoesNotOpenStaffControls() {
        show(FakeLock())
        compose.onNodeWithTag(StandTags.CORNER).performClick()
        compose.onNodeWithTag(StandTags.SWITCH).assertDoesNotExist()
    }

    @Test fun switchingOnPinsAndKeepsAwake_offUnpinsAndClears() {
        val lock = FakeLock()
        show(lock)
        openSettings()
        compose.onNodeWithTag(StandTags.SWITCH).performClick()
        compose.waitForIdle()
        assertTrue(settings.enabled)
        assertEquals(1, lock.lockCalls)
        assertTrue(lock.awake)
        compose.onNodeWithTag(StandTags.SWITCH).performClick()
        compose.waitForIdle()
        assertFalse(settings.enabled)
        assertEquals(1, lock.unlockCalls)
        assertFalse(lock.awake)
    }

    @Test fun comesBackPinnedAfterProcessDeath() {
        settings.enabled = true
        val lock = FakeLock()
        show(lock)
        compose.waitForIdle()
        assertEquals(1, lock.lockCalls)
        assertTrue(lock.awake)
    }

    @Test fun anAlreadyPinnedTaskIsNotAskedAgain() {
        settings.enabled = true
        val lock = FakeLock().apply { locked = true }
        show(lock)
        compose.waitForIdle()
        assertEquals(0, lock.lockCalls)
    }

    @Test fun refusedOrUnavailablePinningNeverCrashesAndKeepsTheRest() {
        settings.enabled = true
        val lock = FakeLock(refuse = true)
        show(lock)
        compose.waitForIdle()
        assertTrue(lock.awake)
        openSettings()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag(StandTags.STATUS).assertIsDisplayed()
        compose.onNodeWithText(app.getString(ai.riskmandate.app.R.string.stand_status_not_pinned)).assertIsDisplayed()
    }

    @Test fun lockThatThrowsIsSurvived() {
        settings.enabled = true
        show(FakeLock(throwing = true))
        compose.waitForIdle()
        compose.onNodeWithText("app").assertIsDisplayed()
    }

    @Test fun idleSendsTheAppHomeOnceThenTouchRearms() {
        settings.enabled = true
        show(FakeLock(), atHome = false)
        compose.waitForIdle()
        now += 3 * 60_000
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(1, homeCalls)
        compose.mainClock.advanceTimeBy(5_000)
        assertEquals(1, homeCalls)
        compose.onNodeWithText("app").performTouchInput { click() }
        now += 3 * 60_000
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(2, homeCalls)
    }

    @Test fun touchKeepsTheAppFromResetting() {
        settings.enabled = true
        show(FakeLock())
        compose.waitForIdle()
        now += 2 * 60_000
        compose.onNodeWithText("app").performTouchInput { click() }
        now += 2 * 60_000
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(0, homeCalls)
    }

    @Test fun noResetWhenAlreadyHomeOrWhenOff() {
        settings.enabled = true
        show(FakeLock(), atHome = true)
        compose.waitForIdle()
        now += 10 * 60_000
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(0, homeCalls)
    }

    @Test fun noResetWhileStandModeIsOff() {
        show(FakeLock(), atHome = false)
        compose.waitForIdle()
        now += 10 * 60_000
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(0, homeCalls)
    }

    @Test fun idleMinutesCanBeChangedAndAreStored() {
        show(FakeLock())
        openSettings()
        compose.onNodeWithTag(StandTags.MORE).performClick()
        compose.onNodeWithTag(StandTags.MORE).performClick()
        assertEquals(5, settings.idleMinutes)
        compose.onNodeWithTag(StandTags.LESS).performClick()
        assertEquals(4, settings.idleMinutes)
    }

    @Test fun withAPinTheControlsStayBehindIt() {
        settings.setPin("2468")
        show(FakeLock())
        openSettings()
        compose.onNodeWithTag(StandTags.SWITCH).assertDoesNotExist()
        compose.onNodeWithTag(StandTags.PIN_FIELD).performTextInput("1111")
        compose.onNodeWithTag(StandTags.PIN_OK).performClick()
        compose.onNodeWithTag(StandTags.SWITCH).assertDoesNotExist()
        compose.onNodeWithText(app.getString(ai.riskmandate.app.R.string.stand_pin_wrong)).assertIsDisplayed()
        compose.onNodeWithTag(StandTags.PIN_FIELD).performTextInput("2468")
        compose.onNodeWithTag(StandTags.PIN_OK).performClick()
        compose.onNodeWithTag(StandTags.SWITCH).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "pt-rPT") fun staffControlsAreInPortuguese() {
        show(FakeLock())
        openSettings()
        compose.onNodeWithText("Modo de bancada").assertIsDisplayed()
    }
}

