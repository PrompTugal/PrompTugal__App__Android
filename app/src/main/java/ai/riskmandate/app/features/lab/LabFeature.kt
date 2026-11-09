package ai.riskmandate.app.features.lab

import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * ABP Lab (feature I): the same model input down two paths, raw and through the ABP. Replay on samples for
 * now; live runs and loop mode follow. Off until the screen works end to end on the tablet.
 */
object LabFeature : Feature {
    override val id = "lab"
    override val title = R.string.feature_lab_title
    override val enabled = false
    override val icon: ImageVector get() = Icons.Filled.Build
    @get:StringRes override val kicker: Int get() = R.string.lab_home_kicker
    @get:StringRes override val summary: Int get() = R.string.lab_home_summary

    @Composable
    override fun Content(context: FeatureContext) {
        LabScreen()
    }
}
