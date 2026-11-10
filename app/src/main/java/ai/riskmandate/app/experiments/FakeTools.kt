package ai.riskmandate.app.experiments

import ai.riskmandate.app.llm.StrictJson

/**
 * Fake tools: they return canned data from the scenario and touch nothing real (no network, file, intent,
 * clipboard or account). Graph tools read the scenario's bundled demo graph. A port of eval's fake_tools.py.
 */
object FakeTools {
    /** The same, for anything that carries fake specs and an optional graph (a scenario, a tool-test case). */
    fun execute(fakes: Map<String, FakeSpec>, graph: Graph?, name: String, args: Map<String, String>): Any? {
        val spec = fakes[name] ?: return "no such tool"
        if (spec.hasReturns) return spec.returns
        if (spec.by != null) return spec.table[args[spec.by]] ?: spec.default
        val g = graph ?: return "no graph"
        return when (spec.graph) {
            "find_node" -> {
                val q = args["name"].orEmpty().lowercase()
                g.nodes.filter { q.isNotEmpty() && q in it.name.lowercase() }
                    .map { mapOf("id" to it.id, "name" to it.name, "type" to it.type) }
                    .ifEmpty { "no node found" }
            }
            "get_neighbours" -> {
                val node = g.nodes.firstOrNull { it.id == args["node_id"] } ?: return "unknown node id"
                g.triplets.filter { node.name == it.s || node.name == it.o }.take(spec.limit)
                    .map { mapOf("id" to it.id, "s" to it.s, "p" to it.p, "o" to it.o) }
            }
            else -> "no such tool"
        }
    }

    /** A result as text: strings as they are, everything else as JSON. */
    fun asText(result: Any?): String = if (result is String) result else StrictJson.render(result)
}
