package ai.riskmandate.app.llm

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class BenchBackend { GPU, CPU }

/** What the bench is doing, for the screen. */
sealed interface BenchProgress {
    data object Loading : BenchProgress
    data class Generating(val chunks: Int, val text: String) : BenchProgress
}

/**
 * Runs the fixed bench prompt once through LiteRT-LM: load the engine, stream one answer, read the runtime's
 * benchmark counters, close everything. Each run loads afresh, so load time is measured every time.
 * The only file that touches LiteRT-LM classes (they load a native library), so JVM tests never reach it.
 */
object LiteRtBench {
    /** Keep in step with the dependency in app/build.gradle.kts. */
    const val RUNTIME = "litertlm-android 0.18.1"

    /** Context the engine is sized for: the prompt plus the answer, with room to spare. */
    private const val MAX_TOKENS = 4096

    @OptIn(ExperimentalApi::class)
    suspend fun run(
        context: Context,
        model: FoundModel,
        backend: BenchBackend,
        verifyMs: Long?,
        onProgress: (BenchProgress) -> Unit,
    ): BenchResult = withContext(Dispatchers.Default) {
        val memoryBefore = MemoryProbe.sample(context)
        var loadMs: Long? = null
        var ttftMs: Long? = null
        var generateMs: Long? = null
        var memoryAfterLoad: MemorySample? = null
        var memoryAfterGenerate: MemorySample? = null
        var chunks = 0
        val text = StringBuilder()
        var prefillTokens: Int? = null
        var prefillTps: Double? = null
        var decodeTokens: Int? = null
        var decodeTps: Double? = null
        var error: String? = null

        onProgress(BenchProgress.Loading)
        val cache = File(context.cacheDir, ModelFiles.DIR).apply { mkdirs() }
        val engine = Engine(
            EngineConfig(
                modelPath = model.file.absolutePath,
                backend = if (backend == BenchBackend.GPU) Backend.GPU() else Backend.CPU(),
                maxNumTokens = MAX_TOKENS,
                cacheDir = cache.absolutePath,
            ),
        )
        try {
            val loadStart = SystemClock.elapsedRealtime()
            // The runtime's benchmark counters must be on while the engine and conversation are created.
            ExperimentalFlags.enableBenchmark = true
            val conversation = try {
                engine.initialize()
                loadMs = SystemClock.elapsedRealtime() - loadStart
                memoryAfterLoad = MemoryProbe.sample(context)
                engine.createConversation(
                    // Greedy (top-k 1), so the same model and backend give the same answer each run.
                    ConversationConfig(samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0)),
                )
            } finally {
                ExperimentalFlags.enableBenchmark = false
            }
            conversation.use {
                val start = SystemClock.elapsedRealtime()
                try {
                    conversation.sendMessageAsync(BenchPrompt.TEXT, maxOutputToken = BenchPrompt.MAX_OUTPUT_TOKENS)
                        .collect { message ->
                            if (ttftMs == null) ttftMs = SystemClock.elapsedRealtime() - start
                            chunks++
                            text.append(message.text())
                            onProgress(BenchProgress.Generating(chunks, text.toString()))
                        }
                } catch (e: CancellationException) {
                    conversation.cancelProcess()
                    throw e
                }
                generateMs = SystemClock.elapsedRealtime() - start
                memoryAfterGenerate = MemoryProbe.sample(context)
                try {
                    val info = conversation.getBenchmarkInfo()
                    prefillTokens = info.lastPrefillTokenCount
                    prefillTps = info.lastPrefillTokensPerSecond
                    decodeTokens = info.lastDecodeTokenCount
                    decodeTps = info.lastDecodeTokensPerSecond
                } catch (e: Exception) {
                    // Counters are experimental; wall-clock numbers still stand.
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "${e.javaClass.simpleName}: ${e.message ?: "no message"}".take(300)
        } finally {
            withContext(NonCancellable) {
                try {
                    engine.close()
                } catch (e: Exception) {
                    // Already closed or never initialised.
                }
            }
        }
        BenchResult(
            model = model.spec.displayName,
            fileName = model.spec.fileName,
            backend = backend.name,
            runtime = RUNTIME,
            verifyMs = verifyMs,
            loadMs = loadMs,
            timeToFirstTokenMs = ttftMs,
            generateMs = generateMs,
            prefillTokens = prefillTokens,
            prefillTokensPerSecond = prefillTps,
            decodeTokens = decodeTokens,
            decodeTokensPerSecond = decodeTps,
            streamedChunks = chunks,
            outputChars = text.length,
            memoryBefore = memoryBefore,
            memoryAfterLoad = memoryAfterLoad,
            memoryAfterGenerate = memoryAfterGenerate,
            error = error,
            outputPreview = text.toString().trim().take(160),
        )
    }

    private fun Message.text(): String = contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
}
