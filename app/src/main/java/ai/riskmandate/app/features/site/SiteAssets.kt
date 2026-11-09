package ai.riskmandate.app.features.site

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

/**
 * Serves the offline copy from `assets/feature_site/` on the app-controlled origin [OfflineCopy.ASSET_BASE]
 * with androidx.webkit's [WebViewAssetLoader] and an `AssetsPathHandler` on `/assets/` only (no `/res/`).
 * Never `file://`. Only URLs under `/assets/feature_site/` reach the loader; anything else on the origin is
 * refused by [SitePolicy] before it gets here.
 */
object SiteAssets {
    private val NO_STORE = mapOf("Cache-Control" to "no-store")

    fun newLoader(context: Context): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .setDomain(OfflineCopy.ASSET_HOST)
            .setHttpAllowed(false)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()

    /** The response for an asset URL, or null for a URL that is not in the offline copy's folder. */
    fun serve(loader: WebViewAssetLoader, url: String, method: String, forMainFrame: Boolean, notInCopyHtml: () -> String): WebResourceResponse? {
        if (!OfflineCopy.isAssetUrl(url)) return null
        if (method != "GET") return response("text/plain", 405, "Method not allowed", ByteArray(0))
        val path = OfflineCopy.assetPath(url)
        // A missing file comes back from AssetsPathHandler as an empty response (no data), not as null.
        val found = if (safe(path)) loader.shouldInterceptRequest(Uri.parse(OfflineCopy.ASSET_BASE + path))?.takeIf { it.data != null } else null
        if (found != null) return found
        // Not bundled: a page says so (in the app's language); a sub-resource is simply absent.
        return if (forMainFrame) {
            response("text/html", 404, "Not in the offline copy", notInCopyHtml().toByteArray(Charsets.UTF_8))
        } else {
            response("text/plain", 404, "Not found", ByteArray(0))
        }
    }

    private val SAFE_PATH = Regex("""^[A-Za-z0-9][A-Za-z0-9._/-]*$""")

    /** Plain relative file names only: no `..`, no encoded characters, no leading slash or backslash. */
    fun safe(path: String): Boolean =
        SAFE_PATH.matches(path) && path.split('/').none { it.isEmpty() || it == "." || it == ".." }

    private fun response(mime: String, status: Int, reason: String, body: ByteArray) =
        WebResourceResponse(mime, "utf-8", status, reason, NO_STORE, ByteArrayInputStream(body))
}
