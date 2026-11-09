package ai.riskmandate.app.llm.cloud

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
    private val dir get() = File(tmp.root, "cloud")

    @Test fun defaultCapIsOneDollarAndSpendAccumulatesAndPersists() {
        val s = CloudSpend(dir)
        assertEquals(CloudSpend.Snapshot(0, 1_000_000), s.snapshot())
        s.record(250_000); s.record(100_000)
        assertEquals(350_000, CloudSpend(dir).snapshot().spentMicros) // a new instance reads the file
        assertEquals(650_000, s.snapshot().remainingMicros)
    }

    @Test fun wouldExceedIsExactAtTheBoundary() {
        val s = CloudSpend(dir)
        s.record(900_000)
        assertFalse(s.wouldExceed(100_000))
        assertTrue(s.wouldExceed(100_001))
    }

    @Test fun capCanBeSetAndResetKeepsIt() {
        val s = CloudSpend(dir)
        s.setCap(5_000_000); s.record(1_000_000); s.reset()
        assertEquals(CloudSpend.Snapshot(0, 5_000_000), s.snapshot())
        assertThrows(IllegalArgumentException::class.java) { s.setCap(-1) }
        assertThrows(IllegalArgumentException::class.java) { s.setCap(CloudSpend.MAX_CAP_MICROS + 1) }
        assertThrows(IllegalArgumentException::class.java) { s.record(-5) }
    }

    @Test fun aDamagedFileFailsClosedUntilReset() {
        CloudSpend(dir).record(1)
        File(dir, CloudSpend.FILE).writeText("{ nope")
        val s = CloudSpend(dir)
        assertTrue(s.wouldExceed(1))
        assertEquals(0, s.snapshot().remainingMicros)
        s.reset()
        assertFalse(s.wouldExceed(1))
    }

    @Test fun noTempFileIsLeftBehind() {
        val s = CloudSpend(dir)
        s.record(5); s.record(5)
        assertEquals(listOf(CloudSpend.FILE), dir.list()!!.toList())
        assertEquals("0.35", CloudSpend.usd(350_000))
    }
}
