package ai.riskmandate.app.features.lab

import ai.riskmandate.app.llm.LlmEvent
import ai.riskmandate.app.llm.ParamType
import ai.riskmandate.app.llm.StrictJson
import ai.riskmandate.app.llm.ToolCheck
import ai.riskmandate.app.llm.ToolParam
import ai.riskmandate.app.llm.checkToolCall
import ai.riskmandate.app.llm.ToolSpec as LlmToolSpec

/**
 * Reading a model's tool calls: strict, bounded, never trusted. The model replies with one JSON object per
 * call, {"tool": "name", "arguments": {...}} (or Gemma's own call syntax). This reader (a port of eval's
 * protocol.py) finds the calls in the text under the Lab's own caps (text scanned, calls parsed, calls per
 * turn); each call is then checked by the app's shared [checkToolCall] against the scenario's tools (architect
 * A-lab-7: missing, extra, wrongly typed and oversize arguments are refused there, once), and only then by
 * [AbpChecks]. A call refused anywhere is a recorded refusal and never runs.
 */
object ToolProtocol {
    /** Longest model text scanned for calls. */
    const val MAX_TEXT_CHARS = 16_000
    /** Calls read from one turn at all; the rest of the text is ignored. */
    const val MAX_PARSED_CALLS = 16
    /** Calls allowed to proceed in one turn; later ones are refused as invalid. */
    const val MAX_CALLS_PER_TURN = 4
    const val MAX_ARGS = 6
    const val MAX_ARG_CHARS = 2_000
    const val MAX_NAME_CHARS = 64
    /** How many "{" positions are tried when looking for JSON in text (bounds the work). */
    private const val MAX_BRACE_TRIES = 64

    /**
     * A call as found. [argsJson], when set, is the exact argument text the model produced (a native proposal
     * from the service); otherwise the arguments were read from text and are rendered back to JSON to check.
     */
    data class RawCall(val name: String, val arguments: Any?, val argsJson: String? = null)

    /** A native proposal from the service. Its arguments are shown if small and flat enough to read; they are never trusted. */
    fun fromProposal(p: LlmEvent.ToolCallProposed): RawCall {
        val shown = if (p.argsJson.length <= 4096) runCatching { StrictJson.parse(p.argsJson.ifBlank { "{}" }) }.getOrNull() else null
        return RawCall(p.name.take(MAX_NAME_CHARS), shown ?: emptyMap<String, Any?>(), p.argsJson)
    }

    private val GEMMA_CALL = Regex("<\\|tool_call>\\s*call:\\s*([A-Za-z_]\\w*)\\s*\\((.*?)\\)\\s*<tool_call\\|>", RegexOption.DOT_MATCHES_ALL)
    private val KV = Regex("([A-Za-z_]\\w*)\\s*=\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|<\\|\"\\|>(.*?)<\\|\"\\|>|([^,\\s)]+))", RegexOption.DOT_MATCHES_ALL)

    /** The calls found in [text0], at most [MAX_PARSED_CALLS], in the order they appear in the Gemma-then-JSON scan. */
    fun extractCalls(text0: String): List<RawCall> {
        val text = if (text0.length > MAX_TEXT_CHARS) text0.substring(0, MAX_TEXT_CHARS) else text0
        val calls = ArrayList<RawCall>()
        for (m in GEMMA_CALL.findAll(text)) {
            if (calls.size >= MAX_PARSED_CALLS) return calls
            val args = LinkedHashMap<String, Any?>()
            for (k in KV.findAll(m.groupValues[2])) {
                if (args.size > MAX_ARGS) break
                args[k.groupValues[1]] = listOf(k.groups[2], k.groups[3], k.groups[4]).firstNotNullOf { it?.value }
            }
            calls += RawCall(m.groupValues[1], args)
        }
        var i = 0
        var tries = 0
        while (calls.size < MAX_PARSED_CALLS && tries < MAX_BRACE_TRIES) {
            i = text.indexOf('{', i)
            if (i < 0) break
            tries++
            val parsed = runCatching { StrictJson.parseAt(text, i) }.getOrNull()
            if (parsed == null) { i++; continue }
            i = parsed.second
            val obj = parsed.first as? Map<*, *> ?: continue
            val name = (obj["tool"] as? String) ?: (obj["name"] as? String) ?: continue
            val args = obj["arguments"] ?: obj["args"] ?: obj["parameters"] ?: emptyMap<String, Any?>()
            calls += RawCall(name.trim().removeSuffix("()"), args)
        }
        return calls
    }

    /** The scenario's tools as the shared contract's [LlmToolSpec]s (string arguments capped at [MAX_ARG_CHARS]). */
    fun llmTools(tools: List<ToolSpec>): List<LlmToolSpec> = tools.map { t ->
        LlmToolSpec(t.name, t.description, t.params.map { p ->
            val type = when (p.type) {
                "string" -> ParamType.STRING
                "integer" -> ParamType.INTEGER
                "number" -> ParamType.NUMBER
                "boolean" -> ParamType.BOOLEAN
                else -> throw IllegalArgumentException("parameter '${p.name}' of ${t.name}: type must be string, integer, number or boolean")
            }
            ToolParam(p.name, type, p.name, p.required, maxLength = MAX_ARG_CHARS)
        })
    }

    /** Checks one found call: this turn's call cap first, then the shared [checkToolCall]. [index] is its place in the turn. */
    fun check(call: RawCall, index: Int, tools: List<LlmToolSpec>): ToolCheck {
        if (index >= MAX_CALLS_PER_TURN) return ToolCheck.Rejected(call.name.take(MAX_NAME_CHARS), "too many calls in one turn (limit $MAX_CALLS_PER_TURN)")
        return checkToolCall(tools, LlmEvent.ToolCallProposed(call.name, call.argsJson ?: StrictJson.render(call.arguments)))
    }

    /** An accepted call as the flat strings the ABP checks read. */
    fun toCall(accepted: ToolCheck.Accepted): ToolCall =
        ToolCall(accepted.tool, accepted.args.mapValues { it.value.toString() })
}
