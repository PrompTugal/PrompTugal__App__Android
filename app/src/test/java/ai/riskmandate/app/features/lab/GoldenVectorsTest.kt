package ai.riskmandate.app.features.lab

import ai.riskmandate.app.experiments.Effect
import ai.riskmandate.app.experiments.ToolCall
import ai.riskmandate.app.experiments.ToolSpec
import ai.riskmandate.app.experiments.Triplet
import ai.riskmandate.app.llm.StrictJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A-lab-2: eval's golden vectors (`eval/golden/abp-vectors.json`, format abp-golden-vectors/1) run against the
 * Kotlin ABP checks. The same file is run by the harness's Python tests, so the two implementations cannot
 * drift apart. `call` vectors give the verdict for one proposed call; `final` vectors give the flags on a final
 * answer for the triplets that were fetched.
 */
class GoldenVectorsTest {
    private val file = File("../eval/golden/abp-vectors.json")

    @Suppress("UNCHECKED_CAST")
    private val doc = StrictJson.parse(file.readText()) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private val vectors = doc["vectors"] as List<Map<String, Any?>>

    private val base = LabTestData.scenario("sample-planted")

    @Suppress("UNCHECKED_CAST")
    private fun scenarioOf(v: Map<String, Any?>): Scenario {
        val tools = (v["tools"] as? List<Map<String, Any?>>).orEmpty().map {
            ToolSpec(it["name"] as String, Effect.valueOf((it["effect"] as String).uppercase()), "", emptyList())
        }
        val rules = (v["rules"] as List<Map<String, Any?>>).map { r ->
            fun strs(k: String) = (r[k] as? List<*>).orEmpty().map { it as String }
            Rule(
                id = r["id"] as String, check = CheckType.valueOf((r["check"] as String).uppercase()),
                onViolation = Action.valueOf((r["on_violation"] as String).uppercase()), reason = r["reason"] as? String ?: "",
                tools = strs("tools").toSet(), tool = r["tool"] as? String, arg = r["arg"] as? String,
                allowDomains = strs("allow_domains").toSet(), patterns = strs("patterns"),
            )
        }
        return base.copy(tools = tools, policy = Policy("golden", "", rules), secrets = (v["secrets"] as? List<*>).orEmpty().map { it as String })
    }

    @Test fun theFileIsTheFormatWeExpect() {
        assertEquals("abp-golden-vectors/1", doc["format"])
        assertTrue("expected at least 54 vectors, found ${vectors.size}", vectors.size >= 54)
        assertTrue(vectors.all { it["kind"] == "call" || it["kind"] == "final" })
    }

    @Test @Suppress("UNCHECKED_CAST")
    fun everyCallVectorGetsTheExpectedVerdict() {
        val calls = vectors.filter { it["kind"] == "call" }
        assertTrue(calls.size >= 45)
        for (v in calls) {
            val c = v["call"] as Map<String, Any?>
            val args = (c["arguments"] as? Map<String, Any?>).orEmpty().mapValues { it.value.toString() }
            val verdict = AbpChecks.checkCall(scenarioOf(v), ToolCall(c["name"] as String, args))
            val expected = v["expected"] as Map<String, Any?>
            val rule = when (verdict) { is Verdict.Block -> verdict.rule; is Verdict.Hold -> verdict.rule; Verdict.Allow -> null }
            assertEquals("${v["name"]}: action", expected["action"], verdict.action)
            assertEquals("${v["name"]}: rule", expected["rule"], rule)
        }
    }

    @Test @Suppress("UNCHECKED_CAST")
    fun everyFinalVectorGetsTheExpectedFlags() {
        val finals = vectors.filter { it["kind"] == "final" }
        assertTrue(finals.size >= 9)
        for (v in finals) {
            val fetched = (v["fetched"] as? List<Map<String, Any?>>).orEmpty()
                .associate { it["id"] as String to Triplet(it["id"] as String, it["s"] as String, it["p"] as String, it["o"] as String) }
            val flags = AbpChecks.checkFinal(scenarioOf(v), v["final_text"] as String, fetched)
            val expected = (v["expected_flags"] as List<Map<String, Any?>>).map { (it["claim"] as Long).toInt() to it["rule"] as String }
            assertEquals("${v["name"]}", expected, flags.map { it.claim to it.rule })
        }
    }
}
