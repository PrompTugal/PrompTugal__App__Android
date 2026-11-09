package ai.riskmandate.app.features.models

import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.features.HoldIdleWhile
import ai.riskmandate.app.llm.LlmHost
import ai.riskmandate.app.llm.ModelFiles
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Feature G, model setup (dev.llm, brief step 3): [ModelSetupScreen] lists the offered models, downloads one
 * (resumable, size and SHA-256 checked), takes an adb-pushed file the same way, deletes, and picks the one used.
 * Stays off the home screen (`enabled = false`) until it works end to end on the tablet. The speed bench is
 * llm/bench, reached from Diagnostics.
 */
object ModelsFeature : Feature {
    override val id = "models"
    override val title = R.string.feature_models_title
    override val enabled = false
    override val icon: ImageVector = Icons.Filled.Settings
    override val kicker = R.string.models_kicker
    override val summary = R.string.models_summary

    @Composable
    override fun Content(context: FeatureContext) = ModelsContent(context)
}

/**
 * G's content with the dispatcher for file work as a parameter, so tests run the file scans in step
 * (Dispatchers.Unconfined) instead of waiting on IO. [ModelsFeature.Content] uses Dispatchers.IO.
 */
@Composable
internal fun ModelsContent(context: FeatureContext, io: CoroutineDispatcher = Dispatchers.IO) {
    val app = LocalContext.current.applicationContext
    val installer = remember { defaultInstaller(app) }
    // Stand mode's idle reset would leave the screen and stop a long download: hold it off while one runs
    // (security R-llm-9). HoldIdleWhile releases when the download ends or the screen is left.
    var busy by remember { mutableStateOf(false) }
    context.HoldIdleWhile(busy)
    ModelSetupScreen(
        installer = installer,
        adbDir = { ModelFiles.adbDir(app) },
        // After an install, delete or switch, the host's service rescans (and unloads a deleted model).
        onModelsChanged = { (context.llm as? LlmHost)?.refresh() },
        onBusy = { busy = it },
        // "Nothing you type leaves the device" holds only while the cloud model is off (M.4.3, security R-llm-14).
        // The host's cloudOn is the only signal (architect): never inferred from anything else. This screen stays
        // on-device and does not declare usesCloud.
        cloudInUse = context.cloudOn,
        io = io,
    )
}
