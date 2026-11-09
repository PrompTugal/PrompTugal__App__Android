// From qa's probe for c02632a (docs/qa/reviews/2026-11-09-dev.llm-c02632a.probe.kt.txt on work/qa), kept as
// regression tests for Q-llm-1. In JVM tests the LiteRT-LM native library cannot load, which is exactly the
// "runtime cannot start" case.
package ai.riskmandate.app.llm.bench

import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.llm.BenchBackend
import ai.riskmandate.app.llm.FoundModel
import ai.riskmandate.app.llm.LiteRtBench
import ai.riskmandate.app.llm.LiteRtRuntime
import ai.riskmandate.app.llm.LlmError
import ai.riskmandate.app.llm.LlmHost
import ai.riskmandate.app.llm.LlmMessage
import ai.riskmandate.app.llm.LlmRequest
import ai.riskmandate.app.llm.LlmState
import ai.riskmandate.app.llm.ModelCatalog
import ai.riskmandate.app.llm.ModelFiles
import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class QaBenchProbeTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()

    /** Right name, right size, wrong bytes (a sparse file of zeros): must be deleted, never loaded, no crash. */
    @Test fun rightSizeWrongBytesIsDeletedAndNotLoaded() {
        val spec = ModelCatalog.GEMMA4_E2B
        val file = File(ModelFiles.adbDir(app)!!, spec.fileName)
        RandomAccessFile(file, "rw").use { it.setLength(spec.sizeBytes) }
        compose.setContent { RiskMandateTheme { ModelBenchScreen(io = Dispatchers.Unconfined) } }
        waitFor(ModelsTags.model(spec.id))
        compose.onNodeWithTag(ModelsTags.RUN).performClick()
        val end = System.currentTimeMillis() + 120_000
        while (file.exists() && System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idle(); compose.waitForIdle(); Thread.sleep(50) }
        assertFalse("wrong bytes must be deleted", file.exists())
        waitFor(ModelsTags.NONE)
    }

    /** The bench runtime cannot start (native library missing): a reported error, not a crash. */
    @Test fun aRuntimeThatCannotStartIsReportedNotThrown() {
        val file = File(app.cacheDir, "fake.litertlm").apply { writeBytes(ByteArray(16)) }
        var thrown: Throwable? = null
        var error: String? = null
        try {
            error = runBlocking { LiteRtBench.run(app, FoundModel(ModelCatalog.GEMMA4_E2B, file), BenchBackend.CPU, null) { } }.error
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue("LiteRtBench.run threw ${thrown?.javaClass?.name} instead of reporting an error", thrown == null && error != null)
    }

    /** The same for the service: the real LiteRT-LM runtime behind LlmHost fails to start, and that is a state. */
    @Test fun theServiceReportsARuntimeThatCannotStart() = runBlocking {
        val bytes = ByteArray(64) { 3 }
        val dir = File(app.cacheDir, "models-probe").apply { mkdirs() }
        val spec = ModelCatalog.GEMMA4_E2B.copy(fileName = "probe.litertlm", sizeBytes = 64, sha256 = ModelFiles.sha256(bytes.inputStream()))
        File(dir, spec.fileName).writeBytes(bytes)
        val host = LlmHost({ dir }, LiteRtRuntime(File(app.cacheDir, "probe-cache")), listOf(spec))
        val r = host.generate(LlmRequest(listOf(LlmMessage.user("hi"))))
        assertEquals(LlmError.LOAD_FAILED, r.error?.reason)
        assertTrue(host.state.value is LlmState.Failed)
    }

    private fun waitFor(tag: String, limitMs: Long = 10_000) {
        val end = System.currentTimeMillis() + limitMs
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(20)
        }
        fail("no node tagged $tag")
    }
}
