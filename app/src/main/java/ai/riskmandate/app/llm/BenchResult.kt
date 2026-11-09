package ai.riskmandate.app.llm

import java.util.Locale

/** Memory as this process and the system see it, in MiB. Values the device does not report are null. */
data class MemorySample(
    /** This process's resident set (VmRSS), model weights mapped in included. */
    val rssMb: Long?,
    /** This process's peak resident set so far (VmHWM). Only grows during the process's life. */
    val peakRssMb: Long?,
    /** System memory available to apps. */
    val availMb: Long?,
    val totalMb: Long?,
)

/** One bench run: a model, a backend, the fixed prompt. Plain values so the report is testable on the JVM. */
data class BenchResult(
    val model: String,
    val fileName: String,
    val backend: String,
    val runtime: String,
    val verifyMs: Long?,
    val loadMs: Long?,
    val timeToFirstTokenMs: Long?,
    val generateMs: Long?,
    /** From the runtime's own benchmark counters; null if it did not report them. */
    val prefillTokens: Int?,
    val prefillTokensPerSecond: Double?,
    val decodeTokens: Int?,
    val decodeTokensPerSecond: Double?,
    /** Pieces of text the stream delivered (about one per token). */
    val streamedChunks: Int,
    val outputChars: Int,
    val memoryBefore: MemorySample?,
    val memoryAfterLoad: MemorySample?,
    val memoryAfterGenerate: MemorySample?,
    /** Set when the run failed; the numbers up to the failure are still reported. */
    val error: String? = null,
    /** The first characters of the answer, so a reader can see it was real text. */
    val outputPreview: String = "",
) {
    /** Decode speed from wall time when the runtime gave no counters: chunks after the first, per second. */
    val wallDecodePerSecond: Double?
        get() {
            val gen = generateMs ?: return null
            val ttft = timeToFirstTokenMs ?: return null
            val span = gen - ttft
            return if (streamedChunks > 1 && span > 0) (streamedChunks - 1) * 1000.0 / span else null
        }
}

/** Device facts for the report header. No identifiers: no serial, IMEI, Android ID or account. */
data class BenchDevice(
    val manufacturer: String,
    val model: String,
    val soc: String?,
    val android: String,
    val sdk: Int,
    val appVersion: String,
)

/** Plain English text, like the diagnostics report: it is pasted into tickets and chats. */
object BenchReport {
    /** The header and every run's block (from [result]) in order. */
    fun text(device: BenchDevice, runs: List<String>): String = buildString {
        appendLine("RiskMandate LLM bench")
        appendLine("App: ${device.appVersion}")
        appendLine("Device: ${device.manufacturer} ${device.model}" + (device.soc?.let { ", SoC $it" } ?: ""))
        appendLine("Android: ${device.android} (API ${device.sdk})")
        appendLine("Prompt: fixed bench prompt v${BenchPrompt.VERSION}, greedy, max ${BenchPrompt.MAX_OUTPUT_TOKENS} output tokens")
        if (runs.isEmpty()) appendLine("No runs yet.")
        runs.forEach {
            appendLine()
            append(it)
        }
    }.trimEnd()

    fun result(n: Int, r: BenchResult): String = buildString {
        appendLine("#$n ${r.model} [${r.backend}] ${r.fileName} (${r.runtime})")
        r.error?.let { appendLine("  FAILED: $it") }
        appendLine("  sha256 check: ${ms(r.verifyMs)}  load: ${ms(r.loadMs)}  first token: ${ms(r.timeToFirstTokenMs)}  total generate: ${ms(r.generateMs)}")
        appendLine("  prefill: ${count(r.prefillTokens)} tokens at ${tps(r.prefillTokensPerSecond)} tok/s")
        appendLine("  decode: ${count(r.decodeTokens)} tokens at ${tps(r.decodeTokensPerSecond)} tok/s (wall: ${tps(r.wallDecodePerSecond)} chunks/s, ${r.streamedChunks} chunks, ${r.outputChars} chars)")
        appendLine("  memory before: ${mem(r.memoryBefore)}")
        appendLine("  memory after load: ${mem(r.memoryAfterLoad)}")
        appendLine("  memory after generate: ${mem(r.memoryAfterGenerate)}")
        if (r.outputPreview.isNotEmpty()) appendLine("  output: ${r.outputPreview.replace('\n', ' ')}")
    }

    private fun ms(v: Long?) = v?.let { if (it >= 10_000) String.format(Locale.ROOT, "%.1f s", it / 1000.0) else "$it ms" } ?: "-"
    private fun count(v: Int?) = v?.toString() ?: "-"
    private fun tps(v: Double?) = v?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "-"
    private fun mem(m: MemorySample?) = if (m == null) "-" else
        "rss ${m.rssMb ?: "-"} MB, peak ${m.peakRssMb ?: "-"} MB, system free ${m.availMb ?: "-"} of ${m.totalMb ?: "-"} MB"
}

/** The fixed prompt every run uses, so numbers compare across models, backends and builds. */
object BenchPrompt {
    /** Bump when [TEXT] or the settings change, so old and new numbers are not mixed up. */
    const val VERSION = 1
    const val MAX_OUTPUT_TOKENS = 256

    /** Neutral and fictional on purpose: no claims about any product. About 450 tokens of prefill. */
    val TEXT: String = """
        Read these notes about a fictional conference stand, then answer the question at the end.

        Notes. The stand is three metres wide and has two tablets on stands, one large screen and a small table with
        printed leaflets. The team arrives at eight in the morning, checks that both tablets are charged, and opens the
        demonstration app on each. The venue Wi-Fi is often slow between ten and noon, when most visitors arrive, so the
        team prepares everything that needs a download the evening before. Each tablet runs a small language model on the
        device itself, which means the demonstration keeps working when the network does not. Visitors usually stay for
        two to five minutes. Most of them ask three things: what the demonstration shows, whether their data leaves the
        tablet, and how fast the model answers. The team keeps a paper notebook for questions they cannot answer on the
        spot and promises to follow up by the end of the week. At lunch time one person stays on the stand while the
        others eat, and the screen loops a short video. In the afternoon the team runs a scheduled talk of fifteen
        minutes at the stand, which draws a small crowd, and the tablets are passed around so people can try the
        demonstration themselves. At six the stand closes, the tablets are put on charge in a locked cupboard, and the
        team writes a short summary of the day: how many visitors came, which questions were asked most often, what
        broke and what to change for the next day. On the last day the team packs the stand in about an hour.

        Question: summarise the notes in five short bullet points, then name one risk the team should plan for.
    """.trimIndent()
}
