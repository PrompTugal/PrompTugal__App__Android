package ai.riskmandate.app.features.xray

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.View
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
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

/**
 * The X-Ray screen's WebView client. Top-level pages: web-summit.sgit.ai loads here; another https page a person
 * tapped (a link, target=_blank, window.open, which all open in this same view) goes to [onOutside]; anything else
 * is ignored ([XrayPolicy.navigation]). Sub-resources from any host over TLS; plain http and ws refused. Load
 * progress goes to the watchdog.
 */
class XrayWebViewClient(
    private val onStarted: () -> Unit,
    private val onFinished: (failedMeanwhile: Boolean) -> Unit,
    private val onMainFrameError: () -> Unit,
    private val onRenderGone: () -> Unit,
    private val onOutside: (url: String) -> Unit = {},
) : WebViewClient() {
    private var failedThisLoad = false

    /** True cancels: a page off web-summit.sgit.ai (opened outside if a person tapped it), or a frame over plain http. */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (!request.isForMainFrame) return XrayPolicy.blockSubresource(url)
        return when (XrayPolicy.navigation(url, request.hasGesture())) {
            XrayPolicy.Nav.LOAD -> false
            XrayPolicy.Nav.OPEN_OUTSIDE -> { onOutside(url); true }
            XrayPolicy.Nav.IGNORE -> true
        }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (request.isForMainFrame && !XrayPolicy.allowPage(url)) return refused()
        if (XrayPolicy.blockSubresource(url)) return refused()
        return null
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        failedThisLoad = false
        onStarted()
    }

    override fun onPageFinished(view: WebView, url: String?) = onFinished(failedThisLoad)

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) fail()
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        // A 5xx on the page itself (not on one of its fetches: the vault's 507s are the page's business).
        if (request.isForMainFrame && errorResponse.statusCode >= 500) fail()
    }

    /** Never proceed past a certificate error. */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        fail()
    }

    /** The renderer died: the screen builds a new WebView (returning false would kill the whole app). */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRenderGone()
        return true
    }

    private fun fail() {
        failedThisLoad = true
        onMainFrameError()
    }

    private fun refused() =
        WebResourceResponse("text/plain", "utf-8", 403, "Blocked", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
}

/**
 * What the page can ask for. The page's own full-screen button ([onPageFullScreen], [onPageFullScreenEnd]) and file
 * inputs ([onFiles], the system picker) work as in Chrome; JavaScript alert, confirm and prompt keep WebView's own
 * dialogs. Camera, microphone and location stay refused: the app holds none of those permissions, and the page never
 * asks for camera or microphone (its voice input is Web Speech, which WebView does not have).
 */
class XrayChromeClient(
    private val onPageFullScreen: (view: View, callback: CustomViewCallback) -> Unit = { _, cb -> cb.onCustomViewHidden() },
    private val onPageFullScreenEnd: () -> Unit = {},
    private val onFiles: (callback: ValueCallback<Array<Uri>>, params: FileChooserParams) -> Boolean = { _, _ -> false },
) : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) = request.deny()

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) =
        callback.invoke(origin, false, false)

    override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
        if (filePathCallback == null || fileChooserParams == null) return false
        return onFiles(filePathCallback, fileChooserParams)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view == null || callback == null) return
        onPageFullScreen(view, callback)
    }

    override fun onHideCustomView() = onPageFullScreenEnd()

    /** No second window: with multiple windows off, target=_blank and window.open load in this view (then the client decides). */
    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false
}

/** The WebView profile the kiosk uses, so the site screen's wipe of the default profile never clears the demo key. */
const val XRAY_PROFILE = "xray"

/**
 * Settings for the kiosk. Unlike the site screen, the page's state is **kept**: DOM storage on and first-party
 * cookies accepted and never cleared, so the demo key a staff member unlocks once (Silvio's page asks a new
 * device for it) stays across reloads and app restarts. Where WebView supports profiles (WebView 110+), the
 * kiosk has its own profile; otherwise it shares the default one, and opening the site screen clears it.
 * Otherwise as the site screen: no file:// or content:// access from the page, Safe Browsing on, no mixed content,
 * no third-party cookies, no JavaScript interface, no second window, remote debugging off. The caller sets the
 * chrome client ([XrayChromeClient]) and the download listener.
 *
 * Returns true when the kiosk has its own profile.
 */
@SuppressLint("SetJavaScriptEnabled")
fun WebView.applyXraySettings(): Boolean {
    // Must come before anything is loaded in this WebView.
    var profileCookies: CookieManager? = null
    try {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val profile = ProfileStore.getInstance().getOrCreateProfile(XRAY_PROFILE)
            WebViewCompat.setProfile(this, XRAY_PROFILE)
            profileCookies = profile.cookieManager
        }
    } catch (e: RuntimeException) {
        profileCookies = null // no own profile: the default one
    }
    val ownProfile = profileCookies != null
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
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
    (profileCookies ?: CookieManager.getInstance()).apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@applyXraySettings, false)
    }
    return ownProfile
}
