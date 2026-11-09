package ai.riskmandate.app.experiments

import ai.riskmandate.app.llm.StrictJson

/**
 * Reads tool-test files (`abp-tooltest/1`): one file per kind, {format, version, kind, title, synthetic, cases[]}.
 * A case: {id, tests, system?, messages[], tools[] (2 to 5), fake_tools{}, checks[], max_steps?, reference?, wrong?}.
 * Tools and fake tools use the same shapes as scenarios (see [LabFormats]); `effect` may be left out. Strict:
 * anything that does not fit is a [FormatException] and the file is skipped and counted.
 */
object ToolTestFormats {
    const val FORMAT = "abp-tooltest/1"
    const val MIN_TOOLS = 2
    const val MAX_TOOLS = 5
    const val MAX_CASES_PER_FILE = 40
    const val MAX_CHECKS = 8
    const val MAX_MESSAGES = 12

    private fun fail(msg: String): Nothing = throw FormatException(msg)

    @Suppress("UNCHECKED_CAST")
    private fun Any?.obj(what: String): Map<String, Any?> = this as? Map<String, Any?> ?: fail("$what must be an object")
    @Suppress("UNCHECKED_CAST")
    private fun Any?.list(what: String): List<Any?> = this as? List<Any?> ?: fail("$what must be a list")
    private fun Map<String, Any?>.str(k: String, what: String = k): String = this[k] as? String ?: fail("'$what' must be text")

    /** One kind's file: its [ToolKind] and cases. */
    fun parse(text: String): Pair<ToolKind, List<ToolCase>> {
        val m = ToolFormats.parseObject(text)
        if (m["format"] != FORMAT) fail("format must be $FORMAT")
        if (m["synthetic"] != true) fail("tool-test data must be marked synthetic: true")
        if ((m["version"] as? String).isNullOrBlank()) fail("'version' is the file's content version, as text")
        val kind = m.str("kind")
        if (!Regex("[a-z][a-z0-9_]{0,39}").matches(kind)) fail("kind '$kind' is not a short lowercase name")
        val title = m.str("title")
        val cases = m["cases"].list("cases")
        if (cases.isEmpty() || cases.size > MAX_CASES_PER_FILE) fail("$kind: between 1 and $MAX_CASES_PER_FILE cases")
        val parsed = cases.map { case(it.obj("case"), kind) }
        if (parsed.map { it.id }.toSet().size != parsed.size) fail("$kind: duplicate case id")
        return ToolKind(kind, title, if ("judges" in m) judgeRefs(m["judges"].list("$kind: judges"), kind) else emptyList()) to parsed
    }

    private val PROMPT_REF = Regex("[a-z0-9][a-z0-9/._-]{0,99}")

    /** The file-level `judges[]`: at most one entry per layer; code takes no model or prompt, the model layers need a prompt_ref. */
    private fun judgeRefs(list: List<*>, kind: String): List<JudgeRef> {
        if (list.size > 3) fail("$kind: judges lists at most three layers")
        val refs = list.map { e ->
            val o = e.obj("$kind: a judges entry")
            val layer = o["layer"] as? String
            if (layer !in setOf("code", "local", "cloud")) fail("$kind: judges layer is code, local or cloud")
            val model = o["model"]?.let { it as? String ?: fail("$kind: judges model is text") }
            if (model != null && (model.isBlank() || model.length > 80)) fail("$kind: judges model is 1 to 80 characters")
            val ref = o["prompt_ref"]?.let { it as? String ?: fail("$kind: judges prompt_ref is text") }
            if (layer == "code") {
                if (model != null || ref != null) fail("$kind: the code layer has no model and no prompt_ref")
            } else {
                if (ref == null || !PROMPT_REF.matches(ref) || ".." in ref || "//" in ref) fail("$kind: the $layer layer needs a prompt_ref (an id or a path of lowercase letters, digits and - _ . /)")
            }
            JudgeRef(layer!!, model, ref)
        }
        if (refs.map { it.layer }.toSet().size != refs.size) fail("$kind: judges lists a layer twice")
        return refs
    }

    private fun case(c: Map<String, Any?>, kind: String): ToolCase {
        val id = c.str("id")
        if (!Regex("[a-z][a-z0-9-]{0,59}").matches(id)) fail("case id '$id' must be lowercase letters, digits and hyphens")
        fun bad(msg: String): Nothing = fail("$id: $msg")
        val tests = c.str("tests")
        if (tests.isBlank() || tests.length > 200 || '\n' in tests) bad("'tests' is one line of at most 200 characters")
        val tools = ToolFormats.parseTools(c["tools"].list("tools"), id, requireEffect = false)
        if (tools.size !in MIN_TOOLS..MAX_TOOLS) bad("offer $MIN_TOOLS to $MAX_TOOLS tools")
        val fakes = ToolFormats.parseFakes(c["fake_tools"].obj("fake_tools"))
        for (t in tools) if (t.name !in fakes) bad("tool ${t.name} has no fake_tools entry")
        (fakes.keys - tools.map { it.name }.toSet()).firstOrNull()?.let { bad("fake_tools has '$it', which is not an offered tool") }
        if (fakes.values.any { it.graph != null }) bad("graph fakes are not used in tool tests")
        val messages = c["messages"].list("messages").map(::message)
        if (messages.isEmpty() || messages.size > MAX_MESSAGES) bad("between 1 and $MAX_MESSAGES messages")
        if (messages.first().role != "user") bad("the first message is the user's")
        val checks = c["checks"].list("checks").map { check(it.obj("check"), tools, id) }
        if (checks.isEmpty() || checks.size > MAX_CHECKS) bad("between 1 and $MAX_CHECKS checks")
        val maxSteps = (c["max_steps"] as? Long)?.toInt() ?: 3
        if (maxSteps !in 1..6) bad("max_steps is 1 to 6")
        return ToolCase(
            id, kind, tests, c["system"] as? String ?: "", messages, tools, fakes, checks, maxSteps,
            (c["reference"] as? Map<*, *>)?.let { steps(it.obj("reference"), id) },
            (c["wrong"] as? Map<*, *>)?.let { steps(it.obj("wrong"), id) },
            (c["judges"] as? Map<*, *>)?.let { judges(it.obj("judges"), id) },
        )
    }

    private fun message(raw: Any?): ToolMsg {
        val o = raw.obj("message")
        val role = o.str("role")
        if (role !in setOf("user", "assistant", "tool")) fail("message role '$role' is not user, assistant or tool")
        val calls = (o["tool_calls"] as? List<*>).orEmpty().map { call(it.obj("tool_call")) }
        if (role == "tool" && o["name"] !is String) fail("a tool message names its tool")
        return ToolMsg(role, o["text"] as? String ?: "", calls, o["name"] as? String)
    }

    private fun call(o: Map<String, Any?>) = ToolMsgCall(o.str("name"), (o["arguments"] as? Map<*, *>)?.let { m -> m.entries.associate { it.key.toString() to it.value } } ?: emptyMap())

    private fun steps(o: Map<String, Any?>, id: String): List<RefStep> {
        val list = o["steps"].list("$id steps")
        if (list.isEmpty() || list.size > 6) fail("$id: a reference or wrong run has 1 to 6 steps")
        return list.map { s -> s.obj("step").let { RefStep(it["text"] as? String ?: "", (it["calls"] as? List<*>).orEmpty().map { c -> call(c.obj("call")) }) } }
    }

    private fun judges(o: Map<String, Any?>, id: String): JudgeSpec {
        val ask = o["ask"] as? String
        if (ask.isNullOrBlank() || ask.length > 400) fail("$id: judges.ask is one question of at most 400 characters")
        val expect = o["expect"] as? String
        if (expect !in setOf("pass", "fail")) fail("$id: judges.expect is pass or fail")
        return JudgeSpec(ask, expect!!)
    }

    private fun check(o: Map<String, Any?>, tools: List<ToolSpec>, id: String): CaseCheck {
        val kind = o.str("kind")
        if (kind !in ToolChecks.KINDS) fail("$id: unknown check '$kind'")
        val params = o.filterKeys { it != "kind" && it != "label" }
        ToolChecks.validateParams(kind, params, tools)?.let { fail("$id: check $kind: $it") }
        return CaseCheck(kind, params, o["label"] as? String ?: ToolChecks.defaultLabel(kind, params))
    }

    /** Hash of the catalogue's content, as the Lab hashes a scenario, so a result names the exact cases it ran. */
    fun hash(files: List<String>): String = StrictJson.hash16(files.sorted().map { StrictJson.parse(it) })
}
