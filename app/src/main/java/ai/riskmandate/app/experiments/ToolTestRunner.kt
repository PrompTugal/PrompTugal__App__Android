package ai.riskmandate.app.experiments

import ai.riskmandate.app.llm.LlmEvent
import ai.riskmandate.app.llm.LlmMessage
import ai.riskmandate.app.llm.LlmRequest
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.StrictJson
import ai.riskmandate.app.llm.ToolCheck
import kotlinx.coroutines.CancellationException

/**
 * Runs tool-test cases on the model. One run: the case's conversation goes to the model; each call it makes is
 * parsed (native proposals as they arrive, or JSON written in the answer in [ToolMode.TEXT]), checked with the
 * same per-turn caps and the shared `checkToolCall` as the Lab ([ToolProtocol.check]), and run on the case's
 * fake tool; the result goes back to the model; this repeats until it answers in text or the step limit.
 * Then [ToolChecks] decide pass or fail. The service never runs a tool, and neither does this: fakes only.
 */
object ToolTestRunner {
    suspend fun runCase(
        llm: LlmService,
        case: ToolCase,
        mode: ToolMode,
        settings: RunSettings = RunSettings(),
        rep: Int = 0,
        now: () -> Long = System::currentTimeMillis,
    ): CaseRun {
        val llmTools = ToolProtocol.llmTools(case.tools)
        val native = mode == ToolMode.NATIVE
        val messages = ArrayList(initialMessages(case, mode))
        val promptChars = PromptSize.chars(case, mode)
        var promptTokens: Int? = null
        val steps = ArrayList<StepRecord>()
        var finalText = ""
        var modelId: String? = null
        var backend: String? = null
        var tokens: Int? = 0
        val rates = ArrayList<Double>()
        val t0 = now()
        fun finish(error: String?) = CaseRun(
            case.id, case.kind, mode, rep, steps.toList(), finalText, now() - t0, modelId, backend,
            tokens, rates.takeIf { it.isNotEmpty() }?.average(), error,
            if (error == null) ToolChecks.evaluate(case, steps, finalText) else emptyList(),
            promptChars, promptTokens,
        )
        try {
            var answered = false
            while (steps.size < case.maxSteps && !answered) {
                val res = llm.generate(LlmRequest(messages.toList(), if (native) llmTools else emptyList(), settings.sampling, settings.maxTokens))
                res.error?.let { return finish(it.reason.name.lowercase().replace('_', ' ')) }
                res.stats?.let { s ->
                    if (steps.isEmpty()) promptTokens = s.promptTokens
                    modelId = s.modelId; backend = s.backend
                    tokens = if (s.outputTokens == null || tokens == null) null else tokens!! + s.outputTokens
                    s.decodeTokensPerSecond?.let { rates += it }
                }
                val text = res.text.take(ToolProtocol.MAX_TEXT_CHARS)
                val raws = (if (native) res.toolCalls.map(ToolProtocol::fromProposal) else ToolProtocol.extractCalls(text)).take(ToolProtocol.MAX_PARSED_CALLS)
                if (raws.isEmpty()) { finalText = text; answered = true; steps += StepRecord(text, emptyList()); break }
                val records = ArrayList<CallRecord>()
                raws.forEachIndexed { i, raw ->
                    val argsJson = raw.argsJson ?: StrictJson.render(raw.arguments)
                    val args = (raw.arguments as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
                    val shown = raw.name.take(ToolProtocol.MAX_NAME_CHARS)
                    when (val checked = ToolProtocol.check(raw, i, llmTools)) {
                        is ToolCheck.Rejected -> records += CallRecord(shown, argsJson.take(ToolProtocol.MAX_ARG_CHARS), args, null, checked.reason, "refused: ${checked.reason}")
                        is ToolCheck.Accepted -> {
                            val flat = checked.args.mapValues { it.value.toString() }
                            val result = FakeTools.asText(FakeTools.execute(case.fakes, null, checked.tool, flat))
                            records += CallRecord(checked.tool, argsJson.take(ToolProtocol.MAX_ARG_CHARS), args, checked.args, null, result)
                        }
                    }
                }
                steps += StepRecord(text, records)
                messages += if (native) LlmMessage.assistant(text.take(ToolProtocol.MAX_TEXT_CHARS), res.toolCalls) else LlmMessage.assistant(text)
                for (r in records) {
                    val back = (r.result ?: "").take(RunSettings.MAX_RESULT_CHARS)
                    messages += if (native) LlmMessage.toolResult(r.name, back) else LlmMessage.user("Tool result from ${r.name}: $back")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return finish("run error")
        }
        return finish(null)
    }

    /** The conversation the model starts from, in the way the mode wants it: the system prompt (with the text protocol in TEXT mode) and the case's messages. */
    fun initialMessages(case: ToolCase, mode: ToolMode): List<LlmMessage> {
        val native = mode == ToolMode.NATIVE
        val system = if (native || case.tools.isEmpty()) case.system else listOf(case.system, ToolProtocol.textInstructions(case.tools)).filter { it.isNotBlank() }.joinToString("\n\n")
        return (if (system.isNotBlank()) listOf(LlmMessage.system(system)) else emptyList()) + case.messages.flatMap { history(it, native) }
    }

    /** A case's starting message in the way the mode wants it: native keeps real tool messages, text writes them out. */
    private fun history(m: ToolMsg, native: Boolean): List<LlmMessage> = when (m.role) {
        "user" -> listOf(LlmMessage.user(m.text))
        "assistant" ->
            if (native) listOf(LlmMessage.assistant(m.text, m.calls.map { LlmEvent.ToolCallProposed(it.name, StrictJson.render(it.arguments)) }))
            else listOf(LlmMessage.assistant((listOf(m.text).filter { it.isNotBlank() } + m.calls.map { StrictJson.render(mapOf("tool" to it.name, "arguments" to it.arguments)) }).joinToString("\n")))
        else -> listOf(if (native) LlmMessage.toolResult(m.tool ?: "tool", m.text) else LlmMessage.user("Tool result from ${m.tool}: ${m.text}"))
    }
}
