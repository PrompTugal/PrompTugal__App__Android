package ai.riskmandate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

/** Home as a "Now"-style page of cards instead of a list of buttons (target for app.android). */
@Composable
internal fun NowSample(wide: Boolean = LocalWideLayout.current) {
    Page {
        ScreenHeader(
            kicker = "RiskMandate",
            title = "Now",
            summary = "Reach, mandate and the gap, for the agents you run.",
        )
        StatGrid {
            tile { StatTile("3", "Features on", detail = "of 5 ready", tone = Tone.Good) }
            tile { StatTile("0", "Collected", detail = "nothing leaves the tablet") }
            tile { StatTile("1", "Needs a look", detail = "model not installed", tone = Tone.Warn) }
        }
        Columns(
            leading = {
                TitledCard("ABP in every layer", kicker = "Demonstration", accessory = { KindBadge(SurfaceKind.Native) }, onClick = {}) {
                    Text("One planted instruction, stopped at six layers.")
                }
                TitledCard("RiskMandate", kicker = "riskmandate.ai", accessory = { KindBadge(SurfaceKind.Web) }, onClick = {}) {
                    Text("The site, inside the app, with native tabs.")
                }
            },
            trailing = {
                TitledCard("This app's mandate", kicker = "Live", accessory = { CountPill(0, "gaps") }, onClick = {}) {
                    Text("What it can reach, what it may do, and the gap.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip("riskmandate.ai only", ChipKind.Allowed)
                        StatusChip("Held: model", ChipKind.Held)
                    }
                }
                StateView(LoadPhase.Loaded, hasData = false, title = "articles")
            },
        )
    }
}

/** A permanent sidebar and a content pane, as the spec asks for tablets. Icons are stand-ins (real ones are app.android's). */
@Composable
internal fun ShellSample() {
    val c = ai.riskmandate.app.Pt.colors
    Row(Modifier.fillMaxSize().background(c.paper)) {
        Column(
            Modifier.width(300.dp).fillMaxHeight().padding(12.dp)
                .background(c.card, androidx.compose.foundation.shape.RoundedCornerShape(UiTokens.CardRadius))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "RM", color = c.paper, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.background(c.ink, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 12.dp),
                )
                Column {
                    Kicker("RiskMandate")
                    Text("Web Summit", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = c.muted)
                }
            }
            Spacer(Modifier.height(16.dp))
            for ((section, items) in listOf(
                "Today" to listOf("Now" to 0, "RiskMandate" to 0, "ABP in every layer" to 0),
                "Explore" to listOf("ABP Lab" to 0, "Chat" to 0, "What's new" to 3),
                "This device" to listOf("This app's mandate" to 0, "Models" to 1),
            )) {
                Text(section, color = c.muted, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                for ((i, item) in items.withIndex()) {
                    val selected = section == "Today" && i == 0
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .background(if (selected) c.red.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(22.dp).border(2.dp, if (selected) c.red else c.ink, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)))
                        Text(item.first, Modifier.weight(1f), color = if (selected) c.red else c.ink, fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else null)
                        if (item.second > 0) CountPill(item.second, "new")
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) { NowSample() }
    }
}
