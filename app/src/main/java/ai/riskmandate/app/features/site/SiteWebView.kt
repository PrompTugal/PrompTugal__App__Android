package ai.riskmandate.app.features.site

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Message
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * The WebView's client: the URL decision, the sub-resource block and the offline copy.
 * Main-frame navigation (links, redirects) that fails the allow-list is handed to [onExternal] and never loads here.
 */
class SiteWebViewClient(
    private val context: Context,
    private val switch: OfflineSwitch,
    private val onExternal: (String) -> Unit,
    private val onMainFrameError: () -> Unit,
    private val onRenderGone: () -> Unit,
    private val notInCopyHtml: () -> String,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        return when (SitePolicy.decide(url)) {
            Decision.OFFLINE_COPY -> false
            Decision.IN_APP -> {
                // Offline, a link to riskmandate.ai opens its bundled copy (or says it is not in the copy).
                val copy = if (switch.offline) OfflineCopy.assetUrlFor(url) else null
                if (copy != null && request.isForMainFrame) {
                    view.loadUrl(copy)
                    true
                } else {
                    copy != null
                }
            }
            // A frame inside a page must not take the visitor away; only a main-frame navigation opens the browser.
            Decision.EXTERNAL -> {
                if (request.isForMainFrame) onExternal(url)
                true
            }
        }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (SitePolicy.blockSubresource(url)) return empty(403, "Blocked")
        SiteAssets.serve(context, url, request.method, request.isForMainFrame, notInCopyHtml)?.let { return it }
        // Offline: nothing is fetched from riskmandate.ai either.
        if (switch.offline) return empty(404, "Offline")
        return null
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame && !switch.offline && SitePolicy.decide(request.url.toString()) == Decision.IN_APP) onMainFrameError()
    }

    /** Never proceed past a certificate error. */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        if (!switch.offline) onMainFrameError()
    }

    /** The renderer died: drop this WebView (returning false would kill the whole app). */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRenderGone()
        return true
    }

    private fun empty(status: Int, reason: String) =
        WebResourceResponse("text/plain", "utf-8", status, reason, mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
}

/** Denies everything a page can ask for: permissions, geolocation, file choosers, new windows. */
class SiteChromeClient : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) = request.deny()

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) =
        callback.invoke(origin, false, false)

    override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
        filePathCallback?.onReceiveValue(null)
        return true
    }

    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false
}

/**
 * Locks the WebView down: JavaScript on (the site's menu and demos are scripts), no JavaScript interface,
 * no file or content access, Safe Browsing on, mixed content never allowed, no third-party cookies, no downloads
 * (no DownloadListener is set), remote debugging left off.
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
    webChromeClient = SiteChromeClient()
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
}

/** Forget what the pages stored (cache, cookies, web storage) and free the WebView. */
fun WebView.disposeSite() {
    stopLoading()
    clearSiteData()
    destroy()
}

/** Cache, history, cookies and web storage: cleared when the screen opens and when it closes. */
fun WebView.clearSiteData() {
    clearCache(true)
    clearHistory()
    CookieManager.getInstance().removeAllCookies(null)
    WebStorage.getInstance().deleteAllData()
}
