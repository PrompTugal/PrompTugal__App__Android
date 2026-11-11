package ai.riskmandate.app.features.xray

import ai.riskmandate.app.Pt
import ai.riskmandate.app.R
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
    const val STAFF_CORNER = "xray_staff_corner"
    const val EXIT_DIALOG = "xray_exit_dialog"
    const val EXIT_LEAVE = "xray_exit_leave"
    const val EXIT_STAY = "xray_exit_stay"
    const val EXIT_RELOAD = "xray_exit_reload"
}


/**
 * The X-Ray page as a stand kiosk: a window over the whole app (so no app chrome), system bars hidden and coming
 * back hidden after a swipe, the screen kept on, Back doing nothing. Staff leave by holding the bottom-start
 * corner for [exitHoldMs] and confirming. The page reloads by itself after an error, a stall or a lost network.
 */
@Composable
fun XrayScreen(
    onExit: () -> Unit,
    startUrl: String = XrayPolicy.START_URL,
    exitHoldMs: Long = 3_000,
    clock: () -> Long = SystemClock::elapsedRealtime,
    /** False in screenshot tests: Robolectric draws no web content. */
    showWebView: Boolean = true,
    /** For tests: start with the offline cover showing. */
    startFailed: Boolean = false,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        KioskWindow()
        XrayKiosk(onExit, startUrl, exitHoldMs, clock, showWebView, startFailed)
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

/** The kiosk's content: the WebView, the cover while it recovers, the staff corner and the exit confirmation. */
@Composable
internal fun XrayKiosk(
    onExit: () -> Unit,
    startUrl: String,
    exitHoldMs: Long,
    clock: () -> Long,
    showWebView: Boolean,
    startFailed: Boolean,
) {
    val context = LocalContext.current
    val watchdog = remember { XrayWatchdog().also { if (startFailed) it.onMainFrameError(clock()) } }
    var cover by remember { mutableStateOf(watchdog.coverShown) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webViewKey by remember { mutableIntStateOf(0) }
    var askExit by remember { mutableStateOf(false) }

    fun sync() { cover = watchdog.coverShown }
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

    // The theme's paper behind the page and the cover (colours come from Theme.kt only).
    val paper = Pt.colors.paper
    Box(Modifier.fillMaxSize().background(paper)) {
        if (showWebView) {
            key(webViewKey) {
                AndroidView(
                    factory = { c ->
                        WebView(c).apply {
                            applyXraySettings()
                            setBackgroundColor(paper.toArgb())
                            contentDescription = c.getString(R.string.feature_xray_webview_description)
                            webViewClient = XrayWebViewClient(
                                onStarted = { watchdog.onLoadStarted(clock()); sync() },
                                onFinished = { failed -> watchdog.onLoadFinished(clock(), failed); sync() },
                                onMainFrameError = { watchdog.onMainFrameError(clock()); sync() },
                                onRenderGone = {
                                    // A new WebView (and renderer) on the next composition; the old one is released below.
                                    webView = null
                                    watchdog.onMainFrameError(clock())
                                    sync()
                                    webViewKey++
                                },
                            )
                            webView = this
                            watchdog.onLoadStarted(clock())
                            loadUrl(startUrl)
                        }
                    },
                    onRelease = { it.stopLoading(); it.destroy() },
                    modifier = Modifier.fillMaxSize().testTag(XrayTags.WEBVIEW),
                )
            }
        }
        if (cover) Cover()
        StaffCorner(exitHoldMs, Modifier.align(Alignment.BottomStart)) { askExit = true }
    }

    if (askExit) {
        AlertDialog(
            onDismissRequest = { askExit = false },
            modifier = Modifier.testTag(XrayTags.EXIT_DIALOG),
            title = { Text(stringResource(R.string.feature_xray_exit_title)) },
            text = { Text(stringResource(R.string.feature_xray_exit_body)) },
            confirmButton = {
                TextButton(onClick = { askExit = false; onExit() }, modifier = Modifier.testTag(XrayTags.EXIT_LEAVE)) {
                    Text(stringResource(R.string.feature_xray_exit_leave))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { askExit = false; reload() }, modifier = Modifier.testTag(XrayTags.EXIT_RELOAD)) {
                        Text(stringResource(R.string.feature_xray_exit_reload))
                    }
                    TextButton(onClick = { askExit = false }, modifier = Modifier.testTag(XrayTags.EXIT_STAY)) {
                        Text(stringResource(R.string.feature_xray_exit_stay))
                    }
                }
            },
        )
    }
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

/** An invisible 56 dp corner: held for [holdMs], it asks to leave. A tap does nothing. */
@Composable
private fun StaffCorner(holdMs: Long, modifier: Modifier = Modifier, onHeld: () -> Unit) {
    val description = stringResource(R.string.feature_xray_staff_corner)
    val held by rememberUpdatedState(onHeld)
    Box(
        modifier
            .size(56.dp)
            .testTag(XrayTags.STAFF_CORNER)
            .semantics { contentDescription = description }
            .pointerInput(holdMs) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = withTimeoutOrNull(holdMs) { waitForUpOrCancellation() }
                    if (up == null) held()
                }
            },
    )
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
