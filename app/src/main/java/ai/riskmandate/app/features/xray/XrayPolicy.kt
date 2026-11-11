package ai.riskmandate.app.features.xray

/**
 * Where the X-Ray kiosk may go. Pure Kotlin, so it runs on the JVM.
 *
 * Top-level pages: https://web-summit.sgit.ai only (Silvio's command centre lives there). Every other main-frame
 * navigation is ignored: a kiosk opens nothing else, not even the system browser (that would leave the kiosk).
 * Sub-resources (scripts, fonts, fetches): the hosts the page was seen to use on 11 Nov 2026 (headless Chromium:
 * web-summit.sgit.ai, cdn.jsdelivr.net for three.js, Google Fonts, promptugal.news for the company list,
 * dev.send.sgraph.ai for the vault) plus openrouter.ai, which the page calls once a demo key is set. Everything
 * else over the network is refused.
 */
object XrayPolicy {
    const val START_URL = "https://web-summit.sgit.ai/people/silvio/app/index.html#xray"
    const val HOME_HOST = "web-summit.sgit.ai"

    /** Hosts a page may fetch from (https only). Adding one is a security review, as for the site feature's list. */
    val SUBRESOURCE_HOSTS: Set<String> = setOf(
        HOME_HOST,
        "cdn.jsdelivr.net",
        "fonts.googleapis.com",
        "fonts.gstatic.com",
        "promptugal.news",
        "dev.send.sgraph.ai",
        "openrouter.ai",
    )

    /** A main-frame page that may load: https on [HOME_HOST], port 443 at most. */
    fun allowPage(url: String): Boolean = httpsHost(url) == HOME_HOST

    /** True when a sub-resource must be refused: network schemes to a host off [SUBRESOURCE_HOSTS], or plain http. */
    fun blockSubresource(url: String): Boolean {
        val scheme = url.trim().substringBefore(':', "").lowercase()
        val network = scheme == "http" || scheme == "https" || scheme == "ws" || scheme == "wss"
        if (!network) return false // data:, blob: and about: never reach the network
        val host = if (scheme == "wss") httpsHost("https" + url.trim().substring(3)) else httpsHost(url)
        return host == null || host !in SUBRESOURCE_HOSTS
    }

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
