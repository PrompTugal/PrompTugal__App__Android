package ai.riskmandate.app.experiments

/** What the loop asks of the device before each run. The Android implementation is in [AndroidDeviceGuard]. */
interface DeviceGuard {
    /** Battery percentage, or null when unknown. */
    fun batteryPct(): Int?
    fun charging(): Boolean
    /** True when the device reports severe thermal pressure. */
    fun hot(): Boolean
}

object NoGuard : DeviceGuard {
    override fun batteryPct(): Int? = null
    override fun charging(): Boolean = false
    override fun hot(): Boolean = false
}

/** [INTERRUPTED]: the screen was turned (the Activity was recreated) while the loop ran; the counts so far are kept. */
enum class StopReason { DONE, STOPPED, LOW_BATTERY, HOT, TIME_CAP, ERRORS, INTERRUPTED }

/**
 * The caps every unattended run of many experiments keeps (security I.5), in one place: the Lab's loop mode, the
 * tool-test suite and the Workbench's sweeps. At most [MAX_TOTAL_MS] in all, a stop on low battery (not charging)
 * or severe thermal status, a stop after [MAX_CONSECUTIVE_ERRORS] runs in a row that did not finish, and a stop
 * request that ends it after the current run.
 */
object RunCaps {
    /** At most this many runs in one go. */
    const val MAX_RUNS = 100
    const val MAX_TOTAL_MS = 30 * 60_000L
    const val LOW_BATTERY_PCT = 15
    const val MAX_CONSECUTIVE_ERRORS = 3
    /** Seed of run i is the base seed plus this times i (the eval harness's convention), so runs differ but repeat. */
    const val SEED_STEP = 1000

    /** Why to stop before the next run, or [StopReason.DONE] to go on. Checked before every run. */
    fun nextStop(guard: DeviceGuard, now: () -> Long, startedAt: Long, stopRequested: () -> Boolean): StopReason = when {
        stopRequested() -> StopReason.STOPPED
        now() - startedAt >= MAX_TOTAL_MS -> StopReason.TIME_CAP
        guard.hot() -> StopReason.HOT
        guard.batteryPct()?.let { it <= LOW_BATTERY_PCT } == true && !guard.charging() -> StopReason.LOW_BATTERY
        else -> StopReason.DONE
    }

    /**
     * Runs [runOne] for index 0 until [total], checking [nextStop] before each and counting runs that did not
     * finish ([runOne] returns false) in a row. Returns why it ended.
     */
    suspend fun capped(
        total: Int,
        guard: DeviceGuard = NoGuard,
        now: () -> Long = System::currentTimeMillis,
        stopRequested: () -> Boolean = { false },
        runOne: suspend (index: Int) -> Boolean,
    ): StopReason {
        val t0 = now()
        var errorsInARow = 0
        for (i in 0 until total) {
            val stop = nextStop(guard, now, t0, stopRequested)
            if (stop != StopReason.DONE) return stop
            if (runOne(i)) errorsInARow = 0 else if (++errorsInARow >= MAX_CONSECUTIVE_ERRORS) return StopReason.ERRORS
        }
        return StopReason.DONE
    }
}

/** Sampling and output length for a run. Used by the Lab's live runs and loop and by the tool tests. */
data class RunSettings(val sampling: ai.riskmandate.app.llm.Sampling = ai.riskmandate.app.llm.Sampling.DEFAULT, val maxTokens: Int = 512) {
    companion object {
        /** Longest tool result handed back to the model, to keep each request inside the context budget. */
        const val MAX_RESULT_CHARS = 2_000
    }
}
