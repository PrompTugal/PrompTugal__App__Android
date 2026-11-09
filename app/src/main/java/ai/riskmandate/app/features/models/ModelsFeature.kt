package ai.riskmandate.app.features.models

import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import androidx.compose.runtime.Composable

/**
 * Feature G, model setup (dev.llm). Step 1 of the on-device LLM brief: for now the screen is the hidden bench
 * ([ModelBenchScreen]); the model list, download and checksum screen replaces it in step 3. Stays off the home
 * screen (`enabled = false`) until it works end to end on the tablet; the bench is reached from Diagnostics.
 */
object ModelsFeature : Feature {
    override val id = "models"
    override val title = R.string.feature_models_title
    override val enabled = false

    @Composable
    override fun Content(context: FeatureContext) = ModelBenchScreen()
}
