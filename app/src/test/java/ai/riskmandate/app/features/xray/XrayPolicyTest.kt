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

    @Test fun subresourcesOnlyFromThePagesHosts() {
        for (u in listOf(
            "https://cdn.jsdelivr.net/npm/three@0.147.0/build/three.min.js", "https://fonts.googleapis.com/css2?family=x",
            "https://fonts.gstatic.com/s/x.woff2", "https://promptugal.news/api/v1/companies.json",
            "https://dev.send.sgraph.ai/api/vault/append/write/x", "https://openrouter.ai/api/v1/chat/completions",
            "https://web-summit.sgit.ai/people/silvio/app/assets/cc/xray.js", "wss://web-summit.sgit.ai/socket",
        )) assertFalse(u, XrayPolicy.blockSubresource(u))
        for (u in listOf(
            "https://tracker.example/p.js", "http://web-summit.sgit.ai/a.js", "ws://web-summit.sgit.ai/s",
            "https://cdn.jsdelivr.net.evil.example.com/x.js", USERINFO_ON_AN_ALLOWED_HOST, "wss://evil.example.com/s",
            "https://api.openai.com/v1/x",
        )) assertTrue(u, XrayPolicy.blockSubresource(u))
        // Not the network: left to WebView.
        for (u in listOf("data:image/png;base64,AAAA", "blob:https://web-summit.sgit.ai/1234", "about:blank")) assertFalse(u, XrayPolicy.blockSubresource(u))
    }

    @Test fun theHostListIsTheOneReviewed() {
        assertEquals(
            setOf("web-summit.sgit.ai", "cdn.jsdelivr.net", "fonts.googleapis.com", "fonts.gstatic.com", "promptugal.news", "dev.send.sgraph.ai", "openrouter.ai"),
            XrayPolicy.SUBRESOURCE_HOSTS,
        )
    }
}
