package ai.riskmandate.app.features

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable

/**
 * A summit feature. Each one lives in its own package under `features/` and is owned by one session,
 * so parallel sessions never edit the same file. The home screen lists [FEATURES] in order.
 */
interface Feature {
    /** Stable route id, also used in tests: lowercase letters only. */
    val id: String

    /** Title on the home screen's button and at the top of the feature's screen. */
    @get:StringRes val title: Int

    /** False hides the button (a feature still being built). */
    val enabled: Boolean get() = true

    @Composable
    fun Content(context: FeatureContext)
}

/** What a feature may use from the host. Ask the conductor before adding to it. */
class FeatureContext(
    val activity: ComponentActivity,
    val onBack: () -> Unit,
)
