package ai.riskmandate.app.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Robolectric runner: org.json is part of Android, not of the plain JVM test classpath. */
@RunWith(AndroidJUnit4::class)
class ToolSpecTest {
    private val share = ToolSpec(
        "share_document", "Share a document with an address.",
        listOf(
            ToolParam("doc_id", ParamType.STRING, "Document id", maxLength = 20),
            ToolParam("to", ParamType.STRING, "Recipient"),
            ToolParam("copies", ParamType.INTEGER, "How many", required = false),
            ToolParam("urgent", ParamType.BOOLEAN, "Urgent", required = false),
            ToolParam("score", ParamType.NUMBER, "Score", required = false),
            ToolParam("mode", ParamType.STRING, "Mode", required = false, allowed = listOf("read", "write")),
        ),
    )
    private val read = ToolSpec("read_item", "Read an item.", listOf(ToolParam("id", ParamType.INTEGER, "Item id")))
    private val tools = listOf(share, read)

    private fun rejected(json: String, tool: ToolSpec = share) = (tool.check(json) as ToolCheck.Rejected).reason

    @Test fun goodCallIsAcceptedWithTypedValues() {
        val c = share.check("""{"doc_id":"d1","to":"x@example.org","copies":2,"urgent":true,"score":0.5,"mode":"read"}""")
        assertEquals(ToolCheck.Accepted("share_document", mapOf("doc_id" to "d1", "to" to "x@example.org", "copies" to 2L, "urgent" to true, "score" to 0.5, "mode" to "read")), c)
    }

    @Test fun optionalArgumentsMayBeLeftOutOrNull() =
        assertEquals(mapOf("doc_id" to "d", "to" to "t"), (share.check("""{"doc_id":"d","to":"t","copies":null}""") as ToolCheck.Accepted).args)

    @Test fun everyKindOfBadCallIsRejected() {
        assertTrue("missing argument: to" in rejected("""{"doc_id":"d"}"""))
        assertTrue("unknown argument (1)" in rejected("""{"doc_id":"d","to":"t","cc":"y"}"""))
        assertTrue("copies should be integer" in rejected("""{"doc_id":"d","to":"t","copies":1.5}"""))
        assertTrue("copies should be integer" in rejected("""{"doc_id":"d","to":"t","copies":"2"}"""))
        assertTrue("urgent should be boolean" in rejected("""{"doc_id":"d","to":"t","urgent":"yes"}"""))
        assertTrue("nested values are not allowed" in rejected("""{"doc_id":"d","to":{"a":1}}"""))
        assertTrue("nested values are not allowed" in rejected("""{"doc_id":"d","to":["a"]}"""))
        assertTrue("to should be string" in rejected("""{"doc_id":"d","to":5}"""))
        assertTrue("doc_id longer than 20" in rejected("""{"doc_id":"${"x".repeat(21)}","to":"t"}"""))
        assertTrue("mode is not one of the allowed values" in rejected("""{"doc_id":"d","to":"t","mode":"delete"}"""))
        assertTrue("not a flat JSON object" in rejected("""["d"]"""))
        assertTrue("not a flat JSON object" in rejected("""{"doc_id":"""))
        assertTrue("longer than 4096" in rejected("""{"doc_id":"${"x".repeat(5000)}"}"""))
        assertTrue("repeated key" in rejected("""{"to":"a","to":"b","doc_id":"d"}"""))
        assertTrue("not a flat JSON object" in rejected("""{'doc_id':'d','to':'t'}"""))
        assertTrue("not a flat JSON object" in rejected("""{doc_id:"d",to:"t"}"""))
        assertTrue("text after" in rejected("""{"doc_id":"d","to":"t"} {"x":1}"""))
        assertTrue("not a flat JSON object" in rejected("""{"doc_id":"d","to":"t",}"""))
    }

    @Test fun reasonsNeverEchoValues() {
        val secret = "PLANTED-SECRET-7f3a-do-not-use"
        assertFalse(secret in rejected("""{"doc_id":"d","to":"t","mode":"$secret"}"""))
        assertFalse(secret in rejected("""{"doc_id":"d","to":"t","$secret":1}"""))
        assertFalse(secret in rejected("""{"doc_id":"d","to":"t","copies":"$secret"}"""))
    }

    @Test fun strictParserReadsValidJson() {
        assertEquals(
            mapOf("a" to "x\n\u00e9\"", "b" to -12L, "c" to 1.5e3, "d" to true, "e" to null, "f" to false),
            StrictJson.parseFlatObject(""" { "a" : "x\n\u00e9\"", "b":-12, "c":1.5e3, "d":true, "e":null, "f":false } """),
        )
        assertEquals(emptyMap<String, Any?>(), StrictJson.parseFlatObject("{}"))
        listOf("{\"a\":01}", "{\"a\":1.}", "{\"a\":\"x\ty\"}".replace("\\t", "\t"), "{\"a\":tru}", "{\"a\":99999999999999999999}", "").forEach {
            assertThrows(it, StrictJson.Error::class.java) { StrictJson.parseFlatObject(it) }
        }
    }

    @Test fun nameMustBeOnTheAllowList() {
        assertEquals(ToolCheck.Rejected("delete_all", "not an offered tool"), checkToolCall(tools, LlmEvent.ToolCallProposed("delete_all", "{}")))
        assertEquals(ToolCheck.Accepted("read_item", mapOf("id" to 7L)), checkToolCall(tools, LlmEvent.ToolCallProposed("read_item", """{"id":7}""")))
        assertTrue(checkToolCall(tools, LlmEvent.ToolCallProposed("Read_Item", """{"id":7}""")) is ToolCheck.Rejected)
    }

    @Test fun schemaIsOpenApiShaped() {
        val s = JSONObject(share.schemaJson())
        assertEquals("share_document", s.getString("name"))
        val p = s.getJSONObject("parameters")
        assertEquals("object", p.getString("type"))
        assertEquals("integer", p.getJSONObject("properties").getJSONObject("copies").getString("type"))
        assertEquals(2, p.getJSONObject("properties").getJSONObject("mode").getJSONArray("enum").length())
        assertEquals(listOf("doc_id", "to"), (0 until p.getJSONArray("required").length()).map { p.getJSONArray("required").getString(it) })
    }

    @Test fun badDeclarationsFailFast() {
        assertThrows(IllegalArgumentException::class.java) { ToolSpec("Share", "x") }
        assertThrows(IllegalArgumentException::class.java) { ToolSpec("a", "x", listOf(ToolParam("b", ParamType.STRING, ""), ToolParam("b", ParamType.STRING, ""))) }
        assertThrows(IllegalArgumentException::class.java) { LlmRequest(listOf(LlmMessage.user("x")), tools = listOf(read, read)) }
        assertThrows(IllegalArgumentException::class.java) { LlmRequest(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { LlmRequest(listOf(LlmMessage.user("x")), maxTokens = 0) }
    }
}
