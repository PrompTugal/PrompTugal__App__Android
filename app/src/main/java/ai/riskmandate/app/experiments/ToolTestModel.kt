package ai.riskmandate.app.experiments

/**
 * Tool tests: small deterministic functional evals of tool calling on the on-device model (Workbench tab 3).
 * A case is data (JSON in assets, format `abp-tooltest/1`, owned by dev.lab); what passes is code (see
 * [ToolChecks]), never a model. Tools are fakes: nothing executes for real. Not an ABP test: there is no
 * policy here, only "did the model call the right tool the right way".
 */

/** Two ways to ask for a tool call, both measured. */
enum class ToolMode {
    /** The runtime's own tool calling: the tools are declared to the runtime and proposals arrive as ToolCallProposed. */
    NATIVE,
    /** A text protocol: the tools are described in the system prompt and the model writes a JSON call in its answer. */
    TEXT,
}

/** A call in a case's history or in a reference run: a tool name and its arguments. */
data class ToolMsgCall(val name: String, val arguments: Map<String, Any?>)

/** One message of a case's starting conversation. [role] is "user", "assistant" or "tool". */
data class ToolMsg(val role: String, val text: String, val calls: List<ToolMsgCall> = emptyList(), val tool: String? = null)

/**
 * One check of a case, in the catalogue's own words. [type] is one of [ToolChecks.TYPES]; [params] are its
 * arguments (see the format in assets/feature_lab/tooltests/README.md); [label] is a one-line description
 * shown with a failure.
 */
data class CaseCheck(val type: String, val params: Map<String, Any?>, val label: String)

/** One scripted step of a reference or wrong run, for the JVM tests over FakeLlmService: what to say and call. */
data class RefStep(val text: String, val calls: List<ToolMsgCall>)

data class ToolCase(
    val id: String,
    val kind: String,
    /** One line: what this case tests. */
    val tests: String,
    val system: String,
    val messages: List<ToolMsg>,
    val tools: List<ToolSpec>,
    val fakes: Map<String, FakeSpec>,
    val checks: List<CaseCheck>,
    val maxSteps: Int,
    /** A run that must pass every check, and one that must fail at least one: CI checks the checks with them. */
    val reference: List<RefStep>?,
    val wrong: List<RefStep>?,
    /** Optional: what to ask a model judge about the output and the verdict expected (reserved for the judge layers). */
    val judges: JudgeSpec? = null,
)

/** What a model judge is asked about a case's output, and the verdict a correct judge gives for the reference run. */
data class JudgeSpec(val ask: String, val expect: String)

data class ToolKind(val id: String, val title: String)

/** What the catalogue holds, and the files it could not read. [hash] ties results to this exact catalogue. */
data class ToolCatalogue(val kinds: List<ToolKind>, val cases: List<ToolCase>, val problems: List<String>, val hash: String) {
    fun casesOf(kind: String) = cases.filter { it.kind == kind }
    fun case(id: String) = cases.firstOrNull { it.id == id }
}

/** One call the model made in a run, with what became of it. [argsJson] is the argument text as the model produced it. */
data class CallRecord(
    val name: String,
    val argsJson: String,
    val args: Map<String, Any?>,
    /** Typed arguments when the shared check accepted the call; null when it was refused. */
    val typed: Map<String, Any>?,
    val refusal: String?,
    val result: String?,
) {
    val accepted: Boolean get() = typed != null
}

data class StepRecord(val text: String, val calls: List<CallRecord>)

/**
 * [kind] is the check's kind; [failure] is the lowercase name of what went wrong (see [ToolChecks.failureKey]) when
 * it failed, null when it passed. [detail] is plain English for a person reading the raw result.
 */
data class CheckResult(val label: String, val passed: Boolean, val detail: String, val kind: String = "", val failure: String? = null)

/**
 * One run of one case in one mode. [error] set means it did not finish (the service failed): it counts in
 * no pass rate. [passed] needs a finished run and every check passing.
 */
data class CaseRun(
    val caseId: String,
    val kind: String,
    val mode: ToolMode,
    val rep: Int,
    val steps: List<StepRecord>,
    val finalText: String,
    val ms: Long,
    val modelId: String?,
    val backend: String?,
    val outputTokens: Int?,
    val decodeTokensPerSecond: Double?,
    val error: String?,
    val checks: List<CheckResult>,
    /** Characters of text sent in the first request in this mode (see [PromptSize]). */
    val promptChars: Int = 0,
    /** Prompt tokens the runtime reported for the first request, or null when it did not report them. */
    val promptTokens: Int? = null,
) {
    val finished: Boolean get() = error == null
    val passed: Boolean get() = finished && checks.isNotEmpty() && checks.all { it.passed }
    val calls: List<CallRecord> get() = steps.flatMap { it.calls }
}
