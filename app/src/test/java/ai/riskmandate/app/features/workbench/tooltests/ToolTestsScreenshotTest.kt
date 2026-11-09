package ai.riskmandate.app.features.workbench.tooltests

import ai.riskmandate.app.FeatureFrame
import ai.riskmandate.app.R
import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.experiments.ExperimentsTestData
import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmEvent
import ai.riskmandate.app.llm.LlmRole
import ai.riskmandate.app.llm.StrictJson
import ai.riskmandate.app.ui.FeatureContent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * Writes app/build/screenshots/workbench-tooltests-<state>-tablet-light.png for review against design's mock
 * (docs/design/workbench/workbench-tooltests-light.png). The model is a FakeLlmService that always answers with
 * the weather case's reference call, so some cases pass and some fail: the words are scripted, the screen is real.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolTestsScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val cat = ExperimentsTestData.toolCatalogue()
    private val weather = cat.case("right-tool-weather")!!

    private fun model() = FakeLlmService(reply = { req ->
        val s = weather.reference!![req.messages.count { it.role == LlmRole.ASSISTANT }.coerceIn(0, weather.reference!!.lastIndex)]
        if (req.tools.isNotEmpty()) FakeReply(s.text, s.calls.map { LlmEvent.ToolCallProposed(it.name, StrictJson.render(it.arguments)) })
        else FakeReply((listOf(s.text).filter { it.isNotBlank() } + s.calls.map { StrictJson.render(mapOf("tool" to it.name, "arguments" to it.arguments)) }).joinToString("\n"))
    })

    private fun click(text: String, exact: Boolean = false) = compose.onNode(hasText(text, substring = !exact) and hasClickAction()).performClick()

    @Test fun afterARun() {
        val out = File("build/screenshots").apply { mkdirs() }
        RuntimeEnvironment.setQualifiers("w1280dp-h900dp-land-notnight-xhdpi")
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.setContent {
                    RiskMandateTheme(darkTheme = false) { FeatureFrame(R.string.workbench_title, {}) { FeatureContent { ToolTestsTab(model(), catalogue = cat) } } }
                }
            }
            compose.waitForIdle()
            click("1", exact = true)
            compose.onNodeWithTag(ToolTestTags.RUN).performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag(ToolTestTags.STOP).fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithTag(ToolTestTags.RUN).fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            click("right-tool-weather")
            compose.waitForIdle()
            var bitmap: Bitmap? = null
            scenario.onActivity { a ->
                val v = a.window.decorView
                bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
            }
            val f = File(out, "workbench-tooltests-run-tablet-light.png")
            f.outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(f.length() > 1000)
        }
    }
}
