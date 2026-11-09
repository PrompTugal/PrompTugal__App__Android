package ai.riskmandate.app.qa

import ai.riskmandate.app.experiments.DeviceGuard
import ai.riskmandate.app.experiments.RunCaps
import ai.riskmandate.app.experiments.StopReason
import ai.riskmandate.app.features.lab.LoopRunner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The caps every unattended run keeps (security I.5): Lab loop, tool-test suite and Workbench sweeps (QA; plan WB-C). */
class RunCapsTest {
    private class Guard(var pct: Int? = 80, var charging: Boolean = false, var hot: Boolean = false) : DeviceGuard {
        override fun batteryPct() = pct
        override fun charging() = charging
        override fun hot() = hot
    }

    private fun stop(g: DeviceGuard = Guard(), elapsed: Long = 0, requested: Boolean = false) =
        RunCaps.nextStop(g, { 1_000_000L + elapsed }, 1_000_000L) { requested }

    @Test fun theLabLoopUsesTheSharedConstantsAndTheValuesAreTheOnesInTheBrief() {
        assertEquals(RunCaps.MAX_RUNS, LoopRunner.MAX_RUNS); assertEquals(RunCaps.MAX_TOTAL_MS, LoopRunner.MAX_TOTAL_MS)
        assertEquals(RunCaps.LOW_BATTERY_PCT, LoopRunner.LOW_BATTERY_PCT); assertEquals(RunCaps.MAX_CONSECUTIVE_ERRORS, LoopRunner.MAX_CONSECUTIVE_ERRORS)
        assertEquals(RunCaps.SEED_STEP, LoopRunner.SEED_STEP)
        // The brief: at most 100 runs and 30 minutes, stop at 15% battery when not charging, 3 failed runs in a row.
        assertEquals(100, RunCaps.MAX_RUNS); assertEquals(30 * 60_000L, RunCaps.MAX_TOTAL_MS)
        assertEquals(15, RunCaps.LOW_BATTERY_PCT); assertEquals(3, RunCaps.MAX_CONSECUTIVE_ERRORS)
    }

    @Test fun timeCapStopsAtThirtyMinutesExactlyNotBefore() {
        assertEquals(StopReason.DONE, stop(elapsed = RunCaps.MAX_TOTAL_MS - 1))
        assertEquals(StopReason.TIME_CAP, stop(elapsed = RunCaps.MAX_TOTAL_MS))
        assertEquals(StopReason.TIME_CAP, stop(elapsed = RunCaps.MAX_TOTAL_MS + 60_000))
    }

    @Test fun batteryStopsAtFifteenPercentOrLessUnlessChargingOrUnknown() {
        assertEquals(StopReason.DONE, stop(Guard(pct = 16)))
        assertEquals(StopReason.LOW_BATTERY, stop(Guard(pct = 15)))
        assertEquals(StopReason.LOW_BATTERY, stop(Guard(pct = 3)))
        assertEquals("charging: no stop", StopReason.DONE, stop(Guard(pct = 3, charging = true)))
        assertEquals("unknown battery: no stop", StopReason.DONE, stop(Guard(pct = null)))
    }

    @Test fun heatStopsEvenWhileChargingAndBeatsTheBatteryRule() {
        assertEquals(StopReason.HOT, stop(Guard(hot = true, charging = true)))
        assertEquals(StopReason.HOT, stop(Guard(hot = true, pct = 5)))
    }

    @Test fun aStopRequestWinsOverEverythingElse() {
        assertEquals(StopReason.STOPPED, stop(Guard(hot = true, pct = 5), elapsed = RunCaps.MAX_TOTAL_MS * 2, requested = true))
    }

    @Test fun threeFailedRunsInARowStopAndASuccessResetsTheCount() = runBlocking {
        var n = 0
        // fail, fail, success, fail, fail, success ... never three in a row: runs all.
        val pattern = listOf(false, false, true)
        assertEquals(StopReason.DONE, RunCaps.capped(9) { pattern[n++ % 3] })
        n = 0
        assertEquals(StopReason.ERRORS, RunCaps.capped(9) { false }.also { })
        var ran = 0
        RunCaps.capped(9) { ran++; false }
        assertEquals("stops after exactly three failures", 3, ran)
    }

    @Test fun theLoopRunsEveryRunWhenNothingStopsItAndStopsBeforeTheNextRunOnARequest() = runBlocking {
        var ran = 0
        assertEquals(StopReason.DONE, RunCaps.capped(7) { ran++; true }); assertEquals(7, ran)
        ran = 0
        val r = RunCaps.capped(10, stopRequested = { ran >= 4 }) { ran++; true }
        assertEquals(StopReason.STOPPED, r); assertEquals("a stop ends the loop after the current run", 4, ran)
    }

    @Test fun theTimeCapIsReadFromTheClockBeforeEveryRun() = runBlocking {
        var t = 0L; var ran = 0
        val r = RunCaps.capped(100, now = { t }) { ran++; t += 10 * 60_000L; true }
        assertEquals(StopReason.TIME_CAP, r); assertEquals("three 10-minute runs fill 30 minutes", 3, ran)
    }
}
