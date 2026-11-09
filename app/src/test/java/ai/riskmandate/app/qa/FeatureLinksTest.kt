package ai.riskmandate.app.qa

import ai.riskmandate.app.Links
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No feature can open a URL outside Links.isAllowed (QA owns this file). Static checks over the
 * features' sources and resources, plus the allow-list against the inputs a feature could meet.
 * Static checks cannot prove a WebView never navigates elsewhere: the manual plan (docs/qa/summit-test-plan.md,
 * A-6 to A-9) and the feature's own tests cover link handling at run time.
 */
class FeatureLinksTest {
    private val featuresDir = File("src/main/java/ai/riskmandate/app/features")
    private val sources get() = featuresDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    private val urlLiteral = Regex("""["'](?:[a-zA-Z][a-zA-Z0-9+.-]*):(?://)[^"'\s]*["']|"(?:https?|ftp|file|data|javascript|intent|market|content)[:][^"]*"""")

    @Test fun sourcesWereFound() = assertTrue("features dir not found: wrong working directory?", sources.isNotEmpty())

    @Test fun everyUrlLiteralInFeatureSourcesIsAllowed() {
        val bad = mutableListOf<String>()
        for (f in sources) {
            // Skip comments: a URL in a doc comment is not code.
            val code = f.readLines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") || it.trim().startsWith("/*") }
            for (line in code) for (m in urlLiteral.findAll(line)) {
                val url = m.value.trim('"', '\'')
                if (!Links.isAllowed(url)) bad += "${f.name}: $url"
            }
        }
        assertEquals("URL literals outside Links.isAllowed", emptyList<String>(), bad)
    }

    /** Any code that navigates somewhere must name the allow-list in the same file. */
    @Test fun navigatingCodeConsultsTheAllowList() {
        val navigates = Regex("""\bloadUrl\(|\bloadDataWithBaseURL\(|\bACTION_VIEW\b|CustomTabsIntent|\bstartActivity\(|shouldOverrideUrlLoading|shouldInterceptRequest|\bUri\.parse\(""")
        val offenders = sources.filter { navigates.containsMatchIn(it.readText()) && !it.readText().contains("Links.isAllowed") && !it.readText().contains("Links.") }
            .map { it.name }
        assertEquals("files that navigate without consulting Links", emptyList<String>(), offenders)
    }

    @Test fun webViewIsNeverGivenFileOrContentAccessOrJavascriptBridgeWithoutReview() {
        // Review triggers, not bans: if one of these appears QA reads the code before PASS.
        val risky = Regex("""allowFileAccessFromFileURLs|allowUniversalAccessFromFileURLs|setAllowContentAccess\(\s*true|addJavascriptInterface|setJavaScriptCanOpenWindowsAutomatically\(\s*true|MIXED_CONTENT_ALWAYS_ALLOW|setAllowFileAccess\(\s*true|onReceivedSslError""")
        val hits = sources.filter { risky.containsMatchIn(it.readText()) }.map { it.name }
        assertEquals("risky WebView settings: QA must review (then adjust this test with a reason)", emptyList<String>(), hits)
    }

    @Test fun noFeatureResourceHoldsAUrlOutsideTheAllowList() {
        val bad = mutableListOf<String>()
        val url = Regex("""https?://[^\s"'<>)\]]+""")
        for (dir in File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()) {
            for (f in dir.listFiles { x -> x.name == "features.xml" || x.name.startsWith("feature_") }.orEmpty()) {
                for (m in url.findAll(f.readText())) if (!Links.isAllowed(m.value.trimEnd('.', ',', ';'))) bad += "${f.path}: ${m.value}"
            }
        }
        assertEquals("URLs in feature strings outside Links.isAllowed", emptyList<String>(), bad)
    }

    @Test fun manifestStillForbidsCleartextAndHasNoExtraExportedComponents() {
        val m = File("src/main/AndroidManifest.xml").readText()
        assertTrue(m.contains("""android:usesCleartextTraffic="false""""))
        // Only MainActivity is exported: a feature must not add a component other apps can reach.
        assertEquals(1, Regex("""android:exported="true"""").findAll(m).count())
    }

    @Test fun allowListRefusesWhatAFeatureCouldMeet() {
        val refused = listOf(
            "http://riskmandate.ai/", "https://sharness.ai/", "https://riskmandate.ai.evil.example/",
            "javascript:alert(1)", "file:///data/data/ai.riskmandate.app/", "content://x", "data:text/html,<b>x</b>",
            "intent://scan/#Intent;scheme=zxing;end", "market://details?id=x", "about:blank", "ftp://riskmandate.ai/",
            "https://riskmandate.ai@evil.example/", "https://riskmandate.ai:8443/", "//riskmandate.ai/", "/relative", "", "  ",
            "https://xn--riskmandate-ai.example/", "mailto:a@riskmandate.ai", "tel:+351000000", "blob:https://riskmandate.ai/x",
            "view-source:https://riskmandate.ai/",
        )
        for (u in refused) assertFalse("should refuse '$u'", Links.isAllowed(u))
        for (u in listOf("https://riskmandate.ai/", "https://www.riskmandate.ai/x?y=1#z")) assertTrue(Links.isAllowed(u))
    }
}
