package ai.riskmandate.app.features.lab

import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.features.HoldIdleWhile
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * ABP Lab (feature I): the same model input down two paths, raw and through the ABP. Replay on samples for
 * now; live runs and loop mode follow. Off until the screen works end to end on the tablet.
 */
object LabFeature : Feature {
    override val id = "lab"
    override val title = R.string.feature_lab_title
    override val enabled = false
    // icon stays null: the host's own line icons apply (design ruling, docs/design/android-theme.md, Icons).
    @get:StringRes override val kicker: Int get() = R.string.lab_home_kicker
    @get:StringRes override val summary: Int get() = R.string.lab_home_summary

    @Composable
    override fun Content(context: FeatureContext) {
        // A live run or a loop runs unattended on purpose: hold stand mode's idle reset off while it does.
        var busy by remember { mutableStateOf(false) }
        context.HoldIdleWhile(busy)
        // Export needs a share path from the host (a request is in the status page); null keeps the button off.
        LabScreen(llm = context.llm, openFeature = context.openFeature, onBusy = { busy = it })
    }
}
