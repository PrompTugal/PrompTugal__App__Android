package ai.riskmandate.app.qa

import ai.riskmandate.app.experiments.ExperimentsTestData
import ai.riskmandate.app.experiments.RefStep
import ai.riskmandate.app.experiments.ToolCase
import ai.riskmandate.app.experiments.ToolMode
import ai.riskmandate.app.experiments.ToolMsgCall
import ai.riskmandate.app.experiments.ToolTestRunner
import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmEvent
import ai.riskmandate.app.llm.StrictJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mutation tests of the tool tests (QA owns this file; plan WB-T, T-4). A pass rate means nothing if a check cannot
 * fail. For every case of the bundled catalogue, in both modes (native and text), this takes the case's own
 * reference run (which must pass) and generates:
 *  - MUTANTS: wrong runs derived from it, each of which must FAIL (a survivor means a check has a hole);
 *  - NON-MUTANTS: harmless changes the case's rules allow, each of which must still PASS (a casualty means a check is
 *    too strict).
 * A mutant is only demanded to fail when the case has a check that is supposed to catch it (the expectation is derived
 * from the case's own check list, never hardcoded per case), so a new case is covered the day it is added.
 */
class ToolTestMutationTest {
    private val cat = ExperimentsTestData.toolCatalogue()

    private fun scripted(steps: List<RefStep>, mode: ToolMode): FakeLlmService {
        var n = 0
        return FakeLlmService(reply = {
            val s = steps[minOf(n++, steps.lastIndex)]
            if (mode == ToolMode.NATIVE) FakeReply(s.text, s.calls.map { c -> LlmEvent.ToolCallProposed(c.name, StrictJson.render(c.arguments)) })
            else FakeReply((listOf(s.text).filter { it.isNotBlank() } + s.calls.map { c -> StrictJson.render(mapOf("tool" to c.name, "arguments" to c.arguments)) }).joinToString("\n"))
        })
    }

    private fun passes(case: ToolCase, mode: ToolMode, steps: List<RefStep>): Boolean =
        runBlocking { ToolTestRunner.runCase(scripted(steps, mode), case, mode) }.let { it.finished && it.passed }

    private class Variant(val name: String, val steps: List<RefStep>)

    private fun mapCalls(steps: List<RefStep>, f: (ToolMsgCall) -> ToolMsgCall?): List<RefStep> =
        steps.map { s -> RefStep(s.text, s.calls.mapNotNull(f)) }

    private fun hasCheck(case: ToolCase, vararg types: String) = case.checks.any { it.type in types }

    private fun argChecks(case: ToolCase) = case.checks.filter { it.type == "arg_equals" }

    /** A value of the right type that the rule of [p] does not accept. */
    private fun outside(p: Map<String, Any?>, type: String?): Any? = when (p["rule"]) {
        "range" -> (p["max"] as Number).toDouble() + 1000.0
        "exact" -> when (val v = p["value"]) { is Number -> v.toDouble() + 1000.0; is Boolean -> !v; else -> "zz-not-this" }
        else -> "zz-not-this"
    }.let { v -> if (v is Double && (type == "integer")) v.toLong() else v }

    private fun mutants(case: ToolCase): List<Variant> {
        val ref = case.reference!!
        val out = ArrayList<Variant>()
        val refCalls = ref.flatMap { it.calls }
        val offered = case.tools.map { it.name }
        if (refCalls.isNotEmpty()) {
            if (hasCheck(case, "first_call_is", "calls_in_order", "no_unoffered_tool", "arg_equals", "args_valid"))
                out += Variant("a made-up tool name", mapCalls(ref) { it.copy(name = "zz_not_offered") })
            if (hasCheck(case, "first_call_is", "calls_in_order", "arg_equals", "args_valid", "final_contains", "answers_after_result"))
                out += Variant("no call at all", ref.map { RefStep("I will not use a tool.", emptyList()) })
            for (other in offered.distinct()) {
                val firstName = refCalls.first().name
                if (other != firstName && hasCheck(case, "first_call_is", "calls_in_order", "arg_equals"))
                    out += Variant("the first call goes to $other instead", ref.mapIndexed { si, s -> RefStep(s.text, s.calls.mapIndexed { ci, c -> if (si == ref.indexOfFirst { it.calls.isNotEmpty() } && ci == 0) c.copy(name = other) else c }) })
            }
            for (check in argChecks(case)) {
                val tool = check.params["tool"] as String; val arg = check.params["arg"] as String
                val type = case.tools.firstOrNull { it.name == tool }?.params?.firstOrNull { it.name == arg }?.type
                out += Variant("$tool.$arg outside its rule (${check.params["rule"]})", mapCalls(ref) { c -> if (c.name == tool && arg in c.arguments) c.copy(arguments = c.arguments + (arg to outside(check.params, type))) else c })
            }
            if (hasCheck(case, "args_valid", "arg_equals")) {
                for (tool in refCalls.map { it.name }.distinct()) {
                    val spec = case.tools.firstOrNull { it.name == tool } ?: continue
                    val covered = hasCheck(case, "args_valid") || argChecks(case).any { it.params["tool"] == tool }
                    if (!covered) continue
                    for (p in spec.params.filter { it.required }) {
                        val dependsOnArg = hasCheck(case, "args_valid") || argChecks(case).any { it.params["tool"] == tool && it.params["arg"] == p.name }
                        if (dependsOnArg) out += Variant("$tool without its required ${p.name}", mapCalls(ref) { c -> if (c.name == tool) c.copy(arguments = c.arguments - p.name) else c })
                    }
                    if (hasCheck(case, "args_valid")) {
                        out += Variant("$tool with an extra unknown argument", mapCalls(ref) { c -> if (c.name == tool) c.copy(arguments = c.arguments + ("zzextra" to "x")) else c })
                        val p = spec.params.firstOrNull { it.required && it.type == "string" }
                        if (p != null) out += Variant("$tool.${p.name} of the wrong type", mapCalls(ref) { c -> if (c.name == tool) c.copy(arguments = c.arguments + (p.name to 12345L)) else c })
                    }
                }
            }
            if (hasCheck(case, "calls_in_order", "max_calls")) {
                val lastStep = ref.indexOfLast { it.calls.isNotEmpty() }
                out += Variant("one call too many", ref.mapIndexed { i, s -> if (i == lastStep) RefStep(s.text, s.calls + s.calls.last()) else s })
            }
            if (hasCheck(case, "answers_after_result")) {
                val extra = refCalls.last()
                // Instead of the answering step (the run ends at the first step without a call), not after it.
                val base = if (ref.last().calls.isEmpty()) ref.dropLast(1) else ref
                out += Variant("calls a tool again instead of answering", base + RefStep("", listOf(extra)))
            }
        } else {
            // The reference makes no call (no tool fits, or answer after a result): a call must fail.
            if (hasCheck(case, "no_tool_call", "answers_after_result", "max_calls", "calls_in_order")) {
                val t = case.tools.first()
                val args = t.params.filter { it.required }.associate { p -> p.name to (when (p.type) { "integer" -> 1L; "number" -> 1.0; "boolean" -> true; else -> "x" } as Any?) }
                out += Variant("calls ${t.name} when none should be called", listOf(RefStep("", listOf(ToolMsgCall(t.name, args)))) + ref)
            }
        }
        // The planted-secret cases: the secret in an argument, in the shapes the check says it sees.
        for (check in case.checks.filter { it.type == "no_secret_in_args" }) {
            val secret = check.params["secret"] as String
            val call = refCalls.firstOrNull() ?: ToolMsgCall(case.tools.first().name, emptyMap())
            val spec = case.tools.first { it.name == call.name }
            val argName = spec.params.firstOrNull { it.type == "string" }?.name ?: continue
            fun leak(label: String, value: String) { out += Variant("secret leaked: $label", ref.ifEmpty { listOf(RefStep("", emptyList())) }.let { r ->
                val idx = r.indexOfFirst { it.calls.isNotEmpty() }.takeIf { it >= 0 } ?: 0
                r.mapIndexed { i, s -> if (i == idx) RefStep(s.text, (s.calls.ifEmpty { listOf(call) }).map { it.copy(arguments = it.arguments + (argName to value)) }) else s }
            }) }
            leak("exact", "note $secret end")
            leak("upper case", secret.uppercase())
            leak("lower case", secret.lowercase())
            leak("spaced out", secret.toList().joinToString(" "))
            leak("with hyphens and dots", secret.toList().joinToString("-.-"))
        }
        return out
    }

    /** Changes the rules allow: the case must still pass. */
    private fun nonMutants(case: ToolCase): List<Variant> {
        val ref = case.reference!!
        val out = ArrayList<Variant>()
        out += Variant("arguments in the other key order", mapCalls(ref) { it.copy(arguments = it.arguments.entries.reversed().associate { e -> e.key to e.value }) })
        for (check in argChecks(case)) {
            val tool = check.params["tool"] as String; val arg = check.params["arg"] as String
            fun with(label: String, v: Any?) { out += Variant("$tool.$arg: $label", mapCalls(ref) { c -> if (c.name == tool && arg in c.arguments) c.copy(arguments = c.arguments + (arg to v)) else c }) }
            when (check.params["rule"]) {
                "ci" -> { val v = check.params["value"] as String; with("upper case", v.uppercase()); with("padded with spaces", "  $v  ") }
                "range" -> { val t = case.tools.first { it.name == tool }.params.first { it.name == arg }.type
                    fun n(x: Number): Any = if (t == "integer") x.toLong() else x.toDouble()
                    with("at the minimum", n(check.params["min"] as Number)); with("at the maximum", n(check.params["max"] as Number)) }
                "one_of" -> (check.params["values"] as List<*>).forEach { with("member '$it'", it as String) }
                "contains" -> with("longer text around it", "please " + check.params["value"] + " now")
            }
        }
        return out
    }

    @Test fun everyReferenceRunPassesInBothModes() {
        for (c in cat.cases) for (m in ToolMode.entries) assertTrue("${c.id} $m reference should pass", passes(c, m, c.reference!!))
    }

    @Test fun everyMutantFailsInBothModes() {
        val survivors = ArrayList<String>()
        var total = 0
        for (c in cat.cases) for (mode in ToolMode.entries) for (v in mutants(c)) {
            total++
            if (passes(c, mode, v.steps)) survivors += "${c.id} [$mode]: ${v.name}"
        }
        println("MUTANTS: $total generated across ${cat.cases.size} cases and 2 modes; survivors=${survivors.size}")
        assertTrue("the mutant generator produced only $total mutants: it is not exercising the catalogue", total >= 150)
        assertEquals("mutants that still PASS (a check has a hole):\n" + survivors.joinToString("\n"), 0, survivors.size)
    }

    @Test fun harmlessChangesStillPassInBothModes() {
        val casualties = ArrayList<String>()
        var total = 0
        for (c in cat.cases) for (mode in ToolMode.entries) for (v in nonMutants(c)) {
            total++
            if (!passes(c, mode, v.steps)) casualties += "${c.id} [$mode]: ${v.name}"
        }
        println("NON-MUTANTS: $total generated; casualties=${casualties.size}")
        assertTrue("only $total non-mutants generated", total >= 40)
        assertEquals("harmless changes that now FAIL (a check is too strict):\n" + casualties.joinToString("\n"), 0, casualties.size)
    }
}
