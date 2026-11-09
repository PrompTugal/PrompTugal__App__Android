package ai.riskmandate.app.experiments

import ai.riskmandate.app.llm.LlmMessage
import ai.riskmandate.app.llm.LlmRequest
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.Sampling
import kotlinx.coroutines.CancellationException

/**
 * Layer 1: the code checks, pure. Authoritative wherever it can decide: it re-runs [ToolChecks] on what the model
 * did and passes only if every check passes. A run that did not finish, or a case with no checks, is
 * [JudgeOutcome.UNSURE]: code cannot decide there, and the model layers are shown side by side instead.
 */
class CodeJudge : Judge {
    override val layer = JudgeLayer.CODE

    override suspend fun judge(case: ToolCase, output: CaseRun): JudgeVerdict {
        if (!output.finished) return JudgeVerdict(layer, JudgeOutcome.UNSURE, "the run did not finish, so the checks could not decide")
        val results = ToolChecks.evaluate(case, output.steps, output.finalText)
        if (results.isEmpty()) return JudgeVerdict(layer, JudgeOutcome.UNSURE, "the case has no checks")
        val failed = results.filter { !it.passed }
        return if (failed.isEmpty()) JudgeVerdict(layer, JudgeOutcome.PASS, "all ${results.size} checks passed")
        else JudgeVerdict(layer, JudgeOutcome.FAIL, ("failed: " + failed.joinToString("; ") { it.failure ?: it.kind }).take(JudgeVerdict.MAX_REASON))
    }
}

/**
 * Layers 2 and 3: a model asked to judge. The judge gets a short, versioned prompt ([JudgePrompt]) with the case's
 * one-line "what this tests", the question, the tools offered, what the user asked and the output under test as
 * delimited data, and nothing else: no device data, no other feature's text, no case system prompt. Its reply is read
 * strictly ([JudgeReply]); anything unreadable, an error, or an unfinished run is [JudgeOutcome.UNSURE], never a
 * pass. The output under test is untrusted: a judge can be argued with by it, which is why a model verdict is
 * recorded beside the code verdict and never overrides it ([JudgePanel]).
 */
open class ModelJudge(override val layer: JudgeLayer, private val llm: LlmService, private val prompt: JudgePrompt, private val maxTokens: Int = MAX_TOKENS) : Judge {
    override suspend fun judge(case: ToolCase, output: CaseRun): JudgeVerdict {
        fun unsure(why: String, modelId: String? = null) = JudgeVerdict(layer, JudgeOutcome.UNSURE, why, modelId, prompt.version)
        if (!output.finished) return unsure("the run did not finish, so there was nothing to judge")
        val user = prompt.fill(
            mapOf(
                "tests" to case.tests,
                "ask" to (case.judges?.ask ?: "Does the output do what this test checks?"),
                "tools" to JudgeOutput.tools(case),
                "request" to JudgeOutput.request(case),
                "output" to JudgeOutput.render(output),
            ),
        )
        val res = try {
            llm.generate(LlmRequest(listOf(LlmMessage.system(prompt.system), LlmMessage.user(user)), emptyList(), Sampling.GREEDY, maxTokens))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return unsure("the judge did not run")
        }
        val modelId = res.stats?.modelId
        res.error?.let { return unsure("the judge did not run: ${it.reason.name.lowercase().replace('_', ' ')}", modelId) }
        return JudgeReply.parse(res.text, layer, modelId, prompt.version)
    }

    companion object {
        const val MAX_TOKENS = 160
    }
}

/** Layer 2: the on-device model as judge. [llm] is the on-device service (`context.llm`). */
class LocalModelJudge(llm: LlmService, prompt: JudgePrompt) : ModelJudge(JudgeLayer.LOCAL, llm, prompt)

/**
 * Layer 3: a cloud model as judge. [llm] is the cloud service (`context.cloudLlm`); this class does no networking
 * of its own. It sees only the synthetic case text and the output under test. Anything it produces is shown with
 * the "Cloud" badge by the screen.
 */
class CloudJudge(llm: LlmService, prompt: JudgePrompt) : ModelJudge(JudgeLayer.CLOUD, llm, prompt)

/** The text a judge is shown: plain, bounded, and built only from the synthetic case and the output under test. */
object JudgeOutput {
    private const val MAX_CHARS = 2_000
    private const val MAX_RESULT = 200

    fun tools(case: ToolCase): String = case.tools.joinToString(", ") { t -> t.name + "(" + t.params.joinToString(", ") { it.name + if (it.required) "" else "?" } + ")" }

    /** What the user asked: the case's user messages. */
    fun request(case: ToolCase): String = case.messages.filter { it.role == "user" }.joinToString(" / ") { it.text }.take(500)

    fun render(run: CaseRun): String {
        val lines = ArrayList<String>()
        for (s in run.steps) {
            if (s.text.isNotBlank()) lines += "model: ${s.text.trim()}"
            for (c in s.calls) {
                lines += "call: ${c.name} ${c.argsJson} -> " + if (c.accepted) "accepted" else "refused (${c.refusal})"
                c.result?.takeIf { c.accepted }?.let { lines += "tool result: ${it.take(MAX_RESULT)}" }
            }
        }
        lines += "final answer: ${run.finalText.trim().ifEmpty { "(none)" }}"
        val text = lines.joinToString("\n")
        return if (text.length > MAX_CHARS) text.take(MAX_CHARS) + "…" else text
    }
}

/**
 * Runs the judges in layer order (code, then local, then cloud) and records what they said. Rules (architect):
 * the code verdict is authoritative where it can decide; model verdicts are recorded but never override it; where
 * code cannot decide the result is [JudgeOutcome.UNSURE] with the model verdicts side by side. Every disagreement
 * between two layers that both decided is recorded, to be counted per case and per kind ([JudgeReport]).
 */
class JudgePanel(judges: List<Judge>) {
    private val judges = judges.sortedBy { it.layer.ordinal }

    suspend fun run(case: ToolCase, output: CaseRun): PanelResult {
        val verdicts = judges.map { j ->
            try { j.judge(case, output) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                JudgeVerdict(j.layer, JudgeOutcome.UNSURE, "the judge failed to run")
            }
        }
        val code = verdicts.firstOrNull { it.layer == JudgeLayer.CODE && it.outcome != JudgeOutcome.UNSURE }
        val decided = verdicts.filter { it.outcome != JudgeOutcome.UNSURE }
        val disagreements = ArrayList<Disagreement>()
        for (i in decided.indices) for (j in i + 1 until decided.size) {
            if (decided[i].outcome != decided[j].outcome) disagreements += Disagreement(case.id, case.kind, output.mode, output.rep, decided[i], decided[j])
        }
        return PanelResult(case.id, case.kind, output.mode, output.rep, verdicts, code?.outcome ?: JudgeOutcome.UNSURE, code?.layer, disagreements)
    }
}

/** Disagreements counted per case and per kind, and how often each model layer agreed with the code layer. */
class JudgeReport(val results: List<PanelResult>) {
    val judged: Int get() = results.size
    val disagreements: List<Disagreement> get() = results.flatMap { it.disagreements }
    fun byCase(): Map<String, Int> = disagreements.groupingBy { it.caseId }.eachCount()
    fun byKind(): Map<String, Int> = disagreements.groupingBy { it.kind }.eachCount()

    /** (agreed, compared) for a model layer against the code layer, over the runs where both decided. */
    fun agreementWithCode(layer: JudgeLayer): Pair<Int, Int> {
        var agreed = 0; var compared = 0
        for (r in results) {
            val code = r.verdicts.firstOrNull { it.layer == JudgeLayer.CODE }?.outcome ?: continue
            val other = r.verdicts.firstOrNull { it.layer == layer }?.outcome ?: continue
            if (code == JudgeOutcome.UNSURE || other == JudgeOutcome.UNSURE) continue
            compared++
            if (code == other) agreed++
        }
        return agreed to compared
    }
}
