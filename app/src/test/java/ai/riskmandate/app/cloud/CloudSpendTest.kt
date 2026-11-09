package ai.riskmandate.app.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CloudSpendTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dir get() = File(tmp.root, "feature_cloud")
    private fun spend(session: Long = 500, total: Long = 2_000) = CloudSpend(dir, session, total)

    @Test fun theRealCapsAreTheConstantsAndNoScreenCanRaiseThem() {
        val s = CloudSpend(dir)
        s.initialise()
        val snap = s.snapshot()
        assertEquals(CloudLimits.SESSION_CAP_MICROS, snap.sessionCapMicros)
        assertEquals(CloudLimits.TOTAL_CAP_MICROS, snap.totalCapMicros)
        assertTrue(CloudSpend::class.java.declaredMethods.none { it.name.startsWith("setCap") }) // there is no setter
    }

    @Test fun theCapsAreWhatDinisSet() {
        assertEquals("US$1 a session", 1_000_000L, CloudLimits.SESSION_CAP_MICROS)
        assertEquals("US$5 a tablet", 5_000_000L, CloudLimits.TOTAL_CAP_MICROS)
        assertTrue("the session cap never exceeds the tablet total", CloudLimits.SESSION_CAP_MICROS <= CloudLimits.TOTAL_CAP_MICROS)
    }

    @Test fun aMissingLedgerRefusesToSpendAndNeverAssumesZero() {
        val s = spend()
        assertFalse(s.snapshot().ledgerOk)
        assertTrue(s.wouldExceed(0))
        assertEquals(0, s.snapshot().remainingMicros)
        s.record(10) // nothing to write to: the session counts it, the ledger stays missing
        assertTrue(s.wouldExceed(0))
        assertFalse(File(dir, CloudSpend.FILE).exists())
    }

    @Test fun initialiseCreatesTheLedgerAtZeroOnlyWhenThereIsNone() {
        val s = spend()
        s.initialise()
        assertTrue(s.snapshot().ledgerOk); assertFalse(s.wouldExceed(100))
        s.record(300); s.initialise()
        assertEquals(300, spend().snapshot().totalSpentMicros) // a second initialise does not wipe it
    }

    @Test fun totalPersistsAcrossInstancesButTheSessionDoesNot() {
        val a = spend(); a.initialise(); a.record(250); a.record(100)
        val b = spend() // the next app start
        assertEquals(350, b.snapshot().totalSpentMicros)
        assertEquals(0, b.snapshot().sessionSpentMicros)
        assertEquals(350, a.snapshot().sessionSpentMicros)
    }

    @Test fun eitherCapStopsACallAndTheBoundariesAreExact() {
        val s = spend(session = 500, total = 2_000)
        s.initialise(); s.record(400)
        assertFalse(s.wouldExceed(100)); assertTrue(s.wouldExceed(101)) // session: 400 + 101 > 500
        val next = spend(session = 500, total = 2_000) // new session, ledger at 400
        assertFalse(next.wouldExceed(500)); assertTrue(next.wouldExceed(501))
        val tight = spend(session = 5_000, total = 450) // the tablet total is the smaller cap
        assertFalse(tight.wouldExceed(50)); assertTrue(tight.wouldExceed(51))
    }

    @Test fun aDamagedLedgerRefusesUntilStaffResetOnPurpose() {
        val s = spend(); s.initialise(); s.record(1)
        File(dir, CloudSpend.FILE).writeText("{ nope")
        val d = spend()
        assertFalse(d.snapshot().ledgerOk); assertTrue(d.wouldExceed(0))
        d.initialise() // staff saving the key again does not hide the damage
        assertTrue(d.wouldExceed(0))
        d.reset(confirmed = true)
        assertTrue(d.snapshot().ledgerOk); assertFalse(d.wouldExceed(1))
        File(dir, CloudSpend.FILE).writeText("""{"v":1,"spent":-5}""")
        assertTrue(spend().wouldExceed(0)) // a negative total is damage, not a refund
    }

    @Test fun resetZeroesTheTotalAndTheSession() {
        val s = spend(); s.initialise(); s.record(300); s.reset(confirmed = true)
        assertEquals(0, s.snapshot().totalSpentMicros); assertEquals(0, s.snapshot().sessionSpentMicros)
        assertThrows(IllegalArgumentException::class.java) { s.record(-5) }
        assertThrows("no reset without a confirmation", IllegalArgumentException::class.java) { s.reset(confirmed = false) }
        assertEquals(0, s.snapshot().totalSpentMicros)
    }

    @Test fun anUnwritableLedgerBecomesRefusingNotSilentlyBehind() {
        val s = spend(); s.initialise()
        File(dir, CloudSpend.FILE).delete()
        File(dir, CloudSpend.FILE).mkdirs() // a directory where the file should be: reads as damaged, cannot be replaced
        s.record(5)
        assertTrue(s.wouldExceed(0))
    }

    @Test fun noTempFileIsLeftBehind() {
        val s = spend(); s.initialise(); s.record(5); s.record(5)
        assertEquals(listOf(CloudSpend.FILE), dir.list()!!.toList())
        assertEquals("0.35", CloudSpend.usd(350_000))
    }
}
