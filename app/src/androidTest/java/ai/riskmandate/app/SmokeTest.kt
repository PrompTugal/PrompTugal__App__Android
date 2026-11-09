package ai.riskmandate.app

import ai.riskmandate.app.features.FEATURES
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test on a real or emulated device: launches MainActivity and walks the home, licences and
 * diagnostics screens and every enabled feature (open, rotate, back). Run with `./gradlew connectedDebugAndroidTest`. It never taps Open, because
 * that leaves the app for a browser.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeScreenShowsOpenButtonAndBuild() {
        compose.onNodeWithTag(HomeTags.OPEN).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.DEVICE_MANAGEMENT).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.VERSION).performScrollTo().assertIsDisplayed()
    }

    @Test fun licencesScreenShowsFullApacheTextAndGoesBack() {
        compose.onNodeWithTag(HomeTags.LICENCES).performScrollTo().performClick()
        compose.onNodeWithTag(LicenceTags.TEXT).assertIsDisplayed()
        compose.onNodeWithTag(LicenceTags.APACHE).assertExists()
        systemBack()
        compose.onNodeWithTag(HomeTags.OPEN).assertIsDisplayed()
    }

    @Test fun diagnosticsScreenShowsTextAndGoesBack() {
        compose.onNodeWithTag(HomeTags.DIAGNOSTICS).performScrollTo().performClick()
        compose.onNodeWithTag(DiagnosticsTags.TEXT).assertIsDisplayed()
        compose.onNodeWithTag(DiagnosticsTags.COPY).assertIsDisplayed()
        systemBack()
        compose.onNodeWithTag(HomeTags.OPEN).assertIsDisplayed()
    }

    /** System Back: works on the old home and on the new shell (which has no Back button on these screens). */
    private fun systemBack() = back()

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** Every enabled feature opens from home and Back returns (a disabled one has no button). */
    @Test fun everyEnabledFeatureOpensAndBackReturns() {
        for (f in FEATURES.filter { it.enabled }) {
            compose.onNodeWithTag(HomeTags.feature(f.id)).performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
            compose.onNodeWithTag(HomeTags.OPEN).assertDoesNotExist()
            back()
            compose.onNodeWithTag(HomeTags.OPEN).assertIsDisplayed()
        }
    }

    /** Recreating the activity (what a rotation does) keeps the user on the feature screen. */
    @Test fun rotationKeepsEveryEnabledFeatureScreen() {
        for (f in FEATURES.filter { it.enabled }) {
            compose.onNodeWithTag(HomeTags.feature(f.id)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithTag(HomeTags.OPEN).assertDoesNotExist()
            back()
            compose.onNodeWithTag(HomeTags.OPEN).assertIsDisplayed()
        }
    }

    @Test fun disabledFeaturesHaveNoButton() {
        for (f in FEATURES.filter { !it.enabled }) compose.onNodeWithTag(HomeTags.feature(f.id)).assertDoesNotExist()
    }
}
