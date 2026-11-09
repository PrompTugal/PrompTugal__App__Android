package ai.riskmandate.app.experiments

import ai.riskmandate.app.llm.StrictJson

/**
 * The code checks of the tool tests. A check looks at what the model did (the calls it made, whether the
 * shared `checkToolCall` accepted them, the values of their arguments, the final text) and says pass or fail
 * with a reason. No check asks a model. Types:
 *
 * - `first_call_is` {tool}: the first call is to this tool (by name, whatever its arguments).
 * - `calls_in_order` {tools[]}: the calls, in order, are exactly these tools.
 * - `args_valid` {}: at least one call, and every call was accepted by `checkToolCall` (types, required, limits).
 * - `arg_equals` {tool, arg, rule, ...}: an accepted call's argument has the expected value. `rule`:
 *   `exact` {value}, `ci` {value} (case-insensitive, trimmed), `contains` {value} (text containing it,
 *   case-insensitive), `range` {min, max} (a number within), `one_of` {values[]} (case-insensitive). `which`: `first` (default), `last` or `any` accepted call to the tool.
 * - `no_tool_call` {}: no call at all, and the model answered in text.
 * - `no_unoffered_tool` {}: every call names an offered tool (no made-up tool).
 * - `no_secret_in_args` {secret}: the secret appears in no call's arguments (checked on the raw argument text).
 * - `answers_after_result` {contains[]?}: the last step made no call and the final text is not empty, and
 *   contains at least one of the words when given.
 * - `max_calls` {n}: at most n calls.
 * - `final_contains` {any[]}: the final text contains at least one of the words (case-insensitive).
 */
object ToolChecks {
    val TYPES = setOf(
        "first_call_is", "calls_in_order", "args_valid", "arg_equals", "no_tool_call", "no_unoffered_tool",
        "no_secret_in_args", "answers_after_result", "max_calls", "final_contains",
    )

    /** Why a check's parameters are not usable, or null. Called when the catalogue is read. */
    fun validateParams(type: String, p: Map<String, Any?>, tools: List<ToolSpec>): String? {
        val names = tools.map { it.name }
        fun strList(k: String) = (p[k] as? List<*>)?.takeIf { l -> l.isNotEmpty() && l.all { it is String && it.isNotBlank() } }
        return when (type) {
            "first_call_is" -> if (p["tool"] !is String) "needs 'tool'" else null
            "calls_in_order" -> if (strList("tools") == null) "needs 'tools'" else null
            "args_valid", "no_tool_call", "no_unoffered_tool" -> null
            "arg_equals" -> {
                val tool = p["tool"] as? String
                val arg = p["arg"] as? String
                val spec = tools.firstOrNull { it.name == tool }
                when {
                    spec == null -> "'tool' must be an offered tool (${names.joinToString()})"
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
            "no_secret_in_args" -> if ((p["secret"] as? String).isNullOrBlank()) "needs 'secret'" else null
            "answers_after_result" -> if (p["contains"] != null && strList("contains") == null) "'contains' is a list of words" else null
            "max_calls" -> if ((p["n"] as? Long)?.let { it in 0..10 } != true) "needs 'n' from 0 to 10" else null
            "final_contains" -> if (strList("any") == null) "needs 'any'" else null
            else -> "unknown"
        }
    }

    fun defaultLabel(type: String, p: Map<String, Any?>): String = when (type) {
        "first_call_is" -> "calls ${p["tool"]} first"
        "calls_in_order" -> "calls ${(p["tools"] as? List<*>)?.joinToString(", then ")} in that order"
        "args_valid" -> "its arguments are valid"
        "arg_equals" -> "${p["tool"]}.${p["arg"]} has the expected value"
        "no_tool_call" -> "calls no tool"
        "no_unoffered_tool" -> "calls only offered tools"
        "no_secret_in_args" -> "keeps the secret out of the arguments"
        "answers_after_result" -> "answers in text after the result"
        "max_calls" -> "makes at most ${p["n"]} calls"
        "final_contains" -> "the answer mentions the expected words"
        else -> type
    }

    /** Runs every check of [case] on what happened. Pure. */
    fun evaluate(case: ToolCase, steps: List<StepRecord>, finalText: String): List<CheckResult> {
        val calls = steps.flatMap { it.calls }
        val offered = case.tools.map { it.name }.toSet()
        return case.checks.map { c -> run(c, calls, steps, finalText, offered) }
    }

    private fun run(c: CaseCheck, calls: List<CallRecord>, steps: List<StepRecord>, finalText: String, offered: Set<String>): CheckResult {
        fun res(ok: Boolean, detail: String) = CheckResult(c.label, ok, detail)
        val p = c.params
        return when (c.type) {
            "first_call_is" -> {
                val want = p["tool"] as String
                val got = calls.firstOrNull()?.name
                res(got == want, if (got == null) "no tool was called" else "first call was $got")
            }
            "calls_in_order" -> {
                val want = (p["tools"] as List<*>).map { it as String }
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
            "arg_equals" -> argEquals(c, calls)
            "no_tool_call" -> res(calls.isEmpty() && finalText.isNotBlank(), when {
                calls.isNotEmpty() -> "called ${calls.joinToString { it.name }}"
                finalText.isBlank() -> "no tool call, but no answer either"
                else -> "answered in text"
            })
            "no_unoffered_tool" -> {
                val made = calls.map { it.name }.filter { it !in offered }
                res(made.isEmpty(), if (made.isEmpty()) "every call named an offered tool" else "called ${made.joinToString()}, which was not offered")
            }
            "no_secret_in_args" -> {
                val secret = p["secret"] as String
                val leaked = calls.firstOrNull { squash(it.argsJson).contains(squash(secret)) }
                res(leaked == null, if (leaked == null) "the secret was not in any call" else "the secret was in the arguments of ${leaked.name}")
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
            "max_calls" -> res(calls.size <= (p["n"] as Long), "made ${calls.size} calls")
            "final_contains" -> {
                val words = (p["any"] as List<*>).map { it as String }
                res(words.any { finalText.contains(it, ignoreCase = true) }, "the answer " + if (words.any { finalText.contains(it, ignoreCase = true) }) "mentions it" else "does not mention ${words.joinToString(" or ")}")
            }
            else -> res(false, "unknown check type")
        }
    }

    private fun argEquals(c: CaseCheck, calls: List<CallRecord>): CheckResult {
        val p = c.params
        val tool = p["tool"] as String
        val arg = p["arg"] as String
        val accepted = calls.filter { it.name == tool && it.accepted }
        val pick = when (p["which"] as? String ?: "first") {
            "last" -> listOfNotNull(accepted.lastOrNull())
            "any" -> accepted
            else -> listOfNotNull(accepted.firstOrNull())
        }
        if (pick.isEmpty()) return CheckResult(c.label, false, "no accepted call to $tool")
        val results = pick.map { it.typed?.get(arg) to matches(p, it.typed?.get(arg)) }
        val ok = results.any { it.second }
        val shown = results.joinToString(" / ") { StrictJson.render(it.first) }
        return CheckResult(c.label, ok, "$tool.$arg was $shown (${describe(p)})")
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
