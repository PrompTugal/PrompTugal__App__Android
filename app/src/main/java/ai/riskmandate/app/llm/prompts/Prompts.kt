package ai.riskmandate.app.llm.prompts

import android.content.res.AssetManager
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared, versioned prompt texts (architect: llm/prompts/). Each is a bundled asset
 * `assets/prompts/<name>.v<N>.txt`, plain UTF-8 text, never fetched at run time. A new wording is a new version
 * file, so a run log or an eval can say exactly which text it used. Claims in them come only from riskmandate.ai
 * (security checks the text). Chat and the Workbench both read them here.
 */
object Prompts {
    /** The RiskMandate base system block (moved from chat's copy). */
    val RISKMANDATE_BASE = PromptId("riskmandate_base", 1)

    const val MAX_CHARS = 8_000

    private val cache = ConcurrentHashMap<PromptId, String>()

    /** Reads [id] off the main thread; cached for the process (assets do not change while it runs). */
    suspend fun load(assets: AssetManager, id: PromptId): String = cache[id] ?: withContext(Dispatchers.IO) { loadBlocking(assets, id) }

    /** Blocking read, for tests and for callers already off the main thread. Throws IOException when missing or too long. */
    fun loadBlocking(assets: AssetManager, id: PromptId): String = cache.getOrPut(id) {
        val text = assets.open(id.path).use { it.readBytes() }.toString(Charsets.UTF_8).trimEnd('\n', '\r')
        if (text.isBlank() || text.length > MAX_CHARS) throw IOException("${id.path}: empty or longer than $MAX_CHARS characters")
        text
    }
}

/** A prompt's name and version. The name is lowercase letters, digits and underscores, so it is always a safe file name. */
data class PromptId(val name: String, val version: Int) {
    init {
        require(NAME.matches(name)) { "prompt name '$name'" }
        require(version in 1..999) { "prompt version $version" }
    }

    val path: String get() = "prompts/$name.v$version.txt"

    override fun toString(): String = "$name.v$version"

    private companion object {
        val NAME = Regex("^[a-z][a-z0-9_]{0,40}$")
    }
}
