package ai.riskmandate.app.features.stand

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class StandLocalisationTest {
    private fun keys(path: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.io.File(path))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("name") }.toSet()
    }

    @Test fun everyStandStringHasAPortugueseTranslation() {
        val en = keys("src/main/res/values/feature_stand.xml")
        val pt = keys("src/main/res/values-pt-rPT/feature_stand.xml")
        assertEquals(emptySet<String>(), en - pt)
        assertEquals(emptySet<String>(), pt - en)
    }
}
