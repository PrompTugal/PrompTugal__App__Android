package ai.riskmandate.app.features.workbench.tooltests

import ai.riskmandate.app.R
import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.experiments.CaseRun
import ai.riskmandate.app.experiments.CheckResult
import ai.riskmandate.app.experiments.ExperimentsTestData
import ai.riskmandate.app.experiments.RefStep
import ai.riskmandate.app.experiments.PromptSize
import ai.riskmandate.app.experiments.StepRecord
import ai.riskmandate.app.experiments.StopReason
import ai.riskmandate.app.experiments.ToolChecks
import ai.riskmandate.app.experiments.ToolMode
import ai.riskmandate.app.experiments.ToolTestFormats
import ai.riskmandate.app.experiments.ToolTestReport
import ai.riskmandate.app.experiments.ToolTestRunner
import ai.riskmandate.app.experiments.ToolTestSnapshot
import ai.riskmandate.app.experiments.ToolTestSuite
import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmEvent
import ai.riskmandate.app.llm.LlmLocation
import ai.riskmandate.app.llm.LlmRole
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.LlmState
import ai.riskmandate.app.llm.StrictJson
import ai.riskmandate.app.ui.LocalWideLayout
import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
class ToolTestsTabTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val cat = ExperimentsTestData.toolCatalogue()
    private val weather = cat.case("right-tool-weather")!!

    private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size
    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }

    /** A model for one case: the reference run, or the wrong one. Native proposals when tools are declared, JSON text otherwise. */
    private fun model(steps: List<RefStep>, delay: Long = 0) = FakeLlmService(tokenDelayMs = delay, reply = { req ->
        val turns = req.messages.count { it.role == LlmRole.ASSISTANT }
        val s = steps[turns.coerceIn(0, steps.lastIndex)]
        if (req.tools.isNotEmpty()) FakeReply(s.text, s.calls.map { LlmEvent.ToolCallProposed(it.name, StrictJson.render(it.arguments)) })
        else FakeReply((listOf(s.text).filter { it.isNotBlank() } + s.calls.map { StrictJson.render(mapOf("tool" to it.name, "arguments" to it.arguments)) }).joinToString("\n"))
    })

    private fun show(llm: FakeLlmService?, onBusy: (Boolean) -> Unit = {}, openFeature: (String) -> Unit = {}, cloud: LlmService? = null, wide: Boolean = false) =
        compose.setContent {
            RiskMandateTheme {
                CompositionLocalProvider(LocalWideLayout provides wide) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { ToolTestsTab(llm, openFeature, onBusy, catalogue = cat, cloudLlm = cloud) }
                }
            }
        }

    /** A table row or a segment, found by its text; it is the one with a click action. */
    private fun pick(text: String, exact: Boolean = false) {
        compose.onNode(hasText(text, substring = !exact) and hasClickAction()).performScrollTo().performClick()
    }

    private fun pickWeatherCase() {
        pick("right-tool-weather")
        pick("1", exact = true)
    }

    /** Done when both tiles show a count and the Run buttons are back. */
    private fun waitDone() = compose.waitUntil(30_000) {
        count(ToolTestTags.STOP) == 0 && compose.onAllNodes(hasTestTag(ToolTestTags.OVERALL_TEXT) and hasText(" of ", substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun oneCaseInBothModesShowsAPassRatePerMode() {
        val busy = mutableListOf<Boolean>()
        show(model(weather.reference!!, delay = 50), onBusy = { busy += it })
        pickWeatherCase()
        click(ToolTestTags.RUN_CASE)
        waitDone()
        compose.onNodeWithTag(ToolTestTags.OVERALL_NATIVE).assertTextContains("100%", substring = true)
        compose.onNodeWithTag(ToolTestTags.OVERALL_TEXT).assertTextContains("100%", substring = true)
        compose.onNodeWithTag(ToolTestTags.OVERALL_NATIVE).assertTextContains("1 of 1", substring = true)
        assertEquals(0, count(ToolTestTags.FAILURE))
        assertTrue(busy.contains(true)); assertEquals(false, busy.last())
    }

    @Test fun aFailureShowsTheFailedCheckAndTheRawOutput() {
        show(model(weather.wrong!!))
        pickWeatherCase()
        click(ToolTestTags.RUN_CASE)
        waitDone()
        compose.onNodeWithTag(ToolTestTags.OVERALL_NATIVE).assertTextContains("0%", substring = true)
        // Two runs (native, text), both failed: the verdict names the failed check, the selected one shows its raw output.
        assertTrue(compose.onAllNodes(hasText("Wrong tool", substring = true)).fetchSemanticsNodes().size >= 2)
        assertEquals(1, count(ToolTestTags.FAILURE))
        compose.onNodeWithTag(ToolTestTags.RAW).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ToolTestTags.RAW).assertTextContains("call set_timer", substring = true)
    }

    @Test fun anAnswerOnlyCaseRunsOncePerRepeatAndIsCountedInItsOwnTile() {
        val c = cat.case("format-three-lines")!!
        show(model(c.wrong!!))
        pick(c.id)
        pick("1", exact = true)
        click(ToolTestTags.RUN_CASE)
        compose.waitUntil(30_000) { count(ToolTestTags.STOP) == 0 && compose.onAllNodes(hasTestTag(ToolTestTags.OVERALL_ANSWERS) and hasText(" of ", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(ToolTestTags.OVERALL_ANSWERS).assertTextContains("0 of 1", substring = true)
        // The two ways of asking were not compared: they have nothing to show.
        assertEquals(0, compose.onAllNodes(hasTestTag(ToolTestTags.OVERALL_NATIVE) and hasText(" of ", substring = true)).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodes(hasTestTag(ToolTestTags.run(0))).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasTestTag(ToolTestTags.run(1))).fetchSemanticsNodes().size)
        assertTrue(compose.onAllNodes(hasText("Wrong number of lines", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun thePromptSizeIsShownBeforeAnyRun() {
        show(model(weather.reference!!))
        pick("right-tool-weather")
        fun inSize(text: String) = compose.onAllNodes(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(ToolTestTags.PROMPT_SIZE))).fetchSemanticsNodes().isNotEmpty()
        assertTrue(inSize("characters"))
        assertTrue(inSize(PromptSize.chars(weather, ToolMode.NATIVE).toString()))
        assertTrue(inSize(PromptSize.chars(weather, ToolMode.TEXT).toString()))
    }

    @Test fun theWideLayoutShowsBothPanes() {
        show(model(weather.reference!!), wide = true)
        pick("right-tool-weather")
        compose.onNodeWithTag(ToolTestTags.TABLE).assertIsDisplayed()
        compose.onNodeWithTag(ToolTestTags.PROMPT_SIZE).assertIsDisplayed()
        compose.onNodeWithTag(ToolTestTags.RUN).assertIsDisplayed()
    }

    @Test fun theLocalJudgeAddsAVerdictNextToTheRunAndNeverChangesIt() {
        // The same fake answers the judge with something that is not a verdict: unsure, never a pass.
        show(model(weather.reference!!))
        pickWeatherCase()
        pick(app.getString(R.string.workbench_tt_judge_local))
        click(ToolTestTags.RUN_CASE)
        waitDone()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Local model unsure", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(ToolTestTags.OVERALL_NATIVE).assertTextContains("100%", substring = true)
        compose.onNodeWithTag(ToolTestTags.DISAGREEMENTS).assertTextContains("0", substring = true)
    }

    private class CloudFake(private val inner: FakeLlmService) : LlmService by inner {
        override val location: LlmLocation get() = LlmLocation.CLOUD
    }

    @Test fun theCloudJudgeIsOffUntilChosenAndLabelledWhileOn() {
        val local = model(weather.reference!!)
        val cloud = CloudFake(FakeLlmService(initial = LlmState.Ready("cloud-model", "cloud")))
        show(local, cloud = cloud)
        compose.onNodeWithTag(ToolTestTags.JUDGE_CLOUD).performScrollTo().assertIsDisplayed()
        assertEquals(0, count(ToolTestTags.CLOUD_ON))
        click(ToolTestTags.JUDGE_CLOUD)
        compose.onNodeWithTag(ToolTestTags.CLOUD_ON).assertTextContains("cloud-model", substring = true)
        compose.onNodeWithTag(ToolTestTags.CLOUD_ON).assertTextContains("openrouter.ai", substring = true)
        click(ToolTestTags.JUDGE_CLOUD)
        assertEquals(0, count(ToolTestTags.CLOUD_ON))
    }

    @Test fun aServiceThatIsNotCloudIsNeverOfferedAsTheCloudJudge() {
        show(model(weather.reference!!), cloud = FakeLlmService())
        assertEquals(0, count(ToolTestTags.JUDGE_CLOUD))
    }

    @Test fun withoutAModelItOffersSetupAndNeverRuns() {
        var opened: String? = null
        val llm = FakeLlmService(initial = LlmState.NoModel)
        show(llm, openFeature = { opened = it })
        compose.onNodeWithTag(ToolTestTags.NO_MODEL).assertIsDisplayed()
        assertEquals(0, count(ToolTestTags.RUN))
        click(ToolTestTags.OPEN_MODELS)
        assertEquals("models", opened)
        assertEquals(0, llm.requests.size)
    }

    @Test fun aNullServiceShowsNoModelToo() {
        show(null)
        compose.onNodeWithTag(ToolTestTags.NO_MODEL).assertIsDisplayed()
        assertEquals(0, count(ToolTestTags.OPEN_MODELS))
    }

    @Test fun aFinishedRunSurvivesRotation() {
        val tester = StateRestorationTester(compose)
        tester.setContent { RiskMandateTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ToolTestsTab(model(weather.reference!!), catalogue = cat) } } }
        pickWeatherCase()
        click(ToolTestTags.RUN_CASE)
        waitDone()
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithTag(ToolTestTags.OVERALL_NATIVE).assertTextContains("100%", substring = true)
        compose.onNodeWithTag(ToolTestTags.OVERALL_TEXT).assertTextContains("100%", substring = true)
    }

    @Test fun aRunningSuiteIsStoppedByRotationAndKeepsItsCounts() {
        val slow = StateRestorationTester(compose)
        slow.setContent { RiskMandateTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ToolTestsTab(model(weather.reference!!, delay = 300), catalogue = cat) } } }
        click(ToolTestTags.RUN)
        compose.waitUntil(60_000) { compose.onAllNodes(hasTestTag(ToolTestTags.PROGRESS) and hasText("Run 2 ", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        slow.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithTag(ToolTestTags.STOP_REASON).assertTextContains(app.getString(R.string.workbench_tt_stop_turned))
        assertEquals(0, count(ToolTestTags.STOP))
    }

    // ---- the saved snapshot ----------------------------------------------------------------------------

    private fun run(i: Int, pass: Boolean, raw: String = "x") = CaseRun(
        "case-$i", "kind", if (i % 2 == 0) ToolMode.NATIVE else ToolMode.TEXT, i / 2, listOf(StepRecord(raw, emptyList())), raw, 100L + i, "m", "gpu", 5, 2.0, null,
        listOf(CheckResult("label $i", pass, "detail $i " + raw.take(50))),
    )

    @Test fun theSnapshotKeepsEveryRunsSummaryAndTheFirstFailuresRawOutputOnly() {
        val runs = (0 until 40).map { run(it, pass = it % 3 != 0, raw = "r".repeat(5_000)) }
        val text = ToolTestSnapshot.encode(runs, 40, StopReason.INTERRUPTED)
        assertTrue("snapshot must stay small, was ${text.length}", text.length < 20_000)
        val back = ToolTestSnapshot.decode(text)!!
        assertEquals(40, back.runs.size)
        assertEquals(StopReason.INTERRUPTED, back.stop)
        assertEquals(runs.map { it.passed }, back.runs.map { it.passed })
        assertEquals(runs.map { it.mode }, back.runs.map { it.mode })
        val failed = back.runs.filter { it.finished && !it.passed }
        assertEquals(runs.count { !it.passed }, failed.size)
        assertEquals(ToolTestSnapshot.KEEP_FAILURES, failed.count { it.steps.isNotEmpty() })
        assertTrue(failed.first().steps.first().text.length <= 600)
        assertEquals(ToolTestReport(runs).overall().passed, ToolTestReport(back.runs).overall().passed)
    }

    @Test fun unfinishedRunsStayUnfinishedAndHostileSnapshotsAreDropped() {
        val unfinished = run(1, false).copy(error = "inference failed", checks = emptyList())
        val back = ToolTestSnapshot.decode(ToolTestSnapshot.encode(listOf(unfinished), 3, null))!!
        assertTrue(!back.runs.single().finished && !back.runs.single().passed)
        assertNull(ToolTestSnapshot.decode(""))
        assertNull(ToolTestSnapshot.encode(emptyList(), 0, null).let { ToolTestSnapshot.decode(it) })
        for (bad in listOf("garbage", "{}", "{\"v\":2,\"planned\":1,\"stop\":null,\"runs\":[]}", "{\"v\":1,\"planned\":9999,\"stop\":null,\"runs\":[]}", "{\"v\":1,\"planned\":1,\"stop\":\"NOPE\",\"runs\":[]}",
            "{\"v\":1,\"planned\":1,\"stop\":null,\"runs\":[{\"c\":\"a\",\"k\":\"k\",\"m\":\"BOTH\",\"r\":0,\"f\":1,\"p\":1,\"t\":1,\"x\":null}]}",
            "{\"v\":1,\"planned\":1,\"stop\":null,\"runs\":[{\"c\":\"a\",\"k\":\"k\",\"m\":\"TEXT\",\"r\":0,\"f\":0,\"p\":1,\"t\":1,\"x\":null}]}",
            "{\"v\":1,\"planned\":1,\"stop\":null,\"runs\":[{\"c\":\"a\",\"k\":\"k\",\"m\":\"TEXT\",\"r\":0,\"f\":1,\"p\":1,\"t\":-5,\"x\":null}]}")) {
            assertNull("accepted: $bad", ToolTestSnapshot.decode(bad))
        }
        assertNotNull(ToolTestSnapshot.decode(ToolTestSnapshot.encode(listOf(run(0, true)), 1, null)))
    }
}
