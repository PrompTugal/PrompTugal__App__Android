package ai.riskmandate.app.features.xray

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
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
 * The kiosk's WebView client: main-frame pages only on web-summit.sgit.ai ([XrayPolicy.allowPage]), anything else
 * ignored (never handed to a browser: that would leave the kiosk); sub-resources off [XrayPolicy.SUBRESOURCE_HOSTS]
 * refused; load progress reported to the watchdog.
 */
class XrayWebViewClient(
    private val onStarted: () -> Unit,
    private val onFinished: (failedMeanwhile: Boolean) -> Unit,
    private val onMainFrameError: () -> Unit,
    private val onRenderGone: () -> Unit,
) : WebViewClient() {
    private var failedThisLoad = false

    /** True cancels: a page off web-summit.sgit.ai, or a frame to a host the page may not fetch from. */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        return if (request.isForMainFrame) !XrayPolicy.allowPage(url) else XrayPolicy.blockSubresource(url)
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
 * Denies what a page can ask for (camera, microphone, location, files, new windows). JavaScript alert and confirm
 * keep WebView's own dialogs (the page asks `confirm` before some actions), which a WebChromeClient enables.
 */
class XrayChromeClient : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) = request.deny()

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) =
        callback.invoke(origin, false, false)

    override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
        filePathCallback?.onReceiveValue(null)
        return true
    }

    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false
}

/** The WebView profile the kiosk uses, so the site screen's wipe of the default profile never clears the demo key. */
const val XRAY_PROFILE = "xray"

/**
 * Settings for the kiosk. Unlike the site screen, the page's state is **kept**: DOM storage on and first-party
 * cookies accepted and never cleared, so the demo key a staff member unlocks once (Silvio's page asks a new
 * device for it) stays across reloads and app restarts. Where WebView supports profiles (WebView 110+), the
 * kiosk has its own profile; otherwise it shares the default one, and opening the site screen clears it.
 * Otherwise as locked down as the site screen: no file or content access, Safe Browsing on, no mixed content,
 * no third-party cookies, no JavaScript interface, no downloads, no new windows, remote debugging off.
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
    webChromeClient = XrayChromeClient()
    (profileCookies ?: CookieManager.getInstance()).apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@applyXraySettings, false)
    }
    return ownProfile
}
