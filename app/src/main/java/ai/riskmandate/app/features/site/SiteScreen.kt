package ai.riskmandate.app.features.site

import ai.riskmandate.app.Browser
import ai.riskmandate.app.R
import android.content.Context
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

object SiteTags {
    const val BACK = "site_back"
    const val TABS = "site_tabs"
    const val OFFLINE_BANNER = "site_offline_banner"
    const val TRY_ONLINE = "site_try_online"
    const val MESSAGE = "site_message"
    const val WEBVIEW = "site_webview"
    const val PRIVACY = "site_privacy"
    fun tab(id: String) = "site_tab_$id"
}

/** Plain fallback chrome (Material 3 only) until design.android's components land in `ui/`. */
@Composable
fun SiteScreen(
    switch: OfflineSwitch,
    onBack: () -> Unit,
    isSystemOffline: (Context) -> Boolean = Browser::isOffline,
    openExternal: (Context, String) -> Int? = { c, url -> Browser.message(Browser.open(c, url)) },
) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }
    var loadKey by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<Int?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webViewKey by remember { mutableIntStateOf(0) }

    BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else onBack() }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp).testTag(SiteTags.BACK)
                    .semantics { contentDescription = context.getString(R.string.feature_site_back_description) },
            ) { Text(stringResource(R.string.feature_site_back)) }
            Text(
                stringResource(R.string.feature_site_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 8.dp).semantics { heading() },
            )
            ScrollableTabRow(
                selectedTabIndex = tabIndex,
                edgePadding = 0.dp,
                modifier = Modifier.weight(1f).testTag(SiteTags.TABS),
            ) {
                SITE_TABS.forEachIndexed { i, tab ->
                    Tab(
                        selected = i == tabIndex,
                        onClick = { tabIndex = i; loadKey++ },
                        modifier = Modifier.testTag(SiteTags.tab(tab.id)),
                        text = { Text(stringResource(tab.label)) },
                    )
                }
            }
        }

        if (switch.offline) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth().testTag(SiteTags.OFFLINE_BANNER)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(
                        stringResource(R.string.feature_site_offline_banner, stringResource(R.string.feature_site_snapshot_date)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { if (switch.tryOnline(isSystemOffline(context))) loadKey++ },
                        modifier = Modifier.heightIn(min = 48.dp).testTag(SiteTags.TRY_ONLINE),
                    ) { Text(stringResource(R.string.feature_site_try_online)) }
                }
            }
        }

        message?.let {
            Text(
                stringResource(it),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag(SiteTags.MESSAGE).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        val description = stringResource(R.string.feature_site_webview_description)
        val notInCopy = stringResource(R.string.feature_site_not_in_copy)
        key(webViewKey) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag(SiteTags.WEBVIEW),
            factory = { c ->
                WebView(c).also { wv ->
                    wv.applySiteSettings()
                    wv.contentDescription = description
                    wv.webViewClient = SiteWebViewClient(
                        context = c.applicationContext,
                        switch = switch,
                        onExternal = { url -> message = openExternal(c, url) },
                        onMainFrameError = { switch.onMainFrameError(); loadKey++ },
                        onRenderGone = { webViewKey++ },
                        notInCopyHtml = { notInCopyPage(notInCopy) },
                    )
                    webView = wv
                }
            },
            update = { wv ->
                // Load the selected tab when it, or the online/offline mode, changed.
                val tab = SITE_TABS[tabIndex]
                val wanted = "${tab.url}|${switch.offline}|$loadKey"
                if (wv.tag != wanted) {
                    wv.tag = wanted
                    message = null
                    wv.loadUrl(if (switch.offline) OfflineCopy.assetUrl(tab) else tab.url)
                }
            },
            onRelease = { wv -> wv.disposeSite(); webView = null },
        )
        }
        Text(
            stringResource(R.string.feature_site_privacy),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag(SiteTags.PRIVACY),
        )
    }
}

/** The page shown for an address that is not in the offline copy. */
fun notInCopyPage(text: String): String {
    val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<style>body{font-family:sans-serif;margin:32px;color:#1A1917;background:#F7F6F2}</style></head><body><p>$escaped</p></body></html>"
}
