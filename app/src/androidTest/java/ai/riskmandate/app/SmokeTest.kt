package ai.riskmandate.app

import androidx.compose.ui.test.assertIsDisplayed
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
 * diagnostics screens. Run with `./gradlew connectedDebugAndroidTest`. It never taps Open, because
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
        compose.onNodeWithTag(LicenceTags.BACK).performClick()
        compose.onNodeWithTag(HomeTags.OPEN).assertIsDisplayed()
    }

    @Test fun diagnosticsScreenShowsTextAndGoesBack() {
        compose.onNodeWithTag(HomeTags.DIAGNOSTICS).performScrollTo().performClick()
        compose.onNodeWithTag(DiagnosticsTags.TEXT).assertIsDisplayed()
        compose.onNodeWithTag(DiagnosticsTags.COPY).assertIsDisplayed()
        compose.onNodeWithTag(DiagnosticsTags.BACK).performClick()
        compose.onNodeWithTag(HomeTags.OPEN).assertIsDisplayed()
    }
}
