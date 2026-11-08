package ai.riskmandate.app

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

object DiagnosticsTags {
    const val TEXT = "diagnostics_text"
    const val COPY = "diagnostics_copy"
    const val COPIED = "diagnostics_copied"
    const val BACK = "diagnostics_back"
}

/** Puts [text] on the clipboard as plain text. Returns false if the system refuses. */
fun copyToClipboard(context: Context, label: String, text: String): Boolean = try {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(label, text))
    true
} catch (e: RuntimeException) {
    false
}

@Composable
fun DiagnosticsScreen(text: String, onCopy: () -> Boolean, onBack: () -> Unit) {
    var copied by rememberSaveable { mutableStateOf<Boolean?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).testTag(DiagnosticsTags.BACK)) {
                Text(stringResource(R.string.licences_back))
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.diagnostics_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.diagnostics_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag(DiagnosticsTags.TEXT),
                    )
                }
                Button(
                    onClick = { copied = onCopy() },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(DiagnosticsTags.COPY),
                ) { Text(stringResource(R.string.diagnostics_copy)) }
                copied?.let { ok ->
                    Text(
                        stringResource(if (ok) R.string.diagnostics_copied else R.string.diagnostics_copy_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(DiagnosticsTags.COPIED).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

@Composable
fun rememberCopyAction(text: String): () -> Boolean {
    val context = LocalContext.current
    val label = stringResource(R.string.diagnostics_title)
    return { copyToClipboard(context, label, text) }
}
