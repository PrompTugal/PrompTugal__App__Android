package ai.riskmandate.app.features.chat

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.FeatureFrame
import ai.riskmandate.app.ManagedConfig
import ai.riskmandate.app.R
import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmService
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Writes app/build/screenshots/chat-<state>-tablet-light.png for review (the model here is FakeLlmService:
 * the words are scripted, only the screen is real). Same size as the other screens' shots.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun shoot(name: String, service: LlmService?, interact: () -> Unit = {}) {
        val out = File("build/screenshots").apply { mkdirs() }
        RuntimeEnvironment.setQualifiers("w1280dp-h800dp-land-notnight-xhdpi")
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.setContent {
                    val ctx = FeatureContext({}, ManagedConfig.DEFAULT, DeviceStatus.from(false, false, null), llm = service)
                    CompositionLocalProvider(LocalChatStore provides MemoryChatHistoryStore()) {
                        RiskMandateTheme(darkTheme = false) { FeatureFrame(R.string.feature_chat_title, {}) { ChatFeature.Content(ctx) } }
                    }
                }
            }
            // start() loads on a real IO thread: wait for the loaded state, not just for idle.
            compose.waitUntil(10_000) { compose.onAllNodesWithTag(ChatTags.NO_MODEL).fetchSemanticsNodes().size == 1 || compose.onAllNodesWithTag(ChatTags.INPUT).fetchSemanticsNodes().size == 1 }
            compose.waitForIdle()
            interact()
            compose.waitForIdle()
            var bitmap: Bitmap? = null
            scenario.onActivity { a ->
                val v = a.window.decorView
                bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
            }
            val f = File(out, "chat-$name-tablet-light.png")
            f.outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(f.length() > 1000)
        }
    }

    @Test fun noModelState() = shoot("no-model", null)

    @Test fun emptyChatWithPresets() = shoot("empty", FakeLlmService())

    @Test fun streamedAnswer() {
        val text = "RiskMandate gives agents access, not free rein. For each AI agent it records what the agent can reach, " +
            "what the business authorised it to do (the mandate), and the gap between the two. " +
            "Source: riskmandate.ai, https://riskmandate.ai/abp"
        shoot("answer", FakeLlmService(reply = { FakeReply(text) })) {
            compose.onNodeWithTag(ChatTags.preset("riskmandate")).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag(ChatTags.STATS).fetchSemanticsNodes().size == 1 }
        }
    }
}
