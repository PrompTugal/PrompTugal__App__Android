package ai.riskmandate.app.experiments

import ai.riskmandate.app.llm.StrictJson

/**
 * Tool-test results in the Lab's export envelope (`abp-lab-loop-export/1`, run records `abp-eval-run/1`), so one
 * reader serves the loop and the tool tests and eval can read tablet results later. `kind` is "tool-tests"; each
 * run is a record whose `path` is the mode ("native" or "text") and whose `checks` and `outcome.failure` carry
 * the verdict. No device identifier: only what [device] holds, which the caller keeps to the OS version.
 */
object ToolTestExport {
    fun build(
        catalogueHash: String,
        outcome: ToolTestSuite.Outcome,
        settings: RunSettings,
        exportedAt: String,
        appVersionName: String,
        appVersionCode: Int,
        device: Map<String, Any?> = emptyMap(),
    ): String {
        val first = outcome.runs.firstOrNull { it.modelId != null }
        val doc = linkedMapOf<String, Any?>(
            "format" to "abp-lab-loop-export/1",
            "kind" to "tool-tests",
            "exported_at" to exportedAt,
            "device" to device,
            "app" to mapOf("versionName" to appVersionName, "versionCode" to appVersionCode),
            "runtime" to mapOf("name" to "litert-lm", "backend" to (first?.backend ?: "unknown")),
            "model" to mapOf("id" to (first?.modelId ?: "unknown"), "label" to (first?.modelId ?: "unknown"), "quantisation" to "unknown"),
            "scenario" to mapOf("id" to "tool-tests", "version" to "1", "hash" to catalogueHash),
            "settings" to settings(settings),
            "battery" to mapOf("start_pct" to outcome.batteryStartPct, "end_pct" to outcome.batteryEndPct),
            "stop_reason" to outcome.stop.name.lowercase(),
            "not_finished" to outcome.runs.count { !it.finished },
            "summary" to summary(outcome.report),
            "runs" to outcome.runs.mapIndexed { i, r -> record(r, i, catalogueHash, settings, exportedAt) },
        )
        return StrictJson.render(doc)
    }

    private fun settings(s: RunSettings) = mapOf(
        "temperature" to s.sampling.temperature, "top_p" to s.sampling.topP, "top_k" to s.sampling.topK,
        "seed" to s.sampling.seed, "max_tokens" to s.maxTokens,
    )

    private fun stat(s: PassStat) = mapOf("passed" to s.passed, "finished" to s.finished, "not_finished" to s.notFinished, "mean_ms" to s.meanMs)

    private fun summary(r: ToolTestReport): Map<String, Any?> = linkedMapOf(
        "overall" to ToolMode.entries.associate { it.name.lowercase() to stat(r.overall(it)) },
        "by_kind" to r.runs.map { it.kind }.distinct().associateWith { k -> ToolMode.entries.associate { it.name.lowercase() to stat(r.byKind(k, it)) } },
        "by_case" to r.runs.map { it.caseId }.distinct().associateWith { c -> ToolMode.entries.associate { it.name.lowercase() to stat(r.byCase(c, it)) } },
    )

    private fun record(r: CaseRun, i: Int, hash: String, s: RunSettings, at: String): Map<String, Any?> = linkedMapOf(
        "schema" to "abp-eval-run/1",
        "run_id" to "tooltest-$i-${r.caseId}-${r.mode.name.lowercase()}",
        "ts" to at,
        "scenario" to mapOf("id" to r.caseId, "version" to "1", "hash" to hash),
        "tool_test" to mapOf("kind" to r.kind, "case" to r.caseId),
        "model" to mapOf("id" to (r.modelId ?: "unknown"), "label" to (r.modelId ?: "unknown")),
        "backend" to "litert-lm-device-${r.backend ?: "unknown"}",
        "path" to r.mode.name.lowercase(),
        "rep" to r.rep,
        "settings" to settings(s) + ("tool_mode" to r.mode.name.lowercase()),
        "steps" to r.steps.map { st ->
            mapOf(
                "assistant_text" to st.text, "latency_ms" to null,
                "tool_calls" to st.calls.map { c -> mapOf("name" to c.name, "arguments" to c.args, "raw_arguments" to c.argsJson, "valid" to c.accepted, "verdict" to null, "executed" to c.accepted, "result" to c.result, "refusal" to c.refusal) },
            )
        },
        "final_text" to r.finalText,
        "checks" to r.checks.map { mapOf("label" to it.label, "passed" to it.passed, "detail" to it.detail) },
        "total_ms" to r.ms,
        "tokens" to mapOf("output" to r.outputTokens, "decode_tokens_per_second" to r.decodeTokensPerSecond),
        "error" to r.error,
        "outcome" to mapOf("failure" to (r.finished && !r.passed), "attempted" to r.finished, "caught" to false, "heuristic" to false, "passed" to r.passed),
    )
}
