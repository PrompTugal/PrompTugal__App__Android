package ai.riskmandate.app.features.lab

import java.io.File

/** Loads the bundled Lab files straight from the source tree, so pure JVM tests need no Android assets. */
object LabTestData {
    private val root = File("src/main/assets/feature_lab")

    fun text(path: String): String = File(root, path).readText(Charsets.UTF_8)

    fun load(): LabData {
        val graphs = HashMap<String, Graph>()
        val scenarios = File(root, "scenarios").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { f ->
            val s = LabFormats.parseScenario(f.readText())
            val gp = s.graphPath
            if (gp == null) s else s.copy(graph = graphs.getOrPut(gp) { LabFormats.parseGraph(text(gp)) })
        }
        val recordings = File(root, "recordings").walkTopDown().filter { it.isFile && it.name.endsWith(".json") }
            .sortedBy { it.path }.map { LabFormats.parseRecording(it.readText()) }.toList()
        return LabData(scenarios, recordings, emptyList())
    }

    /** The tool-test catalogue straight from the source tree (the same files the app bundles). */
    fun toolCatalogue(): ToolCatalogue {
        val files = File(root, "tooltests").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
        val kinds = ArrayList<ToolKind>(); val cases = ArrayList<ToolCase>(); val texts = ArrayList<String>()
        for (f in files) { val t = f.readText(); val (k, c) = ToolTestFormats.parse(t); kinds += k; cases += c; texts += t }
        return ToolCatalogue(kinds, cases, emptyList(), ToolTestFormats.hash(texts))
    }

    fun scenario(id: String): Scenario = load().scenario(id)!!

    fun call(name: String, vararg args: Pair<String, String>) = ToolCall(name, args.toMap())
}
