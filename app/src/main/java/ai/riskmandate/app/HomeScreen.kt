package ai.riskmandate.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

object HomeTags {
    const val OPEN = "open"
    const val VERSION = "version"
    const val DEVICE_MANAGEMENT = "device_management"
    const val DEVICE_LABEL = "device_label"
    const val MESSAGE = "message"
    const val LICENCES = "licences"
}

/** Material's minimum touch target. */
private val MinTouch = 48.dp

/** Landscape and at least this wide: two panes side by side (7" and 10" tablets, phones on their side). */
private val TwoPaneMinWidth = 640.dp

/** Content never gets wider than this, so lines stay readable on a 10" tablet. */
private val ContentMaxWidth = 560.dp

@Composable
fun HomeScreen(
    version: String,
    device: DeviceStatus,
    onOpen: () -> Unit,
    @StringRes message: Int? = null,
    onLicences: () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val twoPane = maxWidth >= TwoPaneMinWidth && maxWidth > maxHeight
        // Always scrollable, so 200% font scale never cuts anything off. Reading order for TalkBack
        // is the order written here: title, open button, message, device, about, licences, build.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (twoPane) {
                Row(
                    modifier = Modifier.widthIn(max = ContentMaxWidth * 2),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        PrimaryPane(onOpen, message)
                    }
                    Column(Modifier.weight(1f)) { InfoPane(version, device, onLicences) }
                }
            } else {
                Column(Modifier.widthIn(max = ContentMaxWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                    PrimaryPane(onOpen, message)
                    Spacer(Modifier.height(32.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.fillMaxWidth()) { InfoPane(version, device, onLicences) }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PrimaryPane(onOpen: () -> Unit, @StringRes message: Int?) {
    Text(
        stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(24.dp))
    val openAction = stringResource(R.string.home_open_action)
    Button(
        onClick = onOpen,
        modifier = Modifier
            .heightIn(min = MinTouch)
            .testTag(HomeTags.OPEN)
            .semantics { onClick(label = openAction, action = null) },
    ) {
        Text(stringResource(R.string.home_open))
    }
    if (message != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            // Announced by TalkBack as soon as it appears.
            modifier = Modifier.testTag(HomeTags.MESSAGE).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun ColumnScope.InfoPane(version: String, device: DeviceStatus, onLicences: () -> Unit) {
    Text(
        stringResource(R.string.home_device_heading),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(device.managementTextRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(HomeTags.DEVICE_MANAGEMENT),
    )
    if (device.label != null) {
        Text(
            stringResource(R.string.home_device_label, device.label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(HomeTags.DEVICE_LABEL),
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.home_about_heading),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.home_about_text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(
        onClick = onLicences,
        modifier = Modifier.heightIn(min = MinTouch).testTag(HomeTags.LICENCES),
    ) {
        Text(stringResource(R.string.home_licences))
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.home_build, version),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(HomeTags.VERSION),
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, widthDp = 900, heightDp = 560)
@Composable
private fun HomePreview() = RiskMandateTheme {
    HomeScreen(
        version = "0.0.0-local (1)",
        device = DeviceStatus.from(hasDeviceOwner = true, inWorkProfile = false, label = "Lisbon kiosk 3"),
        onOpen = {},
    )
}
