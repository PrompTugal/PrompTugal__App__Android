package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.ManagedConfig
import ai.riskmandate.app.Management
import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmLocation
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.LlmState
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** "Our data": grounded answers over the bundled data, with sources and the eval, on the on-device service only. */
@RunWith(AndroidJUnit4::class)
class AskTabTest {
    @get:Rule val compose = createComposeRule()

    private fun waitFor(tag: String, text: String? = null) {
        val end = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            val nodes = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
            if (nodes.isNotEmpty() && (text == null || nodes.any { it.config.toString().contains(text) })) return
            Thread.sleep(20)
        }
        fail("no node tagged $tag" + (text?.let { " with $it" } ?: ""))
    }

    private fun show(llm: LlmService?) = compose.setContent {
        RiskMandateTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AskTab(llm = llm, io = Dispatchers.Unconfined, onOpenModels = {}, onRunning = {})
            }
        }
    }

    @Test fun anAnswerShowsItsNumberedSourcesAndWhichWereCited() {
        val fake = FakeLlmService { FakeReply("Pavilion 1, stand E145 [1].") }
        show(fake)
        waitFor(AskTags.QUESTION)
        compose.onNodeWithTag(AskTags.QUESTION).performTextInput("Where is Portugal's stand at Web Summit?")
        compose.onNodeWithTag(AskTags.ASK).performScrollTo().performClick()
        waitFor(AskTags.SOURCES)
        compose.onNodeWithTag(AskTags.ANSWER).assertTextContains("Pavilion 1, stand E145 [1].")
        compose.onNodeWithTag(AskTags.source(1), useUnmergedTree = true).performScrollTo()
        compose.onNode(androidx.compose.ui.test.hasText("[1] Where to find Portugal", substring = true) and androidx.compose.ui.test.hasAnyAncestor(hasTestTag(AskTags.source(1))), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Cited", useUnmergedTree = true).assertTextContains("Cited", substring = true)
        compose.onNodeWithTag(AskTags.TTFT).assertTextContains("ms", substring = true)
        compose.onNodeWithTag(AskTags.ATTRIBUTION).assertTextContains("From pt.news: Where to find Portugal", substring = true)
        // The request held numbered sources and the grounding rules, and no device claim.
        val req = fake.requests.single()
        assertTrue(req.messages[1].text.startsWith("Sources:\n[1] "))
        assertTrue(req.messages[0].text.contains("only the numbered sources"))
        assertTrue(!req.messages[0].text.contains("this device"))
    }

    @Test fun theEvalRunsEveryQuestionAndSummarises() {
        show(FakeLlmService { FakeReply("I do not know that from our data.") })
        waitFor(AskTags.EVAL)
        compose.onNodeWithTag(AskTags.EVAL).performScrollTo().performClick()
        waitFor(AskTags.EVAL_SUMMARY, "24 of 24")
        // Only the two questions the data cannot answer are right for an answer that always says it does not know.
        compose.onNodeWithTag(AskTags.EVAL_SUMMARY).assertTextContains("answers right 2", substring = true)
    }

    @Test fun withoutAModelItOffersSetupAndCannotAsk() {
        show(FakeLlmService(initial = LlmState.NoModel))
        waitFor(AskTags.NO_MODEL)
        compose.onNodeWithTag(AskTags.ASK).performScrollTo().assertIsNotEnabled()
    }

    @Test fun aCloudServiceIsNeverUsedHere() {
        val inner = FakeLlmService { FakeReply("cloud") }
        show(object : LlmService by inner { override val location = LlmLocation.CLOUD })
        waitFor(AskTags.NO_MODEL)
        compose.onNodeWithTag(AskTags.ASK).performScrollTo().assertIsNotEnabled()
        assertTrue(inner.requests.isEmpty())
    }

    @Test fun theWorkbenchHostsItAsTheFourthTabOnTheDevice() {
        val local = FakeLlmService { FakeReply("Local [1].") }
        val cloudInner = FakeLlmService { FakeReply("cloud") }
        val cloud = object : LlmService by cloudInner { override val location = LlmLocation.CLOUD }
        compose.setContent {
            RiskMandateTheme {
                WorkbenchContent(
                    FeatureContext(onBack = {}, config = ManagedConfig.DEFAULT, device = DeviceStatus(Management.NOT_DETECTED, null), llm = local, cloudLlm = cloud),
                    io = Dispatchers.Unconfined, probe = { null }, guard = ai.riskmandate.app.experiments.NoGuard,
                )
            }
        }
        compose.onNodeWithTag(RunTags.CLOUD).performScrollTo().performClick()
        compose.onNodeWithText("Our data").performClick()
        waitFor(AskTags.QUESTION)
        compose.onNodeWithTag(WorkbenchTags.WHERE_CHIP).assertTextContains("On this device")
        compose.onNodeWithTag(AskTags.QUESTION).performTextInput("What does Cerebras build?")
        compose.onNodeWithTag(AskTags.ASK).performScrollTo().performClick()
        waitFor(AskTags.SOURCES)
        assertTrue("on the device", local.requests.size == 1)
        assertTrue("never the cloud", cloudInner.requests.isEmpty())
    }
}
