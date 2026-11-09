package ai.riskmandate.app.features.site

import ai.riskmandate.app.Browser
import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** riskmandate.ai inside the app: an in-app WebView limited to the allow-list, native tabs and an offline copy. */
object SiteFeature : Feature {
    override val id = "site"
    override val title = R.string.feature_site_title
    override val enabled = true

    @Composable
    override fun Content(context: FeatureContext) {
        val switch = remember { OfflineSwitch(Browser.isOffline(context.activity)) }
        SiteScreen(switch = switch, onBack = context.onBack)
    }
}
