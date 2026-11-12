package ai.riskmandate.app.features.xray

import ai.riskmandate.app.Browser
import ai.riskmandate.app.Pt
import ai.riskmandate.app.R
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

object XrayTags {
    const val WEBVIEW = "xray_webview"
    const val COVER = "xray_cover"
    const val TOOLBAR = "xray_toolbar"
    const val RELOAD = "xray_reload"
    const val FULLSCREEN_ENTER = "xray_fullscreen_enter"
    const val FULLSCREEN_EXIT = "xray_fullscreen_exit"
    const val FULLSCREEN = "xray_fullscreen"
}

/** How long the Exit full screen button stays before it fades; a tap anywhere brings it back. */
const val XRAY_TOGGLE_SHOWN_MS = 4_000L

/**
 * Silvio's X-Ray page in an embedded browser (Android System WebView, which is Chromium), in two ways:
 * - **full screen** (the start): a window over the whole app, system bars hidden (immersive sticky), the screen kept
 *   on; a small Exit full screen tab on the start edge, half way down, that fades after [XRAY_TOGGLE_SHOWN_MS] and returns on a
 *   tap; Back also leaves full screen. The page's own full-screen button works too.
 * - **in the app**: inside the app's frame (its top bar or sidebar, its Back to the menu), with a small bar above
 *   the page: Reload and Full screen.
 * The same WebView moves between the two, so the page keeps its place. It reloads by itself after an error, a stall
 * or a lost network.
 */
@Composable
fun XrayScreen(
    startUrl: String = XrayPolicy.START_URL,
    clock: () -> Long = SystemClock::elapsedRealtime,
    /** False in screenshot tests: Robolectric draws no web content. */
    showWebView: Boolean = true,
    /** For tests: start with the offline cover showing. */
    startFailed: Boolean = false,
    startFullScreen: Boolean = true,
) {
    val context = LocalContext.current
    val watchdog = remember { XrayWatchdog().also { if (startFailed) it.onMainFrameError(clock()) } }
    var cover by remember { mutableStateOf(watchdog.coverShown) }
    var view by remember { mutableStateOf(XrayView(fullScreen = startFullScreen)) }
    var pageView by remember { mutableStateOf<View?>(null) }
    var pageViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var webViewKey by remember { mutableIntStateOf(0) }

    fun sync() { cover = watchdog.coverShown }
    fun endPageFullScreen() {
        val cb = pageViewCallback
        pageView = null
        pageViewCallback = null
        cb?.onCustomViewHidden()
    }
    fun toggle() {
        val ending = view.leavingEndsPageFullScreen
        view = view.toggled()
        if (ending) endPageFullScreen()
    }

    // File inputs on the page: the system picker, as in Chrome.
    var pendingFiles by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingFiles?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        pendingFiles = null
    }

    val webView: WebView? = remember(webViewKey) {
        if (!showWebView) return@remember null
        WebView(context).apply {
            applyXraySettings()
            setBackgroundColor(0) // transparent: the theme's paper behind it shows while it loads
            keepScreenOn = true // in the app's window and in full screen
            contentDescription = context.getString(R.string.feature_xray_webview_description)
            webChromeClient = XrayChromeClient(
                onPageFullScreen = { v, cb ->
                    pageView = v
                    pageViewCallback = cb
                    view = view.pageEntered()
                },
                onPageFullScreenEnd = {
                    pageView = null
                    pageViewCallback = null
                    view = view.pageExited()
                },
                onFiles = { cb, params ->
                    pendingFiles?.onReceiveValue(null)
                    pendingFiles = cb
                    try {
                        filePicker.launch(params.createIntent())
                    } catch (e: ActivityNotFoundException) {
                        pendingFiles = null
                        cb.onReceiveValue(null)
                    }
                    true
                },
            )
            webViewClient = XrayWebViewClient(
                onStarted = { watchdog.onLoadStarted(clock()); sync() },
                onFinished = { failed -> watchdog.onLoadFinished(clock(), failed); sync() },
                onMainFrameError = { watchdog.onMainFrameError(clock()); sync() },
                onRenderGone = {
                    // A new WebView (and renderer); the old one is released by the effect below.
                    endPageFullScreen()
                    watchdog.onMainFrameError(clock())
                    sync()
                    webViewKey++
                },
                onOutside = { url -> openOutside(context, url) },
            )
            // A download link to https opens outside (the browser downloads it); blob: exports need a JS bridge, which this screen never has.
            setDownloadListener { url, _, _, _, _ -> if (XrayPolicy.navigation(url, hasGesture = true) == XrayPolicy.Nav.OPEN_OUTSIDE) openOutside(context, url) }
            watchdog.onLoadStarted(clock())
            loadUrl(startUrl)
        }
    }
    DisposableEffect(webView) {
        onDispose { webView?.apply { stopLoading(); destroy() } }
    }
    fun reload() {
        webView?.loadUrl(startUrl)
        sync()
    }
    val currentReload by rememberUpdatedState(::reload)

    // Every 5 s: a stalled load or a failed page whose retry time has come is reloaded.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            if (watchdog.tick(clock()) == XrayAction.RELOAD) currentReload()
            sync()
        }
    }
    // The network came back: reload at once if the page had failed.
    DisposableEffect(Unit) {
        val unregister = watchNetwork(context) { if (watchdog.onNetworkAvailable(clock()) == XrayAction.RELOAD) currentReload() }
        onDispose(unregister)
    }

    if (view.fullScreen) {
        Dialog(
            onDismissRequest = { toggle() }, // Back leaves full screen
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            KioskWindow()
            FullScreenContent(onExit = { toggle() }) {
                Page(webView, cover)
                pageView?.let { v -> AndroidView(factory = { v.also { (it.parent as? ViewGroup)?.removeView(it) } }, modifier = Modifier.fillMaxSize()) }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Toolbar(onReload = { reload() }, onFullScreen = { toggle() })
            Box(Modifier.weight(1f).fillMaxWidth()) { Page(webView, cover) }
        }
    }
}

/** Full screen for the dialog's window: whole display, no dim, bars hidden (immersive sticky), screen kept on. */
@Composable
private fun KioskWindow() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(window, lifecycle) {
        fun hideBars() {
            val w = window ?: return
            WindowCompat.getInsetsController(w, w.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes = attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        hideBars()
        // Coming back to the app (after Home or Recents): hidden again.
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) hideBars() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** The WebView (moved here from wherever it was) with the calm cover over it while the page recovers. */
@Composable
private fun Page(webView: WebView?, cover: Boolean) {
    Box(Modifier.fillMaxSize().background(Pt.colors.paper)) {
        if (webView != null) {
            AndroidView(
                // The same WebView moves between the window and full screen: take it from its old parent first.
                factory = { webView.also { (it.parent as? ViewGroup)?.removeView(it) } },
                // Destroyed by XrayScreen, not here: leaving one mode is not leaving the page.
                onRelease = {},
                modifier = Modifier.fillMaxSize().testTag(XrayTags.WEBVIEW),
            )
        }
        if (cover) Cover()
    }
}

/** In the app's window: a slim bar above the page, never over it. */
@Composable
private fun Toolbar(onReload: () -> Unit, onFullScreen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag(XrayTags.TOOLBAR),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onReload, modifier = Modifier.testTag(XrayTags.RELOAD)) { Text(stringResource(R.string.feature_xray_reload)) }
        TextButton(onClick = onFullScreen, modifier = Modifier.testTag(XrayTags.FULLSCREEN_ENTER)) { Text(stringResource(R.string.feature_xray_fullscreen_enter)) }
    }
}

/**
 * Full screen: [content] fills the display; a small Exit full screen tab on the start edge shows for
 * [XRAY_TOGGLE_SHOWN_MS], then fades; any tap brings it back (the tap still reaches the page).
 */
@Composable
internal fun FullScreenContent(onExit: () -> Unit, content: @Composable () -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(true) }
    LaunchedEffect(taps) {
        shown = true
        delay(XRAY_TOGGLE_SHOWN_MS)
        shown = false
    }
    Box(
        Modifier
            .fillMaxSize()
            .testTag(XrayTags.FULLSCREEN)
            .pointerInput(Unit) {
                // Watch, never consume: the page gets every touch.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    taps++
                }
            },
    ) {
        content()
        val exitLabel = stringResource(R.string.feature_xray_fullscreen_exit)
        AnimatedVisibility(visible = shown, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterStart)) {
            // A slim tab on the start edge, half way down: on the page at 412 and 1280 px wide that is margin, while
            // the corners hold the page's own buttons (its menu, full screen, bottom bar and voice button).
            Surface(
                onClick = onExit,
                shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                color = Pt.colors.ink.copy(alpha = 0.72f),
                contentColor = Pt.colors.paper,
                modifier = Modifier.size(width = 28.dp, height = 64.dp).testTag(XrayTags.FULLSCREEN_EXIT).semantics { contentDescription = exitLabel },
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

/** Another site, tapped on the page: a Custom Tab is not used for it (Browser.open is riskmandate.ai only), the system browser is. */
private fun openOutside(context: Context, url: String) {
    val message = Browser.message(Browser.openExternal(context, url)) ?: return
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** A calm cover while the page is away: no error text from WebView, just that it is coming back. */
@Composable
private fun Cover() {
    Box(Modifier.fillMaxSize().background(Pt.colors.paper).testTag(XrayTags.COVER), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 520.dp).padding(32.dp)) {
            Text(
                stringResource(R.string.feature_xray_cover_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Pt.colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.feature_xray_cover_body),
                style = MaterialTheme.typography.bodyLarge,
                color = Pt.colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Calls [onAvailable] on the main thread whenever the default network becomes available. Returns the unregister. */
private fun watchNetwork(context: Context, onAvailable: () -> Unit): () -> Unit {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return {}
    val main = Handler(Looper.getMainLooper())
    var active = true
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.post { if (active) onAvailable() }
        }
    }
    try {
        cm.registerDefaultNetworkCallback(callback)
    } catch (e: RuntimeException) {
        return {}
    }
    return {
        active = false
        try { cm.unregisterNetworkCallback(callback) } catch (e: RuntimeException) { /* already gone */ }
    }
}
