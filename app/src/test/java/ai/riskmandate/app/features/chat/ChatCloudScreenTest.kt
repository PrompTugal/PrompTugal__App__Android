package ai.riskmandate.app.features.chat

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.ManagedConfig
import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmLocation
import ai.riskmandate.app.llm.LlmService
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The screen with both services: the cloud (a fake that says CLOUD) and the device. Nothing here touches a network. */
// A phone-sized window (411 x 891 dp), where the controls above the messages must still leave room for the message box.
@Config(qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class ChatCloudScreenTest {
    @get:Rule val compose = createComposeRule()

    private val device = FakeChatLlm(models = listOf(ChatModelInfo("dev-model", "Device model")), pieces = listOf("dev", " answer"))
    private fun cloudFake(pieces: List<String> = listOf("cloud", " answer")) =
        FakeChatLlm(models = listOf(ChatModelInfo("cloud/model", "Cloud model")), pieces = pieces, local = false)

    private fun show(cloud: ChatLlm?) = compose.setContent {
        val ctx = FeatureContext({}, ManagedConfig.DEFAULT, DeviceStatus.from(false, false, null))
        CompositionLocalProvider(LocalChatStore provides MemoryChatHistoryStore(), LocalChatIo provides Dispatchers.Unconfined) {
            RiskMandateTheme { ChatContent(ctx, device, cloud) }
        }
    }

    /** The production path: the host's two fields, `llm` (device) and `cloudLlm`, adapted inside chat. */
    private fun showWithServices(local: LlmService?, cloudService: LlmService?) = compose.setContent {
        val ctx = FeatureContext({}, ManagedConfig.DEFAULT, DeviceStatus.from(false, false, null), llm = local, cloudLlm = cloudService)
        CompositionLocalProvider(LocalChatStore provides MemoryChatHistoryStore(), LocalChatIo provides Dispatchers.Unconfined) {
            RiskMandateTheme { ChatFeature.Content(ctx) }
        }
    }

    private fun count(t: String) = compose.onAllNodesWithTag(t).fetchSemanticsNodes().size
    private fun tag(t: String) = compose.onNodeWithTag(t)
    private fun loaded() {
        compose.waitUntil(15_000) { count(ChatTags.NO_MODEL) == 1 || count(ChatTags.INPUT) == 1 }
        compose.waitForIdle()
    }
    /** Text inside the "what will be sent" dialog only (the chat bubbles behind it also show the messages). */
    private fun dialogCount(s: String) =
        compose.onAllNodes(hasText(s, substring = true) and hasAnyAncestor(hasTestTag(ChatTags.SENT_DIALOG))).fetchSemanticsNodes().size
    private fun textCount(s: String) = compose.onAllNodesWithText(s, substring = true, ignoreCase = true).fetchSemanticsNodes().size
    private fun send(text: String) {
        tag(ChatTags.INPUT).performTextInput(text)
        tag(ChatTags.SEND).performClick()
        compose.waitForIdle()
        if (count(ChatTags.FIRST_SEND_CONFIRM) == 1) tag(ChatTags.FIRST_SEND_CONFIRM).performClick() // the first cloud send of a session asks
        compose.waitUntil(15_000) { count(ChatTags.STATS) == 1 || textCount("Answer on this device") > 0 || textCount("spending cap") > 0 }
    }

    @Test fun theFirstCloudSendShowsWhatWillBeSentAndCancelKeepsTheTextAndSendsNothing() {
        val cloud = cloudFake()
        show(cloud)
        loaded()
        tag(ChatTags.INPUT).performTextInput("my question")
        tag(ChatTags.SEND).performClick()
        compose.waitForIdle()
        assertEquals(1, count(ChatTags.FIRST_SEND_DIALOG))
        assertTrue(compose.onAllNodes(hasText("my question", substring = true) and hasAnyAncestor(hasTestTag(ChatTags.FIRST_SEND_DIALOG))).fetchSemanticsNodes().isNotEmpty())
        tag(ChatTags.FIRST_SEND_CANCEL).performClick()
        compose.waitForIdle()
        assertEquals(0, count(ChatTags.FIRST_SEND_DIALOG))
        assertEquals(0, count(ChatTags.STATS))
        tag(ChatTags.INPUT).assertTextContains("my question")
    }

    @Test fun theSourceChoiceShowsWhichServiceAnswersAndSwitches() {
        show(cloudFake())
        loaded()
        tag(ChatTags.SOURCE_CLOUD).assertIsDisplayed(); tag(ChatTags.SOURCE_DEVICE).assertIsDisplayed()
        tag(ChatTags.BADGE_CLOUD).assertIsDisplayed()
        assertEquals("never both badges", 0, count(ChatTags.BADGE))
        tag(ChatTags.SOURCE_DEVICE).performClick()
        compose.waitUntil(15_000) { count(ChatTags.BADGE) == 1 }
        assertEquals(0, count(ChatTags.BADGE_CLOUD))
        assertEquals("the history switch only exists on the cloud", 0, count(ChatTags.INCLUDE_HISTORY))
    }

    @Test fun withoutACloudServiceThereIsNoChoiceAndNoCloudBadge() {
        show(null)
        loaded()
        assertEquals(0, count(ChatTags.SOURCE_CLOUD)); assertEquals(0, count(ChatTags.SOURCE_DEVICE))
        assertEquals(0, count(ChatTags.BADGE_CLOUD)); assertEquals(1, count(ChatTags.BADGE))
    }

    @Test fun noOnDeviceLineIsShownWhileACloudAnswerIsOnScreen() {
        show(cloudFake())
        loaded()
        send("hello")
        compose.onNodeWithText("Cloud, openrouter.ai, cloud/model", substring = true).assertIsDisplayed() // every answer says where it came from
        for (claim in listOf("Runs on this device", "stays on this device", "Nothing you type leaves the device", "Everything stays")) {
            assertEquals("'$claim' must not be on screen beside a cloud answer", 0, textCount(claim))
        }
        // The device answer, once chosen, is labelled the same way.
        tag(ChatTags.SOURCE_DEVICE).performClick()
        compose.waitUntil(15_000) { count(ChatTags.BADGE) == 1 }
        send("again")
        compose.onNodeWithText("On device, Device model".replace("Device model", "dev-model"), substring = true).assertIsDisplayed()
    }

    @Test fun whatWillBeSentShowsTheInstructionsAndOnlyTheNewMessageUntilHistoryIsSwitchedOn() {
        show(cloudFake())
        loaded()
        send("first question")
        tag(ChatTags.INPUT).performTextInput("second question")
        tag(ChatTags.SHOW_SENT).performClick()
        compose.waitUntil(15_000) { count(ChatTags.SENT_DIALOG) == 1 }
        assertTrue("the RiskMandate texts are part of what is sent", dialogCount("Give agents access. Not free rein.") > 0)
        assertTrue(dialogCount("second question") > 0)
        assertEquals("earlier messages are not sent by default", 0, dialogCount("first question"))
        compose.onNodeWithText("Close").performClick()
        tag(ChatTags.INCLUDE_HISTORY).performClick()
        tag(ChatTags.SHOW_SENT).performClick()
        compose.waitUntil(15_000) { count(ChatTags.SENT_DIALOG) == 1 }
        assertTrue("now the earlier message goes too", dialogCount("first question") > 0)
        // Leave no dialog window open: a stray window would leak into the next test in the same sandbox.
        compose.onNodeWithText("Close").performClick()
        compose.waitUntil(15_000) { count(ChatTags.SENT_DIALOG) == 0 }
    }

    @Test fun aCloudAnswerShowsItsCost() {
        val withCost = cloudFake().apply { reportedStats = ChatAnswerStats(outputTokens = 12, tokensPerSecond = 33.0, totalMs = 900, costUsd = 0.0004) }
        show(withCost)
        loaded()
        send("hello")
        compose.onNodeWithText("US$0.0004", substring = true).assertIsDisplayed()
    }

    @Test fun aCapStopSaysSoAndOneTapAnswersOnTheDevice() {
        show(cloudFake().apply { failWith = ChatLlmException("CAP_REACHED") })
        loaded()
        send("hello")
        compose.onNodeWithText("The cloud spending cap is reached", substring = true).assertIsDisplayed()
        tag(ChatTags.USE_DEVICE).performClick()
        compose.waitUntil(15_000) { count(ChatTags.BADGE) == 1 }
        assertEquals(0, count(ChatTags.USE_DEVICE))
        send("hello again")
        compose.onNodeWithText("dev answer", substring = true).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "pt-rPT-w411dp-h891dp")
    fun portugueseCloudBadgeAndChoice() {
        show(cloudFake())
        loaded()
        compose.onAllNodesWithText("Nuvem: openrouter.ai").fetchSemanticsNodes().isNotEmpty().let { assertTrue(it) }
        compose.onNodeWithText("Este dispositivo").assertIsDisplayed()
    }

    @Test fun thePathThroughTheHostsTwoFieldsOffersTheCloudOnlyForAServiceThatSaysCloud() {
        val local = FakeLlmService(reply = { FakeReply("dev answer") })
        val cloudService = object : LlmService by FakeLlmService(reply = { FakeReply("cloud answer") }) {
            override val location = LlmLocation.CLOUD
        }
        showWithServices(local, cloudService)
        loaded()
        tag(ChatTags.SOURCE_CLOUD).assertIsDisplayed()
    }

    @Test fun aServiceHandedInAsCloudThatIsNotCloudIsIgnored() {
        showWithServices(FakeLlmService(), FakeLlmService()) // location LOCAL: chat never treats it as the cloud
        loaded()
        assertEquals(0, count(ChatTags.SOURCE_CLOUD))
    }

    @Test fun standModeLeavesOnlyTheDevice() {
        showWithServices(FakeLlmService(), null) // the host passes no cloudLlm in stand mode
        loaded()
        assertEquals(0, count(ChatTags.SOURCE_CLOUD)); assertEquals(1, count(ChatTags.BADGE))
    }
}
