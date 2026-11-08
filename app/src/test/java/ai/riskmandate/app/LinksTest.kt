package ai.riskmandate.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test fun homeIsAllowed() = assertTrue(Links.isAllowed(Links.HOME))
    @Test fun subdomainIsAllowed() = assertTrue(Links.isAllowed("https://www.riskmandate.ai/about"))
    @Test fun httpIsRefused() = assertFalse(Links.isAllowed("http://riskmandate.ai/"))
    @Test fun lookalikeIsRefused() = assertFalse(Links.isAllowed("https://riskmandate.ai.evil.example/"))
    @Test fun suffixTrickIsRefused() = assertFalse(Links.isAllowed("https://notriskmandate.ai/"))
    @Test fun userinfoTrickIsRefused() = assertFalse(Links.isAllowed("https://riskmandate.ai@evil.example/"))
    @Test fun otherSchemeIsRefused() = assertFalse(Links.isAllowed("javascript:alert(1)"))
}
