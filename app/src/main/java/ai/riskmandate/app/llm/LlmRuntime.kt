package ai.riskmandate.app.llm

import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * The narrow seam between [LlmHost] (state, locking, unloading, model choice) and a native runtime. LiteRT-LM is
 * the one implementation ([LiteRtRuntime]); tests use a fake, so the host is tested without native code.
 */
interface LlmRuntime {
    /** Blocking: loads [file] on [backend]. Throws on failure. Called on the host's own dispatcher. */
    fun load(file: File, backend: BenchBackend, maxTokens: Int): LoadedModel
}

interface LoadedModel : AutoCloseable {
    val backend: BenchBackend

    /**
     * One answer, as Token and ToolCallProposed events, then Done or Error (stats without modelId and load
     * fields; the host fills those in). Cancelling the collector cancels the native call.
     */
    fun stream(request: LlmRequest, modelId: String): Flow<LlmEvent>
}
