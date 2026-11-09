package ai.riskmandate.app.features.chat

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.ManagedConfig
import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.features.FEATURES
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.LlmState
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ChatScreenTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun show(llm: ChatLlm, store: ChatHistoryStore = MemoryChatHistoryStore()) = compose.setContent {
        val ctx = FeatureContext({}, ManagedConfig.DEFAULT, DeviceStatus.from(false, false, null), openFeature = { opened += it })
        CompositionLocalProvider(LocalChatStore provides store, LocalChatIo provides Dispatchers.Unconfined) {
            RiskMandateTheme { ChatContent(ctx, llm) }
        }
    }

    /** The production path: the host's LlmService through FeatureContext.llm, adapted inside chat. */
    private fun showWithService(service: LlmService?) = compose.setContent {
        val ctx = FeatureContext({}, ManagedConfig.DEFAULT, DeviceStatus.from(false, false, null), openFeature = { opened += it }, llm = service)
        CompositionLocalProvider(LocalChatStore provides MemoryChatHistoryStore(), LocalChatIo provides Dispatchers.Unconfined) { RiskMandateTheme { ChatFeature.Content(ctx) } }
    }

    @Test fun nullServiceShowsTheNoModelStateWithOpenModels() {
        showWithService(null)
        loaded()
        tag(ChatTags.NO_MODEL).assertIsDisplayed()
        tag(ChatTags.OPEN_MODELS).performClick()
        assertEquals(listOf("models"), opened)
    }

    @Test fun noModelStateFromTheServiceShowsTheSameState() {
        showWithService(FakeLlmService(initial = LlmState.NoModel))
        loaded()
        tag(ChatTags.NO_MODEL).assertIsDisplayed()
    }

    @Test fun chatTalksToTheHostServiceAndShowsMarkupAsText() {
        val svc = FakeLlmService(reply = { FakeReply("Hi <b>there</b> https://riskmandate.ai/abp") })
        showWithService(svc)
        loaded()
        tag(ChatTags.INPUT).performTextInput("hello")
        tag(ChatTags.SEND).performClick()
        compose.waitUntil(15_000) { svc.requests.size == 1 && count(ChatTags.STATS) == 1 }
        compose.onNodeWithText("Hi <b>there</b> https://riskmandate.ai/abp").assertIsDisplayed()
        assertEquals(1, count(ChatTags.link(0)))
    }

    /**
     * The screen's history IO runs on an immediate dispatcher here ([LocalChatIo]), so no real thread is involved. This
     * still waits for the loaded state itself (either the no-model notice or the input row) rather than for idle, as a
     * belt and braces: no timing, no sleeps.
     */
    private fun loaded() {
        compose.waitUntil(15_000) { count(ChatTags.NO_MODEL) == 1 || count(ChatTags.INPUT) == 1 }
        compose.waitForIdle()
    }

    private fun tag(t: String) = compose.onNodeWithTag(t)
    private fun count(t: String) = compose.onAllNodesWithTag(t).fetchSemanticsNodes().size

    /** QA's probe (Q-chat-1) as a test: a rotation (saved state restored, a new composition) keeps the open chat on screen. */
    @Test fun rotationKeepsTheOpenConversationOnScreen() {
        val store = MemoryChatHistoryStore()
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {
            val ctx = FeatureContext({}, ManagedConfig.DEFAULT, DeviceStatus.from(false, false, null))
            CompositionLocalProvider(LocalChatStore provides store, LocalChatIo provides Dispatchers.Unconfined) { RiskMandateTheme { ChatContent(ctx, FakeChatLlm(pieces = listOf("An ", "answer."))) } }
        }
        loaded()
        tag(ChatTags.INPUT).performTextInput("hello there")
        tag(ChatTags.SEND).performClick()
        compose.waitUntil(15_000) { count(ChatTags.STATS) == 1 }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.waitUntil(15_000) { count(ChatTags.message(1)) == 1 }
        assertEquals(1, count(ChatTags.message(0)))
        compose.onNodeWithText("hello there").assertIsDisplayed()
    }

    @Test fun featureStaysOffUntilItWorksOnTheTablet() {
        assertFalse(ChatFeature.enabled)
        assertTrue(FEATURES.any { it === ChatFeature })
    }

    @Test fun showsTheOnDeviceBadgeAndNoModelState() {
        show(NoLlm)
        loaded()
        assertEquals(0, count(ChatTags.BADGE)) // "on device" is not claimed with no model installed
        tag(ChatTags.NETWORK).assertIsDisplayed()
        tag(ChatTags.NO_MODEL).assertIsDisplayed()
        assertEquals(0, count(ChatTags.INPUT))
        tag(ChatTags.OPEN_MODELS).performClick()
        assertEquals(listOf("models"), opened) // by id only, through the host
    }

    @Test fun aCloudServiceGetsTheCloudBadgeAndNeverRunsOnThisDevice() {
        show(FakeChatLlm(local = false))
        loaded()
        tag(ChatTags.BADGE_CLOUD).assertIsDisplayed()
        compose.onNodeWithText("Cloud: openrouter.ai").assertIsDisplayed()
        assertEquals(0, count(ChatTags.BADGE))
        assertEquals(0, compose.onAllNodesWithText("Runs on this device").fetchSemanticsNodes().size)
    }

    @Test fun badgeAndNetworkChipShowWithAModel() {
        show(FakeChatLlm())
        loaded()
        compose.onNodeWithText("Runs on this device").assertIsDisplayed() // never "offline" next to "Network: on"
        tag(ChatTags.BADGE).assertIsDisplayed()
        tag(ChatTags.NETWORK).assertIsDisplayed()
    }

    @Test fun typingSendingAndStreamingShowsPlainTextWithStats() {
        show(FakeChatLlm(pieces = listOf("Hel", "lo ", "<b>bold</b>")))
        loaded()
        tag(ChatTags.SEND).assertIsNotEnabled()
        tag(ChatTags.INPUT).performTextInput("hello there")
        tag(ChatTags.SEND).assertIsEnabled().performClick()
        compose.waitUntil(15_000) { count(ChatTags.STATS) == 1 }
        compose.onNodeWithText("hello there").assertIsDisplayed()
        // Markup in the output is shown literally, never rendered.
        compose.onNodeWithText("Hello <b>bold</b>").assertIsDisplayed()
        assertEquals(0, count(ChatTags.link(0))) // no allowed link in this answer
    }

    @Test fun boldMarkersAreNotShown() {
        show(FakeChatLlm(pieces = listOf("**Portugal** is in ", "__Europe__.")))
        loaded()
        tag(ChatTags.INPUT).performTextInput("where?")
        tag(ChatTags.SEND).performClick()
        compose.waitUntil(15_000) { count(ChatTags.STATS) == 1 }
        compose.onNodeWithText("Portugal is in Europe.").assertIsDisplayed()
    }

    @Test fun allowedLinksBecomeButtonsOthersStayText() {
        show(FakeChatLlm(pieces = listOf("Read https://riskmandate.ai/abp or https://evil.example/x")))
        loaded()
        tag(ChatTags.INPUT).performTextInput("links?")
        tag(ChatTags.SEND).performClick()
        compose.waitUntil(15_000) { count(ChatTags.link(0)) == 1 }
        assertEquals(0, count(ChatTags.link(1)))
        compose.onNodeWithText("Read https://riskmandate.ai/abp or https://evil.example/x").assertIsDisplayed()
    }

    @Test fun stopButtonShowsWhileAnsweringAndEndsIt() {
        show(FakeChatLlm(pieces = List(1000) { "t" }, delayMs = 50))
        loaded()
        tag(ChatTags.INPUT).performTextInput("go")
        tag(ChatTags.SEND).performClick()
        compose.waitUntil(15_000) { count(ChatTags.STOP) == 1 }
        tag(ChatTags.STOP).performClick()
        compose.waitUntil(15_000) { count(ChatTags.SEND) == 1 }
        compose.onNodeWithText("Stopped").assertIsDisplayed()
    }

    @Test fun oneModelShowsAReadOnlyLabel() {
        show(FakeChatLlm())
        loaded()
        tag(ChatTags.MODEL_LABEL).assertIsDisplayed()
    }

    @Test fun modelPickerOnlyWithMoreThanOneModel() {
        show(FakeChatLlm(models = listOf(ChatModelInfo("only", "Only"))))
        loaded()
        assertEquals(0, count(ChatTags.model("only")))
    }

    @Test fun modelPickerShowsEveryInstalledModel() {
        show(FakeChatLlm(models = listOf(ChatModelInfo("a", "Model A"), ChatModelInfo("b", "Model B"))))
        loaded()
        tag(ChatTags.model("a")).assertIsDisplayed()
        tag(ChatTags.model("b")).assertIsDisplayed().performClick()
    }

    @Test fun presetsGroundedFillAndDisabled() {
        val llm = FakeChatLlm()
        show(llm)
        loaded()
        tag(ChatTags.preset("triplets")).assertIsNotEnabled() // graph tools are not built yet
        // "Summarise a risk" fills the box and waits for the user's text.
        tag(ChatTags.preset("risk")).performClick()
        compose.onNodeWithText("Summarise this risk in three short sentences:", substring = true).assertIsDisplayed()
        assertEquals(0, llm.calls.size)
        tag(ChatTags.preset("riskmandate")).performClick()
        compose.waitUntil(15_000) { llm.calls.size == 1 }
        assertTrue(llm.calls.single().system!!.contains("Give agents access. Not free rein."))
        assertEquals(0, count(ChatTags.preset("abp"))) // presets give way to the conversation
    }

    @Test fun clearHistoryAsksFirstThenDeletes() {
        val store = MemoryChatHistoryStore(listOf(Conversation("old", listOf(ChatMessage(ChatRole.USER, "old chat")))))
        show(FakeChatLlm(), store)
        loaded()
        tag(ChatTags.HISTORY).performClick()
        tag(ChatTags.conversation(0)).assertIsDisplayed()
        tag(ChatTags.CLEAR).performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, store.saved.size) // cancel deletes nothing
        tag(ChatTags.CLEAR).performClick()
        tag(ChatTags.CLEAR_CONFIRM).performClick()
        compose.waitUntil(15_000) { store.clears == 1 }
        assertTrue(store.saved.isEmpty())
        assertEquals(0, count(ChatTags.conversation(0)))
    }

    @Test @Config(qualifiers = "pt-rPT")
    fun portugueseBadge() {
        show(FakeChatLlm())
        loaded()
        compose.onNodeWithText("Funciona neste dispositivo").assertIsDisplayed()
    }

    @Test fun largeFontStillReachesTheControls() {
        show(FakeChatLlm())
        loaded()
        tag(ChatTags.NEW).assertIsDisplayed()
        tag(ChatTags.INPUT).assertIsDisplayed()
    }
}
