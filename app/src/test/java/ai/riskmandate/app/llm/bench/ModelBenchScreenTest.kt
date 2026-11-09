package ai.riskmandate.app.llm.bench

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.ManagedConfig
import ai.riskmandate.app.Management
import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.features.models.ModelsFeature
import ai.riskmandate.app.llm.ModelCatalog
import ai.riskmandate.app.llm.ModelFiles
import android.app.Application
import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import androidx.compose.ui.test.hasTestTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.Dispatchers

@RunWith(AndroidJUnit4::class)
class ModelBenchScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun show() = compose.setContent { RiskMandateTheme { ModelBenchScreen(io = Dispatchers.Unconfined) } }

    @Test fun theFeatureShowsTheBench() {
        compose.setContent {
            RiskMandateTheme {
                ModelsFeature.Content(FeatureContext(onBack = {}, config = ManagedConfig.DEFAULT, device = DeviceStatus(Management.NOT_DETECTED, null)))
            }
        }
        compose.onNodeWithTag(ModelsTags.COPY).performScrollTo().assertIsDisplayed()
    }

    @Test fun staysOffTheHomeScreenUntilItWorksOnTheTablet() = assertFalse(ModelsFeature.enabled)

    @Test fun benchEntryOnlyWithAModelInTheBenchFolderOrOnADebugBuild() {
        assertFalse(ModelFiles.benchAvailable(app, debuggable = false))
        assertTrue(ModelFiles.benchAvailable(app, debuggable = true))
        File(ModelFiles.adbDir(app)!!, "notes.txt").writeText("x")
        assertFalse("only catalogue files count", ModelFiles.benchAvailable(app, debuggable = false))
        File(ModelFiles.adbDir(app)!!, ModelCatalog.GEMMA4_E2B.fileName).writeBytes(ByteArray(1))
        assertTrue(ModelFiles.benchAvailable(app, debuggable = false))
    }

    @Test fun withNoModelItExplainsAdbAndCannotRun() {
        show()
        waitFor(ModelsTags.NONE)
        compose.onNodeWithTag(ModelsTags.NONE).assertIsDisplayed()
        compose.onNodeWithTag(ModelsTags.RUN).assertIsNotEnabled()
        compose.onNodeWithTag(ModelsTags.FOLDER).performScrollTo().assertTextContains(ModelFiles.DIR, substring = true)
    }

    @Test fun aPushedFileIsListedAndCanBeRun() {
        val dir = ModelFiles.adbDir(app)!!
        File(dir, ModelCatalog.GEMMA4_E2B.fileName).writeBytes(ByteArray(16))
        show()
        waitFor(ModelsTags.model(ModelCatalog.GEMMA4_E2B.id))
        compose.onNodeWithTag(ModelsTags.model(ModelCatalog.GEMMA4_E2B.id)).assertIsDisplayed()
        compose.onNodeWithTag(ModelsTags.RUN).assertIsEnabled()
        compose.onNodeWithTag(ModelsTags.GPU).assertIsDisplayed()
        compose.onNodeWithTag(ModelsTags.CPU).assertIsDisplayed()
    }

    @Test fun aTruncatedFileIsRefusedWithoutLoadingTheRuntime() {
        val file = File(ModelFiles.adbDir(app)!!, ModelCatalog.GEMMA4_E4B.fileName).apply { writeBytes(ByteArray(16)) }
        show()
        waitFor(ModelsTags.model(ModelCatalog.GEMMA4_E4B.id))
        compose.onNodeWithTag(ModelsTags.RUN).performClick()
        waitFor(ModelsTags.STATUS)
        compose.onNodeWithTag(ModelsTags.STATUS).assertTextContains("expected size", substring = true)
        assertTrue("a wrong size is not deleted (adb may still be copying)", file.exists())
    }

    @Test fun copyPutsThePlainTextReportOnTheClipboard() {
        show()
        compose.onNodeWithTag(ModelsTags.COPY).performClick()
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertTrue(clip.startsWith("RiskMandate LLM bench"))
        assertTrue("No runs yet." in clip)
    }

    /**
     * Waits for a node that appears after work on Dispatchers.IO. Compose's own waitUntil does not run
     * Robolectric's paused main looper, where the coroutine resumes, so this idles it between checks.
     */
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
}
