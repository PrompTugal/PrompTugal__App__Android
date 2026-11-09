package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.ManagedConfig
import ai.riskmandate.app.Management
import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.llm.BackendPreference
import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmRunner
import ai.riskmandate.app.llm.LlmStopReason
import ai.riskmandate.app.llm.ModelCatalog
import ai.riskmandate.app.llm.RunRecord
import android.app.Application
import android.content.ClipboardManager
import android.os.Looper
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class RunTabTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun show(fake: FakeLlmService?, records: MutableList<RunRecord> = mutableListOf(), opened: MutableList<Unit> = mutableListOf()) =
        compose.setContent {
            RiskMandateTheme {
                ai.riskmandate.app.ui.FeatureContent(scrollable = false) {
                var form by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(RunForm()) }
                var last by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<RunRecord?>(null) }
                RunTab(
                    form = form, onForm = { form = it }, last = last, onLast = { last = it },
                    runner = fake?.let { LlmRunner(it) },
                    activeModel = if (fake == null) null else "gemma4e2b (GPU)",
                    onOpenModels = { opened += Unit },
                    presets = WORKBENCH_PRESETS,
                    toolSets = emptyList(),
                    onRecord = { records += it },
                )
                }
            }
        }

    private fun waitFor(tag: String) {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(20)
        }
        fail("no node tagged $tag\n" + compose.onRoot(useUnmergedTree = false).printToString(maxDepth = 6).take(3000))
    }

    @Test fun withoutAServiceItSaysSoAndCannotRun() {
        show(null)
        compose.onNodeWithTag(RunTags.NO_SERVICE).assertTextContains("Model setup", substring = true)
        compose.onNodeWithTag(RunTags.PROMPT).performTextInput("Hello")
        compose.onNodeWithTag(RunTags.RUN).performScrollTo().assertIsNotEnabled()
    }

    @Test fun badNumbersBlockTheRun() {
        show(FakeLlmService())
        compose.onNodeWithTag(RunTags.PROMPT).performTextInput("Hello")
        compose.onNodeWithTag(RunTags.RUN).performScrollTo().assertIsEnabled()
        compose.onNodeWithTag(RunTags.TEMPERATURE).performScrollTo().performTextClearance()
        compose.onNodeWithTag(RunTags.TEMPERATURE).performTextInput("5")
        compose.onNodeWithTag(RunTags.INVALID).performScrollTo()
        compose.onNodeWithTag(RunTags.RUN).performScrollTo().assertIsNotEnabled()
    }

    @Test fun aRunUsesTheSettingsShowsFiguresAndCanBeCopiedAndRepeated() {
        val records = mutableListOf<RunRecord>()
        val opened = mutableListOf<Unit>()
        val fake = FakeLlmService { FakeReply("Two short words.") }
        show(fake, records, opened)
        compose.onNodeWithTag(RunTags.MODEL).assertTextContains("gemma4e2b (GPU)")
        compose.onNodeWithTag(RunTags.OPEN_MODELS).performClick()
        assertEquals(1, opened.size)
        compose.onNodeWithTag(RunTags.backend("CPU")).performClick()
        compose.onNodeWithTag(RunTags.COLD).performClick()
        compose.onNodeWithTag(RunTags.systemPreset(1)).performScrollTo().performClick()
        compose.onNodeWithTag(RunTags.PROMPT).performScrollTo().performTextInput("Say two words")
        compose.onNodeWithTag(RunTags.TOP_K).performScrollTo().performTextClearance()
        compose.onNodeWithTag(RunTags.TOP_K).performTextInput("1")
        compose.onNodeWithTag(RunTags.PROMPT).assertTextContains("Say two words")
        compose.onNodeWithTag(RunTags.RUN).performScrollTo().performClick()
        waitFor(RunTags.COPY)
        val req = fake.requests.single()
        assertEquals(BackendPreference.CPU, req.backend)
        assertEquals(1, fake.unloads)
        compose.onNodeWithTag(RunTags.DONE).assertTextContains("Done. 3 tokens", substring = true)
        assertEquals(1, req.settings.topK)
        assertEquals("You are a helpful assistant. Answer briefly and plainly.", req.messages.first().text)
        compose.onNodeWithTag(RunTags.ANSWER).assertTextContains("Two short words.")
        compose.onNodeWithTag(RunTags.STAT_STOP).performScrollTo().assertTextContains("Done")
        compose.onNodeWithTag(RunTags.STAT_OUTPUT).assertTextContains("3")
        compose.onNodeWithTag(RunTags.STAT_OUTPUT).assertTextContains("app")
        compose.onNodeWithTag(RunTags.STAT_DECODE).assertTextContains("not available")
        assertEquals(LlmStopReason.DONE, records.single().stopReason)

        compose.onNodeWithTag(RunTags.COPY).performScrollTo().performClick()
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertTrue(clip.startsWith("RiskMandate Workbench run"))
        assertTrue("Prompt: Say two words" in clip)

        compose.onNodeWithTag(RunTags.RERUN).performScrollTo().performClick()
        val end = System.currentTimeMillis() + 10_000
        while (records.size < 2 && System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idle(); compose.waitForIdle(); Thread.sleep(20) }
        assertEquals(2, fake.requests.size)
        assertEquals(fake.requests[0], fake.requests[1])
    }

    @Test fun stopEndsTheRunAsStopped() {
        val records = mutableListOf<RunRecord>()
        show(FakeLlmService(tokenDelayMs = 30) { FakeReply("w ".repeat(400)) }, records = records)
        compose.onNodeWithTag(RunTags.PROMPT).performTextInput("long")
        compose.onNodeWithTag(RunTags.RUN).performScrollTo().performClick()
        waitFor(RunTags.STOP)
        compose.onNodeWithTag(RunTags.STOP).performScrollTo().performClick()
        waitFor(RunTags.RERUN)
        assertEquals(LlmStopReason.CANCELLED, records.single().stopReason)
        compose.onNodeWithTag(RunTags.STAT_STOP).performScrollTo().assertTextContains("Stopped")
    }

    @Test fun theStatsLabelEveryFigureAndShowAFallBack() {
        val scripted = ai.riskmandate.app.llm.LlmStats("gemma4e2b", "CPU", 120, 40, 300.0, 5.5, 900, 8000, loadedModel = true, loadMs = 6000,
            countsFrom = ai.riskmandate.app.llm.StatSource.RUNTIME, speedsFrom = ai.riskmandate.app.llm.StatSource.RUNTIME, requestedBackend = "AUTO", fellBackToCpu = true)
        show(FakeLlmService { FakeReply("ok", stats = scripted) })
        compose.onNodeWithTag(RunTags.PROMPT).performTextInput("x")
        compose.onNodeWithTag(RunTags.RUN).performScrollTo().performClick()
        waitFor(RunTags.RERUN)
        compose.onNodeWithTag(RunTags.STAT_LOAD).performScrollTo().assertTextContains("Model load (cold)")
        compose.onNodeWithTag(RunTags.STAT_LOAD).assertTextContains("6000 ms")
        compose.onNodeWithTag(RunTags.STAT_DECODE).assertTextContains("5.5")
        compose.onNodeWithTag(RunTags.STAT_DECODE).assertTextContains("runtime")
        compose.onNodeWithTag(RunTags.STAT_TTFT).assertTextContains("app")
        compose.onNodeWithTag(RunTags.STAT_BACKEND).assertTextContains("CPU, fell back")
    }

    @Test fun promptsAreCappedAt2000Characters() {
        show(FakeLlmService())
        compose.onNodeWithTag(RunTags.PROMPT).performTextInput("x".repeat(2_100))
        compose.onNodeWithTag(RunTags.PROMPT).assertTextContains("x".repeat(2_000))
        val f = RunForm.restore(RunForm.save(RunForm(prompt = "y".repeat(3_000), backend = "NPU", toolSet = 999)))
        assertEquals(2_000, f.prompt.length)
        assertEquals("AUTO", f.backend)
        assertEquals(50, f.toolSet)
    }

    private fun cloudFake() = object : ai.riskmandate.app.llm.LlmService by FakeLlmService(initial = ai.riskmandate.app.llm.LlmState.Ready("google/gemma-3-4b-it", "cloud"), reply = {
        FakeReply("From the cloud.", stats = ai.riskmandate.app.llm.LlmStats("google/gemma-3-4b-it", "cloud", 8, 3, null, 20.0, 300, 700, location = ai.riskmandate.app.llm.LlmLocation.CLOUD, costUsd = 0.0002))
    }) {
        override val location = ai.riskmandate.app.llm.LlmLocation.CLOUD
    }

    private fun showWorkbench(cloud: ai.riskmandate.app.llm.LlmService?, local: FakeLlmService) = compose.setContent {
        RiskMandateTheme {
            WorkbenchContent(
                FeatureContext(onBack = {}, config = ManagedConfig.DEFAULT, device = DeviceStatus(Management.NOT_DETECTED, null), llm = local, cloudLlm = cloud),
                io = kotlinx.coroutines.Dispatchers.Unconfined, probe = { null }, guard = ai.riskmandate.app.experiments.NoGuard,
            )
        }
    }

    @Test fun theCloudChoiceAppearsOnlyWithACloudServiceAndLabelsEverything() {
        assertTrue(WorkbenchFeature.usesCloud)
        val local = FakeLlmService { FakeReply("local") }
        showWorkbench(cloud = cloudFake(), local = local)
        compose.onNodeWithTag(WorkbenchTags.WHERE_CHIP).assertTextContains("On this device")
        compose.onNodeWithTag(RunTags.CLOUD).performScrollTo().performClick()
        compose.onNodeWithTag(WorkbenchTags.WHERE_CHIP).assertTextContains("Cloud \u00b7 google/gemma-3-4b-it via openrouter.ai")
        compose.onNodeWithTag(RunTags.PROMPT).performScrollTo().performTextInput("Hello")
        compose.onNodeWithTag(RunTags.RUN).performScrollTo().performClick()
        waitFor(RunTags.RERUN)
        assertTrue("the cloud answered, not the device", local.requests.isEmpty())
        compose.onNodeWithTag(RunTags.ANSWER).performScrollTo().assertTextContains("From the cloud.")
        compose.onNodeWithTag(RunTags.WHERE).performScrollTo().assertTextContains("Cloud \u00b7 google/gemma-3-4b-it via openrouter.ai")
        // Header chip and the answer card's kicker carry it too (the stats row is checked above).
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Cloud \u00b7 google/gemma-3-4b-it via openrouter.ai", substring = true)).fetchSemanticsNodes().size >= 2)
    }

    @Test fun withoutACloudServiceThereIsNoCloudChoice() {
        showWorkbench(cloud = null, local = FakeLlmService())
        assertTrue(compose.onAllNodes(hasTestTag(RunTags.CLOUD)).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag(WorkbenchTags.WHERE_CHIP).assertTextContains("On this device")
        // A saved "cloud" choice is not honoured without a cloud service.
        assertEquals(true, RunForm.restore(RunForm.save(RunForm(useCloud = true))).useCloud)
    }

    @Test fun theFeatureIsOffAndShowsTheRunTab() {
        assertFalse(WorkbenchFeature.enabled)
        compose.setContent {
            RiskMandateTheme {
                WorkbenchContent(FeatureContext(onBack = {}, config = ManagedConfig.DEFAULT, device = DeviceStatus(Management.NOT_DETECTED, null), llm = FakeLlmService()), io = kotlinx.coroutines.Dispatchers.Unconfined)
            }
        }
        compose.onNodeWithTag(WorkbenchTags.TABS).assertExists()
        compose.onNodeWithTag(RunTags.PROMPT).performScrollTo()
        // The RiskMandate base block comes from llm/prompts once it has loaded.
        waitFor(RunTags.systemPreset(3))
        compose.onNodeWithTag(RunTags.systemPreset(1)).assertTextContains("RiskMandate base block")
    }
}
