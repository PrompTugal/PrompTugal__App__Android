package ai.riskmandate.app.llm

import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolCall
import com.google.ai.edge.litertlm.tool
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject

/**
 * [LlmRuntime] on LiteRT-LM (litertlm-android, see [LiteRtBench.RUNTIME]). One fresh runtime conversation per
 * request, so requests never share history. Tools are described to the model but never executed here:
 * automatic tool calling is off, and proposals come back as [LlmEvent.ToolCallProposed].
 */
class LiteRtRuntime(private val cacheDir: File) : LlmRuntime {
    @OptIn(ExperimentalApi::class)
    override fun load(file: File, backend: BenchBackend, maxTokens: Int): LoadedModel {
        cacheDir.mkdirs()
        val engine = Engine(
            EngineConfig(
                modelPath = file.absolutePath,
                backend = if (backend == BenchBackend.GPU) Backend.GPU() else Backend.CPU(),
                maxNumTokens = maxTokens,
                cacheDir = cacheDir.absolutePath,
            ),
        )
        ExperimentalFlags.enableBenchmark = true
        try {
            engine.initialize()
        } catch (e: Exception) {
            runCatching { engine.close() }
            throw e
        } finally {
            ExperimentalFlags.enableBenchmark = false
        }
        return Loaded(engine, backend)
    }

    private class Loaded(private val engine: Engine, override val backend: BenchBackend) : LoadedModel {
        @OptIn(ExperimentalApi::class)
        override fun stream(request: LlmRequest, modelId: String): Flow<LlmEvent> = flow {
            val system = request.messages.filter { it.role == LlmRole.SYSTEM }.joinToString("\n\n") { it.text }
            val turns = request.messages.filter { it.role != LlmRole.SYSTEM }
            if (turns.isEmpty()) {
                emit(LlmEvent.Error(LlmError.INFERENCE_FAILED, "a request needs a user, assistant or tool message"))
                return@flow
            }
            ExperimentalFlags.enableBenchmark = true
            val conversation = try {
                engine.createConversation(
                    ConversationConfig(
                        systemInstruction = if (system.isEmpty()) null else Contents.of(system),
                        initialMessages = turns.dropLast(1).map(::toMessage),
                        tools = request.tools.map { spec -> tool(NotExecuted(spec)) },
                        samplerConfig = request.settings.let { s ->
                            s.seed?.let { SamplerConfig(topK = s.topK, topP = s.topP, temperature = s.temperature, seed = it) }
                                ?: SamplerConfig(topK = s.topK, topP = s.topP, temperature = s.temperature)
                        },
                        automaticToolCalling = false,
                    ),
                )
            } finally {
                ExperimentalFlags.enableBenchmark = false
            }
            conversation.use { c ->
                val start = SystemClock.elapsedRealtime()
                var ttft: Long? = null
                var out = 0
                try {
                    c.sendMessageAsync(toMessage(turns.last()), maxOutputToken = request.maxTokens).collect { m ->
                        if (ttft == null) ttft = SystemClock.elapsedRealtime() - start
                        val text = m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                        if (text.isNotEmpty()) {
                            out++
                            emit(LlmEvent.Token(text))
                        }
                        m.toolCalls.forEach { call -> emit(LlmEvent.ToolCallProposed(call.name, JSONObject(call.arguments).toString())) }
                    }
                } catch (e: CancellationException) {
                    runCatching { c.cancelProcess() }
                    throw e
                }
                val info = runCatching { c.getBenchmarkInfo() }.getOrNull()
                emit(
                    LlmEvent.Done(
                        LlmStats(
                            modelId = modelId, backend = backend.name,
                            promptTokens = info?.lastPrefillTokenCount, outputTokens = info?.lastDecodeTokenCount ?: out,
                            prefillTokensPerSecond = info?.lastPrefillTokensPerSecond, decodeTokensPerSecond = info?.lastDecodeTokensPerSecond,
                            timeToFirstTokenMs = ttft, totalMs = SystemClock.elapsedRealtime() - start,
                        ),
                    ),
                )
            }
        }

        override fun close() = engine.close()
    }

    /** Describes a tool to the model. Never runs: automatic tool calling is off, so the runtime does not call it. */
    private class NotExecuted(private val spec: ToolSpec) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = spec.schemaJson()
        override fun execute(paramsJsonString: String): String = """{"error":"tools are not executed by the model service"}"""
    }

    companion object {
        fun toMessage(m: LlmMessage): Message = when (m.role) {
            LlmRole.SYSTEM -> Message.system(m.text)
            LlmRole.USER -> Message.user(m.text)
            LlmRole.ASSISTANT -> Message.model(
                Contents.of(m.text),
                m.toolCalls.map { ToolCall(it.name, flatArgs(it.argsJson)) },
                emptyMap(),
            )
            LlmRole.TOOL -> Message.tool(Contents.of(Content.ToolResponse(m.toolName ?: "tool", m.text)))
        }

        /** A replayed call's arguments; anything that is not a strict flat object is replayed as empty. */
        private fun flatArgs(json: String): Map<String, Any> =
            runCatching { StrictJson.parseFlatObject(json) }.getOrDefault(emptyMap()).filterValues { it != null }.mapValues { it.value!! }
    }
}
