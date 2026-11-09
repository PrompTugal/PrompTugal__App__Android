package ai.riskmandate.app.features.models

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.ManagedConfig
import ai.riskmandate.app.Management
import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.features.HoldIdleWhile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import ai.riskmandate.app.llm.ModelCatalog
import ai.riskmandate.app.llm.ModelFiles
import ai.riskmandate.app.llm.ModelInstaller
import ai.riskmandate.app.llm.ModelPrefs
import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ModelSetupScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val e2b = ModelCatalog.GEMMA4_E2B
    private val e4b = ModelCatalog.GEMMA4_E4B
    private val dir get() = ModelFiles.internalDir(app).apply { mkdirs() }

    /** Never touches the network: any connection fails, so a tap on Download ends in a reported error. */
    private val offline = ModelInstaller(ModelFiles.internalDir(app), open = { _: URL -> throw IOException("offline") }) { Long.MAX_VALUE }

    private fun show(changed: () -> Unit = {}) = compose.setContent {
        RiskMandateTheme {
            ModelSetupScreen(installer = offline, adbDir = { ModelFiles.adbDir(app) }, onModelsChanged = { changed() }, io = Dispatchers.Unconfined)
        }
    }

    /** An installed, checked model without hashing gigabytes: a file of the pinned size plus a valid marker. */
    private fun fakeInstalled(spec: ai.riskmandate.app.llm.ModelSpec) {
        val f = File(dir, spec.fileName)
        java.io.RandomAccessFile(f, "rw").use { it.setLength(spec.sizeBytes) }
        ModelFiles.markVerified(f, spec)
    }

    @Test fun theFeatureShowsModelSetupWithThePrivacySentence() {
        compose.setContent {
            RiskMandateTheme {
                ModelsFeature.Content(FeatureContext(onBack = {}, config = ManagedConfig.DEFAULT, device = DeviceStatus(Management.NOT_DETECTED, null)))
            }
        }
        compose.onNodeWithTag(ModelSetupTags.PRIVACY)
            .assertTextEquals("Downloads a model file once from huggingface.co (and its download servers, hf.co). Nothing you type leaves the device.")
    }

    @Test fun theIdleResetIsHeldWhileADownloadRuns() {
        var holds = 0
        var released = 0
        val slow = ModelInstaller(ModelFiles.internalDir(app), open = { _: URL -> Thread.sleep(300); throw IOException("offline") }) { Long.MAX_VALUE }
        compose.setContent {
            RiskMandateTheme {
                val ctx = FeatureContext(onBack = {}, config = ManagedConfig.DEFAULT, device = DeviceStatus(Management.NOT_DETECTED, null), holdIdle = { holds++; { released++ } })
                var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                ctx.HoldIdleWhile(busy)
                ModelSetupScreen(installer = slow, adbDir = { null }, onBusy = { busy = it }, io = Dispatchers.Unconfined)
            }
        }
        waitFor(ModelSetupTags.download(e2b.id))
        compose.onNodeWithTag(ModelSetupTags.download(e2b.id)).performClick()
        waitFor(ModelSetupTags.message(e2b.id))
        assertEquals(1, holds)
        assertEquals(1, released)
    }

    @Test fun deletingThroughTheFeatureRefreshesTheHostsService() {
        fakeInstalled(e2b)
        val host = ai.riskmandate.app.llm.LlmHost({ dir }, object : ai.riskmandate.app.llm.LlmRuntime {
            override fun load(file: File, backend: ai.riskmandate.app.llm.BenchBackend, maxTokens: Int) = throw IllegalStateException("not in this test")
        })
        kotlinx.coroutines.runBlocking { host.refresh() }
        assertEquals(ai.riskmandate.app.llm.LlmState.Idle(e2b.id), host.state.value)
        // ModelsContent is ModelsFeature.Content with the file work run in step: no waiting on IO.
        compose.setContent {
            RiskMandateTheme {
                ModelsContent(
                    FeatureContext(onBack = {}, config = ManagedConfig.DEFAULT, device = DeviceStatus(Management.NOT_DETECTED, null), llm = host),
                    io = Dispatchers.Unconfined,
                )
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        compose.onNodeWithTag(ModelSetupTags.delete(e2b.id)).performScrollTo().performClick()
        compose.onNodeWithTag(ModelSetupTags.CONFIRM_DELETE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        assertFalse(File(dir, e2b.fileName).exists())
        // The refresh runs on the host's own dispatcher: wait on the state itself, not on a clock.
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(10_000) { host.state.first { it == ai.riskmandate.app.llm.LlmState.NoModel } }
        }
    }

    @Test fun thePrivacyLineSaysWhatGoesToTheCloudWhileACloudModelIsInUse() {
        var cloud by androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            RiskMandateTheme { ModelSetupScreen(installer = offline, adbDir = { null }, cloudInUse = cloud, io = Dispatchers.Unconfined) }
        }
        compose.onNodeWithTag(ModelSetupTags.PRIVACY).assertTextContains("Nothing you type leaves the device.", substring = true)
        cloud = true
        compose.waitForIdle()
        compose.onNodeWithTag(ModelSetupTags.PRIVACY).assertTextContains("goes to openrouter.ai", substring = true)
        assertFalse(compose.onNodeWithTag(ModelSetupTags.PRIVACY).fetchSemanticsNode().config.toString().contains("Nothing you type leaves the device"))
    }

    @Test fun listsTheOfferedModelsWithSizeAndLicence() {
        show()
        waitFor(ModelSetupTags.row(e2b.id))
        compose.onNode(hasText("2.58 GB, licence apache-2.0") and hasAnyAncestor(hasTestTag(ModelSetupTags.row(e2b.id)))).assertExists()
        compose.onNode(hasText("3.65 GB, licence apache-2.0") and hasAnyAncestor(hasTestTag(ModelSetupTags.row(e4b.id)))).assertExists()
        compose.onNodeWithTag(ModelSetupTags.state(e2b.id)).assertTextEquals("Not installed")
        assertTrue("gated models are not offered", compose.onAllNodes(hasTestTag(ModelSetupTags.row(ModelCatalog.GEMMA3_1B.id))).fetchSemanticsNodes().isEmpty())
    }

    @Test fun aFailedDownloadIsExplainedNotThrown() {
        show()
        waitFor(ModelSetupTags.download(e2b.id))
        compose.onNodeWithTag(ModelSetupTags.download(e2b.id)).performClick()
        waitFor(ModelSetupTags.message(e2b.id))
        compose.onNodeWithTag(ModelSetupTags.message(e2b.id)).assertTextContains("Resume download", substring = true)
    }

    @Test fun anAdbFileOffersImport() {
        File(ModelFiles.adbDir(app)!!, e2b.fileName).writeBytes(ByteArray(8))
        show()
        waitFor(ModelSetupTags.import(e2b.id))
        compose.onNodeWithTag(ModelSetupTags.import(e2b.id)).assertIsDisplayed()
    }

    @Test fun twoInstalledModelsCanBeSwitchedAndOneDeleted() {
        fakeInstalled(e2b)
        fakeInstalled(e4b)
        var changes = 0
        show { changes++ }
        waitFor(ModelSetupTags.use(e4b.id))
        compose.onNodeWithTag(ModelSetupTags.state(e2b.id)).assertTextEquals("Installed, checked and in use")
        compose.onNodeWithTag(ModelSetupTags.use(e4b.id)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(e4b.id, ModelPrefs.preferred(app))
        compose.onNodeWithTag(ModelSetupTags.delete(e4b.id)).performScrollTo().performClick()
        compose.onNodeWithTag(ModelSetupTags.CONFIRM_DELETE).performClick()
        val end = System.currentTimeMillis() + 5_000
        while (File(dir, e4b.fileName).exists() && System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idle(); compose.waitForIdle() }
        assertFalse(File(dir, e4b.fileName).exists())
        assertEquals(null, ModelPrefs.preferred(app))
        assertTrue(changes >= 2)
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
}
