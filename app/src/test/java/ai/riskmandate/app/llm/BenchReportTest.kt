package ai.riskmandate.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchReportTest {
    private val device = BenchDevice("LENOVO", "TB336FU", "Mediatek MT6878", "15", 35, "0.1.40 (40)")
    private val mem = MemorySample(rssMb = 2900, peakRssMb = 3100, availMb = 6000, totalMb = 11500)
    private val ok = BenchResult(
        model = "Gemma 4 E2B", fileName = "gemma-4-E2B-it.litertlm", backend = "GPU", runtime = "litertlm-android 0.18.1",
        verifyMs = 14_200, loadMs = 6_300, timeToFirstTokenMs = 900, generateMs = 9_900,
        prefillTokens = 450, prefillTokensPerSecond = 512.34, decodeTokens = 256, decodeTokensPerSecond = 28.44,
        streamedChunks = 251, outputChars = 1100, memoryBefore = mem, memoryAfterLoad = mem, memoryAfterGenerate = null,
        outputPreview = "- The stand\n- Two tablets",
    )

    @Test fun reportCarriesTheNumbersDinisNeeds() {
        val text = BenchReport.text(device, listOf(BenchReport.result(1, ok)))
        listOf(
            "Device: LENOVO TB336FU, SoC Mediatek MT6878", "Android: 15 (API 35)", "App: 0.1.40 (40)",
            "#1 Gemma 4 E2B [GPU] gemma-4-E2B-it.litertlm (litertlm-android 0.18.1)",
            "sha256 check: 14.2 s", "load: 6300 ms", "first token: 900 ms",
            "prefill: 450 tokens at 512.3 tok/s", "decode: 256 tokens at 28.4 tok/s",
            "rss 2900 MB, peak 3100 MB, system free 6000 of 11500 MB", "memory after generate: -",
            "output: - The stand - Two tablets",
        ).forEach { assertTrue("missing: $it\n$text", it in text) }
        assertFalse("FAILED" in text)
    }

    @Test fun wallDecodeIsChunksAfterTheFirstOverTheRest() = assertEquals(250 * 1000.0 / 9000, ok.wallDecodePerSecond!!, 1e-9)

    @Test fun failedRunsSaySoAndKeepWhatWasMeasured() {
        val r = ok.copy(error = "LiteRtLmJniException: no GPU", timeToFirstTokenMs = null, generateMs = null, prefillTokens = null)
        val text = BenchReport.result(2, r)
        assertTrue("FAILED: LiteRtLmJniException: no GPU" in text)
        assertTrue("load: 6300 ms" in text)
        assertTrue("first token: -" in text)
        assertEquals(null, r.wallDecodePerSecond)
    }

    @Test fun emptyReportSaysNoRuns() = assertTrue("No runs yet." in BenchReport.text(device.copy(soc = null), emptyList()))

    @Test fun promptIsFixedAndNamesNoProduct() {
        assertTrue(BenchPrompt.TEXT.length > 1500)
        assertFalse(BenchPrompt.TEXT.contains("RiskMandate", ignoreCase = true))
        assertFalse(BenchPrompt.TEXT.contains("ABP"))
    }

    @Test fun procStatusIsParsed() {
        val m = MemoryProbe.parseStatus("Name:\tapp\nVmHWM:\t  3174400 kB\nVmRSS:\t 2969600 kB\nThreads:\t40\n")
        assertEquals(3174400L, m["VmHWM"])
        assertEquals(2969600L, m["VmRSS"])
        assertEquals(null, m["Threads"])
        assertEquals(null, m["Name"])
    }
}
