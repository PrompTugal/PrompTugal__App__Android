package ai.riskmandate.app.features.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecordingsTest {
    private val data = LabTestData.load()

    @Test fun everyScenarioHasABundledRecordingTiedToItsHash() {
        assertEquals(5, data.scenarios.size)
        for (s in data.scenarios) {
            val recs = data.recordingsFor(s.id)
            assertTrue("no recording for ${s.id}", recs.isNotEmpty())
            // The hash was computed by eval's Python; Kotlin must agree, or a real recording cannot be tied to its version.
            for (r in recs) assertEquals("hash of ${s.id}", r.scenarioHash, s.hash)
        }
    }

    @Test fun samplesAreLabelledAsSamples() {
        assertTrue(data.recordings.all { it.sourceKind == "sample" && it.modelLabel == "hand-written sample" })
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
}
