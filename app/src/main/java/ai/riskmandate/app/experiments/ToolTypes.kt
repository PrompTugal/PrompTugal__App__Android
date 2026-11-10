package ai.riskmandate.app.experiments

/**
 * The tool data shared by the Lab and the Workbench: a tool and its flat arguments, a fake tool's canned behaviour,
 * the small demo graph the fakes can read, and a validated call. Pure data, no Android.
 */
enum class Effect { READ, WRITE, EGRESS }

/** One flat argument of a tool: [type] is "string", "integer", "number" or "boolean". */
data class Param(val name: String, val type: String, val required: Boolean)

data class ToolSpec(val name: String, val effect: Effect, val description: String, val params: List<Param>)

/** A fake tool's canned behaviour. Fakes touch nothing real. */
data class FakeSpec(
    val hasReturns: Boolean = false,
    val returns: Any? = null,
    val by: String? = null,
    val table: Map<String, Any?> = emptyMap(),
    val default: Any? = "not found",
    val graph: String? = null,
    val limit: Int = 25,
)

data class GraphNode(val id: String, val name: String, val type: String)

/** A fact: subject, predicate, object, with the id the model must cite (for example T02). */
data class Triplet(val id: String, val s: String, val p: String, val o: String)

data class Graph(val nodes: List<GraphNode>, val triplets: List<Triplet>)

/** A tool call as the model proposed it, after validation: flat string arguments. */
data class ToolCall(val name: String, val args: Map<String, String> = emptyMap())

