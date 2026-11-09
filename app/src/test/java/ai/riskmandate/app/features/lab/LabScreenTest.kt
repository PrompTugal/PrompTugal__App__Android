package ai.riskmandate.app.features.lab

import ai.riskmandate.app.R
import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.features.lab.LabFeature
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
class LabScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun show(scenario: String = "planted") =
        compose.setContent { RiskMandateTheme { LabScreen(initialScenario = scenario, network = NetworkState(true, Transport.WIFI)) } }

    @Test fun theSampleBannerIsAlwaysShownAndNothingRunsBeforeTheButtonIsPressed() {
        show()
        compose.onNodeWithTag(LabTags.BANNER).assertIsDisplayed()
        compose.onNodeWithTag(LabTags.RUN).assertIsDisplayed()
        compose.onAllNodesWithTagCount(LabTags.RAW).let { assertFalse(it > 0) }
    }

    @Test fun runningShowsBothPathsAndTheirVerdicts() {
        show()
        compose.onNodeWithTag(LabTags.RUN).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagCount(LabTags.RESULT_ABP) > 0 }
        compose.onNodeWithTag(LabTags.RAW).assertIsDisplayed()
        compose.onNodeWithTag(LabTags.ABP).assertIsDisplayed()
        compose.onNodeWithTag(LabTags.RESULT_RAW).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(LabTags.RESULT_ABP).performScrollTo().assertIsDisplayed()
    }

    @Test fun scenarioFiveShowsTheRealNetworkState() {
        show("selfknowledge")
        compose.onNodeWithTag(LabTags.NETWORK).assertIsDisplayed()
    }

    private fun runsToVerdicts(id: String) {
        show(id)
        compose.onNodeWithTag(LabTags.RUN).performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithTagCount(LabTags.RESULT_ABP) > 0 }
        compose.onNodeWithTag(LabTags.RESULT_RAW).assertExists()
    }

    @Test fun plantedRunsToItsVerdicts() = runsToVerdicts("planted")
    @Test fun inventedRunsToItsVerdicts() = runsToVerdicts("invented")
    @Test fun overreachRunsToItsVerdicts() = runsToVerdicts("overreach")
    @Test fun leakageRunsToItsVerdicts() = runsToVerdicts("leakage")
    @Test fun selfKnowledgeRunsToItsVerdicts() = runsToVerdicts("selfknowledge")

    @Test fun theFeatureIsStillOffUntilItWorksOnTheTablet() {
        assertFalse(LabFeature.enabled)
        app.getString(R.string.lab_run)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
