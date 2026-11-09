package ai.riskmandate.app.qa

import ai.riskmandate.app.R
import ai.riskmandate.app.features.FEATURES
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * pt-PT completeness for every feature string (QA owns this file). Reads the XML directly, so a feature
 * file added later (res/values/feature_<id>.xml) is covered without editing this test. Also: no feature
 * screen hardcodes text, and every feature title resolves in both languages.
 */
@RunWith(AndroidJUnit4::class)
class FeatureStringsTest {
    private val res = File("src/main/res")

    private fun featureFiles(dir: String): List<File> =
        File(res, dir).listFiles { f -> f.name == "features.xml" || (f.name.startsWith("feature_") && f.name.endsWith(".xml")) }
            .orEmpty().sortedBy { it.name }

    /** name -> value for every translatable <string>, plus plurals and string-arrays by name. */
    private fun entries(files: List<File>): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (f in files) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            for (tag in listOf("string", "plurals", "string-array")) {
                val nodes = doc.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val e = nodes.item(i) as Element
                    if (e.getAttribute("translatable") == "false") continue
                    out[e.getAttribute("name")] = e.textContent.trim()
                }
            }
        }
        return out
    }

    @Test fun everyFeatureStringHasAPortugueseTranslation() {
        val en = entries(featureFiles("values"))
        val pt = entries(featureFiles("values-pt-rPT"))
        assertTrue("no feature strings found: wrong working directory?", en.isNotEmpty())
        assertEquals("feature strings missing in pt-PT (values-pt-rPT/feature*.xml)", emptySet<String>(), en.keys - pt.keys)
        assertEquals("pt-PT feature strings with no English original", emptySet<String>(), pt.keys - en.keys)
    }

    @Test fun portugueseFeatureStringsAreNotEmptyOrPlaceholders() {
        for ((k, v) in entries(featureFiles("values-pt-rPT"))) {
            assertFalse("pt-PT string $k is empty", v.isBlank())
            assertFalse("pt-PT string $k looks like a placeholder: $v", Regex("TODO|FIXME|XXX|lorem", RegexOption.IGNORE_CASE).containsMatchIn(v))
        }
    }

    @Test fun formatArgumentsMatchBetweenLanguages() {
        val en = entries(featureFiles("values"))
        val pt = entries(featureFiles("values-pt-rPT"))
        val spec = Regex("%(\\d+\\$)?[sdf]")
        for ((k, v) in en) {
            val p = pt[k] ?: continue
            assertEquals("format arguments of $k differ between en and pt-PT",
                spec.findAll(v).map { it.value }.sorted().toList(), spec.findAll(p).map { it.value }.sorted().toList())
        }
    }

    @Test @Config(qualifiers = "pt-rPT")
    fun everyFeatureTitleResolvesInPortuguese() {
        val app: Application = ApplicationProvider.getApplicationContext()
        for (f in FEATURES) assertTrue("title of ${f.id}", app.getString(f.title).isNotBlank())
    }

    @Test @Config(qualifiers = "en")
    fun everyFeatureTitleResolvesInEnglish() {
        val app: Application = ApplicationProvider.getApplicationContext()
        val titles = FEATURES.map { app.getString(it.title) }
        assertEquals("two features share a title", titles.size, titles.toSet().size)
    }

    @Test fun featureScreensHardcodeNoText() {
        val roots = listOf(File("src/main/java/ai/riskmandate/app/features"))
        val literal = Regex("""\bText\(\s*"|contentDescription\s*=\s*"|\btitle\s*=\s*"|\blabel\s*=\s*"""")
        val offenders = roots.flatMap { r -> r.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { literal.containsMatchIn(it.readText()) }.map { it.path }
        assertEquals("hardcoded user-visible text (use string resources, with pt-PT)", emptyList<String>(), offenders)
    }

    @Test fun titleStringsStayWhereTheContractPutsThem() {
        // Feature.title is a @StringRes: the three skeleton titles must still exist.
        assertTrue(R.string.feature_site_title != 0 && R.string.feature_abpdemo_title != 0 && R.string.feature_mandate_title != 0)
    }
}
