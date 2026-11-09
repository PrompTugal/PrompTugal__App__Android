package ai.riskmandate.app.experiments

import java.io.File

/** Loads the bundled tool-test files straight from the source tree, so pure JVM tests need no Android assets. */
object ExperimentsTestData {
    private val root = File("src/main/assets/experiments")

    fun text(path: String): String = File(root, path).readText(Charsets.UTF_8)

    fun toolCatalogue(): ToolCatalogue {
        val files = File(root, "tooltests").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
        val kinds = ArrayList<ToolKind>(); val cases = ArrayList<ToolCase>(); val texts = ArrayList<String>()
        for (f in files) { val t = f.readText(); val (k, c) = ToolTestFormats.parse(t); kinds += k; cases += c; texts += t }
        return ToolCatalogue(kinds, cases, emptyList(), ToolTestFormats.hash(texts))
    }
}
