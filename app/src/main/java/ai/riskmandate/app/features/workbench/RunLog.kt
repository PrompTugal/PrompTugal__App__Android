package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.llm.RunRecord
import ai.riskmandate.app.llm.StrictJson
import java.io.File

/**
 * One line of the run log: the settings and the key figures of a run, small enough to keep hundreds. No
 * identifiers; the prompt and answer are kept short. Written as one strict JSON object per line.
 */
data class LogEntry(
    val startedAtMs: Long,
    /** "run" for the Run tab, or the experiment value's label. */
    val label: String,
    val model: String,
    val backend: String,
    val fellBack: Boolean,
    val temperature: Double,
    val topK: Long,
    val topP: Double,
    val maxTokens: Long,
    val systemChars: Long,
    val tools: Long,
    val stop: String,
    val error: String,
    val cold: Boolean,
    val loadMs: Long?,
    val firstTokenMs: Long?,
    val totalMs: Long?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val decodeTps: Double?,
    val prefillTps: Double?,
    val sources: String,
    val toolCalls: Long,
    val accepted: Long,
    val prompt: String,
    val answer: String,
    /** True when the cloud model answered (older entries without the field read as on the device). */
    val cloud: Boolean = false,
) {
    fun toJson(): String = StrictJson.canonical(
        linkedMapOf(
            "v" to 1L, "t" to startedAtMs, "label" to label, "model" to model, "backend" to backend, "fell" to fellBack,
            "temp" to temperature, "topk" to topK, "topp" to topP, "max" to maxTokens, "sys" to systemChars, "tools" to tools,
            "stop" to stop, "err" to error, "cold" to cold, "load" to loadMs, "ttft" to firstTokenMs, "total" to totalMs,
            "in" to inputTokens, "out" to outputTokens, "dec" to decodeTps, "pre" to prefillTps, "src" to sources,
            "calls" to toolCalls, "ok" to accepted, "prompt" to prompt, "answer" to answer,
        ).apply { if (cloud) put("cloud", true) }, // only on cloud entries: on-device lines stay as before
    )

    companion object {
        const val PROMPT_CHARS = 200
        const val ANSWER_CHARS = 300

        fun of(label: String, r: RunRecord): LogEntry {
            val s = r.stats
            return LogEntry(
                startedAtMs = r.startedAtMs, label = label.take(40),
                model = s?.modelId ?: "-", backend = s?.backend ?: r.settings.backend.name.lowercase(),
                fellBack = s?.fellBackToCpu == true,
                temperature = r.settings.sampling.temperature, topK = r.settings.sampling.topK.toLong(), topP = r.settings.sampling.topP,
                maxTokens = r.settings.maxTokens.toLong(), systemChars = r.settings.systemPrompt.length.toLong(), tools = r.settings.tools.size.toLong(),
                stop = r.stopReason.name, error = r.error?.reason?.name ?: "",
                cold = s?.loadedModel == true, loadMs = s?.loadMs, firstTokenMs = s?.timeToFirstTokenMs, totalMs = s?.totalMs,
                inputTokens = s?.promptTokens?.toLong(), outputTokens = s?.outputTokens?.toLong(),
                decodeTps = s?.decodeTokensPerSecond, prefillTps = s?.prefillTokensPerSecond,
                sources = if (s == null) "" else "${s.countsFrom.name.lowercase()}/${s.speedsFrom.name.lowercase()}",
                toolCalls = r.toolCalls.size.toLong(), accepted = r.toolCalls.count { it.verdict is ai.riskmandate.app.llm.ToolCheck.Accepted }.toLong(),
                prompt = r.settings.userPrompt.take(PROMPT_CHARS), answer = r.text.take(ANSWER_CHARS),
                cloud = s?.location == ai.riskmandate.app.llm.LlmLocation.CLOUD,
            )
        }

        /** Strict: any line that is not exactly this shape is dropped, never half-read. */
        fun parse(line: String): LogEntry? = try {
            val m = StrictJson.parse(line, 8_000) as? Map<*, *> ?: throw IllegalArgumentException("not an object")
            if (m["v"] != 1L) throw IllegalArgumentException("version")
            fun str(k: String) = m[k] as? String ?: throw IllegalArgumentException(k)
            fun lng(k: String) = m[k] as? Long ?: throw IllegalArgumentException(k)
            fun lngOrNull(k: String) = m[k]?.let { it as? Long ?: throw IllegalArgumentException(k) }
            fun dbl(k: String) = (m[k] as? Number)?.toDouble() ?: throw IllegalArgumentException(k)
            fun dblOrNull(k: String) = m[k]?.let { (it as? Number)?.toDouble() ?: throw IllegalArgumentException(k) }
            fun bool(k: String) = m[k] as? Boolean ?: throw IllegalArgumentException(k)
            LogEntry(
                lng("t"), str("label").take(40), str("model"), str("backend"), bool("fell"), dbl("temp"), lng("topk"), dbl("topp"), lng("max"),
                lng("sys"), lng("tools"), str("stop"), str("err"), bool("cold"), lngOrNull("load"), lngOrNull("ttft"), lngOrNull("total"),
                lngOrNull("in"), lngOrNull("out"), dblOrNull("dec"), dblOrNull("pre"), str("src"), lng("calls"), lng("ok"),
                str("prompt").take(PROMPT_CHARS), str("answer").take(ANSWER_CHARS),
                cloud = m["cloud"]?.let { it as? Boolean ?: throw IllegalArgumentException("cloud") } ?: false,
            )
        } catch (e: StrictJson.JsonException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/**
 * The run log on disk: `noBackupFilesDir/feature_workbench/runs.jsonl` (never backed up or moved to another
 * device), newest last on disk and newest first on screen, at most [MAX_ENTRIES]. App-private; no export path.
 * Blocking: call off the main thread.
 */
class RunLogStore(private val dir: File) {
    private val file get() = File(dir, FILE)

    fun load(): List<LogEntry> {
        val f = file
        if (!f.isFile || f.length() > MAX_BYTES) return emptyList()
        return f.readLines().mapNotNull { LogEntry.parse(it) }.takeLast(MAX_ENTRIES).reversed()
    }

    fun append(e: LogEntry) {
        dir.mkdirs()
        file.appendText(e.toJson() + "\n")
        // Keep the file bounded: rewrite with the newest entries once it grows past the cap.
        val lines = file.readLines()
        if (lines.size > MAX_ENTRIES + TRIM_SLACK) file.writeText(lines.takeLast(MAX_ENTRIES).joinToString("\n", postfix = "\n"))
    }

    fun clear() {
        file.delete()
    }

    companion object {
        const val DIR = "feature_workbench"
        const val FILE = "runs.jsonl"
        const val MAX_ENTRIES = 500
        private const val TRIM_SLACK = 50
        private const val MAX_BYTES = 4L * 1024 * 1024
    }
}
