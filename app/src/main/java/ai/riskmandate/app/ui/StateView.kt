package ai.riskmandate.app.ui

import ai.riskmandate.app.Pt
import ai.riskmandate.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** What a screen knows about its data (iOS `LoadPhase`). */
sealed interface LoadPhase {
    data object Idle : LoadPhase
    data object Loading : LoadPhase
    data object Loaded : LoadPhase
    data class Failed(val message: String) : LoadPhase
}

/**
 * The standard loading, error and empty states (iOS `StateView`), so every screen says the same thing the
 * same way. Draws nothing when [hasData] is true: put it above the content and let it step aside.
 * [title] is the noun in the user's language ("team", "articles"), lower case. Loading and error are polite
 * live regions. The retry button is 48 dp or more and only shown when [onRetry] is given.
 */
@Composable
fun StateView(phase: LoadPhase, hasData: Boolean, title: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    if (hasData) return
    Column(
        modifier.fillMaxWidth().heightIn(min = 180.dp).padding(Space.L).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.M, Alignment.CenterVertically),
    ) {
        when (phase) {
            LoadPhase.Idle, LoadPhase.Loading -> {
                CircularProgressIndicator(color = Pt.colors.red)
                Text(stringResource(R.string.ui_loading, title), style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted)
            }
            is LoadPhase.Failed -> {
                Text(
                    stringResource(R.string.ui_unavailable, title.replaceFirstChar { it.uppercase() }),
                    style = MaterialTheme.typography.titleMedium, color = Pt.colors.ink, textAlign = TextAlign.Center,
                )
                Text(phase.message, style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted, textAlign = TextAlign.Center)
                if (onRetry != null) {
                    Button(onClick = onRetry, modifier = Modifier.heightIn(min = UiTokens.MinTouch)) { Text(stringResource(R.string.ui_try_again)) }
                }
            }
            LoadPhase.Loaded -> {
                Text(stringResource(R.string.ui_nothing_here), style = MaterialTheme.typography.titleMedium, color = Pt.colors.ink)
                Text(stringResource(R.string.ui_is_empty, title), style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted, textAlign = TextAlign.Center)
            }
        }
    }
}
