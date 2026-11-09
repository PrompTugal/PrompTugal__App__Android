package ai.riskmandate.app.features.lab

/**
 * Reading a model's tool calls: strict, bounded, never trusted. The model replies with one JSON object per
 * call, {"tool": "name", "arguments": {...}} (or Gemma's own call syntax); every call is parsed with
 * [StrictJson], matched by name to the scenario's fixed tool list, its arguments type-checked and
 * length-capped, and refused otherwise. An unparseable or unknown call is a recorded refusal, never run.
 * A port of eval's protocol.py plus the caps security asked for (R-lab-1).
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

    data class RawCall(val name: String, val arguments: Any?)

    private val NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
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

    /** Null if the call is valid, else a short reason. [index] is its position in this turn. */
    fun validate(call: RawCall, tools: List<ToolSpec>, index: Int): String? {
        if (index >= MAX_CALLS_PER_TURN) return "too many calls in one turn (limit $MAX_CALLS_PER_TURN)"
        if (call.name.length > MAX_NAME_CHARS || !NAME.matches(call.name)) return "tool name is not valid"
        val spec = tools.firstOrNull { it.name == call.name } ?: return "unknown tool '${call.name}'"
        val args = call.arguments as? Map<*, *> ?: return "arguments must be an object"
        if (args.size > MAX_ARGS) return "too many arguments (limit $MAX_ARGS)"
        for (p in spec.params) if (p.required && p.name !in args) return "missing argument '${p.name}'"
        for ((k, v) in args) {
            val p = spec.params.firstOrNull { it.name == k } ?: return "unexpected argument '$k'"
            val typed = when (p.type) {
                "string" -> v is String
                "integer" -> v is Long
                "number" -> v is Long || v is Double
                "boolean" -> v is Boolean
                else -> false
            }
            if (!typed) return "argument '$k' must be ${p.type}"
            if (v is String && v.length > MAX_ARG_CHARS) return "argument '$k' is longer than $MAX_ARG_CHARS characters"
        }
        return null
    }

    /** Valid raw arguments as the flat strings the checks read. Call only after [validate] returned null. */
    fun toCall(call: RawCall): ToolCall =
        ToolCall(call.name, (call.arguments as Map<*, *>).entries.associate { it.key.toString() to it.value.toString() })
}
