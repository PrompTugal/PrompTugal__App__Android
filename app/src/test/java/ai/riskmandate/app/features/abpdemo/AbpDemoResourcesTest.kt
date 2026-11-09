package ai.riskmandate.app.features.abpdemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/** Plain JVM checks on the feature's own files (no Robolectric). */
class AbpDemoResourcesTest {
    @Test fun everyAbpDemoStringIsTranslatedAndNothingIsHardcoded() {
        fun keys(path: String): Set<String> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.io.File(path))
            val nodes = doc.getElementsByTagName("string")
            return (0 until nodes.length).map { nodes.item(it) as Element }.map { it.getAttribute("name") }.toSet()
        }
        val en = keys("src/main/res/values/feature_abpdemo.xml")
        val pt = keys("src/main/res/values-pt-rPT/feature_abpdemo.xml")
        assertEquals(emptySet<String>(), en - pt)
        assertEquals(emptySet<String>(), pt - en)
        val src = java.io.File("src/main/java/ai/riskmandate/app/features/abpdemo").listFiles { f -> f.name.endsWith(".kt") }!!
        val literal = Regex("""\bText\(\s*"|contentDescription\s*=\s*"[^$]""")
        assertEquals(emptyList<String>(), src.filter { literal.containsMatchIn(it.readText()) }.map { it.name })
    }

    @Test fun noEmDashesInTheStrings() {
        for (p in listOf("values", "values-pt-rPT")) {
            assertTrue(!java.io.File("src/main/res/$p/feature_abpdemo.xml").readText().contains('—'))
        }
    }
}
