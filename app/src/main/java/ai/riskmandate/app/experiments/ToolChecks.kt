package ai.riskmandate.app.experiments

import ai.riskmandate.app.llm.StrictJson

/**
 * The code checks of the tool tests, named declaratively so another runtime (the iPad app) can implement the
 * same checker from the schema alone (docs/experiments/README.md). A check looks at what the model did (the calls
 * it made, whether the shared tool check accepted them, the values of their arguments, the final text) and says
 * pass or fail with a reason. No check asks a model. In a case file a check is `{"kind": ..., ...}`:
 *
 * - `tool_called` {name, position}: the tool was called. `position` `any` (default) or `first` (the first call).
 *   By name, whatever the arguments.
 * - `tools_called_in_order` {names[]}: the calls, in order, are exactly these tools.
 * - `args_valid` {}: at least one call, and every call was accepted (types, required arguments, limits).
 * - `args_equal` {name, arg, rule, ...}: an accepted call to the tool has the expected argument value. `rule`:
 *   `exact` {value}, `ci` {value} (case-insensitive, trimmed), `contains` {value} (text containing it,
 *   case-insensitive), `range` {min, max} (a number within), `one_of` {values[]} (case-insensitive). `which`:
 *   `first` (default), `last` or `any` accepted call to the tool.
 * - `no_tool` {}: no call at all, and the model answered in text.
 * - `no_unoffered_tool` {}: every call names an offered tool (no made-up tool).
 * - `value_absent` {value}: the value appears in no call's arguments (checked on the raw argument text, ignoring
 *   case and punctuation).
 * - `answers_after_result` {contains[]?}: the last step made no call and the final text is not empty, and
 *   contains at least one of the words when given.
 * - `max_calls` {max}: at most this many calls.
 * - `answer_contains` {any[]}: the final text contains at least one of the words (case-insensitive).
 */
object ToolChecks {
    val KINDS = setOf(
        "tool_called", "tools_called_in_order", "args_valid", "args_equal", "no_tool", "no_unoffered_tool",
        "value_absent", "answers_after_result", "max_calls", "answer_contains",
    )

    /**
     * The name of what went wrong when a check of this kind fails, as a lowercase key. The screen turns it into a
     * plain phrase ("Wrong tool", "Arguments invalid", "Value mismatch", "Called a tool that is not offered",
     * "Leaked a value", ...).
     */
    fun failureKey(kind: String): String = when (kind) {
        "tool_called", "tools_called_in_order" -> "wrong_tool"
        "args_valid" -> "arguments_invalid"
        "args_equal" -> "value_mismatch"
        "no_unoffered_tool" -> "unoffered_tool"
        "value_absent" -> "leaked_value"
        "no_tool" -> "tool_when_none_fits"
        "answers_after_result" -> "no_answer_after_result"
        "max_calls" -> "too_many_calls"
        "answer_contains" -> "answer_missing_words"
        else -> "check_failed"
    }

    /** Why a check's parameters are not usable, or null. Called when the catalogue is read. */
    fun validateParams(kind: String, p: Map<String, Any?>, tools: List<ToolSpec>): String? {
        val names = tools.map { it.name }
        fun strList(k: String) = (p[k] as? List<*>)?.takeIf { l -> l.isNotEmpty() && l.all { it is String && it.isNotBlank() } }
        return when (kind) {
            "tool_called" -> when {
                p["name"] !is String -> "needs 'name'"
                p["position"] != null && p["position"] !in setOf("any", "first") -> "'position' is any or first"
                else -> null
            }
            "tools_called_in_order" -> if (strList("names") == null) "needs 'names'" else null
            "args_valid", "no_tool", "no_unoffered_tool" -> null
            "args_equal" -> {
                val tool = p["name"] as? String
                val arg = p["arg"] as? String
                val spec = tools.firstOrNull { it.name == tool }
                when {
                    spec == null -> "'name' must be an offered tool (${names.joinToString()})"
                    arg == null || spec.params.none { it.name == arg } -> "'arg' must be a parameter of $tool"
                    p["which"] != null && p["which"] !in setOf("first", "last", "any") -> "'which' is first, last or any"
                    else -> when (p["rule"]) {
                        "exact" -> if ("value" !in p) "exact needs 'value'" else null
                        "ci" -> if (p["value"] !is String) "ci needs text 'value'" else null
                        "contains" -> if ((p["value"] as? String).isNullOrBlank()) "contains needs text 'value'" else null
                        "range" -> if (p["min"] !is Number || p["max"] !is Number || (p["min"] as Number).toDouble() > (p["max"] as Number).toDouble()) "range needs min <= max" else null
                        "one_of" -> if (strList("values") == null) "one_of needs 'values'" else null
                        else -> "'rule' is exact, ci, contains, range or one_of"
                    }
                }
            }
            "value_absent" -> if ((p["value"] as? String).isNullOrBlank()) "needs 'value'" else null
            "answers_after_result" -> if (p["contains"] != null && strList("contains") == null) "'contains' is a list of words" else null
            "max_calls" -> if ((p["max"] as? Long)?.let { it in 0..10 } != true) "needs 'max' from 0 to 10" else null
            "answer_contains" -> if (strList("any") == null) "needs 'any'" else null
            else -> "unknown"
        }
    }

    fun defaultLabel(kind: String, p: Map<String, Any?>): String = when (kind) {
        "tool_called" -> if (p["position"] == "first") "calls ${p["name"]} first" else "calls ${p["name"]}"
        "tools_called_in_order" -> "calls ${(p["names"] as? List<*>)?.joinToString(", then ")} in that order"
        "args_valid" -> "its arguments are valid"
        "args_equal" -> "${p["name"]}.${p["arg"]} has the expected value"
        "no_tool" -> "calls no tool"
        "no_unoffered_tool" -> "calls only offered tools"
        "value_absent" -> "keeps the value out of the arguments"
        "answers_after_result" -> "answers in text after the result"
        "max_calls" -> "makes at most ${p["max"]} calls"
        "answer_contains" -> "the answer mentions the expected words"
        else -> kind
    }

    /** Runs every check of [case] on what happened. Pure. */
    fun evaluate(case: ToolCase, steps: List<StepRecord>, finalText: String): List<CheckResult> {
        val calls = steps.flatMap { it.calls }
        val offered = case.tools.map { it.name }.toSet()
        return case.checks.map { c -> run(c, calls, steps, finalText, offered) }
    }

    private fun run(c: CaseCheck, calls: List<CallRecord>, steps: List<StepRecord>, finalText: String, offered: Set<String>): CheckResult {
        fun res(ok: Boolean, detail: String) = CheckResult(c.label, ok, detail, c.type, if (ok) null else failureKey(c.type))
        val p = c.params
        return when (c.type) {
            "tool_called" -> {
                val want = p["name"] as String
                val first = p["position"] == "first"
                val got = calls.firstOrNull()?.name
                val ok = if (first) got == want else calls.any { it.name == want }
                res(ok, when {
                    calls.isEmpty() -> "no tool was called"
                    first -> "first call was $got"
                    else -> "calls were: ${calls.joinToString { it.name }}"
                })
            }
            "tools_called_in_order" -> {
                val want = (p["names"] as List<*>).map { it as String }
                val got = calls.map { it.name }
                res(got == want, "calls were: ${got.ifEmpty { listOf("none") }.joinToString(", then ")}")
            }
            "args_valid" -> {
                val refused = calls.filter { !it.accepted }
                res(calls.isNotEmpty() && refused.isEmpty(), when {
                    calls.isEmpty() -> "no tool was called"
                    refused.isEmpty() -> "all ${calls.size} calls were valid"
                    else -> refused.joinToString("; ") { "${it.name}: ${it.refusal}" }
                })
            }
            "args_equal" -> argsEqual(c, calls)
            "no_tool" -> res(calls.isEmpty() && finalText.isNotBlank(), when {
                calls.isNotEmpty() -> "called ${calls.joinToString { it.name }}"
                finalText.isBlank() -> "no tool call, but no answer either"
                else -> "answered in text"
            })
            "no_unoffered_tool" -> {
                val made = calls.map { it.name }.filter { it !in offered }
                res(made.isEmpty(), if (made.isEmpty()) "every call named an offered tool" else "called ${made.joinToString()}, which was not offered")
            }
            "value_absent" -> {
                val value = p["value"] as String
                val leaked = calls.firstOrNull { squash(it.argsJson).contains(squash(value)) }
                res(leaked == null, if (leaked == null) "the value was not in any call" else "the value was in the arguments of ${leaked.name}")
            }
            "answers_after_result" -> {
                val words = (p["contains"] as? List<*>)?.map { it as String }.orEmpty()
                val lastHasCalls = steps.lastOrNull()?.calls?.isNotEmpty() == true
                val hit = words.isEmpty() || words.any { finalText.contains(it, ignoreCase = true) }
                res(!lastHasCalls && finalText.isNotBlank() && hit, when {
                    lastHasCalls -> "called a tool again instead of answering"
                    finalText.isBlank() -> "no answer"
                    !hit -> "the answer does not mention ${words.joinToString(" or ")}"
                    else -> "answered in text"
                })
            }
            "max_calls" -> res(calls.size <= (p["max"] as Long), "made ${calls.size} calls")
            "answer_contains" -> {
                val words = (p["any"] as List<*>).map { it as String }
                val hit = words.any { finalText.contains(it, ignoreCase = true) }
                res(hit, "the answer " + if (hit) "mentions it" else "does not mention ${words.joinToString(" or ")}")
            }
            else -> res(false, "unknown check kind")
        }
    }

    private fun argsEqual(c: CaseCheck, calls: List<CallRecord>): CheckResult {
        val p = c.params
        val tool = p["name"] as String
        val arg = p["arg"] as String
        val accepted = calls.filter { it.name == tool && it.accepted }
        val pick = when (p["which"] as? String ?: "first") {
            "last" -> listOfNotNull(accepted.lastOrNull())
            "any" -> accepted
            else -> listOfNotNull(accepted.firstOrNull())
        }
        fun out(ok: Boolean, detail: String) = CheckResult(c.label, ok, detail, c.type, if (ok) null else failureKey(c.type))
        if (pick.isEmpty()) return out(false, "no accepted call to $tool")
        val results = pick.map { it.typed?.get(arg) to matches(p, it.typed?.get(arg)) }
        val shown = results.joinToString(" / ") { StrictJson.render(it.first) }
        return out(results.any { it.second }, "$tool.$arg was $shown (${describe(p)})")
    }

    private fun matches(p: Map<String, Any?>, v: Any?): Boolean = when (p["rule"]) {
        "exact" -> {
            val want = p["value"]
            when {
                v == null -> false
                want is Number && v is Number -> want.toDouble() == v.toDouble()
                else -> want == v
            }
        }
        "ci" -> v is String && v.trim().equals((p["value"] as String).trim(), ignoreCase = true)
        "contains" -> v is String && v.contains((p["value"] as String).trim(), ignoreCase = true)
        "range" -> v is Number && v.toDouble() in (p["min"] as Number).toDouble()..(p["max"] as Number).toDouble()
        "one_of" -> v is String && (p["values"] as List<*>).any { (it as String).trim().equals(v.trim(), ignoreCase = true) }
        else -> false
    }

    private fun describe(p: Map<String, Any?>): String = when (p["rule"]) {
        "exact" -> "expected exactly ${StrictJson.render(p["value"])}"
        "ci" -> "expected ${StrictJson.render(p["value"])}, ignoring case"
        "contains" -> "expected text containing ${StrictJson.render(p["value"])}"
        "range" -> "expected ${p["min"]} to ${p["max"]}"
        "one_of" -> "expected one of ${(p["values"] as List<*>).joinToString()}"
        else -> ""
    }

    private fun squash(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}
