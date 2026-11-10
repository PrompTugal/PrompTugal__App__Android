package ai.riskmandate.app.features.lab

import ai.riskmandate.app.experiments.FakeTools
import ai.riskmandate.app.experiments.Effect
import ai.riskmandate.app.experiments.FakeSpec
import ai.riskmandate.app.experiments.FormatException
import ai.riskmandate.app.experiments.Graph
import ai.riskmandate.app.experiments.GraphNode
import ai.riskmandate.app.experiments.ToolFormats
import ai.riskmandate.app.experiments.Triplet
import ai.riskmandate.app.llm.StrictJson

/**
 * Reads eval's three file formats with [StrictJson] (no JSON library): scenarios `abp-eval-scenario/1`,
 * the demo graph `abp-eval-graph/1` and replay recordings `abp-lab-recording/1` (eval/FORMATS.md).
 * Anything that does not fit is a [FormatException]; the caller skips that file and says so.
 */
object LabFormats {
    const val SCENARIO = "abp-eval-scenario/1"
    const val GRAPH = "abp-eval-graph/1"
    const val RECORDING = "abp-lab-recording/1"

    /** Fields that do not change what the model sees or the policy, so editing them keeps the hash. */
    private val HASH_EXCLUDED = setOf("exposure", "title", "description", "brief_ref", "i18n")

    /** Longest regex pattern a scenario may carry. Scenarios are bundled with the app; this bounds them if that ever changes. */
    const val MAX_PATTERN_CHARS = 200
    const val MAX_PATTERNS = 20

    private fun fail(msg: String): Nothing = throw FormatException(msg)

    private fun parseObject(text: String): Map<String, Any?> =
        try { StrictJson.parse(text) as? Map<String, Any?> ?: fail("not a JSON object") }
        catch (e: StrictJson.JsonException) { fail(e.message ?: "bad JSON") }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.obj(k: String): Map<String, Any?> = this[k] as? Map<String, Any?> ?: fail("'$k' must be an object")
    private fun Map<String, Any?>.optObj(k: String): Map<String, Any?>? = if (k in this) obj(k) else null
    private fun Map<String, Any?>.str(k: String): String = this[k] as? String ?: fail("'$k' must be text")
    private fun Map<String, Any?>.optStr(k: String): String? = this[k] as? String
    private fun Map<String, Any?>.list(k: String): List<Any?> = this[k] as? List<Any?> ?: fail("'$k' must be a list")
    private fun Map<String, Any?>.strings(k: String): List<String> =
        if (k !in this) emptyList() else list(k).map { it as? String ?: fail("'$k' must hold text") }
    @Suppress("UNCHECKED_CAST")
    private fun Any?.asObj(what: String): Map<String, Any?> = this as? Map<String, Any?> ?: fail("$what must be an object")

    private inline fun <reified E : Enum<E>> enumOf(v: String, what: String): E =
        enumValues<E>().firstOrNull { it.name.equals(v, ignoreCase = true) } ?: fail("unknown $what '$v'")

    /** The language key under `i18n` for a device language: only "pt" has translations so far (pt-PT); anything else reads the English. */
    private fun i18nKey(lang: String) = if (lang.lowercase().startsWith("pt")) "pt-PT" else null

    /** The `i18n` block of a file for [lang]: translated strings that replace the English ones when present. English is the fallback for anything missing. */
    private fun translations(m: Map<String, Any?>, lang: String): Map<String, Any?>? =
        i18nKey(lang)?.let { k -> m.optObj("i18n")?.optObj(k) }

    fun parseScenario(text: String, lang: String = "en"): Scenario {
        val m = parseObject(text)
        val tr = translations(m, lang)
        if (m["format"] != SCENARIO) fail("format must be $SCENARIO")
        if (m["synthetic"] != true) fail("scenario data must be marked synthetic: true")
        val id = m.str("id")
        val tools = ToolFormats.parseTools(m.list("tools"), id, requireEffect = true)
        val fakes = ToolFormats.parseFakes(m.obj("fake_tools"))
        for (t in tools) if (t.name !in fakes) fail("$id: tool ${t.name} has no fake_tools entry")
        val pol = m.obj("policy")
        val rules = pol.list("rules").map { r ->
            val o = r.asObj("rule")
            Rule(
                id = o.str("id"), check = enumOf(o.str("check"), "check"), onViolation = enumOf(o.str("on_violation"), "on_violation"),
                reason = o.optStr("reason") ?: "", tools = o.strings("tools").toSet(), tool = o.optStr("tool"), arg = o.optStr("arg"),
                allowDomains = o.strings("allow_domains").toSet(), patterns = o.strings("patterns"),
            )
        }
        val f = m.obj("failure")
        val failure = Failure(
            type = enumOf(f.str("type"), "failure type"), tools = f.strings("tools").toSet(),
            effects = f.strings("effects").map { enumOf<Effect>(it, "effect") }.toSet(), patterns = f.strings("patterns"),
            heuristic = f["heuristic"] == true,
        )
        for (p in rules.flatMap { it.patterns } + failure.patterns) {
            if (p.length > MAX_PATTERN_CHARS) fail("$id: a pattern is longer than $MAX_PATTERN_CHARS characters")
            try { Regex(p) } catch (e: Exception) { fail("$id: a pattern is not a valid expression") }
        }
        if (rules.sumOf { it.patterns.size } + failure.patterns.size > MAX_PATTERNS) fail("$id: more than $MAX_PATTERNS patterns")
        val maxSteps = (m["max_steps"] as? Long)?.toInt()?.takeIf { it >= 1 } ?: fail("$id: max_steps must be a positive integer")
        return Scenario(
            id = id, version = (m["version"] ?: "").toString(), hash = StrictJson.hash16(m.filterKeys { it !in HASH_EXCLUDED }),
            title = tr?.optStr("title") ?: m.str("title"), description = tr?.optStr("description") ?: m.optStr("description") ?: "", system = m.str("system"), user = m.str("user"),
            tools = tools, fakeTools = fakes, graphPath = m.optStr("graph"), graph = null,
            secrets = m.strings("secrets"), deviceState = m.optObj("device_state"),
            policy = Policy(pol.optStr("name") ?: "", pol.optStr("note") ?: "", rules), failure = failure, maxSteps = maxSteps,
            exposureTools = m.optObj("exposure")?.strings("tools")?.toSet() ?: emptySet(),
        )
    }

    fun parseGraph(text: String): Graph {
        val m = parseObject(text)
        if (m["format"] != GRAPH || m["synthetic"] != true) fail("not a synthetic $GRAPH graph")
        return Graph(
            m.list("nodes").map { n -> n.asObj("node").let { GraphNode(it.str("id"), it.str("name"), it.optStr("type") ?: "") } },
            m.list("triplets").map { t -> t.asObj("triplet").let { Triplet(it.str("id"), it.str("s"), it.str("p"), it.str("o")) } },
        )
    }

    fun parseRecording(text: String, lang: String = "en"): Recording {
        val m = parseObject(text)
        val tr = translations(m, lang)
        if (m["format"] != RECORDING) fail("format must be $RECORDING")
        val sc = m.obj("scenario")
        val src = m.obj("source")
        val model = src.optObj("model")
        return Recording(
            id = m.str("id"), title = m.optStr("title") ?: m.str("id"), label = m.optStr("label") ?: "",
            scenarioId = sc.str("id"), scenarioHash = sc.optStr("hash") ?: "",
            sourceKind = src.str("kind"), backend = src.optStr("backend") ?: "",
            modelLabel = model?.optStr("label") ?: model?.optStr("id"),
            settings = src.optObj("settings") ?: emptyMap(), recordedAt = src.optStr("recorded_at"),
            raw = path(m.obj("raw"), tr?.get("raw")), abp = path(m.obj("abp"), tr?.get("abp")),
        )
    }

    /**
     * One path's events. [translated], when given, is a list with one entry per event (null for none): an object that may
     * hold `text`, `reason` and `result`, the translations of what that event shows. A list of another length is ignored
     * whole, so a stale translation can never put words on the wrong step.
     */
    private fun path(o: Map<String, Any?>, translated: Any? = null): PathRecord {
        val oc = o.obj("outcome")
        val events = o.list("events").map(::event)
        val tr = (translated as? List<*>)?.takeIf { it.size == events.size }
        return PathRecord(
            events.mapIndexed { i, e -> (tr?.get(i) as? Map<*, *>)?.let { t -> translate(e, t) } ?: e },
            Outcome(oc["failure"] == true, oc["attempted"] == true, oc["caught"] == true, oc["heuristic"] == true),
        )
    }

    private fun translate(e: LabEvent, t: Map<*, *>): LabEvent {
        fun s(k: String) = (t[k] as? String)?.takeIf { it.isNotBlank() }
        return when (e) {
            is LabEvent.ModelText -> e.copy(text = s("text") ?: e.text)
            is LabEvent.Final -> e.copy(text = s("text") ?: e.text)
            is LabEvent.Flag -> e.copy(text = s("text") ?: e.text, reason = s("reason") ?: e.reason)
            is LabEvent.VerdictEvent -> e.copy(reason = s("reason") ?: e.reason)
            is LabEvent.ToolResult -> e.copy(result = s("result") ?: e.result)
            is LabEvent.CallProposed -> e
        }
    }

    private fun event(e0: Any?): LabEvent {
        val e = e0.asObj("event")
        val t = (e["t_ms"] as? Long) ?: 0L
        return when (val type = e.str("type")) {
            "model_text" -> LabEvent.ModelText(t, e.str("text"))
            "tool_call" -> LabEvent.CallProposed(t, e.str("name"), e["arguments"] as? Map<String, Any?> ?: emptyMap(), e["valid"] != false)
            "verdict" -> LabEvent.VerdictEvent(t, e.str("action"), e.optStr("rule"), e.optStr("reason"))
            "tool_result" -> LabEvent.ToolResult(t, e.str("name"), e["executed"] == true, FakeTools.asText(e["result"]))
            "flag" -> LabEvent.Flag(t, e.str("rule"), (e["claim"] as? Long)?.toInt() ?: 0, e.optStr("reason") ?: "", e.optStr("text") ?: "")
            "final" -> LabEvent.Final(t, e.optStr("text") ?: "")
            else -> fail("unknown event type '$type'")
        }
    }

    /** Rebuilds a [RunTrace] from a path's events, so a recording can be rescored by [Oracle]. */
    fun traceOf(events: List<LabEvent>): RunTrace {
        val calls = ArrayList<CallTrace>()
        val flags = ArrayList<FinalFlag>()
        var final = ""
        var pending: LabEvent.CallProposed? = null
        var verdict: Verdict? = null
        fun close(executed: Boolean, result: String) {
            val p = pending ?: return
            calls += CallTrace(p.name, p.arguments, p.valid, verdict, executed, result)
            pending = null; verdict = null
        }
        for (e in events) when (e) {
            is LabEvent.CallProposed -> { close(false, ""); pending = e; verdict = null }
            is LabEvent.VerdictEvent -> verdict = when (e.action) {
                "block" -> Verdict.Block(e.rule ?: "", e.reason ?: "")
                "hold" -> Verdict.Hold(e.rule ?: "", e.reason ?: "")
                else -> Verdict.Allow
            }
            is LabEvent.ToolResult -> close(e.executed, e.result)
            is LabEvent.Flag -> flags += FinalFlag(e.rule, e.claim, e.reason, e.text)
            is LabEvent.Final -> final = e.text
            is LabEvent.ModelText -> Unit
        }
        close(false, "")
        return RunTrace(calls, final, flags)
    }
}
