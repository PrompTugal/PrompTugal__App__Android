package ai.riskmandate.app.features

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.ManagedConfig
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf

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
    /**
     * Holds off stand mode's idle reset while a feature runs unattended on purpose (abpdemo's auto-play).
     * Returns the release; calling it twice is harmless. Features do not call this directly: use
     * [HoldIdleWhile], which releases when the condition ends or the feature leaves the screen.
     * The default does nothing, for previews and tests.
     */
    val holdIdle: () -> () -> Unit = { {} },
)

/**
 * Keeps stand mode's idle reset off while [active] is true, and releases it when [active] turns false
 * or the feature leaves the screen. The only way a feature should hold the reset off.
 */
@Composable
fun FeatureContext.HoldIdleWhile(active: Boolean) {
    DisposableEffect(this, active) {
        val release = if (active) holdIdle() else null
        onDispose { release?.invoke() }
    }
}

/**
 * The host's record of idle holds (one per [hold] not yet released). Host-owned: MainActivity keeps one
 * in `remember`, passes [hold] as [FeatureContext.holdIdle] and [held] to StandHost. Main thread only.
 */
class IdleHolds {
    private val tokens = mutableStateListOf<Any>()

    /** True while any feature holds the idle reset off. Compose state: readers recompose on change. */
    val held: Boolean get() = tokens.isNotEmpty()

    fun hold(): () -> Unit {
        val token = Any()
        tokens.add(token)
        return { tokens.remove(token) }
    }

    /** Drops every hold (the host calls this when the route changes, as a safety net). */
    fun clear() = tokens.clear()
}
