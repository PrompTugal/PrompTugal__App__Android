package ai.riskmandate.app.features.models

import ai.riskmandate.app.R
import ai.riskmandate.app.llm.InstallFailure
import ai.riskmandate.app.llm.InstallResult
import ai.riskmandate.app.llm.ModelCatalog
import ai.riskmandate.app.llm.ModelFiles
import ai.riskmandate.app.llm.ModelInstaller
import ai.riskmandate.app.llm.ModelPrefs
import ai.riskmandate.app.llm.ModelSpec
import ai.riskmandate.app.ui.FeatureContent
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ModelSetupTags {
    const val PRIVACY = "models_privacy"
    const val WIFI = "models_wifi"
    fun row(id: String) = "models_row_$id"
    fun state(id: String) = "models_state_$id"
    fun download(id: String) = "models_download_$id"
    fun stop(id: String) = "models_stop_$id"
    fun delete(id: String) = "models_delete_$id"
    fun import(id: String) = "models_import_$id"
    fun use(id: String) = "models_use_$id"
    fun message(id: String) = "models_message_$id"
    const val CONFIRM_DELETE = "models_confirm_delete"
}

/** One model's situation on this device, read off the main thread. */
private data class RowState(val spec: ModelSpec, val installed: Boolean, val partial: Long, val adbFile: File?)

private sealed interface Busy {
    val id: String
    data class Downloading(override val id: String, val done: Long, val total: Long, val checking: Boolean) : Busy
    data class Copying(override val id: String, val done: Long, val total: Long, val checking: Boolean) : Busy
}

/**
 * Feature G (brief step 3): choose a model, see its size and licence, download it once (resumable, checked),
 * or take the file pushed with adb, delete it, and pick which one is used. One operation at a time; leaving the
 * screen stops a download and keeps the partial file for resume.
 *
 * [onModelsChanged] tells the host's LlmService to rescan (LlmHost.refresh) once `FeatureContext.llm` exists.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelSetupScreen(
    installer: ModelInstaller,
    adbDir: () -> File?,
    onModelsChanged: suspend () -> Unit = {},
    /** True while a download or import runs; the feature holds stand mode's idle reset off meanwhile (R-llm-9). */
    onBusy: (Boolean) -> Unit = {},
    io: CoroutineDispatcher = Dispatchers.IO,
    models: List<ModelSpec> = ModelCatalog.ALL.filter { it.downloadable },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<RowState>>(emptyList()) }
    var busy by remember { mutableStateOf<Busy?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var messages by remember { mutableStateOf(mapOf<String, Pair<Int, String>>()) }
    var preferred by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<ModelSpec?>(null) }
    val unmetered = remember { isUnmetered(context) }
    LaunchedEffect(busy != null) { onBusy(busy != null) }

    LaunchedEffect(refresh) {
        val (r, p) = withContext(io) {
            val adb = adbDir()
            models.map { s ->
                RowState(s, installer.isInstalled(s), installer.partialBytes(s), adb?.let { File(it, s.fileName) }?.takeIf { it.isFile })
            } to ModelPrefs.preferred(context)
        }
        rows = r
        preferred = p
    }

    fun finish(spec: ModelSpec, result: InstallResult) {
        messages = messages + (spec.id to messageFor(result))
        busy = null
        refresh++
        if (result is InstallResult.Installed) scope.launch { onModelsChanged() }
    }

    fun start(spec: ModelSpec) {
        messages = messages - spec.id
        job = scope.launch {
            busy = Busy.Downloading(spec.id, installer.partialBytes(spec), spec.sizeBytes, false)
            val result = installer.download(spec) { done, total, checking -> busy = Busy.Downloading(spec.id, done, total, checking) }
            finish(spec, result)
        }.also { j -> j.invokeOnCompletion { job = null; if (busy?.id == spec.id) { busy = null; refresh++ } } }
    }

    fun import(spec: ModelSpec, file: File) {
        messages = messages - spec.id
        job = scope.launch {
            busy = Busy.Copying(spec.id, 0, spec.sizeBytes, false)
            val result = installer.importFrom(file, spec) { done, total, checking -> busy = Busy.Copying(spec.id, done, total, checking) }
            finish(spec, result)
        }.also { j -> j.invokeOnCompletion { job = null; if (busy?.id == spec.id) { busy = null; refresh++ } } }
    }

    FeatureContent {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.models_setup_intro), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.models_privacy, "huggingface.co"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(ModelSetupTags.PRIVACY),
            )
            if (!unmetered) {
                Text(
                    stringResource(R.string.models_wifi),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(ModelSetupTags.WIFI),
                )
            }
            val installedCount = rows.count { it.installed }
            val inUseId = rows.filter { it.installed }.let { inst -> inst.firstOrNull { it.spec.id == preferred } ?: inst.firstOrNull() }?.spec?.id
            rows.forEach { row ->
                val spec = row.spec
                val b = busy?.takeIf { it.id == spec.id }
                val inUse = spec.id == inUseId
                Card(Modifier.fillMaxWidth().testTag(ModelSetupTags.row(spec.id))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(spec.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.models_size_licence, gigabytes(spec.sizeBytes), spec.licence), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when {
                                b is Busy.Downloading && b.checking -> stringResource(R.string.models_checking)
                                b is Busy.Downloading -> stringResource(R.string.models_progress, percent(b.done, b.total), gigabytes(b.done), gigabytes(b.total))
                                b is Busy.Copying && b.checking -> stringResource(R.string.models_checking)
                                b is Busy.Copying -> stringResource(R.string.models_copying, percent(b.done, b.total))
                                row.installed && inUse && installedCount > 1 -> stringResource(R.string.models_state_in_use)
                                row.installed -> stringResource(R.string.models_state_installed)
                                row.partial > 0 -> stringResource(R.string.models_state_partial, gigabytes(row.partial), gigabytes(spec.sizeBytes))
                                else -> stringResource(R.string.models_state_none)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag(ModelSetupTags.state(spec.id)).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        if (b != null) {
                            val (done, total) = when (b) {
                                is Busy.Downloading -> b.done to b.total
                                is Busy.Copying -> b.done to b.total
                            }
                            LinearProgressIndicator(progress = { if (total > 0) done.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth())
                        }
                        messages[spec.id]?.let { (res, detail) ->
                            Text(
                                if (detail.isEmpty()) stringResource(res) else stringResource(res, detail),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (res == R.string.models_installed_ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag(ModelSetupTags.message(spec.id)),
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val idle = busy == null
                            if (b is Busy.Downloading && !b.checking) {
                                Button(onClick = { job?.cancel() }, modifier = Modifier.heightIn(min = 48.dp).testTag(ModelSetupTags.stop(spec.id))) {
                                    Text(stringResource(R.string.models_stop))
                                }
                            }
                            if (!row.installed && b == null) {
                                Button(onClick = { start(spec) }, enabled = idle, modifier = Modifier.heightIn(min = 48.dp).testTag(ModelSetupTags.download(spec.id))) {
                                    Text(stringResource(if (row.partial > 0) R.string.models_resume else R.string.models_download))
                                }
                                row.adbFile?.let { f ->
                                    OutlinedButton(onClick = { import(spec, f) }, enabled = idle, modifier = Modifier.heightIn(min = 48.dp).testTag(ModelSetupTags.import(spec.id))) {
                                        Text(stringResource(R.string.models_import))
                                    }
                                }
                            }
                            if (row.installed && installedCount > 1 && !inUse) {
                                OutlinedButton(
                                    onClick = {
                                        ModelPrefs.setPreferred(context, spec.id)
                                        preferred = spec.id
                                        scope.launch { onModelsChanged() }
                                    },
                                    enabled = idle,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag(ModelSetupTags.use(spec.id)),
                                ) { Text(stringResource(R.string.models_use)) }
                            }
                            if ((row.installed || row.partial > 0) && b == null) {
                                TextButton(onClick = { confirmDelete = spec }, enabled = idle, modifier = Modifier.heightIn(min = 48.dp).testTag(ModelSetupTags.delete(spec.id))) {
                                    Text(stringResource(R.string.models_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { spec ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.models_delete_title, spec.displayName)) },
            text = { Text(stringResource(R.string.models_delete_body, gigabytes(spec.sizeBytes))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = null
                        scope.launch {
                            withContext(io) { installer.delete(spec) }
                            if (ModelPrefs.preferred(context) == spec.id) ModelPrefs.setPreferred(context, null)
                            messages = messages - spec.id
                            refresh++
                            onModelsChanged()
                        }
                    },
                    modifier = Modifier.testTag(ModelSetupTags.CONFIRM_DELETE),
                ) { Text(stringResource(R.string.models_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.models_cancel)) } },
        )
    }
}

/** A string resource taking one %1$s argument, and that argument. */
private fun messageFor(result: InstallResult): Pair<Int, String> = when (result) {
    is InstallResult.Installed -> R.string.models_installed_ok to ""
    is InstallResult.Failed -> when (result.reason) {
        InstallFailure.STORAGE -> R.string.models_err_storage to ""
        InstallFailure.NETWORK -> R.string.models_err_network to result.detail.take(80)
        InstallFailure.HTTP -> R.string.models_err_http to result.detail.take(40)
        InstallFailure.REFUSED -> R.string.models_err_refused to ""
        InstallFailure.SIZE -> R.string.models_err_size to ""
        InstallFailure.HASH -> R.string.models_err_hash to ""
    }
}

private fun isUnmetered(context: Context): Boolean = try {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ?: false
} catch (e: RuntimeException) {
    false
}

private fun percent(done: Long, total: Long) = if (total > 0) (done * 100 / total).toInt() else 0

private fun gigabytes(bytes: Long) = String.format(Locale.ROOT, "%.2f GB", bytes / 1e9)

/** The default installer and adb folder for this app. */
internal fun defaultInstaller(context: Context) = ModelInstaller(ModelFiles.internalDir(context))
