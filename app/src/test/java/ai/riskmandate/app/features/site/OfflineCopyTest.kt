package ai.riskmandate.app.features.site

import ai.riskmandate.app.Links
import ai.riskmandate.app.R
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class OfflineCopyTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun everyTabIsAllowedAndHasAUniqueBundledPage() {
        assertEquals(SITE_TABS.size, SITE_TABS.map { it.id }.toSet().size)
        assertEquals(SITE_TABS.size, SITE_TABS.map { it.url }.toSet().size)
        for (tab in SITE_TABS) {
            assertTrue(tab.url, Links.isAllowed(tab.url))
            assertEquals(tab.id, tab, OfflineCopy.pageFor(tab.url))
        }
    }

    @Test fun bundledPagesAreRealHtmlFromRiskmandate() {
        for (tab in SITE_TABS) {
            val html = app.assets.open("feature_site/" + tab.file).use { it.readBytes().toString(Charsets.UTF_8) }
            assertTrue("${tab.id} is html", html.trimStart().startsWith("<!doctype html>", ignoreCase = true))
            assertTrue("${tab.id} names the product", html.contains("RiskMandate"))
            assertTrue("${tab.id} is more than a stub", html.length > 10_000)
        }
    }

    @Test fun pageLookupNormalisesTheUrl() {
        assertEquals("home", OfflineCopy.pageFor("https://riskmandate.ai")?.id)
        assertEquals("home", OfflineCopy.pageFor("https://riskmandate.ai/index.html")?.id)
        assertEquals("home", OfflineCopy.pageFor("https://www.riskmandate.ai/#pricing")?.id)
        assertEquals("pricing", OfflineCopy.pageFor("https://riskmandate.ai/pricing.html?utm=1#levels")?.id)
        assertEquals("abp", OfflineCopy.pageFor("https://RISKMANDATE.ai/abp.html")?.id)
    }

    @Test fun bundledPagesCarryNoFormAndNoRemoteScript() {
        val remoteScript = Regex("<script[^>]+src=[\"']\\s*(https?:)?//", RegexOption.IGNORE_CASE)
        for (tab in SITE_TABS) {
            val html = app.assets.open("feature_site/" + tab.file).use { it.readBytes().toString(Charsets.UTF_8) }
            assertFalse("${tab.id} has a form", html.contains("<form", ignoreCase = true))
            assertFalse("${tab.id} loads a remote script", remoteScript.containsMatchIn(html))
        }
    }

    @Test fun assetUrlsAreRecognisedStrictly() {
        assertTrue(OfflineCopy.isAssetUrl("https://appassets.androidplatform.net/assets/feature_site/abp.html"))
        assertFalse(OfflineCopy.isAssetUrl("http://appassets.androidplatform.net/assets/feature_site/abp.html"))
        assertFalse(OfflineCopy.isAssetUrl("https://appassets.androidplatform.net/res/feature_site/abp.html"))
        assertFalse(OfflineCopy.isAssetUrl("https://appassets.androidplatform.net/assets/other/abp.html"))
        assertFalse(OfflineCopy.isAssetUrl("https://appassets.androidplatform.net.evil.example/assets/feature_site/abp.html"))
        assertFalse(OfflineCopy.isAssetUrl("https://appassets.androidplatform.net@evil.example/assets/feature_site/abp.html"))
        assertEquals("abp", OfflineCopy.pageFor("https://appassets.androidplatform.net/assets/feature_site/abp.html?x=1#y")?.id)
        assertEquals("home", OfflineCopy.pageFor("https://appassets.androidplatform.net/assets/feature_site/")?.id)
    }

    @Test fun riskmandateUrlsMapToTheirCopy() {
        assertEquals(OfflineCopy.ASSET_BASE + "pricing.html", OfflineCopy.assetUrlFor("https://riskmandate.ai/pricing.html?x#y"))
        assertEquals(OfflineCopy.ASSET_BASE + "index.html", OfflineCopy.assetUrlFor("https://riskmandate.ai/"))
        assertEquals(OfflineCopy.ASSET_BASE + "rules/", OfflineCopy.assetUrlFor("https://riskmandate.ai/rules/"))
        assertNull(OfflineCopy.assetUrlFor("https://evil.example/pricing.html"))
    }

    @Test fun pagesOutsideTheCopyAndForeignUrlsHaveNoCopy() {
        assertNull(OfflineCopy.pageFor("https://riskmandate.ai/contact.html"))
        assertNull(OfflineCopy.pageFor("https://riskmandate.ai/rules/"))
        assertNull(OfflineCopy.pageFor("https://riskmandate.ai/pricing.html/extra"))
        assertNull(OfflineCopy.pageFor("https://evil.example/pricing.html"))
        assertNull(OfflineCopy.pageFor("https://riskmandate.ai:x@evil.example/pricing.html"))
    }

    @Test fun theBannerDateMatchesTheSnapshotDate() {
        assertEquals("9 November 2026", app.getString(R.string.feature_site_snapshot_date))
        assertEquals("2026-11-09", OfflineCopy.SNAPSHOT_DATE_ISO)
        assertTrue(app.getString(R.string.feature_site_offline_banner, app.getString(R.string.feature_site_snapshot_date)).startsWith("Offline copy from 9 November 2026"))
    }

    @Test @Config(qualifiers = "pt-rPT")
    fun theBannerIsInPortugueseToo() {
        assertTrue(app.getString(R.string.feature_site_offline_banner, app.getString(R.string.feature_site_snapshot_date)).startsWith("Cópia offline de 9 de novembro de 2026"))
    }

    @Test fun theNotInCopyPageEscapesItsText() {
        val html = notInCopyPage("a <b> & c")
        assertTrue(html.contains("a &lt;b&gt; &amp; c"))
        assertFalse(html.contains("<b>"))
        assertNotNull(html)
    }
}
