package ai.riskmandate.app.features.mandate

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.Links
import ai.riskmandate.app.ManagedConfig
import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** This app's own reach, mandate and gap, read live from this device. Screen: [MandateScreen]. */
object MandateFeature : Feature {
    override val id = "mandate"
    override val title = R.string.feature_mandate_title
    override val enabled = true

    @Composable
    override fun Content(context: FeatureContext) {
        val app = LocalContext.current
        // Reach is read off the main thread when the screen opens, and again if the host's config or device
        // state changes (the host keeps them current).
        val config = context.config
        val device = context.device
        var state by remember { mutableStateOf<MandateState?>(null) }
        LaunchedEffect(config, device) {
            val loaded = withContext(Dispatchers.IO) {
                MandateState(
                    report = MandateReport(
                        reach = ReachCollector.collect(app),
                        mandate = Mandate.current(
                            app.packageName,
                            managedKeys = listOf(ManagedConfig.KEY_START_URL, ManagedConfig.KEY_DEVICE_LABEL),
                        ),
                    ),
                    probes = HostProbe.run(Links::isAllowed),
                    config = config,
                    device = device,
                )
            }
            state = loaded
        }
        val s = state
        if (s == null) {
            Text(stringResource(R.string.mandate_loading), Modifier.padding(16.dp).testTag(MandateTags.LOADING))
        } else {
            MandateScreen(state = s)
        }
    }
}

/** Everything the screen shows, gathered once. */
data class MandateState(
    val report: MandateReport,
    val probes: List<HostProbe>,
    val config: ManagedConfig,
    val device: DeviceStatus,
)
