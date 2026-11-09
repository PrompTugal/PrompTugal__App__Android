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

    /**
     * The only URL literals a feature may hold that Links.isAllowed refuses: refusal samples a screen SHOWS to the
     * visitor, each run through the real check at run time (feature C, "this app's mandate"). Exact literal, per file,
     * agreed with the owner and reviewed by QA (docs/qa/reviews/2026-11-09-dev.mandate-*.md). Adding one means editing
     * this map, so it is always a reviewed change. Each entry must still be present in its file, and the feature must
     * run its samples through `Links::isAllowed` or `Links.isAllowed` in code (not only in a comment).
     */
    private val shownRefusalSamples: Map<String, Set<String>> = mapOf(
        "MandateModel.kt" to setOf(
            "http://riskmandate.ai/", "https://riskmandate.ai:8443/", "https://example.com/", "https://riskmandate.ai@example.com/",
        ),
    )

    @Test fun sourcesWereFound() = assertTrue("features dir not found: wrong working directory?", sources.isNotEmpty())

    @Test fun everyUrlLiteralInFeatureSourcesIsAllowed() {
        val bad = mutableListOf<String>()
        for (f in sources) {
            // Skip comments: a URL in a doc comment is not code.
            val code = f.readLines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") || it.trim().startsWith("/*") }
            for (line in code) for (m in urlLiteral.findAll(line)) {
                val url = m.value.trim('"', '\'')
                if (url.substringAfter("://", "").isEmpty()) continue // a bare scheme prefix such as "https://" names no host
                if (!Links.isAllowed(url) && url !in shownRefusalSamples[f.name].orEmpty()) bad += "${f.name}: $url"
            }
        }
        assertEquals("URL literals outside Links.isAllowed", emptyList<String>(), bad)
    }

    @Test fun shownRefusalSamplesAreReallyRefusedStillPresentAndRunThroughTheRealCheck() {
        for ((name, urls) in shownRefusalSamples) {
            // Not merged to this branch yet: nothing to check. Once the file exists, every entry is held to account.
            val file = sources.firstOrNull { it.name == name } ?: continue
            val text = file.readText()
            for (u in urls) {
                assertFalse("$u is allowed: it needs no exemption", Links.isAllowed(u))
                assertTrue("$u is no longer in $name: remove it from shownRefusalSamples", text.contains("\"$u\""))
            }
            val inCode = file.parentFile.walkTopDown().filter { it.extension == "kt" }.any { f ->
                f.readLines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") || it.trim().startsWith("/*") }
                    .any { it.contains("Links::isAllowed") || it.contains("Links.isAllowed") }
            }
            assertTrue("${file.parentFile.name}: the samples must be run through Links::isAllowed in code", inCode)
        }
    }

    /**
     * Any code that navigates must go through the allow-list: the file names `Links.` itself, or uses a type
     * declared in a file of the same feature that does (e.g. `SitePolicy`, whose `decide` calls `Links.isAllowed`).
     * And every feature that navigates has at least one file that names `Links.isAllowed`.
     */
    @Test fun navigatingCodeConsultsTheAllowList() {
        val navigates = Regex("""\bloadUrl\(|\bloadDataWithBaseURL\(|\bACTION_VIEW\b|CustomTabsIntent|\bstartActivity\(|shouldOverrideUrlLoading|shouldInterceptRequest|\bUri\.parse\(""")
        val decl = Regex("""^\s*(?:internal\s+|private\s+)?(?:data\s+|enum\s+)?(?:object|class)\s+(\w+)""", RegexOption.MULTILINE)
        val offenders = mutableListOf<String>()
        for (dir in featuresDir.listFiles { f -> f.isDirectory }.orEmpty()) {
            val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            val gated = files.filter { it.readText().contains("Links.isAllowed") }
            val gateTypes = gated.flatMap { f -> decl.findAll(f.readText()).map { it.groupValues[1] }.toList() }.toSet()
            val navigating = files.filter { navigates.containsMatchIn(it.readText()) }
            if (navigating.isNotEmpty() && gated.isEmpty()) offenders += "${dir.name}: navigates, no file names Links.isAllowed"
            for (f in navigating) {
                val t = f.readText()
                val ok = t.contains("Links.") || gateTypes.any { Regex("""\b$it\b""").containsMatchIn(t) }
                if (!ok) offenders += "${dir.name}/${f.name}"
            }
        }
        assertEquals("navigating code that does not go through the allow-list", emptyList<String>(), offenders)
    }

    @Test fun webViewIsNeverGivenFileOrContentAccessOrJavascriptBridgeWithoutReview() {
        // Review triggers, not bans: only an enabling value matches (`= false` is fine), so a locked-down
        // WebView passes; anything that opens a hole makes QA read the code before PASS.
        val risky = Regex(
            """allowFileAccessFromFileURLs\s*=\s*true|allowUniversalAccessFromFileURLs\s*=\s*true|allowFileAccess\s*=\s*true|allowContentAccess\s*=\s*true|""" +
                """setAllowFileAccess(?:FromFileURLs)?\(\s*true|setAllowUniversalAccessFromFileURLs\(\s*true|setAllowContentAccess\(\s*true|""" +
                """addJavascriptInterface|javaScriptCanOpenWindowsAutomatically\s*=\s*true|setJavaScriptCanOpenWindowsAutomatically\(\s*true|""" +
                """MIXED_CONTENT_ALWAYS_ALLOW|MIXED_CONTENT_COMPATIBILITY_MODE|onReceivedSslError|safeBrowsingEnabled\s*=\s*false|WebView\.setWebContentsDebuggingEnabled""")
        val hits = sources.filter { risky.containsMatchIn(it.readText()) }.map { it.name }
        assertEquals("risky WebView settings: QA must review (then adjust this test with a reason)", emptyList<String>(), hits)
    }

    @Test fun aWebViewFeatureLocksTheSettingsDown() {
        // Positive check: a feature that creates a WebView must switch file access, content access and mixed content off.
        for (f in sources.filter { Regex("""\bWebView\(""").containsMatchIn(it.readText()) || it.readText().contains("WebView.") }) {
            val pkg = f.parentFile.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
            for (needle in listOf("allowFileAccess = false", "allowContentAccess = false", "MIXED_CONTENT_NEVER_ALLOW")) {
                assertTrue("${f.parentFile.name}: a WebView feature must set $needle", pkg.contains(needle))
            }
        }
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
