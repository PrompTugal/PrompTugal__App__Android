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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * "Ask our data" end to end on the screen: the real asker (BM25 over the bundled data, no network) with a fake
 * on-device model, through the host's two fields. Nothing here touches a network.
 */
@Config(qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class ChatDataScreenTest {
    @get:Rule val compose = createComposeRule()

    /** A service that says CLOUD, and the fake behind it, so a test can see whether anything reached it. */
    private fun cloudPair(): Pair<LlmService, FakeLlmService> {
        val inner = FakeLlmService(reply = { FakeReply("cloud answer") })
        return object : LlmService by inner { override val location = LlmLocation.CLOUD } to inner
    }

    private fun show(local: LlmService?, cloud: LlmService? = null) = compose.setContent {
        val ctx = FeatureContext({}, ManagedConfig.DEFAULT, DeviceStatus.from(false, false, null), llm = local, cloudLlm = cloud)
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
    private fun inSources(s: String) =
        compose.onAllNodes(hasText(s, substring = true) and hasAnyAncestor(hasTestTag(ChatTags.sources(1)))).fetchSemanticsNodes().size

    private fun ask(question: String) {
        tag(ChatTags.ASK_DATA).performClick()
        tag(ChatTags.ASK_DATA_BANNER).assertIsDisplayed()
        tag(ChatTags.INPUT).performTextInput(question)
        tag(ChatTags.SEND).performClick()
        compose.waitUntil(15_000) { count(ChatTags.sources(1)) == 1 }
    }

    @Test fun aDataAnswerShowsItsSourcesAndTheLabelAndOpensNothing() {
        val local = FakeLlmService(reply = { FakeReply("RiskMandate gives agents access, not free rein [1].") })
        show(local)
        loaded()
        ask("what is RiskMandate?")
        // The question went to the on-device model, once, as numbered sources plus the question.
        assertEquals(1, local.requests.size)
        assertTrue(local.requests.single().messages.last().text.contains("Question: what is RiskMandate?"))
        assertTrue("the source [1] is listed as plain text", inSources("[1]") > 0)
        assertTrue(compose.onAllNodes(hasText("our data", substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()) // "On device, <model>, our data"
        assertEquals("a source is never a button", 0, count(ChatTags.link(0)))
        assertEquals("armed for one question only", 0, count(ChatTags.ASK_DATA_BANNER))
    }

    @Test fun aMadeUpCitationNumberIsShownAsNotARealSource() {
        show(FakeLlmService(reply = { FakeReply("Yes, it is so [9].") }))
        loaded()
        ask("what is RiskMandate?")
        assertTrue(inSources("Not a real source: [9]") > 0)
    }

    @Test fun anIDoNotKnowAnswerHasNoSourcesAndNoCredit() {
        show(FakeLlmService(reply = { FakeReply("I do not know that from our data.") }))
        loaded()
        ask("what is the airspeed of a swallow?")
        assertEquals(0, inSources("From pt.news"))
        assertEquals(0, inSources("["))
    }

    @Test fun aCloudServiceIsNeverUsedForADataQuestion() {
        val local = FakeLlmService(reply = { FakeReply("RiskMandate gives agents access [1].") })
        val (cloud, cloudInner) = cloudPair()
        show(local, cloud)
        loaded()
        ask("what is RiskMandate?")
        assertEquals("the on-device model answered", 1, local.requests.size)
        assertEquals("the cloud saw nothing", 0, cloudInner.requests.size)
    }

    @Test fun withoutAnOnDeviceModelThereIsNoDataQuestion() {
        show(null)
        loaded()
        assertEquals(0, count(ChatTags.ASK_DATA))
    }

    @Test fun theAskerRefusesAServiceThatIsNotLocal() {
        assertThrows(IllegalArgumentException::class.java) {
            GroundedDataAsker(cloudPair().first, androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().assets)
        }
    }
}
