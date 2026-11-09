package ai.riskmandate.app.features.site

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Serves the offline copy from `assets/feature_site/` on the app-controlled origin
 * [OfflineCopy.ASSET_BASE]: the same job as WebViewAssetLoader's AssetsPathHandler, limited to this one folder.
 * Never `file://`. TODO: swap for androidx.webkit WebViewAssetLoader once app.android adds the dependency.
 */
object SiteAssets {
    private val SAFE_PATH = Regex("""^[A-Za-z0-9][A-Za-z0-9._/-]*$""")
    private val NO_STORE = mapOf("Cache-Control" to "no-store")

    /** The response for an asset URL, or null for a URL that is not on the asset origin. */
    fun serve(context: Context, url: String, method: String, forMainFrame: Boolean, notInCopyHtml: () -> String): WebResourceResponse? {
        if (!OfflineCopy.isAssetUrl(url)) return null
        if (method != "GET") return response("text/plain", 405, "Method not allowed", ByteArray(0))
        val path = OfflineCopy.assetPath(url)
        val bytes = if (safe(path)) read(context, path) else null
        if (bytes != null) return response(mimeOf(path), 200, "OK", bytes)
        // Not bundled: a page says so (in the app's language); a sub-resource is simply absent.
        return if (forMainFrame) {
            response("text/html", 404, "Not in the offline copy", notInCopyHtml().toByteArray(Charsets.UTF_8))
        } else {
            response("text/plain", 404, "Not found", ByteArray(0))
        }
    }

    /** Plain relative file names only: no `..`, no encoded characters, no leading slash or backslash. */
    fun safe(path: String): Boolean =
        SAFE_PATH.matches(path) && path.split('/').none { it.isEmpty() || it == "." || it == ".." }

    private fun read(context: Context, path: String): ByteArray? = try {
        context.assets.open("${OfflineCopy.ASSET_DIR}/$path").use { it.readBytes() }
    } catch (e: IOException) {
        null
    }

    fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html"
        "css" -> "text/css"
        "js" -> "text/javascript"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "woff2" -> "font/woff2"
        "json" -> "application/json"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun response(mime: String, status: Int, reason: String, body: ByteArray) =
        WebResourceResponse(mime, "utf-8", status, reason, NO_STORE, ByteArrayInputStream(body))
}
