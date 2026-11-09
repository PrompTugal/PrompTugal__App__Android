package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.ManagedConfig
import ai.riskmandate.app.Management
import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The tablet layout (design: three panes at 1280 x 800 dp): settings, answer and stats all on screen at once. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp-land")
class WorkbenchWideTest {
    @get:Rule val compose = createComposeRule()

    private fun waitFor(tag: String) {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(20)
        }
        fail("no node tagged $tag")
    }

    @Test fun threePanesShowSettingsAnswerAndStatsTogether() {
        compose.setContent {
            RiskMandateTheme {
                WorkbenchContent(
                    FeatureContext(onBack = {}, config = ManagedConfig.DEFAULT, device = DeviceStatus(Management.NOT_DETECTED, null), llm = FakeLlmService { FakeReply("Hello from the tablet.") }),
                    io = Dispatchers.Unconfined, probe = { null },
                )
            }
        }
        compose.onNodeWithTag(RunTags.PROMPT).performTextInput("Say hello")
        compose.onNodeWithTag(RunTags.RUN).performClick()
        waitFor(RunTags.ANSWER)
        // Typing scrolled the settings pane to the prompt; it stays on screen beside the answer and the stats.
        compose.onNodeWithTag(RunTags.PROMPT).assertIsDisplayed()
        compose.onNodeWithTag(RunTags.ANSWER).assertIsDisplayed()
        compose.onNodeWithTag(RunTags.STATS).assertIsDisplayed()
        // Experiments: builder and results side by side.
        compose.onNodeWithText("Experiments").performClick()
        compose.onNodeWithTag(ExperimentTags.START).assertIsDisplayed()
        compose.onNodeWithTag(ExperimentTags.VIEW).assertIsDisplayed()
        // Tool tests: dev.lab's tab, hosted as the third tab (its catalogue loads from the bundled assets).
        compose.onNodeWithText("Tool tests").performClick()
        waitFor(ai.riskmandate.app.features.workbench.tooltests.ToolTestTags.RUN)
    }
}
