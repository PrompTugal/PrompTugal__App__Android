package ai.riskmandate.app.experiments

/** The three layers that judge a result, in the order they run. */
enum class JudgeLayer {
    CODE, LOCAL, CLOUD;

    /** Where the service behind a model layer must run (the code layer has none and is never given a service). */
    val location: ai.riskmandate.app.llm.LlmLocation
        get() = if (this == CLOUD) ai.riskmandate.app.llm.LlmLocation.CLOUD else ai.riskmandate.app.llm.LlmLocation.LOCAL
}

/** [UNSURE] is a verdict too: the judge could not tell, or did not run, or its answer could not be read. Never read as a pass. */
enum class JudgeOutcome { PASS, FAIL, UNSURE }

/**
 * One layer's verdict on one run. [modelId] and [promptVersion] say what judged (null for the code layer, which has
 * no model and no prompt). [reason] is plain text, one short sentence, cut at [MAX_REASON].
 */
data class JudgeVerdict(
    val layer: JudgeLayer,
    val outcome: JudgeOutcome,
    val reason: String,
    val modelId: String? = null,
    val promptVersion: String? = null,
) {
    companion object {
        const val MAX_REASON = 300
    }
}

/** A judge of a run of a tool-test case. The code judge is pure; the model judges call the model they were given. */
interface Judge {
    val layer: JudgeLayer
    suspend fun judge(case: ToolCase, output: CaseRun): JudgeVerdict
}

/** Two layers that both decided and decided differently, recorded as data (case, run, and both verdicts). */
data class Disagreement(val caseId: String, val kind: String, val mode: ToolMode, val rep: Int, val a: JudgeVerdict, val b: JudgeVerdict)

/**
 * What the panel made of one run: every layer's verdict in order, the [final] outcome and which layer it came from.
 * [final] is the code layer's when it decided (it is authoritative); otherwise it is [JudgeOutcome.UNSURE] and the
 * model verdicts are shown side by side, never promoted.
 */
data class PanelResult(
    val caseId: String,
    val kind: String,
    val mode: ToolMode,
    val rep: Int,
    val verdicts: List<JudgeVerdict>,
    val final: JudgeOutcome,
    val decidedBy: JudgeLayer?,
    val disagreements: List<Disagreement>,
)
