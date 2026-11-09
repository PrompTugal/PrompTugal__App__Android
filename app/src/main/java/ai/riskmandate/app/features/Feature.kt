package ai.riskmandate.app.features

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.ManagedConfig
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable

/**
 * A summit feature. Each one lives in its own package under `features/` and is owned by one session,
 * so parallel sessions never edit the same file. The home screen lists [FEATURES] in order.
 *
 * A feature is an `object`, so a process-wide singleton: it holds only `val` constants, never state.
 * Rules: docs/architecture/README.md.
 */
interface Feature {
    /** Stable route id, also used in tests: lowercase letters only, never a built-in route name. */
    val id: String

    /** Title on the home screen's button and at the top of the feature's screen. */
    @get:StringRes val title: Int

    /** False hides the button (a feature still being built). */
    val enabled: Boolean get() = true

    @Composable
    fun Content(context: FeatureContext)
}

/**
 * Everything a feature gets from the host. Deliberately not the Activity: a feature uses
 * `LocalContext`, `LocalLifecycleOwner` and `LocalView` from Compose for the rest, and never casts
 * them to an Activity. Changes to this class go through the architect (docs/architecture/README.md).
 */
class FeatureContext(
    /** Takes the user back to the home screen (the host's own back handler does the same). */
    val onBack: () -> Unit,
    /** The managed configuration as the host last read it (on resume and on change). Read-only. */
    val config: ManagedConfig,
    /** This device's management state as the host last detected it. Read-only. */
    val device: DeviceStatus,
) {
    /**
     * Transitional, for the host only, so MainActivity keeps compiling until app.android passes
     * the host's own values (`remember(config, device) { FeatureContext(goHome, config, device) }`).
     * Features never call this.
     */
    @Deprecated("Host only: pass the host's config and device instead.")
    constructor(activity: ComponentActivity, onBack: () -> Unit) : this(onBack, ManagedConfig.read(activity), activity)

    private constructor(onBack: () -> Unit, config: ManagedConfig, activity: ComponentActivity) :
        this(onBack, config, DeviceStatus.detect(activity, config.deviceLabel))
}
