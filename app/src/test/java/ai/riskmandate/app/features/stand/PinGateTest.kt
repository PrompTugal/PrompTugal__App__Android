package ai.riskmandate.app.features.stand

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinGateTest {
    private var now = 0L

    @Test fun locksAfterFiveWrongTriesEvenForTheRightPin() {
        val g = PinGate({ now })
        repeat(5) { assertFalse(g.attempt { false }) }
        assertTrue(g.isLocked())
        assertFalse(g.attempt { true })
    }

    @Test fun unlocksAfterTheLockout() {
        val g = PinGate({ now })
        repeat(5) { g.attempt { false } }
        now += 60_000
        assertFalse(g.isLocked())
        assertTrue(g.attempt { true })
    }

    @Test fun aCorrectPinResetsTheCount() {
        val g = PinGate({ now })
        repeat(4) { g.attempt { false } }
        assertTrue(g.attempt { true })
        repeat(4) { g.attempt { false } }
        assertFalse(g.isLocked())
    }
}
