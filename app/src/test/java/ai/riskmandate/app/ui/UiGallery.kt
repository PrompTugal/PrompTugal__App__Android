package ai.riskmandate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Test-only layer names (the real ones are the abpdemo feature's strings). */
internal val sampleLayers = listOf("Browser", "Model", "Hook", "Inline proxy", "Secure harness", "Device policy")

/** Screen B's shape, built only from the shared components: banner, six layers, a note card. */
@Composable
internal fun LayersSample(states: List<LayerState>, large: Boolean) {
    PreviewFrame(title = "ABP in every layer", maxContentWidth = UiTokens.TextMaxWidth * 1.4f) {
        NoticeBanner("Demonstration: a staged example, not a live capture.")
        Spacer(Modifier.height(UiTokens.Gap))
        Column(verticalArrangement = Arrangement.spacedBy(if (large) 8.dp else 12.dp)) {
            sampleLayers.forEachIndexed { i, name ->
                LayerRow(
                    index = i + 1,
                    name = name,
                    state = states[i],
                    stateLabel = if (states[i] == LayerState.Checking) "Checking" else "Stopped here",
                    description = if (large) null else "One line on what this layer does.",
                    large = large,
                )
            }
        }
    }
}

/** Screen C's shape: three section cards with chips. */
@Composable
internal fun CardsSample() {
    PreviewFrame(title = "This app's mandate") {
        StatusChip("Live", ChipKind.Allowed)
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.Gap)) {
            SectionCard("Reach") {
                Text("What this app can touch on this device.")
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip("Internet", ChipKind.Held)
                    StatusChip("Camera: no", ChipKind.Blocked)
                    StatusChip("Nothing collected", ChipKind.Neutral)
                }
            }
            SectionCard("Mandate") {
                Text("What it is for: showing riskmandate.ai.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip("riskmandate.ai only", ChipKind.Allowed)
                    StatusChip("Other links redacted", ChipKind.Redacted)
                }
            }
            SectionCard("Gap") { Text("Reach minus mandate.") }
        }
    }
}
