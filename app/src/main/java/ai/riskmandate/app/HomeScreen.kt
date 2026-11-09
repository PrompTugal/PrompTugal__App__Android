package ai.riskmandate.app

import ai.riskmandate.app.ui.Card
import ai.riskmandate.app.ui.Columns
import ai.riskmandate.app.ui.Page
import ai.riskmandate.app.ui.ScreenHeader
import ai.riskmandate.app.ui.StatGrid
import ai.riskmandate.app.ui.StatTile
import ai.riskmandate.app.ui.TitledCard
import ai.riskmandate.app.ui.Tone
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

object HomeTags {
    const val OPEN = "open"
    const val VERSION = "version"
    const val DEVICE_MANAGEMENT = "device_management"
    const val DEVICE_LABEL = "device_label"
    const val MESSAGE = "message"
    const val LICENCES = "licences"
    const val DIAGNOSTICS = "diagnostics"
    const val STATS = "home_stats"
    fun feature(id: String) = "feature_$id"
    fun featureBadge(id: String) = "feature_badge_$id"
}

/** Material's minimum touch target. */
private val MinTouch = 48.dp

/**
 * Home, "Now" (docs/design/android-theme.md): a page of cards, not a list of buttons. A header, three
 * stat tiles, one card per feature (kicker, serif title, one-line summary; the whole card opens it),
 * then the device card and the links for this app. Two columns at 840 dp of content width, one below.
 * TalkBack order: header, message, stats, feature cards, device, this app.
 *
 * @param attention how many things need a look (shown in the third tile; zero is good).
 * @param showDiagnostics false hides the Diagnostics link (stand mode: visitors do not see it).
 */
@Composable
fun HomeScreen(
    version: String,
    device: DeviceStatus,
    onOpen: () -> Unit,
    @StringRes message: Int? = null,
    onLicences: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    features: List<FeatureButton> = emptyList(),
    showDiagnostics: Boolean = true,
    attention: Int = 0,
) {
    Page(Modifier.safeDrawingPadding()) {
        ScreenHeader(
            kicker = stringResource(R.string.app_name),
            title = stringResource(R.string.home_now_title),
            summary = stringResource(R.string.home_now_summary),
        )
        if (message != null) {
            Card {
                Text(
                    stringResource(message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    // Announced by TalkBack as soon as it appears.
                    modifier = Modifier.testTag(HomeTags.MESSAGE).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        StatGrid(Modifier.testTag(HomeTags.STATS)) {
            tile { StatTile(features.size.toString(), stringResource(R.string.home_stat_features)) }
            tile { StatTile("0", stringResource(R.string.home_stat_collected), tone = Tone.Good) }
            tile {
                StatTile(
                    attention.toString(),
                    stringResource(R.string.home_stat_attention),
                    detail = stringResource(if (attention == 0) R.string.home_stat_attention_none else R.string.home_stat_attention_some),
                    tone = if (attention == 0) Tone.Good else Tone.Warn,
                )
            }
        }
        Columns(
            leading = {
                for (f in features) {
                    val badge = f.badge?.let { stringResource(it) }
                    TitledCard(
                        title = stringResource(f.title),
                        kicker = f.kicker?.let { stringResource(it) },
                        onClick = f.onClick,
                        modifier = Modifier.testTag(HomeTags.feature(f.id)),
                        accessory = { if (badge != null) BadgePill(badge, Modifier.testTag(HomeTags.featureBadge(f.id))) },
                    ) {
                        if (f.summary != null) {
                            Text(stringResource(f.summary), style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted)
                        }
                    }
                }
            },
            trailing = {
                TitledCard(title = stringResource(R.string.home_device_heading)) {
                    Text(
                        stringResource(device.managementTextRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Pt.colors.muted,
                        modifier = Modifier.testTag(HomeTags.DEVICE_MANAGEMENT),
                    )
                    if (device.label != null) {
                        Text(
                            stringResource(R.string.home_device_label, device.label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Pt.colors.muted,
                            modifier = Modifier.testTag(HomeTags.DEVICE_LABEL),
                        )
                    }
                    Text(
                        stringResource(R.string.home_build, version),
                        style = MaterialTheme.typography.bodySmall,
                        color = Pt.colors.muted,
                        modifier = Modifier.testTag(HomeTags.VERSION),
                    )
                }
                TitledCard(title = stringResource(R.string.home_links_heading)) {
                    Text(
                        stringResource(R.string.home_about_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Pt.colors.muted,
                    )
                    val openAction = stringResource(R.string.home_open_action)
                    LinkRow(
                        stringResource(R.string.home_open),
                        HomeTags.OPEN,
                        onOpen,
                        Modifier.semantics { onClick(label = openAction, action = null) },
                    )
                    LinkRow(stringResource(R.string.home_licences), HomeTags.LICENCES, onLicences)
                    if (showDiagnostics) LinkRow(stringResource(R.string.home_diagnostics), HomeTags.DIAGNOSTICS, onDiagnostics)
                }
            },
        )
        // Room to scroll the last card clear of the bottom-end corner (stand mode's long-press target).
        Spacer(Modifier.height(48.dp))
    }
}

/** A text link row, 48 dp high: "small text rows, not buttons" (design spec). */
@Composable
private fun LinkRow(text: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = MinTouch).testTag(tag)) {
        Text(text, color = Pt.colors.red)
    }
}

/**
 * A feature's card on Home. [badge] is the short word on its [Feature.homeBadge]; [kicker] and [summary]
 * are the optional string resources a feature gives (Feature.kicker, Feature.summary).
 */
data class FeatureButton(
    val id: String,
    @StringRes val title: Int,
    @StringRes val badge: Int? = null,
    @StringRes val kicker: Int? = null,
    @StringRes val summary: Int? = null,
    val onClick: () -> Unit,
)

@Preview(showBackground = true)
@Preview(showBackground = true, widthDp = 900, heightDp = 700)
@Composable
private fun HomePreview() = RiskMandateTheme {
    HomeScreen(
        version = "0.0.0-local (1)",
        device = DeviceStatus.from(hasDeviceOwner = true, inWorkProfile = false, label = "Lisbon kiosk 3"),
        onOpen = {},
        features = listOf(FeatureButton("site", R.string.home_open, kicker = R.string.app_name, summary = R.string.home_now_summary) {}),
    )
}
