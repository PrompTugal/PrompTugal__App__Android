package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmRunner
import ai.riskmandate.app.llm.RunRecord
import ai.riskmandate.app.llm.RunSettings
import ai.riskmandate.app.ui.FeatureContent
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ExperimentsTabTest {
    @get:Rule val compose = createComposeRule()
    private val base = RunSettings(userPrompt = "Say hi")

    private fun show(fake: FakeLlmService, base: RunSettings? = this.base, cleared: () -> Unit = {}, logged: MutableList<String> = mutableListOf()) =
        compose.setContent {
            RiskMandateTheme {
                FeatureContent(scrollable = false) {
                    var log by remember { mutableStateOf(listOf<LogEntry>()) }
                    ExperimentsTab(
                        runner = LlmRunner(fake), base = base, probe = { null }, guard = ai.riskmandate.app.experiments.NoGuard, log = log,
                        onRecord = { l, r: RunRecord -> logged += l; log = listOf(LogEntry.of(l, r)) + log },
                        onClearLog = { log = emptyList(); cleared() },
                    )
                }
            }
        }

    private fun waitFor(tag: String, text: String? = null) {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            val nodes = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes()
            if (nodes.isNotEmpty() && (text == null || nodes.any { n -> n.config.toString().contains(text) })) return
            Thread.sleep(20)
        }
        fail("no node tagged $tag" + (text?.let { " with $it" } ?: ""))
    }

    @Test fun withoutValidRunSettingsItSaysSo() {
        show(FakeLlmService(), base = null)
        compose.onNodeWithTag(ExperimentTags.NO_BASE).assertTextContains("Run tab", substring = true)
        compose.onNodeWithTag(ExperimentTags.START).performScrollTo().assertIsNotEnabled()
    }

    @Test fun aSweepRunsTheGridShowsTheTableAndLogsEveryRun() {
        val logged = mutableListOf<String>()
        val fake = FakeLlmService { FakeReply("a b c") }
        show(fake, logged = logged)
        compose.onNodeWithTag(ExperimentTags.VALUES).performTextClearance()
        compose.onNodeWithTag(ExperimentTags.VALUES).performTextInput("0.1 0.9")
        compose.onNodeWithTag(ExperimentTags.REPEATS).performScrollTo().performTextClearance()
        compose.onNodeWithTag(ExperimentTags.REPEATS).performTextInput("2")
        compose.onNodeWithTag(ExperimentTags.START).performScrollTo().assertTextContains("4 runs", substring = true).performClick()
        waitFor(ExperimentTags.COPY)
        assertEquals(listOf("0.1", "0.9", "0.1", "0.9"), logged)
        compose.onNode(hasContentDescription("Temperature: 0.9, Runs: 2,", substring = true)).assertExists()
        compose.onNodeWithTag(ExperimentTags.VIEW).performScrollTo()
        compose.onNodeWithText("Run log (4)").performScrollTo().performClick()
        compose.onAllNodes(hasContentDescription("Run and settings: 0.9 | fake", substring = true)).assertCountEquals(2)
    }

    @Test fun anABRunsBothValuesAlternately() {
        val fake = FakeLlmService { FakeReply("x") }
        show(fake)
        compose.onNodeWithTag(ExperimentTags.MODE_AB).performClick()
        compose.onNodeWithTag(ExperimentTags.START).performScrollTo().assertTextContains("6 runs", substring = true).performClick()
        waitFor(ExperimentTags.COPY)
        assertEquals(listOf(0.2, 1.0, 0.2, 1.0, 0.2, 1.0), fake.requests.map { it.settings.temperature })
    }

    @Test fun aBadValueIsExplainedAndBlocksTheStart() {
        show(FakeLlmService())
        compose.onNodeWithTag(ExperimentTags.VALUES).performTextClearance()
        compose.onNodeWithTag(ExperimentTags.VALUES).performTextInput("0.5 9")
        compose.onNodeWithTag(ExperimentTags.PLAN_ERROR).performScrollTo().assertTextContains("not a valid value", substring = true)
        compose.onNodeWithTag(ExperimentTags.START).performScrollTo().assertIsNotEnabled()
    }

    @Test fun theResultsTableSurvivesARotationAsCountersOnly() {
        val restorer = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        val fake = FakeLlmService { FakeReply("a b") }
        restorer.setContent {
            RiskMandateTheme {
                FeatureContent(scrollable = false) {
                    ExperimentsTab(runner = LlmRunner(fake), base = base, probe = { null }, guard = ai.riskmandate.app.experiments.NoGuard, log = emptyList(), onRecord = { _, _ -> }, onClearLog = {})
                }
            }
        }
        compose.onNodeWithTag(ExperimentTags.REPEATS).performScrollTo().performTextClearance()
        compose.onNodeWithTag(ExperimentTags.REPEATS).performTextInput("1")
        compose.onNodeWithTag(ExperimentTags.START).performScrollTo().performClick()
        waitFor(ExperimentTags.COPY)
        restorer.emulateSavedInstanceStateRestore()
        compose.onNode(hasContentDescription("Temperature: 0.7, Runs: 1,", substring = true)).assertExists()
    }

    @Test fun aCapStopShowsTheBanner() {
        var n = 0
        compose.setContent {
            RiskMandateTheme {
                FeatureContent(scrollable = false) {
                    ExperimentsTab(
                        runner = LlmRunner(FakeLlmService { FakeReply("x") }), base = base, log = emptyList(), onRecord = { _, _ -> }, onClearLog = {},
                        probe = { null },
                        guard = object : ai.riskmandate.app.experiments.DeviceGuard {
                            override fun batteryPct() = if (++n > 3) 10 else 80
                            override fun charging() = false
                            override fun hot() = false
                        },
                    )
                }
            }
        }
        compose.onNodeWithTag(ExperimentTags.START).performScrollTo().performClick()
        waitFor(ExperimentTags.CAP_BANNER)
        compose.onNodeWithTag(ExperimentTags.CAP_BANNER).assertTextContains("battery", substring = true)
    }

    @Test fun theSummaryCodecRefusesAnythingOutOfRange() {
        val plan = ExperimentPlan(SweepVariable.TEMPERATURE, listOf("0.2"), 2)
        val rows = listOf(ValueSummary("0.2", 2, 0, Spread(2, 100.0, 90.0, 110.0, 14.1), null, Spread(2, 5.0, 4.0, 6.0, 1.4), null))
        val text = SummaryCodec.encode(plan, rows, ExperimentStop.HOT)
        assertEquals(Triple(plan, rows, ExperimentStop.HOT), SummaryCodec.decode(text))
        listOf("", "x", text.replace("s1", "s2"), text.replace("|2|", "|500|"), text.replace("0.2,2,0", "0.2,2,3"), text.replace("100.0", "NaN"), text.replace("HOT", "LAVA"))
            .forEach { assertEquals(it, null, SummaryCodec.decode(it)) }
    }

    @Test fun theLogCanBeClearedAfterAConfirmation() {
        var cleared = 0
        show(FakeLlmService { FakeReply("x") }, cleared = { cleared++ })
        compose.onNodeWithTag(ExperimentTags.REPEATS).performScrollTo().performTextClearance()
        compose.onNodeWithTag(ExperimentTags.REPEATS).performTextInput("1")
        compose.onNodeWithTag(ExperimentTags.START).performScrollTo().assertIsEnabled().performClick()
        waitFor(ExperimentTags.COPY)
        compose.onNodeWithText("Run log", substring = true).performScrollTo().performClick()
        waitFor(ExperimentTags.CLEAR)
        compose.onNodeWithTag(ExperimentTags.CLEAR).performScrollTo().performClick()
        compose.onNodeWithTag(ExperimentTags.CONFIRM_CLEAR).performClick()
        compose.waitForIdle()
        assertEquals(1, cleared)
        assertTrue(compose.onAllNodes(hasTestTag(ExperimentTags.LOG)).fetchSemanticsNodes().isEmpty())
    }
}
