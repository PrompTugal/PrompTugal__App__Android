package ai.riskmandate.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** dev.lab's tests for the parser, moved with it (A-lab-7), plus the flat-object cases for tool arguments. */
class StrictJsonTest {
    private fun rejects(s: String, maxChars: Int = 1000) {
        try { StrictJson.parse(s, maxChars); fail("accepted: $s") } catch (_: StrictJson.JsonException) {}
    }

    @Test fun parsesTheJsonTypes() {
        val v = StrictJson.parse("""{"a":[1,-2,3.5,1e2,true,false,null,"x\n\u00e9\/"],"b":{}}""") as Map<*, *>
        assertEquals(listOf(1L, -2L, 3.5, 100.0, true, false, null, "x\né/"), v["a"])
        assertEquals(emptyMap<String, Any?>(), v["b"])
        assertEquals(0L, StrictJson.parse("0"))
        assertEquals(emptyList<Any?>(), StrictJson.parse(" [ ] "))
    }

    @Test fun refusesWhatStrictJsonRefuses() {
        rejects("{'a':1}")
        rejects("""{"a":1,}""")
        rejects("[1,]")
        rejects("""{"a":1 "b":2}""")
        rejects("""{"a":1,"a":2}""")
        rejects("01")
        rejects("-")
        rejects("1.")
        rejects("1e")
        rejects("+1")
        rejects("NaN")
        rejects("\"a\nb\"")
        rejects("\"\\x41\"")
        rejects("\"\\u12g4\"")
        rejects("\"abc")
        rejects("{\"a\":1} extra")
        rejects("// c\n{}")
        rejects("")
        rejects("tru")
    }

    @Test fun boundsDepthAndSize() {
        val ok = "[".repeat(StrictJson.MAX_DEPTH) + "]".repeat(StrictJson.MAX_DEPTH)
        StrictJson.parse(ok)
        rejects("[".repeat(StrictJson.MAX_DEPTH + 2) + "]".repeat(StrictJson.MAX_DEPTH + 2))
        rejects("[" + "1,".repeat(600) + "1]", maxChars = 1000)
        // A deeply nested bomb is refused without recursing past the cap.
        rejects("[".repeat(100_000), maxChars = 200_000)
    }

    @Test fun parseAtReadsOneValueInsideText() {
        val t = "prefix {\"tool\":\"x\"} suffix"
        val (v, end) = StrictJson.parseAt(t, t.indexOf('{'))
        assertEquals(mapOf("tool" to "x"), v)
        assertEquals(" suffix", t.substring(end))
    }

    @Test fun canonicalFormAndHashMatchTheHarness() {
        val v = StrictJson.parse("""{"b":1,"a":["é","x\ny","q\"\\","\u0001"],"c":{"z":true,"y":null}}""")
        assertEquals("""{"a":["é","x\ny","q\"\\","\u0001"],"b":1,"c":{"y":null,"z":true}}""", StrictJson.canonical(v))
        // Computed with eval's Python: sha256 of that canonical text, first 16 hex characters.
        assertEquals("7883ba1acd648dce", StrictJson.hash16(v))
        assertTrue(StrictJson.render(mapOf("k" to listOf(1L, "s"))) == """{"k": [1, "s"]}""")
    }

    @Test fun flatObjectsOnly() {
        assertEquals(
            mapOf("a" to "x\n\u00e9\"", "b" to -12L, "c" to 1.5e3, "d" to true, "e" to null, "f" to false),
            StrictJson.parseFlatObject(""" { "a" : "x\n\u00e9\"", "b":-12, "c":1.5e3, "d":true, "e":null, "f":false } """),
        )
        assertEquals(emptyMap<String, Any?>(), StrictJson.parseFlatObject("{}"))
        listOf("""{"a":{"b":1}}""", """{"a":[1]}""", "[1]", "\"s\"", "{\"a\":01}", "", "x".repeat(10)).forEach {
            try { StrictJson.parseFlatObject(it); fail("accepted: $it") } catch (_: StrictJson.JsonException) {}
        }
        try { StrictJson.parseFlatObject("{\"a\":\"" + "x".repeat(ToolSpec.MAX_ARGS_CHARS) + "\"}"); fail("too long accepted") } catch (_: StrictJson.JsonException) {}
    }
}
