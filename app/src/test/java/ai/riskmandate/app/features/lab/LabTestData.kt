package ai.riskmandate.app.features.lab

import ai.riskmandate.app.experiments.Graph
import ai.riskmandate.app.experiments.ToolCall
import java.io.File

/** Loads the bundled Lab files straight from the source tree, so pure JVM tests need no Android assets. */
object LabTestData {
    private val root = File("src/main/assets/feature_lab")

    fun text(path: String): String = File(root, path).readText(Charsets.UTF_8)

    fun load(lang: String = "en"): LabData {
        val graphs = HashMap<String, Graph>()
        val scenarios = File(root, "scenarios").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { f ->
            val s = LabFormats.parseScenario(f.readText(), lang)
            val gp = s.graphPath
            if (gp == null) s else s.copy(graph = graphs.getOrPut(gp) { LabFormats.parseGraph(text(gp)) })
        }
        val recordings = File(root, "recordings").walkTopDown().filter { it.isFile && it.name.endsWith(".json") }
            .sortedBy { it.path }.map { LabFormats.parseRecording(it.readText(), lang) }.toList()
        return LabData(scenarios, recordings, emptyList())
    }

    fun scenario(id: String): Scenario = load().scenario(id)!!

    fun call(name: String, vararg args: Pair<String, String>) = ToolCall(name, args.toMap())
}
