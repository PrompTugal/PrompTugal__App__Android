package ai.riskmandate.app.features.lab

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
