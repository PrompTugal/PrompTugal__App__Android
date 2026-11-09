package ai.riskmandate.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    private fun ok(u: String) = assertTrue("should allow $u", Links.isAllowed(u))
    private fun no(u: String) = assertFalse("should refuse $u", Links.isAllowed(u))

    @Test fun homeIsAllowed() = ok(Links.HOME)
    @Test fun wwwIsAllowed() = ok("https://www.riskmandate.ai/about")
    @Test fun bareHostWithoutPathIsAllowed() = ok("https://riskmandate.ai")
    @Test fun queryAndFragmentAreAllowed() { ok("https://riskmandate.ai?a=b"); ok("https://riskmandate.ai/#x") }
    @Test fun httpIsRefused() = no("http://riskmandate.ai/")
    @Test fun lookalikeIsRefused() = no("https://riskmandate.ai.evil.example/")
    @Test fun suffixTrickIsRefused() = no("https://notriskmandate.ai/")
    @Test fun userinfoTrickIsRefused() = no("https://riskmandate.ai@evil.example/")
    @Test fun otherSchemeIsRefused() = no("javascript:alert(1)")

    // The bypasses from docs/security/review-2026-11-08.md (A-1).
    @Test fun colonUserinfoIsRefused() = no("https://riskmandate.ai:x@evil.example/")
    @Test fun port443UserinfoIsRefused() = no("https://riskmandate.ai:443@evil.example/")
    @Test fun backslashBeforeAtIsRefused() = no("https://evil.example\\@riskmandate.ai/")
    @Test fun backslashAfterHostIsRefused() = no("https://riskmandate.ai\\@evil.example/")
    @Test fun tabOrNewlineIsRefused() {
        no("https://riskmandate.ai\t.evil.example/")
        no("https://riskmandate.ai\n/")
        no("https://risk\u0000mandate.ai/")
    }
    @Test fun percentEncodedHostIsRefused() {
        no("https://riskmandate%2eai/")
        no("https://riskmandate.ai%40evil.example/")
    }

    @Test fun uppercaseHostIsAllowed() = ok("https://RISKMANDATE.AI/")
    /** Decision: the scheme is lowercased only (the check is case-insensitive on `https://`). */
    @Test fun uppercaseSchemeIsAllowed() { ok("HTTPS://riskmandate.ai/"); ok("HtTpS://RiskMandate.ai/") }
    @Test fun trailingDotHostIsRefused() = no("https://riskmandate.ai./")
    @Test fun port443IsAllowed() { ok("https://riskmandate.ai:443/"); ok("https://www.riskmandate.ai:443") }
    @Test fun otherPortsAreRefused() {
        no("https://riskmandate.ai:8443/")
        no("https://riskmandate.ai:80/")
        no("https://riskmandate.ai:/")
        no("https://riskmandate.ai:0443/")
        no("https://riskmandate.ai:x/")
    }
    @Test fun emptyHostIsRefused() { no("https:///"); no("https://"); no("https://:443/"); no("https://?x") }
    @Test fun singleSlashIsRefused() { no("https:/riskmandate.ai"); no("https:riskmandate.ai") }
    @Test fun lookalikesAreRefused() {
        no("https://xn--riskmandate-9ib.ai/")
        no("https://rıskmandate.ai/") // dotless i
        no("https://riskmandate.аi/") // Cyrillic a
        no("https://riskmandate。ai/") // ideographic full stop
    }
    @Test fun otherSubdomainsAreRefused() {
        no("https://app.riskmandate.ai/")
        no("https://a.b.riskmandate.ai/")
        no("https://.riskmandate.ai/")
        no("https://www.www.riskmandate.ai/")
    }
    @Test fun surroundingWhitespaceIsTrimmed() = ok("  https://riskmandate.ai/ ")
    @Test fun emptyAndBlankAreRefused() { no(""); no("   ") }

    private fun extOk(u: String) = assertTrue(u, Links.isExternalOk(u))
    private fun extNo(u: String) = assertFalse(u, Links.isExternalOk(u))

    @Test fun externalAcceptsOrdinaryHttpsLinks() {
        extOk("https://example.org/")
        extOk("https://example.org:8443/a/b?c=d#e")
        extOk("https://medium.com/@someone/post") // '@' in a path is fine
        extOk("HTTPS://Example.ORG")
        extOk("https://sub.domain-1.example.co.uk/x")
    }

    @Test fun externalRefusesEverythingThatIsNotPlainHttps() {
        for (u in listOf(
            "http://example.org/", "javascript:alert(1)", "intent://x#Intent;scheme=https;end", "file:///etc/passwd",
            "content://x/y", "data:text/html,hi", "market://details?id=x", "tel:123", "mailto:a@b.c", "//example.org/",
            "example.org", "", "   ", "https://", "https:///x", "https://:443/", "https://.example.org/",
        )) extNo(u)
    }

    @Test fun externalRefusesUserinfoBackslashWhitespaceControlAndBadHosts() {
        extNo("https://example.org@evil.example/")
        extNo("https://example.org:x@evil.example/")
        extNo("https://evil.example\\@example.org/")
        extNo("https://example.org/a b")
        extNo("https://example.org/a\nb")
        extNo("https://exa mple.org/")
        extNo("https://ex%61mple.org/")
        extNo("https://rıskmandate.ai/") // non-ASCII host
        extNo("https://example.org:99999999/")
        extNo("https://example.org:80a/")
        extNo("https://example.org/" + "a".repeat(2100))
    }
}
