package ai.riskmandate.app.features.abpdemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbpDemoModelTest {
    @Test fun stepsAreTheSceneThenTheSixLayersInOrder() {
        assertEquals(7, STEPS.size)
        assertEquals((0..6).toList(), STEPS.map { it.index })
        assertNull(STEPS[0].layer)
        assertEquals(
            listOf(Layer.BROWSER, Layer.MODEL, Layer.HOOK, Layer.PROXY, Layer.HARNESS, Layer.DEVICE),
            STEPS.drop(1).map { it.layer },
        )
    }

    @Test fun strengthsRunFromGuidanceToTheFloorAndNeverWeakenGoingIn() {
        assertNull(STEPS[0].strength)
        val s = STEPS.drop(1).map { it.strength!! }
        assertEquals(Strength.GUIDANCE, s.first())
        assertEquals(Strength.FLOOR, s.last())
        assertEquals(s.sorted(), s)
        assertEquals(Layer.entries.toSet(), STRENGTH_OF.keys)
    }

    @Test fun nextAndBackStopAtTheEnds() {
        var p = DemoPlayer()
        assertFalse(p.canGoBack)
        assertEquals(p, p.back())
        repeat(10) { p = p.next() }
        assertEquals(6, p.index)
        assertFalse(p.canGoNext)
        assertEquals(p, p.next())
        assertEquals(5, p.back().index)
    }

    @Test fun autoPlayAdvancesOnTickAndLoopsAtTheEnd() {
        var p = DemoPlayer().toggleAutoPlay()
        assertTrue(p.autoPlay)
        val seen = mutableListOf(p.index)
        repeat(7) { p = p.tick(); seen += p.index }
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 0), seen)
        assertTrue(p.autoPlay)
    }

    @Test fun tickDoesNothingWhenAutoPlayIsOff() {
        val p = DemoPlayer(index = 3)
        assertEquals(p, p.tick())
    }

    @Test fun aPersonPressingNextOrBackStopsAutoPlay() {
        val p = DemoPlayer(index = 2, autoPlay = true)
        assertEquals(DemoPlayer(3, false), p.nextByHand())
        assertEquals(DemoPlayer(1, false), p.backByHand())
        assertFalse(p.toggleAutoPlay().autoPlay)
    }

    @Test fun layersDoneAreTheOnesBeforeTheCurrentStep() {
        assertEquals(emptyList<Layer>(), DemoPlayer(0).layersDone())
        assertEquals(emptyList<Layer>(), DemoPlayer(1).layersDone())
        assertEquals(listOf(Layer.BROWSER, Layer.MODEL), DemoPlayer(3).layersDone())
        assertEquals(Layer.entries.dropLast(1), DemoPlayer(6).layersDone())
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRangeIndexIsRefused() { DemoPlayer(index = 7) }

    @Test fun intervalIsLongEnoughToReadFromTwoMetres() = assertTrue(AUTO_PLAY_STEP_MS >= 5_000)
}
