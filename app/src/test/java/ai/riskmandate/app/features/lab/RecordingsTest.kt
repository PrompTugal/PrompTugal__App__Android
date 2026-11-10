package ai.riskmandate.app.features.lab

import ai.riskmandate.app.experiments.FormatException
import ai.riskmandate.app.experiments.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecordingsTest {
    private val data = LabTestData.load()

    @Test fun everyScenarioHasABundledRecordingTiedToItsHash() {
        assertEquals(5 + 6, data.scenarios.size)
        for (s in data.scenarios) {
            val recs = data.recordingsFor(s.id)
            assertTrue("no recording for ${s.id}", recs.isNotEmpty())
            // The hash was computed by eval's Python; Kotlin must agree, or a real recording cannot be tied to its version.
            for (r in recs) assertEquals("hash of ${s.id}", r.scenarioHash, s.hash)
        }
    }

    @Test fun samplesAndCaseStudiesAreLabelledAsWhatTheyAre() {
        // Nothing here is a model's run: a sample is hand-written, a case study is written to illustrate a real policy.
        for (r in data.recordings) when (r.sourceKind) {
            "sample" -> assertEquals(r.id, "hand-written sample", r.modelLabel)
            "case_study" -> { assertEquals(r.id, "written to illustrate the policy", r.modelLabel); assertTrue(r.id, r.scenarioId.startsWith(LabTags.CASE_PREFIX)) }
            else -> org.junit.Assert.fail("${r.id}: unexpected source kind ${r.sourceKind}")
        }
        assertTrue(data.scenarios.filter { it.id.startsWith(LabTags.CASE_PREFIX) }.all { s -> data.recordingsFor(s.id).all { it.sourceKind == "case_study" } })
        assertTrue(data.scenarios.filter { !it.id.startsWith(LabTags.CASE_PREFIX) }.all { s -> data.recordingsFor(s.id).all { it.sourceKind == "sample" } })
    }

    /** Case studies quote a real policy as it is, and say plainly that it runs in observe mode (nothing invented). */
    @Test fun caseStudiesQuoteTheRealPolicyAndSayItRunsInObserveMode() {
        val cases = data.scenarios.filter { it.id.startsWith(LabTags.CASE_PREFIX) }
        assertEquals(6, cases.size)
        val source = File("../briefs/2026-11-10__riskmandate-demos/abp-source")
        val sourceText = source.listFiles { f -> f.name.endsWith(".md") }!!.joinToString("\n") { it.readText() }
            .replace("**", "").replace("`", "") // the policy's words, without its markdown
        for (s in cases) {
            assertTrue("${s.id} says observe mode", s.description.contains("observe mode"))
            assertTrue("${s.id} says the tools are fakes", s.description.contains("fakes"))
            val quote = Regex("\u201c(.+?)\u201d").find(s.description)?.groupValues?.get(1)
            assertTrue("${s.id} quotes the policy", quote != null)
            assertTrue("${s.id}: the quote is in the real ABP text, word for word: $quote", sourceText.contains(quote!!))
        }
    }

    @Test fun theRecordedOutcomeIsWhatTheOracleSaysAboutTheRecordedEvents() {
        for (r in data.recordings) {
            val s = data.scenario(r.scenarioId)!!
            for ((abp, path) in listOf(false to r.raw, true to r.abp)) {
                val mine = Oracle.outcome(s, LabFormats.traceOf(path.events), abp)
                assertEquals("${r.id} abp=$abp failure", path.outcome.failure, mine.failure)
                assertEquals("${r.id} abp=$abp attempted", path.outcome.attempted, mine.attempted)
                assertEquals("${r.id} abp=$abp caught", path.outcome.caught, mine.caught)
                assertEquals("${r.id} heuristic", path.outcome.heuristic, mine.heuristic)
            }
        }
    }

    @Test fun onEverySampleTheRawLaneFailsAndTheAbpLaneStopsItAtTheSameStep() {
        for (r in data.recordings) {
            val s = data.scenario(r.scenarioId)!!
            val raw = LaneModel.rows(s, r.raw, abp = false)
            val abp = LaneModel.rows(s, r.abp, abp = true)
            assertNotNull("raw fails in ${r.id}", LaneModel.decisiveStep(raw))
            assertEquals("stopped at the step where the raw lane went wrong in ${r.id}", LaneModel.decisiveStep(raw), LaneModel.decisiveStep(abp))
            assertFalse(r.abp.outcome.failure)
        }
    }

    @Test fun anAbpVerdictOnARecordingIsTheOneKotlinWouldGive() {
        for (r in data.recordings) {
            val s = data.scenario(r.scenarioId)!!
            val trace = LabFormats.traceOf(r.abp.events)
            for (c in trace.calls.filter { it.valid }) {
                val args = c.arguments.mapValues { it.value.toString() }
                val mine = AbpChecks.checkCall(s, ToolCall(c.name, args))
                assertEquals("${r.id} ${c.name}", c.verdict?.action, mine.action)
            }
            val fetched = Oracle.fetchedTriplets(s, trace.calls)
            assertEquals("${r.id} flags", trace.flags.map { it.claim to it.reason }, AbpChecks.checkFinal(s, trace.finalText, fetched).map { it.claim to it.reason })
        }
    }

    @Test fun noBundledFileLooksLikeASecretOrContainsAContactDetail() {
        val bad = Regex("sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{12,}|hf_[A-Za-z0-9]{20,}")
        val email = Regex("[A-Za-z0-9._-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*")
        for (f in File("src/main/assets/feature_lab").walkTopDown().filter { it.isFile }) {
            val t = f.readText()
            assertFalse("${f.name} has a secret-shaped string", bad.containsMatchIn(t))
            for (m in email.findAll(t)) assertTrue("${f.name} has a real-looking address: ${m.value}", m.value.endsWith(".test") || m.value.endsWith("example.test"))
        }
    }

    @Test fun scenarioPatternsAreBoundedAndMustCompile() {
        val base = LabTestData.text("scenarios/sample-leakage.json")
        val ok = base.replace("\"patterns\": []", "\"patterns\": [\"sk-[a-z]{4}\"]")
        LabFormats.parseScenario(ok)
        val long = base.replace("\"patterns\": []", "\"patterns\": [\"" + "a".repeat(LabFormats.MAX_PATTERN_CHARS + 1) + "\"]")
        val bad = base.replace("\"patterns\": []", "\"patterns\": [\"(\"]")
        for (t in listOf(long, bad)) try { LabFormats.parseScenario(t); org.junit.Assert.fail("accepted") } catch (_: FormatException) {}
    }

    @Test fun scenarioToolsMustFitTheSharedToolContract() {
        val base = LabTestData.text("scenarios/sample-planted.json")
        // A tool name outside the contract (lowercase letters, digits, underscore; 40 characters) is refused at load.
        val badName = base.replace("\"read_note\"", "\"Read-Note\"")
        val badType = base.replace("\"type\": \"string\"", "\"type\": \"object\"")
        val extra = (1..7).joinToString("") { "\"a$it\": {\"type\": \"string\"}," }
        val many = base.replaceFirst(Regex("\"properties\": \\{\\s*\"name\""), "\"properties\": {" + extra + "\"name\"")
        for (t in listOf(badName, badType, many)) {
            org.junit.Assert.assertNotEquals("test edit did not change the file", base, t)
            try { LabFormats.parseScenario(t); org.junit.Assert.fail("accepted") } catch (_: FormatException) {}
        }
    }
}
