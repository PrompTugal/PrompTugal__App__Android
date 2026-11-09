package ai.riskmandate.app.experiments

import android.content.res.AssetManager

/**
 * Loads the tool-test catalogue from the app's own assets (`experiments/tooltests`), one JSON file per kind.
 * Read-only, bundled files only.
 */
object ToolTestAssets {
    const val ROOT = "experiments/tooltests"

    private fun list(assets: AssetManager): List<String> = runCatching { assets.list(ROOT)?.toList() }.getOrNull().orEmpty()

    /**
     * The tool-test catalogue from the `tooltests` folder, one JSON file per kind, in file-name order. A bad file is
     * skipped and reported; a case id used twice across files is a problem and the second is dropped. Off the
     * main thread.
     */
    fun load(assets: AssetManager): ToolCatalogue {
        val problems = ArrayList<String>()
        val kinds = ArrayList<ToolKind>()
        val cases = ArrayList<ToolCase>()
        val texts = ArrayList<String>()
        for (f in list(assets).filter { it.endsWith(".json") }.sorted()) {
            try {
                val text = assets.open("$ROOT/$f").bufferedReader(Charsets.UTF_8).use { it.readText() }
                val (kind, parsed) = ToolTestFormats.parse(text)
                val fresh = parsed.filter { c -> (cases.none { it.id == c.id }).also { ok -> if (!ok) problems += "tooltests/$f: duplicate case id ${c.id}" } }
                kinds += kind; cases += fresh; texts += text
            } catch (e: Exception) {
                problems += "tooltests/$f: ${e.message}"
            }
        }
        return ToolCatalogue(kinds, cases, problems, ToolTestFormats.hash(texts))
    }
}
