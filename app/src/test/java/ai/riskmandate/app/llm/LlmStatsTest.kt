package ai.riskmandate.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmStatsTest {
    private fun stats(outT: Int?, decT: Double?, pre: Int? = 120, preT: Double? = 300.0, pieces: Int = 50, ttft: Long? = 1_000, total: Long = 11_000, max: Int = 256) =
        LlmStats.from("gemma4e2b", "GPU", max, pre, outT, preT, decT, pieces, ttft, total)

    @Test fun runtimeFiguresAreUsedAndLabelled() {
        val s = stats(outT = 48, decT = 5.5)
        assertEquals(StatSource.RUNTIME, s.countsFrom)
        assertEquals(StatSource.RUNTIME, s.speedsFrom)
        assertEquals(120, s.promptTokens)
        assertEquals(48, s.outputTokens)
        assertEquals(300.0, s.prefillTokensPerSecond!!, 0.0)
        assertEquals(5.5, s.decodeTokensPerSecond!!, 0.0)
        assertEquals(LlmStopReason.DONE, s.stopReason)
    }

    @Test fun withoutRuntimeCountersKotlinMeasuresAndSaysSo() {
        val s = stats(outT = null, decT = null)
        assertEquals(StatSource.MEASURED, s.countsFrom)
        assertEquals(StatSource.MEASURED, s.speedsFrom)
        assertEquals(50, s.outputTokens)
        assertNull("input tokens cannot be measured from outside", s.promptTokens)
        assertNull("prefill speed cannot be measured from outside", s.prefillTokensPerSecond)
        assertEquals(49 * 1000.0 / 10_000, s.decodeTokensPerSecond!!, 1e-9)
    }

    @Test fun zeroCountersMeanNotReported() {
        val s = stats(outT = 0, decT = 0.0, pre = 0, preT = 0.0)
        assertEquals(StatSource.MEASURED, s.countsFrom)
        assertEquals(StatSource.MEASURED, s.speedsFrom)
        assertNull(s.promptTokens)
    }

    @Test fun reachingMaxTokensIsTheStopReason() {
        assertEquals(LlmStopReason.MAX_TOKENS, stats(outT = 256, decT = 5.0).stopReason)
        assertEquals(LlmStopReason.MAX_TOKENS, stats(outT = null, decT = null, pieces = 300).stopReason)
        assertEquals(256, stats(outT = 256, decT = 5.0).maxTokens)
    }

    @Test fun oneOrNoPieceGivesNoMeasuredSpeed() {
        assertNull(stats(outT = null, decT = null, pieces = 1).decodeTokensPerSecond)
        assertNull(stats(outT = null, decT = null, ttft = null).decodeTokensPerSecond)
    }

    @Test fun thermalStatusMapsAndSevereIsTheCap() {
        assertEquals(Thermal.SEVERE, Thermal.of(android.os.PowerManager.THERMAL_STATUS_SEVERE))
        assertEquals(Thermal.UNKNOWN, Thermal.of(null))
        assertEquals(false, Thermal.MODERATE.severeOrWorse)
        assertEquals(true, Thermal.CRITICAL.severeOrWorse)
    }
}
