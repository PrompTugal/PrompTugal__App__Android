package ai.riskmandate.app.features.site

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePolicyTest {
    @Test fun riskmandatePagesLoadInTheApp() {
        for (u in listOf(
            "https://riskmandate.ai/", "https://riskmandate.ai/abp.html", "https://www.riskmandate.ai/pricing.html?x=1#top",
            "HTTPS://RISKMANDATE.AI/about.html", "https://riskmandate.ai:443/",
        )) assertEquals(u, Decision.IN_APP, SitePolicy.decide(u))
    }

    @Test fun everythingElseGoesToTheExternalBrowser() {
        for (u in listOf(
            "http://riskmandate.ai/", "https://evil.example/", "https://riskmandate.ai.evil.example/",
            "https://evilriskmandate.ai/", "https://customers.riskmandate.ai/", "https://riskmandate.ai:8443/",
            "https://riskmandate.ai@evil.example/", "https://riskmandate.ai:x@evil.example/",
            "https://evil.example\\@riskmandate.ai/", "https://riskmandate.ai%2e.evil.example/",
            "https://github.com/Risk-Mandate/riskmandate.ai", "https://web-summit.sgit.ai/videos/index.html",
            "mailto:agent@riskmandate.ai", "javascript:alert(1)", "intent://riskmandate.ai/#Intent;scheme=https;end",
            "file:///data/data/ai.riskmandate.app/", "content://x/y", "data:text/html,hi", "", "riskmandate.ai", "//riskmandate.ai/",
        )) assertEquals(u, Decision.EXTERNAL, SitePolicy.decide(u))
    }

    @Test fun subresourcesOffTheAllowListAreBlocked() {
        assertTrue(SitePolicy.blockSubresource("https://tracker.example/p.js"))
        assertTrue(SitePolicy.blockSubresource("http://riskmandate.ai/a.js"))
        assertTrue(SitePolicy.blockSubresource("wss://evil.example/socket"))
        assertFalse(SitePolicy.blockSubresource("https://riskmandate.ai/assets/fonts/geist-latin.woff2"))
        assertFalse(SitePolicy.blockSubresource("data:image/png;base64,AAAA"))
        assertFalse(SitePolicy.blockSubresource("blob:https://riskmandate.ai/1234"))
    }
}
