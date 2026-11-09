package ai.riskmandate.app.features.lab

import android.content.res.AssetManager

/** What the Lab found in `assets/feature_lab/`, and the files it could not read (shown as a count). */
data class LabData(val scenarios: List<Scenario>, val recordings: List<Recording>, val problems: List<String>) {
    fun scenario(id: String) = scenarios.firstOrNull { it.id == id }
    fun recordingsFor(id: String) = recordings.filter { it.scenarioId == id }
}

/**
 * Loads scenarios (the `scenarios` folder), their graphs (the path a scenario names, under the same folder)
 * and recordings (the `recordings` folder, any depth) from the app's own assets, copied there at build time from the
 * vault. Read-only, bundled files only. Call off the main thread. One bad file is skipped and reported.
 */
object LabAssets {
    const val ROOT = "feature_lab"

    fun load(assets: AssetManager): LabData {
        val problems = ArrayList<String>()
        val graphs = HashMap<String, Graph?>()
        fun read(path: String) = assets.open("$ROOT/$path").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val scenarios = list(assets, "scenarios").filter { it.endsWith(".json") }.sorted().mapNotNull { f ->
            try {
                val s = LabFormats.parseScenario(read("scenarios/$f"))
                val gp = s.graphPath
                if (gp == null) s else s.copy(graph = graphs.getOrPut(gp) { LabFormats.parseGraph(read(gp)) })
            } catch (e: Exception) { problems += "scenarios/$f: ${e.message}"; null }
        }
        val recordings = files(assets, "recordings").sorted().mapNotNull { f ->
            try { LabFormats.parseRecording(read(f)) } catch (e: Exception) { problems += "$f: ${e.message}"; null }
        }
        return LabData(scenarios, recordings, problems)
    }

    /**
     * The tool-test catalogue from the `tooltests` folder, one JSON file per kind, in file-name order. A bad file is
     * skipped and reported; a case id used twice across files is a problem and the second is dropped. Off the
     * main thread.
     */
    fun loadToolTests(assets: AssetManager): ToolCatalogue {
        val problems = ArrayList<String>()
        val kinds = ArrayList<ToolKind>()
        val cases = ArrayList<ToolCase>()
        val texts = ArrayList<String>()
        for (f in list(assets, "tooltests").filter { it.endsWith(".json") }.sorted()) {
            try {
                val text = assets.open("$ROOT/tooltests/$f").bufferedReader(Charsets.UTF_8).use { it.readText() }
                val (kind, parsed) = ToolTestFormats.parse(text)
                val fresh = parsed.filter { c -> (cases.none { it.id == c.id }).also { ok -> if (!ok) problems += "tooltests/$f: duplicate case id ${c.id}" } }
                kinds += kind; cases += fresh; texts += text
            } catch (e: Exception) {
                problems += "tooltests/$f: ${e.message}"
            }
        }
        return ToolCatalogue(kinds, cases, problems, ToolTestFormats.hash(texts))
    }

    private fun list(assets: AssetManager, dir: String): List<String> =
        runCatching { assets.list("$ROOT/$dir")?.toList() }.getOrNull().orEmpty()

    private fun files(assets: AssetManager, dir: String): List<String> =
        list(assets, dir).flatMap { n ->
            val p = "$dir/$n"
            if (n.endsWith(".json")) listOf(p) else files(assets, p)
        }
}
