package ai.riskmandate.app.experiments

import ai.riskmandate.app.llm.StrictJson

/** Anything in a data file that does not fit its format. The caller skips that file and says so. */
class FormatException(message: String) : Exception(message)

/**
 * Reads the parts of the data files that the Lab and the tool tests share: strict JSON objects, tools (name,
 * description, flat JSON-schema parameters) and fake tools. All through [StrictJson].
 */
object ToolFormats {
    private fun fail(msg: String): Nothing = throw FormatException(msg)

    fun parseObject(text: String): Map<String, Any?> =
        try { StrictJson.parse(text) as? Map<String, Any?> ?: fail("not a JSON object") }
        catch (e: StrictJson.JsonException) { fail(e.message ?: "bad JSON") }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.obj(k: String): Map<String, Any?> = this[k] as? Map<String, Any?> ?: fail("'$k' must be an object")
    private fun Map<String, Any?>.optObj(k: String): Map<String, Any?>? = if (k in this) obj(k) else null
    private fun Map<String, Any?>.str(k: String): String = this[k] as? String ?: fail("'$k' must be text")
    private fun Map<String, Any?>.optStr(k: String): String? = this[k] as? String
    private fun Map<String, Any?>.strings(k: String): List<String> =
        if (k !in this) emptyList() else (this[k] as? List<*> ?: fail("'$k' must be a list")).map { it as? String ?: fail("'$k' must hold text") }
    @Suppress("UNCHECKED_CAST")
    private fun Any?.asObj(what: String): Map<String, Any?> = this as? Map<String, Any?> ?: fail("$what must be an object")

    private inline fun <reified E : Enum<E>> enumOf(v: String, what: String): E =
        enumValues<E>().firstOrNull { it.name.equals(v, ignoreCase = true) } ?: fail("unknown $what '$v'")

    /**
     * Tools as eval writes them (name, description, flat JSON-schema parameters). [requireEffect] false lets a
     * tool-test case leave `effect` out (it is then a plain read: functional tests have no policy). Refuses a
     * tool the shared contract (llm.ToolSpec) would, one with more than [ToolProtocol.MAX_ARGS] parameters, and
     * a duplicate name.
     */
    fun parseTools(list: List<Any?>, id: String, requireEffect: Boolean): List<ToolSpec> {
        val tools = list.map { t ->
            val o = t.asObj("tool")
            val params = o.obj("parameters")
            val props = params.optObj("properties") ?: emptyMap()
            val required = params.strings("required").toSet()
            ToolSpec(
                o.str("name"),
                if (requireEffect || "effect" in o) enumOf(o.str("effect"), "effect") else Effect.READ,
                o.str("description"),
                props.entries.map { (k, v) -> Param(k, v.asObj("parameter").optStr("type") ?: "string", k in required) },
            )
        }
        for (t in tools) if (t.params.size > ToolProtocol.MAX_ARGS) fail("$id: tool ${t.name} has more than ${ToolProtocol.MAX_ARGS} parameters")
        try { ToolProtocol.llmTools(tools) } catch (e: IllegalArgumentException) { fail("$id: ${e.message}") }
        val names = tools.map { it.name }
        if (names.size != names.toSet().size) fail("$id: duplicate tool")
        return tools
    }

    fun parseFakes(raw: Map<String, Any?>): Map<String, FakeSpec> = raw.mapValues { (_, v) ->
        val o = v.asObj("fake tool")
        FakeSpec(
            hasReturns = "returns" in o, returns = o["returns"], by = o.optStr("by"),
            table = o.optObj("table") ?: emptyMap(), default = if ("default" in o) o["default"] else "not found",
            graph = o.optStr("graph"), limit = (o["limit"] as? Long)?.toInt() ?: 25,
        )
    }
}
