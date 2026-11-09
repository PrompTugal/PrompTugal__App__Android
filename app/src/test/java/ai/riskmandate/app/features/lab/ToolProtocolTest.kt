package ai.riskmandate.app.features.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolProtocolTest {
    private val tools = listOf(
        ToolSpec("read_note", Effect.READ, "read", listOf(Param("name", "string", true))),
        ToolSpec("share", Effect.EGRESS, "share", listOf(Param("to", "string", true), Param("text", "string", false), Param("n", "integer", false), Param("on", "boolean", false), Param("x", "number", false))),
        ToolSpec("list_items", Effect.READ, "list", emptyList()),
    )
    private fun valid(text: String, i: Int = 0) = ToolProtocol.extractCalls(text).map { ToolProtocol.validate(it, tools, i) }

    @Test fun findsJsonCallsInsideProse() {
        val c = ToolProtocol.extractCalls("Sure. {\"tool\": \"read_note\", \"arguments\": {\"name\": \"q\"}} then more text")
        assertEquals(1, c.size)
        assertEquals("read_note", c[0].name)
        assertEquals(mapOf("name" to "q"), c[0].arguments)
        assertEquals(listOf<String?>(null), valid("{\"tool\":\"read_note\",\"arguments\":{\"name\":\"q\"}}"))
        assertEquals(listOf<String?>(null), valid("{\"name\":\"list_items()\",\"args\":{}}"))
        assertEquals(emptyList<Any>(), ToolProtocol.extractCalls("plain answer with {braces} and no call"))
    }

    @Test fun readsGemmasOwnCallSyntax() {
        val c = ToolProtocol.extractCalls("<|tool_call>call:read_note(name=\"quarterly\")<tool_call|>")
        assertEquals(ToolProtocol.RawCall("read_note", mapOf("name" to "quarterly")), c.single())
    }

    @Test fun refusesUnknownMissingExtraAndWronglyTypedArguments() {
        assertTrue(valid("{\"tool\":\"delete_all\",\"arguments\":{}}").single()!!.contains("unknown tool"))
        assertTrue(valid("{\"tool\":\"read_note\",\"arguments\":{}}").single()!!.contains("missing argument 'name'"))
        assertTrue(valid("{\"tool\":\"read_note\",\"arguments\":{\"name\":\"q\",\"url\":\"x\"}}").single()!!.contains("unexpected argument 'url'"))
        assertTrue(valid("{\"tool\":\"read_note\",\"arguments\":{\"name\":5}}").single()!!.contains("must be string"))
        assertTrue(valid("{\"tool\":\"read_note\",\"arguments\":{\"name\":[\"a\"]}}").single()!!.contains("must be string"))
        assertTrue(valid("{\"tool\":\"read_note\",\"arguments\":{\"name\":{\"a\":1}}}").single()!!.contains("must be string"))
        assertTrue(valid("{\"tool\":\"read_note\",\"arguments\":\"name=q\"}").single()!!.contains("must be an object"))
        assertTrue(valid("{\"tool\":\"share\",\"arguments\":{\"to\":\"a\",\"n\":1.5}}").single()!!.contains("must be integer"))
        assertNull(valid("{\"tool\":\"share\",\"arguments\":{\"to\":\"a\",\"n\":2,\"on\":true,\"x\":1.5}}").single())
        assertTrue(valid("{\"tool\":\"bad name!\",\"arguments\":{}}").single()!!.contains("not valid"))
        assertTrue(valid("{\"tool\":\"${"a".repeat(65)}\",\"arguments\":{}}").single()!!.contains("not valid"))
    }

    @Test fun capsLengthArgumentCountAndCallsPerTurn() {
        val long = "x".repeat(ToolProtocol.MAX_ARG_CHARS + 1)
        assertTrue(valid("{\"tool\":\"read_note\",\"arguments\":{\"name\":\"$long\"}}").single()!!.contains("longer than"))
        assertNull(valid("{\"tool\":\"read_note\",\"arguments\":{\"name\":\"${"x".repeat(ToolProtocol.MAX_ARG_CHARS)}\"}}").single())
        val many = (1..ToolProtocol.MAX_ARGS + 1).joinToString(",") { "\"a$it\":\"v\"" }
        assertTrue(valid("{\"tool\":\"share\",\"arguments\":{$many}}").single()!!.contains("too many arguments"))
        val text = (1..6).joinToString(" ") { "{\"tool\":\"list_items\",\"arguments\":{}}" }
        val calls = ToolProtocol.extractCalls(text)
        assertEquals(6, calls.size)
        val verdicts = calls.mapIndexed { i, c -> ToolProtocol.validate(c, tools, i) }
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

    @Test fun validArgumentsBecomeFlatStrings() {
        val raw = ToolProtocol.extractCalls("{\"tool\":\"share\",\"arguments\":{\"to\":\"a\",\"n\":2,\"on\":true}}").single()
        val call = ToolProtocol.toCall(raw)
        assertNotNull(call)
        assertEquals(mapOf("to" to "a", "n" to "2", "on" to "true"), call.args)
    }
}
