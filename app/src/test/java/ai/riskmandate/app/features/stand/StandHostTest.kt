package ai.riskmandate.app.features.stand

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.After
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
    override fun isLocked() = locked
    override fun lock(): Boolean {
        lockCalls++
        if (throwing) return false
        if (!refuse) locked = true
        return true
    }
    override fun unlock() { unlockCalls++; locked = false }
}

@RunWith(AndroidJUnit4::class)
class StandHostTest {
    @get:Rule val compose = createComposeRule()

    @After fun release() { held = false }

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var settings: StandSettings
    private var now = 0L
    private var homeCalls = 0
    private lateinit var lifecycle: LifecycleRegistry
    private lateinit var hostView: android.view.View
    private val awake get() = hostView.keepScreenOn

    @Before fun setUp() {
        settings = StandSettings.of(app)
        settings.enabled = false
        settings.idleMinutes = 3
        val owner = object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = this@StandHostTest.lifecycle
        }
        lifecycle = LifecycleRegistry.createUnsafe(owner)
        lifecycle.currentState = Lifecycle.State.RESUMED
    }

    private val enabledCalls = mutableListOf<Boolean>()
    private var held by androidx.compose.runtime.mutableStateOf(false)

    private fun show(lock: ScreenLock, atHome: Boolean = true) {
        compose.setContent {
            StandHostContent(settings, lock, lifecycle, { now }, atHome, { homeCalls++ }, held, { enabledCalls.add(it) }) {
                val v = androidx.compose.ui.platform.LocalView.current
                hostView = v
                Text("app")
            }
        }
        startLoops()
    }

    /**
     * Moves Compose's virtual clock on in one-second steps. The idle loop runs in a LaunchedEffect, so this
     * clock drives it: no dependence on real time or on the main looper.
     */
    private fun tick(seconds: Int) = repeat(seconds) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    /** Lets everything pending settle, then moves the fake clock: no effect can run "late" across the jump. */
    private fun advanceClock(ms: Long) {
        compose.waitForIdle()
        now += ms
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
        assertFalse(awake)
    }

    @Test fun aPlainTapOnTheCornerDoesNotOpenStaffControls() {
        show(FakeLock())
        compose.onNodeWithTag(StandTags.CORNER).performClick()
        compose.onNodeWithTag(StandTags.SWITCH).assertDoesNotExist()
    }

    private fun startLoops() {
        compose.waitForIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        compose.waitForIdle()
    }

    private fun showWithButtonInCorner(atHome: Boolean, onTap: () -> Unit) = compose.setContent {
        StandHostContent(settings, FakeLock(), lifecycle, { now }, atHome, { homeCalls++ }, false, {}) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomEnd)
                        .size(48.dp).testTag("under").clickable { onTap() },
                )
            }
        }
    }

    @Test fun aControlUnderTheCornerStillGetsItsTapOnTheHomeScreen() {
        var taps = 0
        showWithButtonInCorner(atHome = true) { taps++ }
        compose.onNodeWithTag("under").performTouchInput { click(center) }
        assertEquals(1, taps)
        compose.onNodeWithTag(StandTags.SWITCH).assertDoesNotExist()
    }

    @Test fun aControlUnderTheCornerStillGetsItsTapOnOtherScreens() {
        var taps = 0
        showWithButtonInCorner(atHome = false) { taps++ }
        compose.onNodeWithTag("under").performTouchInput { click(center) }
        assertEquals(1, taps)
    }

    @Test fun longPressOutsideHomeOpensNothing() {
        show(FakeLock(), atHome = false)
        compose.onNodeWithText("app").performTouchInput { longClick(bottomRight) }
        compose.onNodeWithTag(StandTags.SWITCH).assertDoesNotExist()
    }

    @Test fun longPressElsewhereOnHomeOpensNothing() {
        show(FakeLock())
        compose.onNodeWithText("app").performTouchInput { longClick(topLeft) }
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
        assertTrue(awake)
        compose.onNodeWithTag(StandTags.SWITCH).performClick()
        compose.waitForIdle()
        assertFalse(settings.enabled)
        assertEquals(1, lock.unlockCalls)
        assertFalse(awake)
    }

    @Test fun comesBackPinnedAfterProcessDeath() {
        settings.enabled = true
        val lock = FakeLock()
        show(lock)
        compose.waitForIdle()
        assertEquals(1, lock.lockCalls)
        assertTrue(awake)
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
        assertTrue(awake)
        openSettings()
        tick(2)
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
        advanceClock(3 * 60_000)
        tick(2)
        assertEquals(1, homeCalls)
        tick(5)
        assertEquals(1, homeCalls)
        compose.onNodeWithText("app").performTouchInput { click() }
        advanceClock(3 * 60_000)
        tick(2)
        assertEquals(2, homeCalls)
    }

    @Test fun touchKeepsTheAppFromResetting() {
        settings.enabled = true
        show(FakeLock(), atHome = false)
        compose.waitForIdle()
        advanceClock(2 * 60_000)
        compose.onNodeWithText("app").performTouchInput { click() }
        advanceClock(2 * 60_000)
        tick(2)
        assertEquals(0, homeCalls)
    }

    @Test fun noResetWhenAlreadyHomeOrWhenOff() {
        settings.enabled = true
        show(FakeLock(), atHome = true)
        compose.waitForIdle()
        advanceClock(10 * 60_000)
        tick(2)
        assertEquals(0, homeCalls)
    }

    @Test fun heldLongerThanTheTimeoutNeverResets_releasedThenTimeoutResetsOnce() {
        settings.enabled = true
        held = true
        show(FakeLock(), atHome = false)
        compose.waitForIdle()
        advanceClock(10 * 60_000)
        tick(2)
        assertEquals(0, homeCalls)
        held = false
        compose.waitForIdle()
        tick(2)
        assertEquals("released: countdown restarts, no instant reset", 0, homeCalls)
        advanceClock(3 * 60_000 + 1_000)
        tick(2)
        assertEquals(1, homeCalls)
    }

    @Test fun onEnabledChangeGetsTheStartValueThenEachChange() {
        show(FakeLock())
        compose.waitForIdle()
        assertEquals(listOf(false), enabledCalls)
        openSettings()
        compose.onNodeWithTag(StandTags.SWITCH).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(StandTags.SWITCH).performClick()
        compose.waitForIdle()
        assertEquals(listOf(false, true, false), enabledCalls)
    }

    @Test fun onEnabledChangeStartsWithTheSavedValue() {
        settings.enabled = true
        show(FakeLock())
        compose.waitForIdle()
        assertEquals(listOf(true), enabledCalls)
    }

    @Test fun noResetWhileStandModeIsOff() {
        show(FakeLock(), atHome = false)
        compose.waitForIdle()
        advanceClock(10 * 60_000)
        tick(2)
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

    @Test @Config(qualifiers = "pt-rPT") fun staffControlsAreInPortuguese() {
        show(FakeLock())
        openSettings()
        compose.onNodeWithText("Modo de bancada").assertIsDisplayed()
    }
}

