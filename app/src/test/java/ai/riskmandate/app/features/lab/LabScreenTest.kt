package ai.riskmandate.app.features.lab

import ai.riskmandate.app.RiskMandateTheme
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class LabScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val data = LabTestData.load()

    private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    private fun show(scenario: String) =
        compose.setContent { RiskMandateTheme { LabScreen(data = data, initialScenario = scenario, network = NetworkState(true, Transport.WIFI)) } }

    private fun replays(id: String) {
        show(id)
        compose.onNodeWithTag(LabTags.BANNER).assertIsDisplayed()
        assertEquals(0, count(LabTags.RAW))
        compose.onNodeWithTag(LabTags.RUN).performClick()
        compose.waitUntil(15_000) { count(LabTags.SUMMARY_ABP) > 0 }
        compose.onNodeWithTag(LabTags.RAW).assertExists()
        compose.onNodeWithTag(LabTags.ABP).assertExists()
        assertTrue(count(LabTags.REPLAY_STRIP) == 2)
        compose.onNodeWithTag(LabTags.SUMMARY_RAW).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(LabTags.SUMMARY_ABP).performScrollTo().assertIsDisplayed()
    }

    @Test fun plantedReplays() = replays("sample-planted")
    @Test fun inventedReplays() = replays("sample-invented")
    @Test fun overreachReplays() = replays("sample-overreach")
    @Test fun leakageReplays() = replays("sample-leakage")
    @Test fun selfKnowledgeReplaysAndShowsTheRealNetworkState() {
        replays("sample-selfknowledge")
        compose.onNodeWithTag(LabTags.NETWORK).assertExists()
    }

    @Test fun otherScenariosDoNotShowTheNetworkCard() {
        show("sample-planted")
        assertFalse(count(LabTags.NETWORK) > 0)
    }

    @Test fun theBundledAssetsLoadWithoutProblems() {
        val d = LabAssets.load(app.assets)
        assertEquals(emptyList<String>(), d.problems)
        assertEquals(data.scenarios.map { it.id }, d.scenarios.map { it.id })
        assertEquals(data.recordings.map { it.id }, d.recordings.map { it.id })
        assertTrue(d.scenario("sample-invented")!!.graph!!.triplets.isNotEmpty())
    }

    @Test fun theFeatureIsStillOffUntilItWorksOnTheTablet() {
        assertFalse(LabFeature.enabled)
    }
}
