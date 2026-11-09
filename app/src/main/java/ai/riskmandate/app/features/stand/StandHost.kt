package ai.riskmandate.app.features.stand

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable

/**
 * Stand mode (feature D) wraps the whole app: screen pinning, keep-screen-on, return home when idle.
 * Placeholder: renders the app unchanged. Owned by the stand session.
 *
 * @param atHome true while the home screen shows.
 * @param goHome takes the app back to the home screen (for the idle reset).
 */
@Composable
fun StandHost(activity: ComponentActivity, atHome: Boolean, goHome: () -> Unit, content: @Composable () -> Unit) {
    content()
}
