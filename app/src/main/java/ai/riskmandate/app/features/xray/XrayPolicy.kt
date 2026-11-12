package ai.riskmandate.app.features.xray

/**
 * Where the X-Ray screen may go. Pure Kotlin, so it runs on the JVM.
 *
 * Build 2 (Angelo, 11 Nov 2026: "run it as an embedded app in a browser ... keep the features"): the page behaves
 * as it does in Chrome. Sub-resources from any host over TLS (https, wss); plain http and ws refused (WebView's
 * mixed-content rule refuses them too). Top-level pages stay on https://web-summit.sgit.ai in this view; a link or
 * window.open elsewhere, tapped by a person, opens outside (Custom Tab or browser); one the page makes by itself is
 * ignored.
 */
object XrayPolicy {
    const val START_URL = "https://web-summit.sgit.ai/people/silvio/app/index.html#xray"
    const val HOME_HOST = "web-summit.sgit.ai"

    /** What to do with a top-level navigation. */
    enum class Nav { LOAD, OPEN_OUTSIDE, IGNORE }

    /** A main-frame page that may load in this view: https on [HOME_HOST], port 443 at most. */
    fun allowPage(url: String): Boolean = httpsHost(url) == HOME_HOST

    /**
     * A top-level navigation (a link, target=_blank or window.open, which open in this same view): on the site,
     * load it; another https page tapped by a person, open it outside; anything else (another scheme, a redirect
     * the page made without a tap), ignore.
     */
    fun navigation(url: String, hasGesture: Boolean): Nav = when {
        allowPage(url) -> Nav.LOAD
        hasGesture && httpsHost(url) != null -> Nav.OPEN_OUTSIDE
        else -> Nav.IGNORE
    }

    /** How long after a person touched the page a download may still open outside (check 2, finding 1). */
    const val DOWNLOAD_TAP_WINDOW_MS = 2_000L

    /**
     * A download (WebView's download listener has no gesture flag): it opens outside only as a tapped link would
     * ([navigation] with a tap), and only when a person touched the page in the last [DOWNLOAD_TAP_WINDOW_MS].
     * One a script or a frame starts by itself is ignored. [lastTouchAt] is null when the page was never touched.
     */
    fun downloadOpensOutside(url: String, lastTouchAt: Long?, now: Long): Boolean {
        val tapped = lastTouchAt != null && now - lastTouchAt in 0..DOWNLOAD_TAP_WINDOW_MS
        return navigation(url, hasGesture = tapped) == Nav.OPEN_OUTSIDE
    }

    /** True when a sub-resource must be refused: plain http or ws, or a TLS URL with a suspicious authority. */
    fun blockSubresource(url: String): Boolean {
        val scheme = url.trim().substringBefore(':', "").lowercase()
        return when (scheme) {
            "http", "ws" -> true
            "https" -> httpsHost(url) == null
            "wss" -> httpsHost("https" + url.trim().substring(3)) == null
            else -> false // data:, blob: and about: never reach the network
        }
    }

    /** Camera, microphone and the like: only ever for the site itself (and only if the app holds the permission). */
    fun mayAskForMedia(origin: String): Boolean = httpsHost(origin) == HOME_HOST

    /**
     * The lower-case host of an https URL, or null when the URL is not plain https or is suspicious: a backslash,
     * whitespace or control character anywhere, an '@' or a '%' in the authority, a port other than 443, an empty host.
     * The same refusals as [ai.riskmandate.app.Links.isAllowed].
     */
    internal fun httpsHost(url: String): String? {
        val u = url.trim()
        if (u.any { it == '\\' || it.isWhitespace() || it.isISOControl() }) return null
        val prefix = "https://"
        if (u.length < prefix.length || !u.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) return null
        val authority = u.substring(prefix.length).takeWhile { it != '/' && it != '?' && it != '#' }
        // '@' is refused in the authority (userinfo hides the real host); in the path it is fine (three@0.147.0).
        if (authority.isEmpty() || authority.contains('%') || authority.contains('@')) return null
        val host = authority.substringBefore(':')
        val port = authority.substringAfter(':', "")
        if (authority.contains(':') && port != "443") return null
        if (host.isEmpty() || !host.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '-' }) return null
        return host.lowercase()
    }
}

/** What the screen should do now. */
enum class XrayAction { NONE, RELOAD }

/**
 * Keeps the kiosk alive without a person: reloads after a load error (10 s, then 20, 40, at most 60 s apart),
 * after a stall (a page that started and has not finished in [stallMs]), and at once when the network comes back
 * while the page had failed. Pure state, the clock is passed in, so it is tested on the JVM.
 */
class XrayWatchdog(
    private val stallMs: Long = 45_000,
    private val firstRetryMs: Long = 10_000,
    private val maxRetryMs: Long = 60_000,
) {
    enum class State { LOADING, SHOWING, FAILED }

    var state: State = State.LOADING
        private set
    private var since = 0L
    private var failures = 0

    /** True while the calm offline cover should show over the WebView. */
    val coverShown: Boolean get() = state == State.FAILED

    fun onLoadStarted(now: Long) {
        // A failed page keeps its cover while the retry loads, so the visitor never sees the WebView's error page.
        if (state != State.FAILED) state = State.LOADING
        since = now
    }

    fun onLoadFinished(now: Long, failedMeanwhile: Boolean = false) {
        if (failedMeanwhile) return
        state = State.SHOWING
        failures = 0
        since = now
    }

    fun onMainFrameError(now: Long) {
        if (state != State.FAILED) failures = 0
        state = State.FAILED
        failures++
        since = now
    }

    /**
     * The network came back: reload at once if the page had failed. Android also reports the current network as
     * soon as the screen listens, so a page that is loading or showing is left alone (the stall check covers it).
     */
    fun onNetworkAvailable(now: Long): XrayAction {
        if (state != State.FAILED) return XrayAction.NONE
        since = now
        return XrayAction.RELOAD
    }

    /** Called every few seconds. */
    fun tick(now: Long): XrayAction = when (state) {
        State.SHOWING -> XrayAction.NONE
        State.LOADING -> if (now - since >= stallMs) { onMainFrameError(now); XrayAction.RELOAD } else XrayAction.NONE
        State.FAILED -> if (now - since >= retryDelayMs()) { since = now; XrayAction.RELOAD } else XrayAction.NONE
    }

    /** 10 s after the first failure, doubling, at most [maxRetryMs]. */
    fun retryDelayMs(): Long {
        val shift = (failures - 1).coerceIn(0, 10)
        return (firstRetryMs shl shift).coerceAtMost(maxRetryMs)
    }
}

/**
 * Full screen or in the app's window. Pure state, tested on the JVM.
 *
 * [fullScreen]: the page fills the display, system bars hidden. [pageFullScreen]: the page's own full-screen button
 * is in force (WebView hands over a view to show). [byPage]: full screen was entered by the page from the window,
 * so the page leaving it goes back to the window.
 */
data class XrayView(val fullScreen: Boolean = true, val pageFullScreen: Boolean = false, val byPage: Boolean = false) {
    /** The toggle (or Back while full screen): in and out. Leaving also ends the page's own full screen. */
    fun toggled(): XrayView = if (fullScreen) XrayView(fullScreen = false) else copy(fullScreen = true)

    /**
     * Back while full screen. During the page's own full screen it ends only that, as Chrome's Back does (check 2,
     * finding 2): the app's full screen stays unless the page had brought us there from the window. Otherwise it
     * leaves full screen, as the toggle does.
     */
    fun back(): XrayView = when {
        pageFullScreen -> pageExited()
        fullScreen -> toggled()
        else -> this // in the window, Back belongs to the app's frame
    }

    /** True when leaving now must tell WebView the page's full screen is over. */
    val leavingEndsPageFullScreen: Boolean get() = fullScreen && pageFullScreen

    /** The page asked for full screen (its own button). */
    fun pageEntered(): XrayView = XrayView(fullScreen = true, pageFullScreen = true, byPage = byPage || !fullScreen)

    /** The page left full screen: back to the window if the page had brought us to full screen. */
    fun pageExited(): XrayView = if (byPage) XrayView(fullScreen = false) else copy(pageFullScreen = false)
}
