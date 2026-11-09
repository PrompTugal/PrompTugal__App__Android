package ai.riskmandate.app.features.stand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleTimerTest {
    private var now = 1_000L
    private val minute = 60_000L

    @Test fun doesNotFireBeforeTheTimeout() {
        val t = IdleTimer({ now }, 3 * minute)
        now += 3 * minute - 1
        assertFalse(t.poll())
    }

    @Test fun firesOnceAtTheTimeoutThenStaysQuiet() {
        val t = IdleTimer({ now }, 3 * minute)
        now += 3 * minute
        assertTrue(t.poll())
        now += 10 * minute
        assertFalse(t.poll())
    }

    @Test fun touchRestartsTheCountdownAndRearms() {
        val t = IdleTimer({ now }, 3 * minute)
        now += 2 * minute
        t.touch()
        now += 2 * minute
        assertFalse(t.poll())
        now += minute
        assertTrue(t.poll())
        t.touch()
        now += 3 * minute
        assertTrue(t.poll())
    }

    @Test fun changingTheTimeoutAppliesToTheRunningCountdown() {
        val t = IdleTimer({ now }, 3 * minute)
        now += minute
        t.timeoutMs = minute
        assertTrue(t.poll())
    }

    @Test fun aClockGoingBackwardsNeverFires() {
        val t = IdleTimer({ now }, minute)
        now -= 5 * minute
        assertEquals(0, t.idleMs())
        assertFalse(t.poll())
    }

    @Test fun timeoutHasAFloor() {
        val t = IdleTimer({ now }, 0)
        assertEquals(1, t.timeoutMs)
    }
}
