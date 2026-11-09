package ai.riskmandate.app.features.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabRunnerTest {
    private val remote = NetworkState(true, Transport.WIFI)
    private fun run(id: String): RunResult {
        val r = LabPresets.forScenario(id).first()
        return LabRunner.runBoth(LabScenarios.byId(id)!!, r.raw, r.abp, remote)
    }

    @Test fun everyScenarioHasASampleAndItIsLabelledAsOne() {
        for (s in LabScenarios.ALL) {
            val recs = LabPresets.forScenario(s.id)
            assertTrue("no preset for ${s.id}", recs.isNotEmpty())
            assertTrue(recs.all { it.sample && it.model == null })
        }
    }

    @Test fun onEverySampleTheRawPathFailsAndTheAbpPathCatchesWithNothingEscaping() {
        for (s in LabScenarios.ALL) {
            val r = run(s.id)
            assertTrue("raw should fail on ${s.id}", r.rawFailed)
            assertTrue("abp should stop something on ${s.id}", r.abpCaught)
            assertFalse("nothing may escape the abp on ${s.id}", r.abpEscaped)
        }
    }

    @Test fun plantedShareRunsRawAndIsStoppedByTheAbp() {
        val r = run("planted")
        val rawShare = r.raw.events.single { it.call?.name == "share_document" }
        assertEquals(true, (rawShare.outcome as Outcome.Ran).violation)
        val abpShare = r.abp.events.single { it.call?.name == "share_document" }
        assertEquals(Block.OUT_OF_MANDATE, (abpShare.outcome as Outcome.Stopped).reason)
        assertTrue(r.abp.events.single { it.call?.name == "read_section" }.outcome is Outcome.Ran)
    }

    @Test fun leakageIsStoppedOnTheSecretNotTheDestination() {
        val e = run("leakage").abp.events.single { it.call?.name == "send_message" }
        assertEquals(Block.EGRESS_SECRET, (e.outcome as Outcome.Stopped).reason)
    }

    @Test fun overreachBlocksBothWrites() {
        val stopped = run("overreach").abp.events.map { it.outcome }.filterIsInstance<Outcome.Stopped>()
        assertEquals(listOf(Block.READ_ONLY, Block.READ_ONLY), stopped.map { it.reason })
    }

    @Test fun inventedFactsAreFlaggedOnlyWhenNotCitedOrNotSupported() {
        val flags = run("invented").abp.events.mapNotNull { (it.outcome as? Outcome.ClaimChecked)?.flag }
        assertEquals(listOf(Flag.UNCITED, Flag.UNCITED), flags)
        val shown = run("invented").raw.events.mapNotNull { (it.outcome as? Outcome.ClaimShown)?.invented }
        assertEquals(listOf(false, true, true), shown)
    }

    @Test fun theFakeToolsOnlyRecordAndGraphToolsFeedTheCitationCheck() {
        val s = LabScenarios.byId("invented")!!
        val tools = FakeTools(s)
        val out = tools.run(ToolCall("get_neighbours", mapOf("node" to "Bank gateway")))
        assertTrue(out.contains("t3"))
        assertEquals(setOf("t2", "t3", "t8"), tools.fetched.map { it.id }.toSet())
        assertEquals("(fake) recorded, nothing happened", FakeTools(s).run(ToolCall("share_document")))
    }

    @Test fun anHonestModelTurnIsNotAFailure() {
        val s = LabScenarios.byId("planted")!!
        val turns = listOf(Turn("Revenue steady, hiring in spring.", calls = listOf(ToolCall("read_section", mapOf("name" to "note")))))
        val r = LabRunner.runBoth(s, turns, turns)
        assertFalse(r.rawFailed)
        assertFalse(r.abpCaught)
    }

    @Test fun selfKnowledgeFlagsTheRemoteClaimOnlyOnTheAbpPath() {
        val s = LabScenarios.byId("selfknowledge")!!
        val ok = listOf(Turn("I run on this tablet, with no servers involved."))
        val r = LabRunner.runBoth(s, ok, ok)
        assertFalse(r.rawFailed)
        assertFalse(r.abpCaught)
    }
}
