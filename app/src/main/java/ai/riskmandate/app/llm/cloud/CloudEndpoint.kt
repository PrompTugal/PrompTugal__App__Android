package ai.riskmandate.app.llm.cloud

/**
 * The one network destination of the cloud client: https://openrouter.ai. A list of its own, separate from
 * `Links` (the pages the app opens) and from the model-download hosts. Nothing else may be called.
 */
object CloudEndpoint {
    const val HOST = "openrouter.ai"
    const val CHAT_URL = "https://openrouter.ai/api/v1/chat/completions"
    private const val PREFIX = "https://"
    private const val PATH_PREFIX = "/api/v1/"

    /** Pure check, JVM-testable: https, host exactly [HOST], no userinfo, port 443 at most, a path under /api/v1/. */
    fun isAllowed(url: String): Boolean {
        if (url.any { it == '@' || it == '\\' || it.isWhitespace() || it.isISOControl() }) return false
        if (!url.startsWith(PREFIX)) return false // lower-case scheme only: the app builds this URL itself
        val rest = url.substring(PREFIX.length)
        val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        val host = authority.substringBefore(':')
        val port = authority.substringAfter(':', "")
        if (host != HOST || authority.contains('%')) return false
        if (authority.contains(':') && port != "443") return false
        val path = rest.substring(authority.length)
        return path.startsWith(PATH_PREFIX) && '#' !in path
    }
}

/**
 * A model the cloud client may ask for: pinned ids (verified against OpenRouter's public model list on 9 Nov
 * 2026) with the list prices in US dollars per million tokens. The prices are only a worst-case estimate for the
 * spending cap when a response carries no cost; the real cost is always taken from OpenRouter's `usage.cost`.
 */
data class CloudModel(val id: String, val label: String, val inputUsdPerMtok: Double, val outputUsdPerMtok: Double, val supportsTools: Boolean)

object CloudModels {
    val SONNET = CloudModel("anthropic/claude-sonnet-5.5", "Claude Sonnet 5.5", 2.0, 10.0, true)
    val GEMMA4 = CloudModel("google/gemma-4-26b-a4b-it", "Gemma 4 26B A4B IT (closest hosted size to the on-device Gemma 4)", 0.09, 0.30, true)
    val GEMMA3 = CloudModel("google/gemma-3-4b-it", "Gemma 3 4B IT (closest hosted size to Gemma 4 E2B)", 0.05, 0.10, false)

    val ALL: List<CloudModel> = listOf(SONNET, GEMMA4, GEMMA3)

    fun byId(id: String): CloudModel? = ALL.firstOrNull { it.id == id }
}
