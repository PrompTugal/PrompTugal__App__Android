package ai.riskmandate.app.features.lab

import ai.riskmandate.app.llm.LlmMessage
import ai.riskmandate.app.llm.LlmRequest
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.Sampling
import kotlinx.coroutines.CancellationException

/**
 * Live runs on the on-device model. The service is the app's one loaded model and serialises requests, so the
 * raw lane and the ABP lane can run side by side: each is one [runPath] and their requests interleave.
 *
 * The model proposes, code decides: the service never runs a tool. Each step's text and native proposals go to
 * [PathRun], which checks every call (per-turn caps, the shared `checkToolCall`, then the ABP checks on the ABP
 * path) before a fake tool runs. Model output is plain text only; it is shown as text and never parsed as
 * anything but a tool call.
 */
object LiveRunner {
    /** Settings of a live run. Defaults are the shared service's; the settings panel changes them. */
    data class Settings(val sampling: Sampling = Sampling.DEFAULT, val maxTokens: Int = 512)

    /** Longest tool result handed back to the model, to keep each request inside the context budget. */
    const val MAX_RESULT_CHARS = 2_000

    sealed interface Result {
        /** The path finished: [record] is the full path with its score, [modelId] and [backend] as the service reported. */
        data class Done(val record: PathRecord, val modelId: String?, val backend: String?, val totalMs: Long) : Result
        /** The run did not finish. It does not count in any counter. */
        data class Failed(val reason: String) : Result
    }

    /**
     * Runs one path of [scenario] (raw or [abp]). [onUpdate] gets the path so far after every model step, on
     * the caller's context, so a screen can show steps as they happen. Cancelling the coroutine stops the model.
     */
    suspend fun runPath(
        llm: LlmService,
        scenario: Scenario,
        abp: Boolean,
        settings: Settings = Settings(),
        now: () -> Long = System::currentTimeMillis,
        onUpdate: (PathRecord) -> Unit = {},
    ): Result {
        val run = PathRun(scenario, abp)
        val tools = ToolProtocol.llmTools(scenario.tools)
        val messages = mutableListOf(LlmMessage.system(scenario.system), LlmMessage.user(scenario.user))
        val t0 = now()
        var modelId: String? = null
        var backend: String? = null
        try {
            while (!run.done) {
                val res = llm.generate(LlmRequest(messages.toList(), tools, settings.sampling, settings.maxTokens))
                res.error?.let { return Result.Failed("${it.reason.name.lowercase().replace('_', ' ')}") }
                res.stats?.let { modelId = it.modelId; backend = it.backend }
                val text = res.text
                run.step(text, now() - t0, res.toolCalls)
                onUpdate(run.snapshot())
                if (run.done) break
                messages += LlmMessage.assistant(text.take(ToolProtocol.MAX_TEXT_CHARS), res.toolCalls)
                for ((tool, result) in run.lastResults) messages += LlmMessage.toolResult(tool, result.take(MAX_RESULT_CHARS))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.Failed("run error")
        }
        val total = now() - t0
        return Result.Done(run.finish(total), modelId, backend, total)
    }

    /** The user's own instruction, for interactive mode. At most [MAX_INJECT_CHARS], shown to the visitor as typed. */
    const val MAX_INJECT_CHARS = 280

    /**
     * The scenario with [typed] added to the document the model reads: appended to what the scenario's trap tool
     * returns when that is plain text, otherwise to the task as an added note. Nothing else changes (tools,
     * policy and failure rule are the scenario's own). Blank [typed] returns the scenario unchanged.
     */
    fun withInjected(scenario: Scenario, typed: String): Scenario {
        val t = typed.trim().take(MAX_INJECT_CHARS)
        if (t.isEmpty()) return scenario
        val trap = scenario.exposureTools.firstOrNull { (scenario.fakeTools[it]?.returns as? String) != null }
        if (trap != null) {
            val spec = scenario.fakeTools.getValue(trap)
            return scenario.copy(fakeTools = scenario.fakeTools + (trap to spec.copy(returns = (spec.returns as String) + " " + t)))
        }
        return scenario.copy(user = scenario.user + "\n\nAdditional note in the document: " + t)
    }
}
