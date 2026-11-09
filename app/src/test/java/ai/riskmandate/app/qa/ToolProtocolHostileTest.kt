package ai.riskmandate.app.qa

import ai.riskmandate.app.experiments.ExperimentsTestData
import ai.riskmandate.app.experiments.ToolProtocol
import ai.riskmandate.app.llm.ToolCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hostile model output for the text tool protocol (QA; plan WB-T, T-5): bounded, never throws, never trusted. */
class ToolProtocolHostileTest {
    private val c = ExperimentsTestData.toolCatalogue().cases.first { it.tools.isNotEmpty() }
    private val llmTools = ToolProtocol.llmTools(c.tools)
    private val tool = c.tools.first().name

    private fun call(args: String = "{}", name: String = tool) = """{"tool":"$name","arguments":$args}"""

    @Test fun neverThrowsOnAnyOfTheseAndNeverExceedsTheCaps() {
        val deep = "[".repeat(5000) + "]".repeat(5000)
        val table = listOf(
            "", " ", "{", "}", "{{{{{{{{", "}}}}}}}}", "{\"tool\":", "{\"tool\":\"$tool\",\"arguments\":{", "{\"tool\"", "\u0000", "\uD800",
            "{\"tool\":\"$tool\",\"arguments\":$deep}", "{\"tool\":\"$tool\",\"arguments\":{\"a\":$deep}}", "{".repeat(20000),
            "{\"tool\":\"$tool\",\"tool\":\"other\"}", "{\"tool\":\"$tool\",\"arguments\":{\"a\":1,\"a\":2}}",
            "<|tool_call>call:$tool(", "<|tool_call>call:$tool(a=<|\"|>unterminated", "<|tool_call>call:(x=1)<tool_call|>", "<|tool_call>" .repeat(5000),
            "```json\n${call()}\n```", "{\"tool\":123}", "{\"tool\":null,\"name\":null}", "{\"tool\":\"${"n".repeat(100000)}\"}",
            (call() + "\n").repeat(5000), "{\"a\":".repeat(3000), "\"" .repeat(10000), "\\".repeat(10000),
        )
        for (t in table) {
            val calls = ToolProtocol.extractCalls(t)
            assertTrue("at most ${ToolProtocol.MAX_PARSED_CALLS} calls from: ${t.take(40)}", calls.size <= ToolProtocol.MAX_PARSED_CALLS)
            calls.forEachIndexed { i, rc -> ToolProtocol.check(rc, i, llmTools) } // checking a found call must not throw either
        }
    }

    private fun validArgs() = c.tools.first().params.filter { it.required }.associate { p ->
        p.name to (when (p.type) { "integer" -> 1L; "number" -> 1.0; "boolean" -> true; else -> "x" } as Any?)
    }

    @Test fun manyCallsAreCutAtSixteenAndOnlyFourAreEverRunInATurn() {
        val calls = ToolProtocol.extractCalls((call() + "\n").repeat(100))
        assertEquals(ToolProtocol.MAX_PARSED_CALLS, calls.size)
        val ok = ToolProtocol.RawCall(tool, validArgs())
        assertTrue("a valid call is accepted inside the cap", ToolProtocol.check(ok, ToolProtocol.MAX_CALLS_PER_TURN - 1, llmTools) is ToolCheck.Accepted)
        assertTrue("the same valid call past the per-turn cap is rejected", ToolProtocol.check(ok, ToolProtocol.MAX_CALLS_PER_TURN, llmTools) is ToolCheck.Rejected)
    }

    @Test fun textBeyondSixteenThousandCharactersIsNotRead() {
        val pad = "x".repeat(ToolProtocol.MAX_TEXT_CHARS)
        assertEquals(emptyList<Any>(), ToolProtocol.extractCalls(pad + call()))
        assertEquals(1, ToolProtocol.extractCalls(call() + pad).size)
    }

    @Test fun aCallInProseOrAFenceIsFoundAndSeveralAreKeptInOrder() {
        assertEquals(listOf(tool), ToolProtocol.extractCalls("Sure, I will do it: ${call()} Done.").map { it.name })
        assertEquals(listOf(tool), ToolProtocol.extractCalls("```json\n${call()}\n```").map { it.name })
        val two = ToolProtocol.extractCalls(call(name = "first_x") + " then " + call(name = "second_x")).map { it.name }
        assertEquals(listOf("first_x", "second_x"), two)
    }

    @Test fun partialAndMalformedJsonIsNotACall() {
        for (t in listOf("{\"tool\":\"$tool\",\"arguments\":{", "{\"tool\":\"$tool\"", "tool: $tool", "{\"tool\":\"$tool\",}", "{'tool':'$tool'}"))
            assertEquals("not a call: $t", 0, ToolProtocol.extractCalls(t).filter { it.name == tool }.size)
    }

    @Test fun anUnofferedOrBlankNameIsRejectedNotRun() {
        for (n in listOf("made_up_tool", "", " ", "../../etc/passwd", "${tool}x", tool.uppercase())) {
            val rc = ToolProtocol.RawCall(n, emptyMap<String, Any?>())
            assertTrue("name '$n' must be rejected", ToolProtocol.check(rc, 0, llmTools) is ToolCheck.Rejected)
        }
    }

    @Test fun oversizedAndNonFlatArgumentsAreRejected() {
        val p = c.tools.first().params.firstOrNull { it.type == "string" } ?: return
        val big = ToolProtocol.RawCall(tool, mapOf(p.name to "x".repeat(ToolProtocol.MAX_ARG_CHARS + 1)))
        assertTrue(ToolProtocol.check(big, 0, llmTools) is ToolCheck.Rejected)
        val nested = ToolProtocol.RawCall(tool, mapOf(p.name to mapOf("a" to listOf(1L))))
        assertTrue(ToolProtocol.check(nested, 0, llmTools) is ToolCheck.Rejected)
        val many = ToolProtocol.RawCall(tool, (1..ToolProtocol.MAX_ARGS + 3).associate { "k$it" to "v" })
        assertTrue(ToolProtocol.check(many, 0, llmTools) is ToolCheck.Rejected)
    }

    @Test fun gemmaSyntaxIsReadAndBoundedToo() {
        val g = ToolProtocol.extractCalls("<|tool_call>call:$tool(a=\"x\", b=<|\"|>two words<|\"|>, n=5)<tool_call|>")
        assertEquals(1, g.size); assertEquals(tool, g[0].name)
        val args = g[0].arguments as Map<*, *>
        assertEquals("x", args["a"]); assertEquals("two words", args["b"]); assertEquals("5", args["n"])
        val many = ToolProtocol.extractCalls("<|tool_call>call:$tool(a=1)<tool_call|>".repeat(200))
        assertEquals(ToolProtocol.MAX_PARSED_CALLS, many.size)
    }
}
