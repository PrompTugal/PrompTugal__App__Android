package ai.riskmandate.app.experiments

import ai.riskmandate.app.llm.ToolCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolProtocolTest {
    private val tools = listOf(
        ToolSpec("read_note", Effect.READ, "read", listOf(Param("name", "string", true))),
        ToolSpec("share", Effect.EGRESS, "share", listOf(Param("to", "string", true), Param("text", "string", false), Param("n", "integer", false), Param("on", "boolean", false), Param("x", "number", false))),
        ToolSpec("list_items", Effect.READ, "list", emptyList()),
    )
    private val llm = ToolProtocol.llmTools(tools)

    /** null when accepted, else the refusal reason from the shared check. */
    private fun verdicts(text: String, i: Int = 0): List<String?> =
        ToolProtocol.extractCalls(text).map { (ToolProtocol.check(it, i, llm) as? ToolCheck.Rejected)?.reason }

    private fun reason(text: String) = verdicts(text).single()!!

    @Test fun findsJsonCallsInsideProse() {
        val c = ToolProtocol.extractCalls("Sure. {\"tool\": \"read_note\", \"arguments\": {\"name\": \"q\"}} then more text")
        assertEquals(1, c.size)
        assertEquals("read_note", c[0].name)
        assertEquals(mapOf("name" to "q"), c[0].arguments)
        assertEquals(listOf<String?>(null), verdicts("{\"tool\":\"read_note\",\"arguments\":{\"name\":\"q\"}}"))
        assertEquals(listOf<String?>(null), verdicts("{\"name\":\"list_items()\",\"args\":{}}"))
        assertEquals(emptyList<Any>(), ToolProtocol.extractCalls("plain answer with {braces} and no call"))
    }

    @Test fun readsGemmasOwnCallSyntax() {
        val c = ToolProtocol.extractCalls("<|tool_call>call:read_note(name=\"quarterly\")<tool_call|>")
        assertEquals(ToolProtocol.RawCall("read_note", mapOf("name" to "quarterly")), c.single())
    }

    @Test fun theSharedCheckRefusesUnknownMissingExtraAndWronglyTypedArguments() {
        assertEquals("not an offered tool", reason("{\"tool\":\"delete_all\",\"arguments\":{}}"))
        assertEquals("missing argument: name", reason("{\"tool\":\"read_note\",\"arguments\":{}}"))
        assertEquals("unknown argument (1)", reason("{\"tool\":\"read_note\",\"arguments\":{\"name\":\"q\",\"url\":\"x\"}}"))
        assertEquals("name should be string", reason("{\"tool\":\"read_note\",\"arguments\":{\"name\":5}}"))
        assertTrue(reason("{\"tool\":\"read_note\",\"arguments\":{\"name\":[\"a\"]}}").contains("nested values are not allowed"))
        assertTrue(reason("{\"tool\":\"read_note\",\"arguments\":{\"name\":{\"a\":1}}}").contains("nested values are not allowed"))
        assertTrue(reason("{\"tool\":\"read_note\",\"arguments\":\"name=q\"}").contains("not a flat JSON object"))
        assertEquals("n should be integer", reason("{\"tool\":\"share\",\"arguments\":{\"to\":\"a\",\"n\":1.5}}"))
        assertEquals(listOf<String?>(null), verdicts("{\"tool\":\"share\",\"arguments\":{\"to\":\"a\",\"n\":2,\"on\":true,\"x\":1.5}}"))
        assertEquals("not an offered tool", reason("{\"tool\":\"bad name!\",\"arguments\":{}}"))
        assertEquals("not an offered tool", reason("{\"tool\":\"${"a".repeat(65)}\",\"arguments\":{}}"))
    }

    @Test fun capsLengthAndCallsPerTurn() {
        val long = "x".repeat(ToolProtocol.MAX_ARG_CHARS + 1)
        assertEquals("name longer than ${ToolProtocol.MAX_ARG_CHARS} characters", reason("{\"tool\":\"read_note\",\"arguments\":{\"name\":\"$long\"}}"))
        assertEquals(listOf<String?>(null), verdicts("{\"tool\":\"read_note\",\"arguments\":{\"name\":\"${"x".repeat(ToolProtocol.MAX_ARG_CHARS)}\"}}"))
        val many = (1..8).joinToString(",") { "\"a$it\":\"v\"" }
        assertTrue(reason("{\"tool\":\"share\",\"arguments\":{$many}}").startsWith("unknown argument"))
        val text = (1..6).joinToString(" ") { "{\"tool\":\"list_items\",\"arguments\":{}}" }
        val calls = ToolProtocol.extractCalls(text)
        assertEquals(6, calls.size)
        val verdicts = calls.mapIndexed { i, c -> (ToolProtocol.check(c, i, llm) as? ToolCheck.Rejected)?.reason }
        assertEquals(List(ToolProtocol.MAX_CALLS_PER_TURN) { null } + List(2) { "too many calls in one turn (limit 4)" }, verdicts)
    }

    @Test fun boundsTheWorkOnHostileText() {
        val calls = ToolProtocol.extractCalls((1..200).joinToString(" ") { "{\"tool\":\"list_items\",\"arguments\":{}}" })
        assertTrue(calls.size <= ToolProtocol.MAX_PARSED_CALLS)
        // Thousands of unbalanced braces and a huge text: finishes, finds nothing, scans at most the cap.
        val junk = "{".repeat(50_000) + "{\"tool\":\"list_items\",\"arguments\":{}}"
        val t0 = System.nanoTime()
        assertEquals(0, ToolProtocol.extractCalls(junk).size)
        assertTrue("took too long", (System.nanoTime() - t0) / 1_000_000 < 2_000)
        // A call placed beyond the scan limit is never read.
        val late = "a".repeat(ToolProtocol.MAX_TEXT_CHARS) + "{\"tool\":\"list_items\",\"arguments\":{}}"
        assertEquals(0, ToolProtocol.extractCalls(late).size)
        // Deep nesting inside arguments is refused by the parser, not recursed into.
        assertEquals(0, ToolProtocol.extractCalls("{\"tool\":\"read_note\",\"arguments\":" + "[".repeat(5_000) + "}").size)
    }

    @Test fun acceptedArgumentsBecomeFlatStrings() {
        val raw = ToolProtocol.extractCalls("{\"tool\":\"share\",\"arguments\":{\"to\":\"a\",\"n\":2,\"on\":true}}").single()
        val call = ToolProtocol.toCall(ToolProtocol.check(raw, 0, llm) as ToolCheck.Accepted)
        assertEquals(ToolCall("share", mapOf("to" to "a", "n" to "2", "on" to "true")), call)
    }

    @Test fun anArgumentObjectOverTheSharedCapIsRefused() {
        val args = (1..3).joinToString(",") { "\"text\":\"${"y".repeat(1500)}\"".replace("\"text\"", "\"k$it\"") }
        assertTrue(reason("{\"tool\":\"share\",\"arguments\":{$args}}").let { it.contains("longer than") || it.startsWith("unknown argument") })
    }
}
