package ai.riskmandate.app.llm

import org.json.JSONArray
import org.json.JSONObject

/** Argument types a tool may take. Flat only: no objects or arrays (architect: short names, flat arguments). */
enum class ParamType { STRING, INTEGER, NUMBER, BOOLEAN }

data class ToolParam(
    val name: String,
    val type: ParamType,
    val description: String,
    val required: Boolean = true,
    /** For [ParamType.STRING]: the longest value accepted. */
    val maxLength: Int = 500,
    /** For [ParamType.STRING]: if not empty, the only values accepted. */
    val allowed: List<String> = emptyList(),
)

/**
 * A tool a feature offers the model, declared in Kotlin. The model only ever sees [schemaJson]; what it proposes
 * comes back as [LlmEvent.ToolCallProposed] and is checked with [check] (or [checkToolCall]) before anything runs.
 */
data class ToolSpec(val name: String, val description: String, val params: List<ToolParam> = emptyList()) {
    init {
        require(NAME.matches(name)) { "tool name '$name': lowercase letters, digits and _ only, 1..40" }
        params.forEach { require(NAME.matches(it.name)) { "parameter name '${it.name}' in $name" } }
        require(params.map { it.name }.toSet().size == params.size) { "duplicate parameter in $name" }
    }

    /** OpenAPI-style function description, the shape LiteRT-LM and Gemma's tool format expect. */
    fun schemaJson(): String {
        val properties = JSONObject()
        params.forEach { p ->
            val prop = JSONObject()
                .put("type", p.type.name.lowercase())
                .put("description", p.description)
            if (p.allowed.isNotEmpty()) prop.put("enum", JSONArray(p.allowed))
            properties.put(p.name, prop)
        }
        val parameters = JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(params.filter { it.required }.map { it.name }))
        return JSONObject().put("name", name).put("description", description).put("parameters", parameters).toString()
    }

    /** Checks one proposal's arguments against this tool. Nothing in [argsJson] is trusted. */
    fun check(argsJson: String): ToolCheck {
        val obj = try {
            StrictJson.parseFlatObject(argsJson.ifBlank { "{}" })
        } catch (e: StrictJson.JsonException) {
            return ToolCheck.Rejected(name, "arguments are not a flat JSON object: ${category(e.message ?: "")}")
        }
        val known = params.associateBy { it.name }
        val extra = obj.keys.filter { it !in known }
        if (extra.isNotEmpty()) return ToolCheck.Rejected(name, "unknown argument (${extra.size})")
        val args = LinkedHashMap<String, Any>()
        for (p in params) {
            val raw = obj[p.name]
            if (raw == null) {
                if (p.required) return ToolCheck.Rejected(name, "missing argument: ${p.name}")
                continue
            }
            val value: Any = when (p.type) {
                ParamType.STRING -> {
                    val s = raw as? String ?: return wrongType(p)
                    if (s.length > p.maxLength) return ToolCheck.Rejected(name, "${p.name} longer than ${p.maxLength} characters")
                    if (p.allowed.isNotEmpty() && s !in p.allowed) return ToolCheck.Rejected(name, "${p.name} is not one of the allowed values")
                    s
                }
                ParamType.INTEGER -> raw as? Long ?: return wrongType(p)
                ParamType.NUMBER -> (raw as? Number)?.toDouble() ?: return wrongType(p)
                ParamType.BOOLEAN -> raw as? Boolean ?: return wrongType(p)
            }
            args[p.name] = value
        }
        return ToolCheck.Accepted(name, args)
    }

    /** A fixed phrase for a parser error: the parser's own message can quote a key the model wrote. */
    private fun category(message: String) = when {
        "duplicate key" in message -> "repeated key"
        "nested" in message || "nesting" in message -> "nested values are not allowed"
        "longer than" in message -> "longer than $MAX_ARGS_CHARS characters"
        "after the value" in message -> "text after the object"
        "not a JSON object" in message -> "not a JSON object"
        else -> "not valid JSON"
    }

    private fun wrongType(p: ToolParam) = ToolCheck.Rejected(name, "${p.name} should be ${p.type.name.lowercase()}")

    companion object {
        private val NAME = Regex("^[a-z][a-z0-9_]{0,39}$")
        const val MAX_ARGS_CHARS = 4096
    }
}

sealed interface ToolCheck {
    val tool: String
    /** Typed values: String, Long (integer), Double (number), Boolean. Only declared parameters. */
    data class Accepted(override val tool: String, val args: Map<String, Any>) : ToolCheck
    /** Never run it. [reason] is safe to show (it never echoes argument values). */
    data class Rejected(override val tool: String, val reason: String) : ToolCheck
}

/** Matches a proposal to the feature's own allow-list by exact name, then checks its arguments. */
fun checkToolCall(tools: List<ToolSpec>, call: LlmEvent.ToolCallProposed): ToolCheck {
    val spec = tools.firstOrNull { it.name == call.name }
        ?: return ToolCheck.Rejected(call.name.take(40), "not an offered tool")
    return spec.check(call.argsJson)
}
