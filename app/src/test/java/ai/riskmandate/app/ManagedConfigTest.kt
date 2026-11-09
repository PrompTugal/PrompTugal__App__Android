package ai.riskmandate.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedConfigTest {
    private fun parse(vararg pairs: Pair<String, Any?>) = ManagedConfig.fromMap(mapOf(*pairs))

    @Test fun emptyGivesDefaults() {
        val c = parse()
        assertEquals(Links.HOME, c.startUrl)
        assertNull(c.deviceLabel)
        assertFalse(c.startUrlRefused)
    }

    @Test fun allowedStartUrlIsUsed() =
        assertEquals("https://riskmandate.ai/about", parse("start_url" to "https://riskmandate.ai/about").startUrl)

    @Test fun startUrlIsTrimmed() =
        assertEquals("https://www.riskmandate.ai/", parse("start_url" to "  https://www.riskmandate.ai/ ").startUrl)

    @Test fun httpStartUrlFallsBack() {
        val c = parse("start_url" to "http://riskmandate.ai/")
        assertEquals(Links.HOME, c.startUrl)
        assertTrue(c.startUrlRefused)
    }

    @Test fun otherHostFallsBack() {
        val c = parse("start_url" to "https://evil.example/")
        assertEquals(Links.HOME, c.startUrl)
        assertTrue(c.startUrlRefused)
    }

    @Test fun blankStartUrlIsDefaultNotRefused() {
        val c = parse("start_url" to "   ")
        assertEquals(Links.HOME, c.startUrl)
        assertFalse(c.startUrlRefused)
    }

    @Test fun nonStringStartUrlIsIgnored() = assertEquals(Links.HOME, parse("start_url" to 42).startUrl)

    @Test fun labelIsKept() = assertEquals("Lisbon kiosk 3", parse("device_label" to " Lisbon kiosk 3 ").deviceLabel)

    @Test fun blankLabelIsNull() = assertNull(parse("device_label" to "  ").deviceLabel)

    @Test fun labelControlCharactersAreStripped() =
        assertEquals("ab", parse("device_label" to "a\nb\u0000").deviceLabel)

    @Test fun longLabelIsCut() = assertEquals(64, parse("device_label" to "x".repeat(200)).deviceLabel?.length)
}
