package ai.riskmandate.app.features.site

import ai.riskmandate.app.Browser
import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext

/** riskmandate.ai inside the app: an in-app WebView limited to the allow-list, native tabs and an offline copy. */
object SiteFeature : Feature {
    override val id = "site"
    override val title = R.string.feature_site_title
    override val enabled = true
    // No icon: design's ruling (docs/design/android-theme.md, Icons), the host's id-based line icon applies.
    override val kicker = R.string.feature_site_kicker
    override val summary = R.string.feature_site_summary

    @Composable
    override fun Content(context: FeatureContext) {
        val android = LocalContext.current
        // Saved across rotation, like the selected tab.
        val switch = rememberSaveable(saver = Saver<OfflineSwitch, Boolean>(save = { it.offline }, restore = { OfflineSwitch(it) })) {
            OfflineSwitch(Browser.isOffline(android))
        }
        SiteScreen(switch = switch, onBack = context.onBack)
    }
}
