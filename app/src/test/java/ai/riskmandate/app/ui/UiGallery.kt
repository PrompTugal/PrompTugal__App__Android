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

/** Screen I's shape at stand size: two lanes of the same steps, counters under them. */
@Composable
internal fun LabSample() {
    PreviewFrame(title = "ABP Lab", scrollable = true, maxContentWidth = androidx.compose.ui.unit.Dp.Unspecified) {
        Row(Modifier.fillMaxWidth().padding(horizontal = UiTokens.ScreenPadding), horizontalArrangement = Arrangement.spacedBy(UiTokens.Gap)) {
            Column(Modifier.weight(1f)) {
                SectionCard("Without ABP", large = true) {
                    StepRow(1, "Read document", "Ran", ChipKind.Neutral, large = true)
                    StepRow(2, "Proposed: share_document", "Failure: shared", ChipKind.Blocked, detail = "to=outside@example.com", large = true)
                }
            }
            Column(Modifier.weight(1f)) {
                SectionCard("Through the ABP", large = true) {
                    StepRow(1, "Read document", "Allowed", ChipKind.Allowed, large = true)
                    StepRow(2, "Proposed: share_document", "Blocked", ChipKind.Blocked, detail = "out of mandate", large = true)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(UiTokens.ScreenPadding), horizontalArrangement = Arrangement.spacedBy(UiTokens.Gap)) {
            StatTile("1 in 3", "Failed without ABP", Modifier.weight(1f), large = true)
            StatTile("20 of 20", "Caught with ABP", Modifier.weight(1f), large = true)
        }
    }
}
