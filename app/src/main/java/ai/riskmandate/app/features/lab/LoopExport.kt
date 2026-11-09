package ai.riskmandate.app.features.lab

import ai.riskmandate.app.llm.StrictJson

/**
 * Loop results as `abp-lab-loop-export/1` (eval/FORMATS.md): the runs as run records, so one report and one
 * oracle serve the tablet like any other backend. For staff, shared only by an explicit tap. It carries the
 * scenario id, version and hash, settings, counts, verdicts, timings and the model's answers to the scenario's
 * own fixed strings; no device identifier (only what [device] holds, which the caller keeps to the OS version),
 * no account, no user text other than a typed instruction already part of a run's prompt.
 */
object LoopExport {
    fun build(
        scenario: Scenario,
        outcome: LoopRunner.Outcome,
        exportedAt: String,
        appVersionName: String,
        appVersionCode: Int,
        device: Map<String, Any?> = emptyMap(),
    ): String {
        val first = outcome.records.firstOrNull()
        val runs = outcome.records.flatMap { r -> listOf(runRecord(scenario, r, "raw", r.raw, exportedAt), runRecord(scenario, r, "abp", r.abp, exportedAt)) }
        val doc = linkedMapOf<String, Any?>(
            "format" to "abp-lab-loop-export/1",
            "exported_at" to exportedAt,
            "device" to device,
            "app" to mapOf("versionName" to appVersionName, "versionCode" to appVersionCode),
            "runtime" to mapOf("name" to "litert-lm", "backend" to (first?.backend ?: "unknown")),
            "model" to mapOf("id" to (first?.modelId ?: "unknown"), "label" to (first?.modelId ?: "unknown"), "quantisation" to "unknown"),
            "scenario" to scenarioRef(scenario),
            "settings" to (first?.settings?.let(::settings) ?: emptyMap<String, Any?>()),
            "battery" to mapOf("start_pct" to outcome.counters.batteryStartPct, "end_pct" to outcome.counters.batteryEndPct),
            "stop_reason" to outcome.stop.name.lowercase(),
            "not_finished" to outcome.counters.notFinished,
            "runs" to runs,
        )
        return StrictJson.render(doc)
    }

    private fun scenarioRef(s: Scenario) = mapOf("id" to s.id, "version" to s.version, "hash" to s.hash)

    private fun settings(s: LiveRunner.Settings) = mapOf(
        "temperature" to s.sampling.temperature, "top_p" to s.sampling.topP, "top_k" to s.sampling.topK,
        "seed" to s.sampling.seed, "max_tokens" to s.maxTokens, "tool_mode" to "native",
    )

    private fun runRecord(s: Scenario, r: LoopRunner.RunRecord, path: String, p: PathRecord, at: String): Map<String, Any?> {
        val trace = LabFormats.traceOf(p.events)
        return linkedMapOf(
            "schema" to "abp-eval-run/1",
            "run_id" to "device-${r.index}-$path",
            "ts" to at,
            "scenario" to scenarioRef(s),
            "model" to mapOf("id" to (r.modelId ?: "unknown"), "label" to (r.modelId ?: "unknown")),
            "backend" to "litert-lm-device-${r.backend ?: "unknown"}",
            "path" to path,
            "rep" to r.index,
            "settings" to settings(r.settings),
            "prompt" to mapOf("system" to s.system, "user" to s.user),
            "steps" to steps(p.events),
            "final_text" to trace.finalText,
            "abp_flags" to trace.flags.map { mapOf("rule" to it.rule, "claim" to it.claim, "reason" to it.reason, "text" to it.text) },
            "total_ms" to r.ms,
            "outcome" to mapOf("failure" to p.outcome.failure, "attempted" to p.outcome.attempted, "caught" to p.outcome.caught, "heuristic" to p.outcome.heuristic),
        )
    }

    /** Events grouped into model steps (every event of one step carries the same time offset). */
    private fun steps(events: List<LabEvent>): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>()
        for ((t, group) in events.filter { it !is LabEvent.Flag && it !is LabEvent.Final }.groupBy { it.tMs }) {
            val calls = ArrayList<Map<String, Any?>>()
            var cur: LinkedHashMap<String, Any?>? = null
            for (e in group) when (e) {
                is LabEvent.CallProposed -> { cur?.let { calls += it }; cur = linkedMapOf("name" to e.name, "arguments" to e.arguments, "valid" to e.valid, "verdict" to null, "executed" to false, "result" to null) }
                is LabEvent.VerdictEvent -> cur?.set("verdict", mapOf("action" to e.action, "rule" to e.rule, "reason" to e.reason))
                is LabEvent.ToolResult -> { cur?.set("executed", e.executed); cur?.set("result", e.result) }
                else -> Unit
            }
            cur?.let { calls += it }
            out += mapOf("assistant_text" to group.filterIsInstance<LabEvent.ModelText>().joinToString("\n") { it.text }, "latency_ms" to t, "tool_calls" to calls)
        }
        return out
    }
}
