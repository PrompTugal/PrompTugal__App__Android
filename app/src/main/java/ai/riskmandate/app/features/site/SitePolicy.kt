package ai.riskmandate.app.features.site

import ai.riskmandate.app.Links
import ai.riskmandate.app.R
import androidx.annotation.StringRes

/** Where a URL goes from the site screen. */
enum class Decision {
    /** Loads in the WebView: https on riskmandate.ai only ([Links.isAllowed]). */
    IN_APP,

    /** The app's own offline copy, on the app-controlled asset origin ([OfflineCopy.ASSET_BASE]). */
    OFFLINE_COPY,

    /** Anything else: never in the WebView, handed to [ai.riskmandate.app.Browser.open] (which refuses what is not allowed). */
    EXTERNAL,
}

/** The URL decision. Pure Kotlin, so it runs on the JVM. */
object SitePolicy {
    fun decide(url: String): Decision = when {
        Links.isAllowed(url) -> Decision.IN_APP
        OfflineCopy.isAssetUrl(url) -> Decision.OFFLINE_COPY
        else -> Decision.EXTERNAL
    }

    /**
     * Sub-resources (scripts, images, fonts, frames) of a page: http(s) requests to anything that is neither
     * allowed nor the offline copy are blocked outright. Other schemes (data:, blob:) do not reach the network.
     */
    fun blockSubresource(url: String): Boolean {
        val scheme = url.substringBefore(':', "").lowercase()
        val network = scheme == "http" || scheme == "https" || scheme == "ws" || scheme == "wss"
        return network && decide(url) == Decision.EXTERNAL
    }
}

/** One native tab: a main page of riskmandate.ai, chosen from the site's own menu. */
data class SiteTab(
    val id: String,
    @StringRes val label: Int,
    val url: String,
    /** The bundled copy: `assets/feature_site/<file>`. */
    val file: String,
)

/** The tabs, in the order of the site's own menu (Product, How it works, Open source, Company). */
val SITE_TABS: List<SiteTab> = listOf(
    SiteTab("home", R.string.feature_site_tab_home, "https://riskmandate.ai/", "index.html"),
    SiteTab("abp", R.string.feature_site_tab_abp, "https://riskmandate.ai/abp.html", "abp.html"),
    SiteTab("how_it_works", R.string.feature_site_tab_how, "https://riskmandate.ai/how-it-works.html", "how-it-works.html"),
    SiteTab("pricing", R.string.feature_site_tab_pricing, "https://riskmandate.ai/pricing.html", "pricing.html"),
    SiteTab("secure_harness", R.string.feature_site_tab_harness, "https://riskmandate.ai/secure-harness.html", "secure-harness.html"),
    SiteTab("about", R.string.feature_site_tab_about, "https://riskmandate.ai/about.html", "about.html"),
)

/** The bundled snapshot. The date is the day the pages were read from riskmandate.ai. */
object OfflineCopy {
    const val SNAPSHOT_DATE_ISO = "2026-11-09"
    const val ASSET_HOST = "appassets.androidplatform.net"
    const val ASSET_BASE = "https://$ASSET_HOST/assets/feature_site/"
    const val ASSET_DIR = "feature_site"

    fun isAssetUrl(url: String): Boolean {
        val u = url.trim()
        if (u.any { it == '@' || it == '\\' || it.isWhitespace() || it.isISOControl() }) return false
        return u.regionMatches(0, ASSET_BASE, 0, ASSET_BASE.length, ignoreCase = false)
    }

    /** The path of an allowed riskmandate.ai URL, normalised (`/` for the home page), or null. */
    private fun pathOf(url: String): String? {
        if (!Links.isAllowed(url)) return null
        val afterScheme = url.trim().substring("https://".length)
        val pathStart = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val rest = if (pathStart < 0) "" else afterScheme.substring(pathStart)
        val path = rest.takeWhile { it != '?' && it != '#' }.ifEmpty { "/" }
        return if (path == "/index.html") "/" else path
    }

    /** The bundled page for [url] (a riskmandate.ai URL or an asset URL), or null when it is not one of the tabs. */
    fun pageFor(url: String): SiteTab? {
        val rel = if (isAssetUrl(url)) assetPath(url) else pathOf(url)?.removePrefix("/")?.ifEmpty { "index.html" }
        return SITE_TABS.firstOrNull { it.file == rel }
    }

    /** The offline-copy URL for an allowed riskmandate.ai URL (the page may not exist in the copy), or null. */
    fun assetUrlFor(url: String): String? {
        val rel = pathOf(url)?.removePrefix("/")?.ifEmpty { "index.html" } ?: return null
        return ASSET_BASE + rel
    }

    /** The offline-copy URL of a tab. */
    fun assetUrl(tab: SiteTab): String = ASSET_BASE + tab.file

    /** The path below [ASSET_BASE] of an asset URL: no query, no fragment; `index.html` for the folder itself. */
    fun assetPath(url: String): String {
        val rest = url.trim().substring(ASSET_BASE.length).takeWhile { it != '?' && it != '#' }
        return if (rest.isEmpty() || rest.endsWith("/")) rest + "index.html" else rest
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
