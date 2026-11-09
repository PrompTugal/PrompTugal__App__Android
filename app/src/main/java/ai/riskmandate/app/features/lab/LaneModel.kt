package ai.riskmandate.app.features.lab

import ai.riskmandate.app.R
import ai.riskmandate.app.ui.ChipKind
import androidx.annotation.StringRes

/**
 * One row of a lane, ready to draw. Pure: built from a path's events, the scenario and the scorer, so the
 * verdict words come from the recorded or computed verdicts and never from typed text. The same index in
 * the raw lane and the ABP lane is the same step.
 */
data class LaneRow(
    /** What the model said just before this step (plain text). */
    val say: String?,
    @StringRes val titleRes: Int,
    val titleArg: String?,
    val detail: String?,
    @StringRes val chipRes: Int,
    val chipArg: Int?,
    val kind: ChipKind,
    /** The policy's reason, in the policy's own words, for a stopped or flagged step. */
    val note: String?,
    /** This row is where the path went outside the policy (raw) or where it was stopped or flagged (ABP). */
    val decisive: Boolean,
)

object LaneModel {
    fun rows(scenario: Scenario, path: PathRecord, abp: Boolean): List<LaneRow> {
        val trace = LabFormats.traceOf(path.events)
        val failures = Oracle.failureEvents(scenario, trace)
        val failedCalls = failures.mapNotNull { it.call }.toSet()
        val failedClaims = failures.any { it.claim != null }
        val rows = ArrayList<LaneRow>()
        var say: String? = null
        var callIdx = -1
        var i = 0
        val ev = path.events
        while (i < ev.size) {
            when (val e = ev[i]) {
                is LabEvent.ModelText -> say = e.text
                is LabEvent.CallProposed -> {
                    callIdx++
                    var verdict: LabEvent.VerdictEvent? = null
                    var result: LabEvent.ToolResult? = null
                    var j = i + 1
                    while (j < ev.size && ev[j] !is LabEvent.CallProposed && ev[j] !is LabEvent.Final && ev[j] !is LabEvent.Flag && ev[j] !is LabEvent.ModelText) {
                        (ev[j] as? LabEvent.VerdictEvent)?.let { verdict = it }
                        (ev[j] as? LabEvent.ToolResult)?.let { result = it }
                        j++
                    }
                    val args = if (e.arguments.isEmpty()) "" else StrictJson.render(e.arguments)
                    rows += callRow(say, e, args, verdict, result, abp, callIdx in failedCalls)
                    say = null
                    i = j - 1
                }
                is LabEvent.Final -> {
                    val flags = ev.filterIsInstance<LabEvent.Flag>()
                    rows += finalRow(say, e, flags, abp, failedClaims)
                    say = null
                }
                else -> Unit // verdicts, results and flags are consumed with their call or the answer
            }
            i++
        }
        return rows
    }

    private fun callRow(say: String?, e: LabEvent.CallProposed, args: String, v: LabEvent.VerdictEvent?, r: LabEvent.ToolResult?, abp: Boolean, failed: Boolean): LaneRow {
        val title = R.string.lab_step_proposed
        if (!e.valid) return LaneRow(say, title, e.name, args.ifEmpty { null }, R.string.lab_chip_refused, null, ChipKind.Held, r?.result, false)
        if (!abp) {
            return if (failed) LaneRow(say, title, e.name, args.ifEmpty { null }, R.string.lab_chip_failure, null, ChipKind.Blocked, null, true)
            else LaneRow(say, title, e.name, args.ifEmpty { null }, R.string.lab_chip_ran, null, ChipKind.Neutral, null, false)
        }
        return when (v?.action) {
            "block" -> LaneRow(say, title, e.name, args.ifEmpty { null }, R.string.lab_chip_blocked, null, ChipKind.Blocked, v.reason, true)
            "hold" -> LaneRow(say, title, e.name, args.ifEmpty { null }, R.string.lab_chip_held, null, ChipKind.Held, v.reason, true)
            else -> LaneRow(say, title, e.name, args.ifEmpty { null }, R.string.lab_chip_allowed, null, ChipKind.Allowed, null, false)
        }
    }

    private fun finalRow(say: String?, e: LabEvent.Final, flags: List<LabEvent.Flag>, abp: Boolean, failedClaims: Boolean): LaneRow {
        val title = R.string.lab_step_answer
        val text = e.text.ifBlank { null }
        if (!abp) {
            return if (failedClaims) LaneRow(say, title, null, text, R.string.lab_chip_failure_answer, null, ChipKind.Blocked, null, true)
            else LaneRow(say, title, null, text, R.string.lab_chip_answer, null, ChipKind.Neutral, null, false)
        }
        return if (flags.isNotEmpty())
            LaneRow(say, title, null, text, R.string.lab_chip_flagged, flags.size, ChipKind.Redacted, flags.joinToString("\n") { it.reason }, true)
        else LaneRow(say, title, null, text, R.string.lab_chip_answer, null, ChipKind.Neutral, null, false)
    }

    /** 1-based number of the first row where the path went outside the policy or was stopped, or null. */
    fun decisiveStep(rows: List<LaneRow>): Int? = rows.indexOfFirst { it.decisive }.takeIf { it >= 0 }?.plus(1)
}
