package ai.riskmandate.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

object LicenceTags {
    const val TEXT = "licences_text"
    const val BACK = "licences_back"
}

/** The licences text, from res/raw/licences.txt (rebuilt by app/tools/make-licences.sh). */
fun readLicences(context: Context): String =
    context.resources.openRawResource(R.raw.licences).bufferedReader().use { it.readText() }

@Composable
fun LicencesScreen(text: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.testTag(LicenceTags.BACK)) { Text("Back") }
        Text("Open source licences", style = MaterialTheme.typography.titleLarge)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.verticalScroll(rememberScrollState()).testTag(LicenceTags.TEXT),
        )
    }
}
