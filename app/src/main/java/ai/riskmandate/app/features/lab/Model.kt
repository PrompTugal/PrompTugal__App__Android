package ai.riskmandate.app.features.lab

import ai.riskmandate.app.experiments.Effect
import ai.riskmandate.app.experiments.FakeSpec
import ai.riskmandate.app.experiments.Graph
import ai.riskmandate.app.experiments.ToolSpec

/**
 * The Lab's data, in the shape of eval's formats (eval/FORMATS.md on origin/work/eval): scenarios
 * (abp-eval-scenario/1), the demo graph (abp-eval-graph/1) and replay recordings (abp-lab-recording/1).
 * Files are read by [LabFormats]; nothing here touches Android.
 */
enum class CheckType { TOOL_ALLOWLIST, READ_ONLY, EGRESS_RECIPIENTS, SECRET_EGRESS, CITATIONS_REQUIRED, STATE_CONSISTENCY }

/** BLOCK and HOLD stop a call before it runs (HOLD: held for approval); FLAG marks an answer. */
enum class Action { BLOCK, HOLD, FLAG }

data class Rule(
    val id: String,
    val check: CheckType,
    val onViolation: Action,
    val reason: String,
    val tools: Set<String> = emptySet(),
    val tool: String? = null,
    val arg: String? = null,
    val allowDomains: Set<String> = emptySet(),
    val patterns: List<String> = emptyList(),
)

data class Policy(val name: String, val note: String, val rules: List<Rule>)

enum class FailureType { TOOL_CALLED, EFFECT_CALLED, SECRET_IN_ARGS, UNSUPPORTED_CLAIM, CLAIMS_REMOTE }

data class Failure(
    val type: FailureType,
    val tools: Set<String> = emptySet(),
    val effects: Set<Effect> = emptySet(),
    val patterns: List<String> = emptyList(),
    val heuristic: Boolean = false,
)

data class Scenario(
    val id: String,
    val version: String,
    /** Content hash of the scenario as the harness computes it, so a recording can be tied to a version. */
    val hash: String,
    val title: String,
    val description: String,
    val system: String,
    val user: String,
    val tools: List<ToolSpec>,
    val fakeTools: Map<String, FakeSpec>,
    /** Path of the graph file, relative to the Lab's assets folder, when the scenario uses one. */
    val graphPath: String?,
    val graph: Graph?,
    val secrets: List<String>,
    /** The state the scenario states for the device (scenario 5). The screen shows the real state instead. */
    val deviceState: Map<String, Any?>?,
    val policy: Policy,
    val failure: Failure,
    val maxSteps: Int,
    val exposureTools: Set<String>,
) {
    fun effectOf(tool: String): Effect? = tools.firstOrNull { it.name == tool }?.effect
}

sealed interface Verdict {
    val action: String

    data object Allow : Verdict { override val action = "allow" }
    data class Block(val rule: String, val reason: String) : Verdict { override val action = "block" }
    data class Hold(val rule: String, val reason: String) : Verdict { override val action = "hold" }
}

/** A flag on a final answer (a claim without support, or an answer that contradicts the device). */
data class FinalFlag(val rule: String, val claim: Int, val reason: String, val text: String)

/** One entry of a replayed or live run, in order. [tMs] is the offset from the start of the run. */
sealed interface LabEvent {
    val tMs: Long

    data class ModelText(override val tMs: Long, val text: String) : LabEvent
    data class CallProposed(override val tMs: Long, val name: String, val arguments: Map<String, Any?>, val valid: Boolean) : LabEvent
    data class VerdictEvent(override val tMs: Long, val action: String, val rule: String?, val reason: String?) : LabEvent
    data class ToolResult(override val tMs: Long, val name: String, val executed: Boolean, val result: String) : LabEvent
    data class Flag(override val tMs: Long, val rule: String, val claim: Int, val reason: String, val text: String) : LabEvent
    data class Final(override val tMs: Long, val text: String) : LabEvent
}

/** What the scorer says about one path of a run. */
data class Outcome(val failure: Boolean, val attempted: Boolean, val caught: Boolean, val heuristic: Boolean = false)

data class PathRecord(val events: List<LabEvent>, val outcome: Outcome)

/**
 * A curated recording to replay. [sourceKind] is "hosted", "device" or "sample"; a sample was written by
 * hand to show the mechanism and is shown as such, never as a recording of a model.
 */
data class Recording(
    val id: String,
    val title: String,
    val label: String,
    val scenarioId: String,
    val scenarioHash: String,
    val sourceKind: String,
    val backend: String,
    val modelLabel: String?,
    val settings: Map<String, Any?>,
    val recordedAt: String?,
    val raw: PathRecord,
    val abp: PathRecord,
)
