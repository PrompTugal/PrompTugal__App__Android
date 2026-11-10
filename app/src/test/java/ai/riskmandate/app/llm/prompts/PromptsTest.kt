package ai.riskmandate.app.llm.prompts

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.app.Application
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromptsTest {
    private val assets = ApplicationProvider.getApplicationContext<Application>().assets

    @Test fun theBaseBlockLoadsAndNamesTheProductOnly() = runBlocking {
        val t = Prompts.load(assets, Prompts.RISKMANDATE_BASE)
        assertTrue(t.startsWith("This is the RiskMandate app, running on this device."))
        assertTrue("quotes riskmandate.ai", "From riskmandate.ai: \"Give agents access. Not free rein.\"" in t)
        assertFalse(t.endsWith("\n"))
        assertFalse(t.contains("—"))
        assertSame("cached", t, Prompts.load(assets, Prompts.RISKMANDATE_BASE))
    }

    @Test fun v2DropsOnlyTheDeviceClaim() {
        val v1 = Prompts.loadBlocking(assets, Prompts.RISKMANDATE_BASE)
        val v2 = Prompts.loadBlocking(assets, Prompts.RISKMANDATE_BASE_ANYWHERE)
        assertFalse("no device claim (R-llm-13)", v2.contains("device", ignoreCase = true))
        assertEquals(v1.replace("This is the RiskMandate app, running on this device. ", "This is the RiskMandate app. "), v2)
        listOf("\"Give agents access. Not free rein.\"", "and the gap between the two.\"").forEach { assertTrue(it, it in v2) }
    }

    @Test fun idsAreSafeFileNames() {
        assertEquals("prompts/riskmandate_base.v1.txt", Prompts.RISKMANDATE_BASE.path)
        assertEquals("riskmandate_base.v1", Prompts.RISKMANDATE_BASE.toString())
        listOf("../x", "A", "a/b", "a.b", "", "a b").forEach { n -> assertThrows(n, IllegalArgumentException::class.java) { PromptId(n, 1) } }
        assertThrows(IllegalArgumentException::class.java) { PromptId("a", 0) }
    }

    @Test fun aMissingPromptIsAnIoError() {
        assertThrows(IOException::class.java) { Prompts.loadBlocking(assets, PromptId("not_there", 1)) }
    }

    @Test fun everyBundledPromptFileIsAValidId() {
        val files = assets.list("prompts")!!.toList()
        assertTrue(files.isNotEmpty())
        files.forEach { f ->
            val m = Regex("^([a-z][a-z0-9_]{0,40})\\.v([0-9]{1,3})\\.txt$").matchEntire(f)
            assertTrue("bad prompt file name $f", m != null)
            Prompts.loadBlocking(assets, PromptId(m!!.groupValues[1], m.groupValues[2].toInt()))
        }
    }
}
