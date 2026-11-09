package ai.riskmandate.app.llm

import java.net.MalformedURLException
import java.net.URL

/**
 * Where a model download may go (security checklist G.1, G.2). Separate from `Links.isAllowed`: the browser and
 * WebView allow-list does not grow. The first URL is always a pinned [ModelSpec.url]; Hugging Face answers with
 * a redirect to its CDN (seen: us.aws.cdn.hf.co), so redirects are followed by hand, and every hop must be https
 * on port 443, without user info, to `huggingface.co` or a host under `hf.co`. At most [MAX_REDIRECTS] hops.
 */
object DownloadPolicy {
    const val MAX_REDIRECTS = 5

    fun hostAllowed(host: String): Boolean {
        val h = host.lowercase()
        return h == "huggingface.co" || h == "hf.co" || (h.endsWith(".hf.co") && LABELS.matches(h))
    }

    /** Null if [url] may be fetched, else the reason. */
    fun refusal(url: URL): String? = when {
        url.protocol != "https" -> "not https"
        url.userInfo != null -> "user info in the address"
        url.port != -1 && url.port != 443 -> "port ${url.port}"
        !hostAllowed(url.host) -> "host not allowed"
        else -> null
    }

    /** The next hop for a redirect from [from] to [location] (absolute or relative), or the reason it is refused. */
    fun redirect(from: URL, location: String?): Result<URL> {
        if (location.isNullOrBlank()) return Result.failure(IllegalStateException("redirect without a location"))
        val next = try {
            URL(from, location.trim())
        } catch (e: MalformedURLException) {
            return Result.failure(IllegalStateException("bad redirect location"))
        }
        return refusal(next)?.let { Result.failure(IllegalStateException("redirect refused: $it")) } ?: Result.success(next)
    }

    /** Room for the rest of the file plus a margin, so the device is never filled to the brim. */
    fun hasRoom(availableBytes: Long, spec: ModelSpec, alreadyHave: Long): Boolean =
        availableBytes >= (spec.sizeBytes - alreadyHave).coerceAtLeast(0) + MARGIN_BYTES

    const val MARGIN_BYTES = 500L * 1024 * 1024

    private val LABELS = Regex("^([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+hf\\.co$")
}
