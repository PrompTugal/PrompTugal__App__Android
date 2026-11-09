package ai.riskmandate.app.features.abpdemo

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.ManagedConfig
import ai.riskmandate.app.R
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.features.IdleHolds
import ai.riskmandate.app.RiskMandateTheme
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
class AbpDemoScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun show(index: Int = 0, auto: Boolean = false, fontScale: Float = 1f) =
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                RiskMandateTheme { AbpDemoScreen(initial = DemoPlayer(index, auto)) }
            }
        }

    @Test fun everyStepRendersWithItsContentAndTheBanner() {
        show()
        for (step in STEPS) {
            compose.onNodeWithTag(AbpDemoTags.BANNER).assertIsDisplayed()
            compose.onNodeWithTag(AbpDemoTags.DETAIL).assertIsDisplayed()
            compose.onNodeWithText(app.getString(R.string.abpdemo_step_of, step.index + 1, STEPS.size)).assertIsDisplayed()
            val layer = step.layer
            if (layer == null) {
                compose.onNodeWithTag(AbpDemoTags.SCENARIO).assertIsDisplayed()
                compose.onNodeWithText(app.getString(R.string.abpdemo_scenario_title)).assertIsDisplayed()
            } else {
                // The layer's name is both its diagram row and the detail heading; its strength word is shown.
                compose.onNodeWithTag(AbpDemoTags.layer(layer)).assertIsDisplayed()
                compose.onAllNodesWithText(app.getString(layerName(layer))).assertCountEquals(2)
                compose.onNodeWithText(app.getString(strengthName(step.strength!!))).assertIsDisplayed()
            }
            if (step.index < STEPS.lastIndex) compose.onNodeWithTag(AbpDemoTags.NEXT).performClick()
        }
    }

    private fun layerName(l: Layer) = when (l) {
        Layer.BROWSER -> R.string.abpdemo_layer_browser
        Layer.MODEL -> R.string.abpdemo_layer_model
        Layer.HOOK -> R.string.abpdemo_layer_hook
        Layer.PROXY -> R.string.abpdemo_layer_proxy
        Layer.HARNESS -> R.string.abpdemo_layer_harness
        Layer.DEVICE -> R.string.abpdemo_layer_device
    }

    private fun strengthName(s: Strength) = when (s) {
        Strength.GUIDANCE -> R.string.abpdemo_strength_guidance
        Strength.SOFT -> R.string.abpdemo_strength_soft
        Strength.FIRM -> R.string.abpdemo_strength_firm
        Strength.STRONG -> R.string.abpdemo_strength_strong
        Strength.FLOOR -> R.string.abpdemo_strength_floor
    }

    @Test fun nextAndBackWalkTheStepsAndStopAtTheEnds() {
        show()
        compose.onNodeWithTag(AbpDemoTags.BACK).assertIsNotEnabled()
        compose.onNodeWithText(app.getString(R.string.abpdemo_step_of, 1, 7)).assertIsDisplayed()
        repeat(6) { compose.onNodeWithTag(AbpDemoTags.NEXT).performClick() }
        compose.onNodeWithText(app.getString(R.string.abpdemo_step_of, 7, 7)).assertIsDisplayed()
        compose.onNodeWithTag(AbpDemoTags.NEXT).assertIsNotEnabled()
        compose.onNodeWithTag(AbpDemoTags.BACK).assertIsEnabled().performClick()
        compose.onNodeWithText(app.getString(R.string.abpdemo_step_of, 6, 7)).assertIsDisplayed()
    }

    @Test fun autoPlayAdvancesWithTheClockAndAHandPressStopsIt() {
        compose.mainClock.autoAdvance = false
        show(auto = true)
        compose.onNodeWithText(app.getString(R.string.abpdemo_step_of, 1, 7)).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(AUTO_PLAY_STEP_MS + 100)
        compose.onNodeWithText(app.getString(R.string.abpdemo_step_of, 2, 7)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.abpdemo_autoplay_stop)).assertIsDisplayed()
        compose.onNodeWithTag(AbpDemoTags.NEXT).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(app.getString(R.string.abpdemo_autoplay_start)).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(AUTO_PLAY_STEP_MS * 2)
        compose.onNodeWithText(app.getString(R.string.abpdemo_step_of, 3, 7)).assertIsDisplayed()
    }

    @Test fun autoPlayHoldsTheStandIdleResetAndReleasesWhenItStops() {
        val holds = IdleHolds()
        val ctx = FeatureContext(
            onBack = {}, config = ManagedConfig.DEFAULT,
            device = DeviceStatus.from(hasDeviceOwner = false, inWorkProfile = false, label = null),
            holdIdle = holds::hold,
        )
        compose.mainClock.autoAdvance = false
        compose.setContent { RiskMandateTheme { AbpDemoScreen(ctx) } }
        assertFalse(holds.held)
        compose.onNodeWithTag(AbpDemoTags.AUTOPLAY).performClick()
        compose.mainClock.advanceTimeByFrame()
        assertTrue(holds.held)
        compose.onNodeWithTag(AbpDemoTags.AUTOPLAY).performClick()
        compose.mainClock.advanceTimeByFrame()
        assertFalse(holds.held)
    }

    @Test fun hasNoTitleOrHomeOfItsOwn() {
        show()
        compose.onAllNodesWithText(app.getString(R.string.feature_abpdemo_title)).assertCountEquals(0)
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun portraitPhoneReachesEverythingByScrolling() {
        show(index = 4)
        compose.onNodeWithTag(AbpDemoTags.BANNER).assertIsDisplayed()
        compose.onNodeWithTag(AbpDemoTags.layer(Layer.DEVICE)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(AbpDemoTags.NEXT).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag(AbpDemoTags.AUTOPLAY).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun at200PercentFontEverythingIsReachable() {
        show(index = 5, fontScale = 2f)
        compose.onNodeWithTag(AbpDemoTags.BANNER).assertIsDisplayed()
        compose.onNodeWithTag(AbpDemoTags.layer(Layer.DEVICE)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(AbpDemoTags.AUTOPLAY).assertIsDisplayed()
        compose.onNodeWithTag(AbpDemoTags.NEXT).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "pt-rPT-w1280dp-h800dp-land-xhdpi")
    fun portugueseBannerIsShown() {
        show()
        compose.onNodeWithText(app.getString(R.string.abpdemo_banner)).assertIsDisplayed()
        assertTrue(app.getString(R.string.abpdemo_banner).startsWith("DEMONSTRAÇÃO"))
    }
}
