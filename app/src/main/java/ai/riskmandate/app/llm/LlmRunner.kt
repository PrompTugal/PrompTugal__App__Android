package ai.riskmandate.app.llm

import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Everything one Workbench run is set up with. Plain values, so a run can be repeated exactly. */
data class RunSettings(
    /** The model is chosen in Model setup (architect: no model parameter); this is the backend only. */
    val backend: BackendPreference = BackendPreference.AUTO,
    val systemPrompt: String = "",
    val userPrompt: String,
    val sampling: Sampling = Sampling.DEFAULT,
    val maxTokens: Int = 256,
    val tools: List<ToolSpec> = emptyList(),
    /** Unload the model first, so the run measures a cold load (LlmService.unload; security R-wb-1). */
    val coldStart: Boolean = false,
) {
    fun request(): LlmRequest {
        require(userPrompt.isNotBlank()) { "the prompt is empty" }
        require(userPrompt.length <= MAX_TYPED_CHARS && systemPrompt.length <= MAX_TYPED_CHARS) { "a prompt is longer than $MAX_TYPED_CHARS characters" }
        return LlmRequest(
        messages = buildList {
            if (systemPrompt.isNotBlank()) add(LlmMessage.system(systemPrompt))
            add(LlmMessage.user(userPrompt))
        },
        tools = tools,
        settings = sampling,
        maxTokens = maxTokens,
        backend = backend,
        )
    }

    companion object {
        /** Typed prompts are capped (security checklist L). */
        const val MAX_TYPED_CHARS = 2_000
    }
}

/** One proposed tool call and what [checkToolCall] said about it. Nothing ran. */
data class ToolCallRecord(val name: String, val argsJson: String, val verdict: ToolCheck)

/** A finished run, whatever the ending. Held in memory and in the run log; shown as text by [RunReport]. */
data class RunRecord(
    val settings: RunSettings,
    /** Wall-clock start, for ordering the log (not an identifier). */
    val startedAtMs: Long,
    val text: String,
    val toolCalls: List<ToolCallRecord>,
    /** Null when the run ended before the runtime reported (stopped early, or an error). */
    val stats: LlmStats?,
    val stopReason: LlmStopReason,
    val error: LlmEvent.Error?,
    /** Time from the start to the end as the runner saw it, load included. */
    val wallMs: Long,
    val before: DeviceSample?,
    val after: DeviceSample?,
)

/** What the screen sees while a run goes on. */
sealed interface RunUpdate {
    /** The answer so far and the time since the start; [firstTokenMs] once the first piece arrived. */
    data class Progress(val text: String, val pieces: Int, val elapsedMs: Long, val firstTokenMs: Long?) : RunUpdate
    data class Finished(val record: RunRecord) : RunUpdate
}

/**
 * Runs one Workbench experiment through the app's [LlmService]: unloads first for a cold start when asked, reads
 * the device before and after, streams the answer (the backend goes on the request), checks every proposed tool
 * call against the offered tools (nothing runs), and always ends with [RunUpdate.Finished], also when cancelled
 * (CANCELLED) or failed (ERROR). Shared by the Run tab and the sweeps.
 *
 * [probe] reads the device (DeviceProbe.sample in the app, a fake in tests); [clock] is a monotonic millisecond
 * clock, [wallClock] the time of day for [RunRecord.startedAtMs]; both injectable for tests (QA).
 */
class LlmRunner(
    private val service: LlmService,
    private val probe: () -> DeviceSample? = { null },
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallClock: () -> Long = { System.currentTimeMillis() },
) {
    /** Runs [settings]; returns the record that [onUpdate] also got as Finished. Rethrows cancellation after reporting. */
    suspend fun run(settings: RunSettings, onUpdate: (RunUpdate) -> Unit): RunRecord {
        val startedAt = wallClock()
        val start = clock()
        val before = probe()
        val text = StringBuilder()
        var pieces = 0
        var firstToken: Long? = null
        val calls = mutableListOf<ToolCallRecord>()
        var stats: LlmStats? = null
        var error: LlmEvent.Error? = null

        fun finish(stop: LlmStopReason): RunRecord {
            val r = RunRecord(
                settings = settings, startedAtMs = startedAt, text = text.toString(), toolCalls = calls.toList(),
                stats = stats?.copy(stopReason = stop), stopReason = stop, error = error,
                wallMs = clock() - start, before = before, after = probe(),
            )
            onUpdate(RunUpdate.Finished(r))
            return r
        }

        try {
            val request = try {
                settings.request()
            } catch (e: IllegalArgumentException) {
                error = LlmEvent.Error(LlmError.INFERENCE_FAILED, e.message ?: "bad settings")
                return finish(LlmStopReason.ERROR)
            }
            if (settings.coldStart) service.unload()
            onUpdate(RunUpdate.Progress("", 0, clock() - start, null))
            service.stream(request).collect { e ->
                when (e) {
                    is LlmEvent.Token -> {
                        if (firstToken == null) firstToken = clock() - start
                        text.append(e.text)
                        pieces++
                        onUpdate(RunUpdate.Progress(text.toString(), pieces, clock() - start, firstToken))
                    }
                    is LlmEvent.ToolCallProposed -> calls += ToolCallRecord(e.name, e.argsJson, checkToolCall(settings.tools, e))
                    is LlmEvent.Done -> stats = e.stats
                    is LlmEvent.Error -> error = e
                }
            }
        } catch (e: CancellationException) {
            finish(LlmStopReason.CANCELLED)
            throw e
        }
        return finish(
            when {
                error != null -> LlmStopReason.ERROR
                else -> stats?.stopReason ?: LlmStopReason.DONE
            },
        )
    }
}

/** Plain-text view of a run, for "Copy run" and the log. English by design (pasted into chats); no identifiers. */
object RunReport {
    fun text(r: RunRecord): String = buildString {
        val s = r.settings
        val st = r.stats
        appendLine("RiskMandate Workbench run")
        appendLine("Where: ${if ((st?.location ?: LlmLocation.LOCAL) == LlmLocation.LOCAL) "on this device" else "Cloud \u00b7 ${st?.modelId ?: "-"} via openrouter.ai"}" + (st?.costUsd?.let { String.format(Locale.ROOT, "  cost: %.5f USD", it) } ?: ""))
        appendLine("Model: ${st?.modelId ?: "-"}  backend: ${st?.backend ?: "-"} (asked ${s.backend.name.lowercase(Locale.ROOT)})" + (if (st?.fellBackToCpu == true) "  GPU failed, fell back to CPU" else "") + if (s.coldStart) "  cold start asked" else "")
        appendLine("Sampling: temperature ${num(s.sampling.temperature)}, top-k ${s.sampling.topK}, top-p ${num(s.sampling.topP)}, seed ${s.sampling.seed ?: "-"}, max tokens ${s.maxTokens}")
        appendLine("Tools offered: ${if (s.tools.isEmpty()) "none" else s.tools.joinToString { it.name }}")
        appendLine("Stop: ${r.stopReason.name.lowercase(Locale.ROOT).replace('_', ' ')}" + (r.error?.let { "  (${it.reason.name}: ${it.message.take(200)})" } ?: ""))
        appendLine("Load: " + when {
            st == null -> "-"
            st.loadedModel -> "cold, ${ms(st.loadMs)} (measured)"
            else -> "warm (model already loaded)"
        })
        appendLine("Time to first token: ${ms(st?.timeToFirstTokenMs)} (measured)  total: ${ms(st?.totalMs)} (measured)  wall, load included: ${ms(r.wallMs)}")
        appendLine("Input tokens: ${st?.promptTokens ?: "-"}  output tokens: ${st?.outputTokens ?: "-"} (${src(st?.countsFrom)})")
        appendLine("Prefill: ${tps(st?.prefillTokensPerSecond)} tok/s  decode: ${tps(st?.decodeTokensPerSecond)} tok/s (${src(st?.speedsFrom)})")
        appendLine("Device before: ${dev(r.before)}")
        appendLine("Device after: ${dev(r.after)}")
        if (r.toolCalls.isNotEmpty()) {
            appendLine("Tool calls proposed (none ran):")
            r.toolCalls.forEach { c ->
                val v = when (val x = c.verdict) {
                    is ToolCheck.Accepted -> "accepted"
                    is ToolCheck.Rejected -> "rejected: ${x.reason}"
                }
                appendLine("  ${c.name.take(40)} ${c.argsJson.take(300)}  -> $v")
            }
        }
        appendLine("System prompt: ${if (s.systemPrompt.isBlank()) "none" else "${s.systemPrompt.length} characters"}")
        appendLine("Prompt: ${s.userPrompt.take(500)}")
        append("Answer: ${r.text.take(2000)}")
    }

    private fun num(d: Double) = String.format(Locale.ROOT, "%.2f", d)
    private fun ms(v: Long?) = v?.let { if (it >= 10_000) String.format(Locale.ROOT, "%.1f s", it / 1000.0) else "$it ms" } ?: "-"
    private fun tps(v: Double?) = v?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "-"
    private fun src(s: StatSource?) = when (s) {
        StatSource.RUNTIME -> "from the runtime"
        StatSource.MEASURED -> "measured"
        null -> "-"
    }
    private fun dev(d: DeviceSample?) = if (d == null) "-" else
        "app memory ${d.memory.rssMb ?: "-"} MB (peak ${d.memory.peakRssMb ?: "-"}), battery ${d.batteryPct?.let { "$it%" } ?: "-"}" +
            (if (d.charging == true) " charging" else "") + ", thermal ${d.thermal.name.lowercase(Locale.ROOT)}"
}
