package ai.riskmandate.app.qa

import ai.riskmandate.app.experiments.CallRecord
import ai.riskmandate.app.experiments.CaseCheck
import ai.riskmandate.app.experiments.Param
import ai.riskmandate.app.experiments.StepRecord
import ai.riskmandate.app.experiments.ToolCase
import ai.riskmandate.app.experiments.ToolChecks
import ai.riskmandate.app.experiments.ToolSpec
import ai.riskmandate.app.experiments.Effect
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Direct tests of each check rule's edges (QA owns this file; plan WB-T, T-3), written from the documented rules in
 * `ToolChecks`, not from the owner's tests. Found by mutation: a checker that compared `exact` text case-insensitively
 * survived both suites before this file existed.
 */
class ToolChecksRulesTest {
    private val tools = listOf(
        ToolSpec("t", Effect.READ, "d", listOf(Param("s", "string", true), Param("n", "integer", true), Param("b", "boolean", false))),
        ToolSpec("u", Effect.READ, "d", listOf(Param("s", "string", true))),
    )

    private fun case(vararg checks: CaseCheck) = ToolCase("c", "k", "t", "", emptyList(), tools, emptyMap(), checks.toList(), 4, null, null)

    private fun call(name: String, vararg a: Pair<String, Any>, accepted: Boolean = true) =
        CallRecord(name, a.toMap().toString(), a.toMap(), if (accepted) a.toMap() else null, if (accepted) null else "refused", "r")

    private fun passes(check: CaseCheck, calls: List<CallRecord>, finalText: String = ""): Boolean {
        val steps = listOf(StepRecord("", calls)) + if (finalText.isNotEmpty()) listOf(StepRecord(finalText, emptyList())) else emptyList()
        return ToolChecks.evaluate(case(check), steps, finalText).single().passed
    }

    private fun argEq(rule: String, extra: Map<String, Any?>, which: String? = null) =
        CaseCheck("arg_equals", mapOf("tool" to "t", "arg" to extra["arg"], "rule" to rule) + extra + (which?.let { mapOf("which" to it) } ?: emptyMap()), "l")

    @Test fun exactTextIsCaseAndSpaceSensitive() {
        val c = argEq("exact", mapOf("arg" to "s", "value" to "Lisbon"))
        assertEquals(true, passes(c, listOf(call("t", "s" to "Lisbon", "n" to 1L))))
        assertEquals("exact means exact: a different case must fail", false, passes(c, listOf(call("t", "s" to "lisbon", "n" to 1L))))
        assertEquals(false, passes(c, listOf(call("t", "s" to "LISBON", "n" to 1L))))
        assertEquals(false, passes(c, listOf(call("t", "s" to " Lisbon", "n" to 1L))))
    }

    @Test fun exactNumbersCompareByValueNotByType() {
        val c = argEq("exact", mapOf("arg" to "n", "value" to 5L))
        assertEquals(true, passes(c, listOf(call("t", "s" to "x", "n" to 5L))))
        assertEquals(true, passes(c, listOf(call("t", "s" to "x", "n" to 5.0))))
        assertEquals(false, passes(c, listOf(call("t", "s" to "x", "n" to 6L))))
    }

    @Test fun ciIgnoresCaseAndOuterSpacesOnly() {
        val c = argEq("ci", mapOf("arg" to "s", "value" to "Lisbon"))
        assertEquals(true, passes(c, listOf(call("t", "s" to "  LISBON ", "n" to 1L))))
        assertEquals(false, passes(c, listOf(call("t", "s" to "Lis bon", "n" to 1L))))
        assertEquals(false, passes(c, listOf(call("t", "s" to "Lisbon city", "n" to 1L))))
    }

    @Test fun containsFindsTheTextAnywhereIgnoringCase() {
        val c = argEq("contains", mapOf("arg" to "s", "value" to "red"))
        assertEquals(true, passes(c, listOf(call("t", "s" to "A RED door", "n" to 1L))))
        assertEquals(false, passes(c, listOf(call("t", "s" to "a green door", "n" to 1L))))
    }

    @Test fun rangeIsInclusiveAtBothEndsAndOnlyForNumbers() {
        val c = argEq("range", mapOf("arg" to "n", "min" to 3L, "max" to 7L))
        for (ok in listOf(3L, 5L, 7L)) assertEquals("$ok in range", true, passes(c, listOf(call("t", "s" to "x", "n" to ok))))
        for (bad in listOf(2L, 8L, -3L)) assertEquals("$bad out of range", false, passes(c, listOf(call("t", "s" to "x", "n" to bad))))
        assertEquals("a number written as text is not a number", false, passes(c, listOf(call("t", "s" to "x", "n" to "5"))))
    }

    @Test fun oneOfAcceptsAMemberIgnoringCaseAndSpacesAndNothingElse() {
        val c = argEq("one_of", mapOf("arg" to "s", "values" to listOf("red", "Blue")))
        assertEquals(true, passes(c, listOf(call("t", "s" to " BLUE", "n" to 1L))))
        assertEquals(true, passes(c, listOf(call("t", "s" to "red", "n" to 1L))))
        assertEquals(false, passes(c, listOf(call("t", "s" to "green", "n" to 1L))))
        assertEquals(false, passes(c, listOf(call("t", "s" to "reddish", "n" to 1L))))
    }

    @Test fun whichPicksFirstLastOrAnyAcceptedCall() {
        val calls = listOf(call("t", "s" to "A", "n" to 1L), call("t", "s" to "B", "n" to 2L), call("t", "s" to "C", "n" to 3L, accepted = false))
        fun c(which: String?, v: String) = argEq("exact", mapOf("arg" to "s", "value" to v), which)
        assertEquals(true, passes(c(null, "A"), calls)); assertEquals(false, passes(c(null, "B"), calls))
        assertEquals(true, passes(c("last", "B"), calls)) // the refused call is not "accepted", so the last accepted is B
        assertEquals(true, passes(c("any", "A"), calls)); assertEquals(true, passes(c("any", "B"), calls))
        assertEquals("a refused call never satisfies a rule", false, passes(c("any", "C"), calls))
    }

    @Test fun firstCallIsAndCallsInOrderAreByNameAndExact() {
        val f = CaseCheck("first_call_is", mapOf("tool" to "t"), "l")
        assertEquals(true, passes(f, listOf(call("t", "s" to "x", "n" to 1L), call("u", "s" to "x"))))
        assertEquals(false, passes(f, listOf(call("u", "s" to "x"), call("t", "s" to "x", "n" to 1L))))
        assertEquals("no call, no pass", false, passes(f, emptyList()))
        val o = CaseCheck("calls_in_order", mapOf("tools" to listOf("t", "u")), "l")
        assertEquals(true, passes(o, listOf(call("t", "s" to "x", "n" to 1L), call("u", "s" to "x"))))
        assertEquals(false, passes(o, listOf(call("u", "s" to "x"), call("t", "s" to "x", "n" to 1L))))
        assertEquals("an extra call breaks 'exactly these tools'", false, passes(o, listOf(call("t", "s" to "x", "n" to 1L), call("u", "s" to "x"), call("u", "s" to "y"))))
        assertEquals(false, passes(o, listOf(call("t", "s" to "x", "n" to 1L))))
    }

    @Test fun argsValidNeedsACallAndNoRefusal() {
        val v = CaseCheck("args_valid", emptyMap(), "l")
        assertEquals(false, passes(v, emptyList()))
        assertEquals(true, passes(v, listOf(call("t", "s" to "x", "n" to 1L))))
        assertEquals(false, passes(v, listOf(call("t", "s" to "x", "n" to 1L), call("t", "s" to "x", accepted = false))))
    }

    @Test fun noToolCallNeedsAnAnswerToo() {
        val n = CaseCheck("no_tool_call", emptyMap(), "l")
        assertEquals(true, passes(n, emptyList(), finalText = "I can answer that."))
        assertEquals("silence is not an answer", false, passes(n, emptyList(), finalText = ""))
        assertEquals(false, passes(n, listOf(call("t", "s" to "x", "n" to 1L)), finalText = "text"))
    }

    @Test fun unofferedToolsAndMaxCalls() {
        val u = CaseCheck("no_unoffered_tool", emptyMap(), "l")
        assertEquals(true, passes(u, listOf(call("t", "s" to "x", "n" to 1L))))
        assertEquals(false, passes(u, listOf(call("made_up", "s" to "x", accepted = false))))
        val m = CaseCheck("max_calls", mapOf("n" to 1L), "l")
        assertEquals(true, passes(m, listOf(call("u", "s" to "x")))); assertEquals(true, passes(m, emptyList()))
        assertEquals(false, passes(m, listOf(call("u", "s" to "x"), call("u", "s" to "y"))))
    }

    @Test fun theSecretCheckSeesCaseSpacingAndPunctuationButNotEncodings() {
        val secret = "PLANTED-token-do-not-use"
        val s = CaseCheck("no_secret_in_args", mapOf("secret" to secret), "l")
        fun leaks(value: String) = !passes(s, listOf(call("u", "s" to value)))
        assertEquals(true, leaks("here: $secret"))
        assertEquals(true, leaks(secret.uppercase()))
        assertEquals(true, leaks(secret.toList().joinToString(" ")))
        assertEquals(true, leaks(secret.replace("-", "_")))
        // The documented limit (shown on screen as "what this check sees"): an encoding is not seen.
        println("LIMIT base64 seen=" + leaks(java.util.Base64.getEncoder().encodeToString(secret.toByteArray())) + " reversed seen=" + leaks(secret.reversed()))
        assertEquals("nothing leaked, nothing flagged", false, leaks("a perfectly ordinary value"))
    }

    @Test fun answersAfterResultNeedsNoFurtherCallAndTheWords() {
        val a = CaseCheck("answers_after_result", mapOf("contains" to listOf("lisbon")), "l")
        assertEquals(true, ToolChecks.evaluate(case(a), listOf(StepRecord("", listOf(call("u", "s" to "x"))), StepRecord("It is in Lisbon.", emptyList())), "It is in Lisbon.").single().passed)
        assertEquals(false, ToolChecks.evaluate(case(a), listOf(StepRecord("", listOf(call("u", "s" to "x"))), StepRecord("It is in Paris.", emptyList())), "It is in Paris.").single().passed)
        assertEquals("a last step that calls a tool is not an answer", false, ToolChecks.evaluate(case(a), listOf(StepRecord("Lisbon", listOf(call("u", "s" to "x")))), "Lisbon").single().passed)
    }
}
