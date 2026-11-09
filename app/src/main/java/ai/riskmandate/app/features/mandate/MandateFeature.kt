package ai.riskmandate.app.features.mandate

import ai.riskmandate.app.R
import ai.riskmandate.app.features.Feature
import ai.riskmandate.app.features.FeatureContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** Placeholder: the owning session replaces this file's contents (keep the object name and id). */
object MandateFeature : Feature {
    override val id = "mandate"
    override val title = R.string.feature_mandate_title
    override val enabled = false

    @Composable
    override fun Content(context: FeatureContext) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(title)) }
    }
}
