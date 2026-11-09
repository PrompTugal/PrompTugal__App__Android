package ai.riskmandate.app.qa

import ai.riskmandate.app.features.stand.ScreenLock
import ai.riskmandate.app.features.stand.StandHostContent
import ai.riskmandate.app.features.stand.StandSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plan X-11 (QA owns this file): stand mode's staff corner (bottom-end) must never take a tap from the control
 * under it, with stand mode off or on. Found on work/dev.stand 94da82a, fixed in 9f207e8 (a plain tap passes through).
 */
@RunWith(AndroidJUnit4::class)
class StandCornerTapsTest {
    @get:Rule val compose = createComposeRule()

    private class Lock : ScreenLock {
        override fun isLocked() = false
        override fun lock() = true
        override fun unlock() {}
    }

    private fun tapUnderTheCorner(standOn: Boolean): Int {
        var taps = 0
        val settings = StandSettings.of(ApplicationProvider.getApplicationContext()).also { it.enabled = standOn }
        lateinit var lc: LifecycleRegistry
        val owner = object : LifecycleOwner { override val lifecycle: Lifecycle get() = lc }
        lc = LifecycleRegistry.createUnsafe(owner).also { it.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            StandHostContent(settings, Lock(), lc, { 0L }, atHome = false, goHome = {}, idleHeld = false) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.BottomEnd).size(48.dp).testTag("under").clickable { taps++ })
                }
            }
        }
        compose.onNodeWithTag("under").performTouchInput { click(center) }
        settings.enabled = false
        return taps
    }

    @Test fun aControlInTheBottomEndCornerGetsItsTapWithStandModeOff() = assertEquals(1, tapUnderTheCorner(false))
    @Test fun aControlInTheBottomEndCornerGetsItsTapWithStandModeOn() = assertEquals(1, tapUnderTheCorner(true))
}
