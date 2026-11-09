package ai.riskmandate.app.features.site

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * The WebView's client: the URL decision, the sub-resource block and the offline copy.
 * Navigation that fails the allow-list is handed to [onExternal] and never loads here.
 */
class SiteWebViewClient(
    private val context: Context,
    private val switch: OfflineSwitch,
    private val onExternal: (String) -> Unit,
    private val onMainFrameError: () -> Unit,
    private val notInCopyHtml: () -> String,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (SitePolicy.decide(url) == Decision.IN_APP) return false
        // A frame inside a page must not take the visitor away; only a main-frame navigation opens the browser.
        if (request.isForMainFrame) onExternal(url)
        return true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (SitePolicy.blockSubresource(url)) return empty(403, "Blocked")
        if (!switch.offline) return null
        if (request.method != "GET") return empty(405, "Offline")
        val page = OfflineCopy.pageFor(url)
        if (page != null) {
            val bytes = context.resources.openRawResource(page.raw).use { it.readBytes() }
            return WebResourceResponse("text/html", "utf-8", 200, "OK", NO_STORE, ByteArrayInputStream(bytes))
        }
        // Offline: nothing else is fetched. A page that is not in the copy says so; a sub-resource is simply absent.
        if (request.isForMainFrame) {
            return WebResourceResponse("text/html", "utf-8", 404, "Not in the offline copy", NO_STORE,
                ByteArrayInputStream(notInCopyHtml().toByteArray(Charsets.UTF_8)))
        }
        return empty(404, "Offline")
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame && !switch.offline) onMainFrameError()
    }

    @Deprecated("Pre-API 23 signature; kept so a very old WebView still reports a failed main page")
    override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
        if (!switch.offline && failingUrl != null && SitePolicy.decide(failingUrl) == Decision.IN_APP) onMainFrameError()
    }

    private fun empty(status: Int, reason: String) =
        WebResourceResponse("text/plain", "utf-8", status, reason, NO_STORE, ByteArrayInputStream(ByteArray(0)))

    private companion object {
        val NO_STORE = mapOf("Cache-Control" to "no-store")
    }
}

/**
 * Locks the WebView down: JavaScript on (the site's menu and demos are scripts), no JavaScript interface,
 * no file or content access, Safe Browsing on, mixed content never allowed, no third-party cookies.
 */
@SuppressLint("SetJavaScriptEnabled")
fun WebView.applySiteSettings() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        safeBrowsingEnabled = true
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        setGeolocationEnabled(false)
        mediaPlaybackRequiresUserGesture = true
        cacheMode = WebSettings.LOAD_DEFAULT
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
}
