package ai.riskmandate.app.features.xray

import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.features.HoldIdleWhile
import androidx.compose.runtime.Composable

/**
 * Silvio's X-Ray page (web-summit.sgit.ai) in an embedded browser: full screen at the start, with a toggle to the
 * app's window and back; screen on; recovering by itself. In the window, the app's Back returns to the menu.
 *
 * Not on the stand-mode visitor list: the page itself can call AI services (openrouter.ai) with a demo key typed
 * on the device, which stand mode's rule keeps away from visitors until security.android decides otherwise.
 */
object XrayFeature : Feature {
    override val id = "xray"
    override val title = R.string.feature_xray_title
    override val enabled = true
    override val kicker = R.string.feature_xray_kicker
    override val summary = R.string.feature_xray_summary

    @Composable
    override fun Content(context: FeatureContext) {
        // Left running on purpose: never reset by the idle timer while it shows.
        context.HoldIdleWhile(true)
        XrayScreen()
    }
}
