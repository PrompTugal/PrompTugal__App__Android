package ai.riskmandate.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

object HomeTags {
    const val OPEN = "open"
    const val VERSION = "version"
    const val DEVICE_MANAGEMENT = "device_management"
    const val DEVICE_LABEL = "device_label"
    const val MESSAGE = "message"
}

@Composable
fun HomeScreen(
    version: String,
    device: DeviceStatus,
    onOpen: () -> Unit,
    message: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("RiskMandate", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpen, modifier = Modifier.testTag(HomeTags.OPEN)) {
            Text("Open riskmandate.ai")
        }
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(HomeTags.MESSAGE),
            )
        }
        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth()) {
            Text("Device", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                device.managementText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(HomeTags.DEVICE_MANAGEMENT),
            )
            if (device.label != null) {
                Text(
                    "Label: ${device.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(HomeTags.DEVICE_LABEL),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("About", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "RiskMandate: this app opens riskmandate.ai.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Build $version",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(HomeTags.VERSION),
            )
        }
    }
}

@Preview
@Composable
private fun HomePreview() = MaterialTheme {
    HomeScreen(
        version = "0.0.0-local (1)",
        device = DeviceStatus.from(hasDeviceOwner = true, inWorkProfile = false, label = "Lisbon kiosk 3"),
        onOpen = {},
    )
}
