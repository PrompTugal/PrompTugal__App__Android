package ai.riskmandate.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

object LicenceTags {
    const val TEXT = "licences_text"
    const val APACHE = "licences_apache"
}

/** The app's licence notice and library list, from res/raw/licences.txt (rebuilt by app/tools/make-licences.sh). */
fun readLicences(context: Context): String = readRaw(context, R.raw.licences)

/** The full Apache License 2.0 text (res/raw/apache_license_2_0.txt, a copy of /LICENSE), Apache 2.0 section 4(a). */
fun readApacheLicence(context: Context): String = readRaw(context, R.raw.apache_license_2_0)

private fun readRaw(context: Context, id: Int): String =
    context.resources.openRawResource(id).bufferedReader().use { it.readText() }

@Composable
fun LicencesScreen(text: String, apacheText: String) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(LicenceTags.TEXT),
                )
                Text(
                    stringResource(R.string.licences_apache_heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
                Text(
                    apacheText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(LicenceTags.APACHE),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LicencesPreview() = RiskMandateTheme {
    LicencesScreen(text = "RiskMandate\n\nLicensed under the Apache License, Version 2.0.", apacheText = "Apache License ...")
}
