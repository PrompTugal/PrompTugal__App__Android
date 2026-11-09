package ai.riskmandate.app.features.mandate

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.Links
import ai.riskmandate.app.ManagedConfig
import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** This app's own reach, mandate and gap, read live from this device. Screen: [MandateScreen]. */
object MandateFeature : Feature {
    override val id = "mandate"
    override val title = R.string.feature_mandate_title
    override val enabled = true

    @Composable
    override fun Content(context: FeatureContext) {
        val app = LocalContext.current
        // Reach is read when the screen opens and again if the host's config or device state changes.
        val config = context.config
        val device = context.device
        val state = remember(config, device) {
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
        MandateScreen(state = state, onBack = context.onBack)
    }
}

/** Everything the screen shows, gathered once. */
data class MandateState(
    val report: MandateReport,
    val probes: List<HostProbe>,
    val config: ManagedConfig,
    val device: DeviceStatus,
)
