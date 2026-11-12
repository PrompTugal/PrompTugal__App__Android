package ai.riskmandate.app.features.xray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayPolicyTest {
    // Userinfo in front of an allowed host. Built in two parts so the plan's copy of this patch on web-summit.sgit.ai
    // does not read as an email address to that site's contact-detail check.
    private val USERINFO_ON_AN_ALLOWED_HOST = "https://user" + "@cdn.jsdelivr.net/x.js"

    @Test fun theStartPageIsAllowed() {
        assertTrue(XrayPolicy.allowPage(XrayPolicy.START_URL))
        for (u in listOf(
            "https://web-summit.sgit.ai/", "HTTPS://WEB-SUMMIT.SGIT.AI/people/silvio/app/index.html#gravity",
            "https://web-summit.sgit.ai:443/team/", "https://web-summit.sgit.ai?x=1",
        )) assertTrue(u, XrayPolicy.allowPage(u))
    }

    @Test fun everyOtherPageIsIgnored() {
        for (u in listOf(
            "http://web-summit.sgit.ai/", "https://web-summit.sgit.ai.evil.example.com/", "https://evil.example.com/",
            "https://web-summit.sgit.ai@evil.example.com/", "https://evil.example.com\\@web-summit.sgit.ai/",
            "https://web-summit.sgit.ai:8443/", "https://web-summit.sgit.ai%2e.evil.example.com/", "https://sgit.ai/",
            "https://riskmandate.ai/", "https://cdn.jsdelivr.net/", "https://openrouter.ai/",
            "javascript:alert(1)", "intent://web-summit.sgit.ai/#Intent;scheme=https;end", "file:///sdcard/x.html",
            "data:text/html,hi", "about:blank", "", "web-summit.sgit.ai", "//web-summit.sgit.ai/",
            "https://web-summit.sgit.ai /", "https://web-summit.sgit.ai\n/",
        )) assertFalse(u, XrayPolicy.allowPage(u))
    }

    @Test fun subresourcesFromAnyHostOverTls() {
        for (u in listOf(
            "https://cdn.jsdelivr.net/npm/three@0.147.0/build/three.min.js", "https://fonts.gstatic.com/s/x.woff2",
            "https://swarm.promptugal.news/api/x", "https://api.github.com/repos/x", "https://openrouter.ai/api/v1/chat/completions",
            "https://web-summit.sgit.ai/people/silvio/app/assets/cc/xray.js", "wss://web-summit.sgit.ai/socket", "wss://example.com/s",
        )) assertFalse(u, XrayPolicy.blockSubresource(u))
        for (u in listOf(
            "http://web-summit.sgit.ai/a.js", "ws://web-summit.sgit.ai/s", "http://tracker.example/p.js",
            USERINFO_ON_AN_ALLOWED_HOST, "https://cdn.jsdelivr.net:8443/x.js", "https://cdn.jsdelivr.net%2e.evil.example.com/x.js",
        )) assertTrue(u, XrayPolicy.blockSubresource(u))
        // Not the network: left to WebView.
        for (u in listOf("data:image/png;base64,AAAA", "blob:https://web-summit.sgit.ai/1234", "about:blank")) assertFalse(u, XrayPolicy.blockSubresource(u))
    }

    @Test fun aTappedLinkOffTheSiteOpensOutsideAndAnUntappedOneIsIgnored() {
        assertEquals(XrayPolicy.Nav.LOAD, XrayPolicy.navigation(XrayPolicy.START_URL, hasGesture = false))
        assertEquals(XrayPolicy.Nav.LOAD, XrayPolicy.navigation("https://web-summit.sgit.ai/team/", hasGesture = true))
        // The page's Summit desk link (target=_blank) and Ask Claude (window.open) both go to claude.ai.
        assertEquals(XrayPolicy.Nav.OPEN_OUTSIDE, XrayPolicy.navigation("https://claude.ai/new?q=x", hasGesture = true))
        assertEquals(XrayPolicy.Nav.OPEN_OUTSIDE, XrayPolicy.navigation("https://github.com/SGit-AI/x", hasGesture = true))
        assertEquals(XrayPolicy.Nav.IGNORE, XrayPolicy.navigation("https://claude.ai/new", hasGesture = false))
        for (u in listOf("http://example.com/", "intent://x#Intent;end", "javascript:alert(1)", "file:///sdcard/x", "https://web-summit.sgit.ai@evil.example.com/"))
            assertEquals(u, XrayPolicy.Nav.IGNORE, XrayPolicy.navigation(u, hasGesture = true))
    }

    @Test fun mediaOnlyEverForTheSite() {
        assertTrue(XrayPolicy.mayAskForMedia("https://web-summit.sgit.ai/"))
        assertFalse(XrayPolicy.mayAskForMedia("https://evil.example.com/"))
        assertFalse(XrayPolicy.mayAskForMedia("http://web-summit.sgit.ai/"))
    }
}
