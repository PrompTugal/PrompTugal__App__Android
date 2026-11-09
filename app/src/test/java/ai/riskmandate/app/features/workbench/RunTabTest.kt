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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
                ai.riskmandate.app.ui.FeatureContent {
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
        fail("no node tagged $tag")
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
        compose.onNodeWithTag(RunTags.RUN).performScrollTo().performClick()
        waitFor(RunTags.COPY)
        val req = fake.requests.single()
        assertEquals(BackendPreference.CPU, req.backend)
        assertEquals(1, fake.unloads)
        compose.onNodeWithTag(RunTags.DONE).assertTextContains("Done. 3 tokens", substring = true)
        assertEquals(1, req.settings.topK)
        assertEquals("You are a helpful assistant. Answer briefly and plainly.", req.messages.first().text)
        compose.onNodeWithTag(RunTags.ANSWER).assertTextContains("Two short words.")
        compose.onNodeWithTag(RunTags.REPORT).assertTextContains("Stop: done", substring = true)
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
        waitFor(RunTags.REPORT)
        assertEquals(LlmStopReason.CANCELLED, records.single().stopReason)
        compose.onNodeWithTag(RunTags.REPORT).assertTextContains("Stop: cancelled", substring = true)
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

    @Test fun theFeatureIsOffAndShowsTheRunTab() {
        assertFalse(WorkbenchFeature.enabled)
        compose.setContent {
            RiskMandateTheme {
                WorkbenchContent(FeatureContext(onBack = {}, config = ManagedConfig.DEFAULT, device = DeviceStatus(Management.NOT_DETECTED, null), llm = FakeLlmService()), io = kotlinx.coroutines.Dispatchers.Unconfined)
            }
        }
        compose.onNodeWithTag(WorkbenchTags.tab(0)).assertIsEnabled()
        compose.onNodeWithTag(RunTags.PROMPT).performScrollTo()
        // The RiskMandate base block comes from llm/prompts once it has loaded.
        waitFor(RunTags.systemPreset(3))
        compose.onNodeWithTag(RunTags.systemPreset(1)).assertTextContains("RiskMandate base block")
    }
}
