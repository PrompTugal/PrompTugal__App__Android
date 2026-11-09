package ai.riskmandate.app.features.lab

import ai.riskmandate.app.experiments.StopReason

/** What one finished loop run says, from the scorer. Counted by code, never read from text. */
data class RunScore(
    /** The raw path went outside the policy. */
    val rawFailed: Boolean,
    /** The ABP path saw something to stop (the model tried it). */
    val attempted: Boolean,
    /** Everything the ABP path saw was stopped. */
    val caught: Boolean,
    /** Something outside the policy got through on the ABP path (should never be true). */
    val escaped: Boolean,
    val ms: Long,
) {
    companion object {
        fun of(raw: PathRecord, abp: PathRecord, ms: Long) =
            RunScore(raw.outcome.failure, abp.outcome.attempted, abp.outcome.caught, abp.outcome.failure, ms)
    }
}

/**
 * The loop's live counters. Only runs that finished count; a run that did not finish is tallied apart in
 * [notFinished] and never enters a rate. Replays are never added here.
 */
data class LoopCounters(
    /** Runs asked for. */
    val planned: Int,
    val runs: List<RunScore> = emptyList(),
    val notFinished: Int = 0,
    val batteryStartPct: Int? = null,
    val batteryEndPct: Int? = null,
) {
    val finished: Int get() = runs.size
    /** Runs where the raw path failed ("failed without ABP"). */
    val rawFailures: Int get() = runs.count { it.rawFailed }
    /** Runs where the ABP path had something to stop. */
    val attempts: Int get() = runs.count { it.attempted }
    /** Of those, runs where everything was stopped ("caught with ABP"). */
    val caught: Int get() = runs.count { it.attempted && it.caught }
    val escaped: Int get() = runs.count { it.escaped }
    /** X in "1 in X": finished runs per raw failure, or null when it never failed. */
    val oneInX: Double? get() = if (rawFailures == 0) null else finished.toDouble() / rawFailures
    /** Under 10 finished runs the figures are shown with a caution. */
    val smallSample: Boolean get() = finished < SMALL_SAMPLE
    val averageMs: Long? get() = if (runs.isEmpty()) null else runs.sumOf { it.ms } / runs.size
    /** Battery percentage points used, or null when either reading is missing. A charge reads as 0. */
    val batteryUsedPct: Int? get() = if (batteryStartPct == null || batteryEndPct == null) null else maxOf(0, batteryStartPct - batteryEndPct)

    fun withRun(s: RunScore, batteryPct: Int?) = copy(runs = runs + s, batteryEndPct = batteryPct ?: batteryEndPct)
    fun withFailedRun() = copy(notFinished = notFinished + 1)

    companion object {
        const val SMALL_SAMPLE = 10
    }
}

/**
 * Saves loop counters across a rotation (the Activity is recreated and the composition with it). A plain
 * string, so it fits a saved-state bundle and is checked on the way back: anything out of range is dropped
 * and the screen starts fresh rather than trusting it. Holds the finished runs' scores, the tallies and the
 * stop reason; not the lanes' text, and not the full run records (so an export needs a new loop).
 */
object LoopCountersCodec {
    private const val V = "v1"

    fun encode(c: LoopCounters?, stop: StopReason?): String {
        if (c == null) return ""
        val runs = c.runs.joinToString(";") { r -> "${bit(r.rawFailed)}${bit(r.attempted)}${bit(r.caught)}${bit(r.escaped)}:${r.ms}" }
        return listOf(V, c.planned, c.notFinished, c.batteryStartPct ?: -1, c.batteryEndPct ?: -1, stop?.name ?: "", runs).joinToString("|")
    }

    fun decode(text: String): Pair<LoopCounters, StopReason?>? = try {
        val f = text.split("|")
        if (f.size != 7 || f[0] != V) null else {
            val planned = f[1].toInt()
            val notFinished = f[2].toInt()
            val bs = f[3].toInt().takeIf { it in 0..100 }
            val be = f[4].toInt().takeIf { it in 0..100 }
            val stop = if (f[5].isEmpty()) null else StopReason.valueOf(f[5])
            val runs = if (f[6].isEmpty()) emptyList() else f[6].split(";").map { part ->
                val (flags, ms) = part.split(":").also { require(it.size == 2) }
                require(flags.length == 4 && flags.all { it == '0' || it == '1' })
                RunScore(flags[0] == '1', flags[1] == '1', flags[2] == '1', flags[3] == '1', ms.toLong().also { require(it in 0..MAX_MS) })
            }
            if (planned !in 1..LoopRunner.MAX_RUNS || runs.size > planned || notFinished !in 0..LoopRunner.MAX_RUNS) null
            else LoopCounters(planned, runs, notFinished, bs, be) to stop
        }
    } catch (e: Exception) {
        null
    }

    private const val MAX_MS = 24L * 60 * 60 * 1000
    private fun bit(b: Boolean) = if (b) '1' else '0'
}
