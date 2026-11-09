package ai.riskmandate.app.features.lab

/**
 * One model step as proposed: what it said, the tool calls it wants, the factual claims it made.
 * Output is text only: nothing in a turn is ever executed except through [FakeTools].
 */
data class Turn(val say: String, val calls: List<ToolCall> = emptyList(), val claims: List<Claim> = emptyList())

/** Why a claim or answer was flagged (as opposed to a call being blocked). */
enum class Flag { UNCITED, MISMATCH, UNKNOWN_CITE, CONTRADICTS_DEVICE }

/** What happened to one proposal. */
sealed interface Outcome {
    /** The fake tool ran (it only records the call). [violation]: the policy would have refused it. */
    data class Ran(val result: String, val violation: Boolean) : Outcome

    /** The ABP stopped the call before it ran. */
    data class Stopped(val reason: Block, val detail: String) : Outcome

    /** A claim shown as the model made it (raw path). [invented]: not in the fetched triplets. */
    data class ClaimShown(val invented: Boolean) : Outcome

    /** A claim checked on the ABP path. */
    data class ClaimChecked(val flag: Flag?) : Outcome

    /** The model's plain answer (scenario 5), raw. [remote]: it claims to run somewhere remote. */
    data class Answer(val remote: Boolean) : Outcome
}

data class Event(val step: Int, val say: String?, val call: ToolCall?, val claim: Claim?, val outcome: Outcome)

/** One path's result. [failed]: something outside policy happened (raw) or got through (ABP). */
data class PathResult(val events: List<Event>, val failed: Boolean, val stopped: Int)

/** Both paths for one run, as the loop counters will use them. */
data class RunResult(val raw: PathResult, val abp: PathResult) {
    /** The raw path did something outside policy. */
    val rawFailed get() = raw.failed
    /** The ABP path stopped at least one proposal. */
    val abpCaught get() = abp.stopped > 0
    /** Something outside policy got through on the ABP path (should never be true by construction). */
    val abpEscaped get() = abp.failed
}

/** The device's real state, shown beside scenario 5's answer. */
enum class Transport { WIFI, CELLULAR, ETHERNET, OTHER, NONE }

data class NetworkState(val connected: Boolean, val transport: Transport)

/** Fake tools: record the call, touch nothing real. Graph tools read the scenario's demo graph. */
class FakeTools(private val scenario: Scenario) {
    val recorded = mutableListOf<ToolCall>()
    val fetched = mutableListOf<Triplet>()

    fun run(call: ToolCall): String {
        recorded += call
        return when (call.name) {
            "find_node" -> {
                val name = call.args["name"].orEmpty()
                val hits = scenario.graph.filter { it.subject.equals(name, true) || it.obj.equals(name, true) }
                fetch(hits.take(3))
            }
            "get_neighbours" -> {
                val node = call.args["node"].orEmpty()
                fetch(scenario.graph.filter { it.subject.equals(node, true) || it.obj.equals(node, true) })
            }
            "read_section" -> "(fake) section text"
            "list_items", "read_item" -> "(fake) items listed"
            "lookup_order" -> "(fake) order 7: shipped"
            else -> "(fake) recorded, nothing happened"
        }
    }

    private fun fetch(t: List<Triplet>): String {
        t.forEach { if (it !in fetched) fetched += it }
        return if (t.isEmpty()) "(none)" else t.joinToString("; ") { "${it.id}: ${it.subject} ${it.relation} ${it.obj}" }
    }
}

object LabRunner {
    /** Runs [turns] down the raw path: every proposed call executes against the fakes. */
    fun runRaw(scenario: Scenario, turns: List<Turn>): PathResult = run(scenario, turns, abp = false, network = null)

    /** Runs [turns] down the ABP path: every call is checked first, claims are checked against what was fetched. */
    fun runAbp(scenario: Scenario, turns: List<Turn>, network: NetworkState? = null): PathResult =
        run(scenario, turns, abp = true, network = network)

    private fun run(scenario: Scenario, turns: List<Turn>, abp: Boolean, network: NetworkState?): PathResult {
        val tools = FakeTools(scenario)
        val events = mutableListOf<Event>()
        var failed = false
        var stopped = 0
        var step = 0
        for (turn in turns) {
            step++
            var say: String? = turn.say.ifBlank { null }
            for (call in turn.calls) {
                val verdict = AbpChecks.checkCall(scenario.policy, call)
                val outcome = when {
                    abp && verdict is Verdict.Deny -> { stopped++; Outcome.Stopped(verdict.reason, verdict.detail) }
                    else -> {
                        val violation = verdict is Verdict.Deny
                        if (violation) failed = true
                        Outcome.Ran(tools.run(call), violation)
                    }
                }
                events += Event(step, say, call, null, outcome); say = null
            }
            if (turn.claims.isNotEmpty()) {
                val status = AbpChecks.checkClaims(tools.fetched, turn.claims)
                turn.claims.forEachIndexed { i, claim ->
                    val outcome = if (abp) {
                        val flag = when (status[i]) {
                            ClaimStatus.CITED -> null
                            ClaimStatus.UNCITED -> Flag.UNCITED
                            ClaimStatus.MISMATCH -> Flag.MISMATCH
                            ClaimStatus.UNKNOWN_CITE -> Flag.UNKNOWN_CITE
                        }
                        if (flag != null) stopped++
                        Outcome.ClaimChecked(flag)
                    } else {
                        val invented = AbpChecks.isInvented(tools.fetched, claim)
                        if (invented) failed = true
                        Outcome.ClaimShown(invented)
                    }
                    events += Event(step, say, null, claim, outcome); say = null
                }
            } else if (scenario.selfKnowledge) {
                val remote = AbpChecks.claimsRemote(turn.say)
                if (abp) {
                    // The model runs on this device; the app shows the real network state beside the answer.
                    val contradicts = remote
                    if (contradicts) stopped++
                    events += Event(step, say, null, null, Outcome.ClaimChecked(if (contradicts) Flag.CONTRADICTS_DEVICE else null))
                } else {
                    if (remote) failed = true
                    events += Event(step, say, null, null, Outcome.Answer(remote))
                }
                say = null
            }
            if (say != null) events += Event(step, say, null, null, if (abp) Outcome.ClaimChecked(null) else Outcome.Answer(false))
        }
        // On the ABP path a denied call never runs, so [failed] stays false unless something got through.
        return PathResult(events, failed = failed, stopped = stopped)
    }

    fun runBoth(scenario: Scenario, rawTurns: List<Turn>, abpTurns: List<Turn>, network: NetworkState? = null) =
        RunResult(runRaw(scenario, rawTurns), runAbp(scenario, abpTurns, network))
}
