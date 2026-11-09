package ai.riskmandate.app.features.site

import ai.riskmandate.app.Links
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import ai.riskmandate.app.R

/** Where a URL goes from the site screen. */
enum class Decision {
    /** Loads in the WebView: https on riskmandate.ai only ([Links.isAllowed]). */
    IN_APP,

    /** Anything else: never in the WebView, handed to [ai.riskmandate.app.Browser.open] (which refuses what is not allowed). */
    EXTERNAL,
}

/** The URL decision. Pure Kotlin, so it runs on the JVM. */
object SitePolicy {
    fun decide(url: String): Decision = if (Links.isAllowed(url)) Decision.IN_APP else Decision.EXTERNAL

    /**
     * Sub-resources (scripts, images, fonts, frames) of a page: http(s) requests to anything that fails the
     * allow-list are blocked outright. Other schemes (data:, blob:) do not reach the network.
     */
    fun blockSubresource(url: String): Boolean {
        val scheme = url.substringBefore(':', "").lowercase()
        return (scheme == "http" || scheme == "https" || scheme == "ws" || scheme == "wss") && !Links.isAllowed(url)
    }
}

/** One native tab: a main page of riskmandate.ai, chosen from the site's own menu. */
data class SiteTab(
    val id: String,
    @StringRes val label: Int,
    val url: String,
    /** The bundled offline copy of the page: `res/raw/site_<id>.html`. */
    @RawRes val raw: Int,
)

/** The tabs, in the order of the site's own menu (Product, How it works, Open source, Company). */
val SITE_TABS: List<SiteTab> = listOf(
    SiteTab("home", R.string.site_tab_home, "https://riskmandate.ai/", R.raw.site_home),
    SiteTab("abp", R.string.site_tab_abp, "https://riskmandate.ai/abp.html", R.raw.site_abp),
    SiteTab("how_it_works", R.string.site_tab_how, "https://riskmandate.ai/how-it-works.html", R.raw.site_how_it_works),
    SiteTab("pricing", R.string.site_tab_pricing, "https://riskmandate.ai/pricing.html", R.raw.site_pricing),
    SiteTab("secure_harness", R.string.site_tab_harness, "https://riskmandate.ai/secure-harness.html", R.raw.site_secure_harness),
    SiteTab("about", R.string.site_tab_about, "https://riskmandate.ai/about.html", R.raw.site_about),
)

/** The bundled snapshot. The date is the day the pages were read from riskmandate.ai. */
object OfflineCopy {
    const val SNAPSHOT_DATE_ISO = "2026-11-09"

    /** The bundled page for [url], or null when it is not one of the tabs (or not an allowed URL). */
    fun pageFor(url: String): SiteTab? {
        if (!Links.isAllowed(url)) return null
        val afterScheme = url.trim().substring("https://".length)
        val pathStart = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val rest = if (pathStart < 0) "" else afterScheme.substring(pathStart)
        val path = rest.takeWhile { it != '?' && it != '#' }.ifEmpty { "/" }
        val normalised = if (path == "/index.html") "/" else path
        return SITE_TABS.firstOrNull { tab ->
            val tabPath = "/" + tab.url.removePrefix("https://riskmandate.ai/")
            tabPath == normalised
        }
    }
}

/**
 * Online or offline copy. Starts from the system's connectivity hint and flips to the offline copy when the
 * main page fails to load; the visitor can ask to try online again. Pure state, no Android types.
 */
class OfflineSwitch(initialOffline: Boolean) {
    private val state = androidx.compose.runtime.mutableStateOf(initialOffline)

    /** Safe to read from the WebView's network thread. */
    val offline: Boolean get() = state.value

    /** The main page failed to load while online: show the offline copy. Returns true when the mode changed. */
    fun onMainFrameError(): Boolean {
        if (state.value) return false
        state.value = true
        return true
    }

    /** The visitor asked to try online again; [systemOffline] is the current connectivity hint. */
    fun tryOnline(systemOffline: Boolean): Boolean {
        if (systemOffline) return false
        val changed = state.value
        state.value = false
        return changed
    }
}
