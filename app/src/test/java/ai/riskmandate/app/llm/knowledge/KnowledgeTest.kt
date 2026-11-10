package ai.riskmandate.app.llm.knowledge

import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmRole
import ai.riskmandate.app.llm.LlmStopReason
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The bundled data, the index and grounded answers, on the JVM (the asset files are read from the source tree). */
class KnowledgeTest {
    private val dir = File("src/main/assets/knowledge")
    private val knowledge by lazy { Knowledge.parse(File(dir, "knowledge.v1.jsonl").readText()) }
    private val index by lazy { Bm25Index(knowledge) }
    private val eval by lazy { KnowledgeEval.parse(File(dir, "eval.v1.json").readText(), knowledge) }

    @Test fun theBundleLoadsAndMatchesItsManifest() {
        val manifest = File(dir, "manifest.json").readText()
        assertTrue(manifest.contains("\"items\": ${knowledge.items.size}"))
        assertTrue(manifest.contains("\"passages\": ${knowledge.passageCount}"))
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(File(dir, "knowledge.v1.jsonl").readBytes()).joinToString("") { "%02x".format(it) }
        assertTrue("manifest sha256 is the file's", manifest.contains(sha))
        assertTrue(knowledge.items.any { it.kind == KnowledgeKind.ARTICLE } && knowledge.items.any { it.kind == KnowledgeKind.COMPANY })
    }

    @Test fun riskMandateTextIsOnlyTheRiskmandateAiQuotes() {
        val rm = knowledge.items.filter { it.kind == KnowledgeKind.RISKMANDATE }
        assertEquals(setOf("Give agents access. Not free rein.",
            "Agent Behaviour Policies for the AI agents in Gmail, HR, payroll, CRM and meeting apps: what each one can reach, what you authorised it to do, and the gap between the two."),
            rm.flatMap { it.passages }.toSet())
        knowledge.items.filter { it.kind != KnowledgeKind.RISKMANDATE }.forEach { i ->
            assertFalse("${i.id} mentions RiskMandate", (i.title + i.passages.joinToString(" ")).contains(Regex("risk\\s*mandate", RegexOption.IGNORE_CASE)))
        }
    }

    @Test fun badLinesAreRefusedWhole() {
        val good = File(dir, "knowledge.v1.jsonl").readLines().first()
        listOf(
            good.replace("\"id\":\"rm:", "\"id\":\"zz:"),
            good.replace("\"source\":\"riskmandate.ai\"", "\"source\":\"https://evil.example/x\""),
            good.replace("\"kind\":\"riskmandate\"", "\"kind\":\"article\""),
            "[1,2]",
            "$good\n$good",
        ).forEach { bad ->
            try { Knowledge.parse(bad); fail("accepted: ${bad.take(80)}") } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun wordsFoldAccentsAndDropStopWords() {
        assertEquals(listOf("tecnico", "stand"), Words.of("O Técnico's stand"))
        assertEquals(listOf("agent", "pavilion"), Words.of("the Agents in Pavilion"))
        assertTrue(Words.of("??? !!").isEmpty())
    }

    @Test fun retrievalFindsTheExpectedItemForMostQuestions() {
        val r4 = KnowledgeEval.recall(index, eval, 4)
        println("recall@1=${KnowledgeEval.recall(index, eval, 1)} recall@4=$r4")
        val misses = eval.filter { q -> q.expect.isNotEmpty() && index.search(q.q, 4).none { it.item.id in q.expect } }
        println("misses: ${misses.map { it.q to index.search(it.q, 4).map { h -> h.item.id } }}")
        assertTrue("recall@4 $r4; misses: ${misses.map { it.q to index.search(it.q, 4).map { h -> h.item.id } }}", r4 >= 0.9)
    }

    @Test fun searchIsVariedAndEmptyForNoKnownWords() {
        val hits = index.search("Web Summit Lisbon startups", 6)
        assertEquals(hits.size, hits.map { it.item.id }.toSet().size)
        assertTrue(index.search("zzqx", 4).isEmpty())
    }

    @Test fun thePromptNumbersTheSourcesAndStaysWithinBudget() {
        val s = Sources(index.search("Portugal stand Pavilion", 8))
        val req = GroundedPrompt.request("SYSTEM", "Where is Portugal?", s)
        assertEquals(LlmRole.SYSTEM, req.messages[0].role)
        val user = req.messages[1].text
        assertTrue(user.startsWith("Sources:\n[1] "))
        assertTrue(user.endsWith("Question: Where is Portugal?"))
        assertTrue(user.length <= GroundedPrompt.MAX_SOURCE_CHARS + 8 * 4 + 200)
    }

    @Test fun citationsAreCheckedAgainstTheSourcesGiven() {
        val c = Citations.of("Pavilion 1 [1]. Also [2, 3] and [9].", 3)
        assertEquals(listOf(1, 2, 3), c.valid)
        assertEquals(listOf(9), c.invalid)
        assertTrue(Citations.saysUnknown("I do not know that from our data."))
        assertFalse(Citations.saysUnknown("Pavilion 1 [1]."))
    }

    @Test fun anAnswerCitesItemsAndIsTimed() = runBlocking {
        var t = 0L
        val answerer = GroundedAnswerer(FakeLlmService { FakeReply("Pavilion 1, stand E145 [1].") }, index, "SYSTEM", clock = { t += 5; t })
        val a = answerer.ask("Where is Portugal's stand at Web Summit?")
        assertEquals(LlmStopReason.DONE, a.stopReason)
        assertEquals("a:where-to-find-portugal", a.cited.single().id)
        assertTrue(a.firstTokenMs != null && a.firstTokenMs!! <= a.totalMs)
        assertTrue(EvalResult(eval.first { it.expect == listOf("a:where-to-find-portugal") }, a).right)
    }

    @Test fun anUnknownAnswerIsRightOnlyForAQuestionTheDataCannotAnswer() = runBlocking {
        val answerer = GroundedAnswerer(FakeLlmService { FakeReply("I do not know that from our data.") }, index, "SYSTEM")
        val unknown = eval.first { it.expect.isEmpty() }
        assertTrue(EvalResult(unknown, answerer.ask(unknown.q)).right)
        val known = eval.first { it.expect.isNotEmpty() }
        assertFalse(EvalResult(known, answerer.ask(known.q)).right)
    }
}
