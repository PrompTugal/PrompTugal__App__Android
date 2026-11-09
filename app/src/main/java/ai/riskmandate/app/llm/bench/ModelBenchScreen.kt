package ai.riskmandate.app.llm.bench

import ai.riskmandate.app.BuildConfig
import ai.riskmandate.app.R
import ai.riskmandate.app.copyToClipboard
import ai.riskmandate.app.llm.BenchBackend
import ai.riskmandate.app.llm.BenchDevice
import ai.riskmandate.app.llm.BenchProgress
import ai.riskmandate.app.llm.BenchReport
import ai.riskmandate.app.llm.FoundModel
import ai.riskmandate.app.llm.LiteRtBench
import ai.riskmandate.app.llm.ModelCatalog
import ai.riskmandate.app.llm.ModelFiles
import ai.riskmandate.app.llm.Verdict
import ai.riskmandate.app.ui.FeatureContent
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ModelsTags {
    const val NONE = "models_none"
    const val FOLDER = "models_folder"
    const val REFRESH = "models_refresh"
    const val GPU = "models_backend_gpu"
    const val CPU = "models_backend_cpu"
    const val RUN = "models_run"
    const val STOP = "models_stop"
    const val STATUS = "models_status"
    const val OUTPUT = "models_output"
    const val REPORT = "models_report"
    const val COPY = "models_copy"
    fun model(id: String) = "models_model_$id"
}

/** What the bench shows under the buttons. */
private sealed interface Status {
    data object Idle : Status
    data class Verifying(val percent: Int) : Status
    data object Loading : Status
    data class Generating(val chunks: Int) : Status
    data object Done : Status
    data object WrongSize : Status
    data object WrongHash : Status
    data class Failed(val message: String) : Status
}

/**
 * The hidden bench (brief step 1). Lives in llm/ (not features/models) so the host's Diagnostics route can show
 * it without importing a feature package (architect A-llm-4): pick a model file pushed with adb, run the fixed prompt on GPU or CPU, and
 * copy load time, prefill and decode speed and memory. Content only: the host's frame draws title and Back.
 * Leaving the screen cancels a run; the engine is closed either way.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelBenchScreen(
    /** Where file scans and SHA-256 checks run. Tests pass Dispatchers.Unconfined to run them in step. */
    io: CoroutineDispatcher = Dispatchers.IO,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<List<FoundModel>?>(null) }
    var adbDir by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var backend by rememberSaveable { mutableStateOf(BenchBackend.GPU) }
    var status by remember { mutableStateOf<Status>(Status.Idle) }
    var output by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    // Reports are small strings, so they survive rotation; the results behind them do not need to.
    var reports by rememberSaveable { mutableStateOf(listOf<String>()) }
    var copied by remember { mutableStateOf<Boolean?>(null) }
    // Files already checked this visit (path, size, modified) -> time the check took. Re-checked after any change.
    val verified = remember { mutableStateMapOf<String, Long>() }

    LaunchedEffect(refreshKey) {
        val (list, dir) = withContext(io) { ModelFiles.find(context) to ModelFiles.adbDir(context)?.absolutePath }
        found = list
        adbDir = dir
        if (list.none { it.file.absolutePath == selected }) selected = list.firstOrNull()?.file?.absolutePath
    }

    fun run(model: FoundModel) {
        job = scope.launch {
            output = ""
            copied = null
            val key = "${model.file.absolutePath}:${model.file.length()}:${model.file.lastModified()}"
            var verifyMs = verified[key]
            if (verifyMs == null) {
                status = Status.Verifying(0)
                val start = SystemClock.elapsedRealtime()
                val verdict = withContext(io) {
                    // verifyOnce: a file feature G installed and checked (its marker still matches size, time and
                    // pinned hash) is not hashed again; anything else is hashed in full.
                    ModelFiles.verifyOnce(model.file, model.spec) { p -> ensureActive(); status = Status.Verifying(p) }
                }
                when (verdict) {
                    Verdict.WRONG_SIZE -> { status = Status.WrongSize; return@launch }
                    Verdict.WRONG_HASH -> { status = Status.WrongHash; refreshKey++; return@launch }
                    Verdict.OK -> verifyMs = (SystemClock.elapsedRealtime() - start).also { verified[key] = it }
                }
            }
            val result = LiteRtBench.run(context, model, backend, verifyMs) { p ->
                when (p) {
                    BenchProgress.Loading -> status = Status.Loading
                    is BenchProgress.Generating -> { status = Status.Generating(p.chunks); output = p.text }
                }
            }
            reports = reports + BenchReport.result(reports.size + 1, result)
            status = result.error?.let { Status.Failed(it) } ?: Status.Done
        }.also { it.invokeOnCompletion { job = null } }
    }

    val running = job != null
    FeatureContent {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.models_bench_intro), style = MaterialTheme.typography.bodyLarge)

            val list = found
            if (list != null && list.isEmpty()) {
                Text(
                    stringResource(R.string.models_bench_none),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(ModelsTags.NONE),
                )
            }
            if (list != null && list.isNotEmpty()) {
                Text(stringResource(R.string.models_bench_pick), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Column(Modifier.selectableGroup()) {
                    list.forEach { m ->
                        val path = m.file.absolutePath
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = selected == path, enabled = !running, role = Role.RadioButton) { selected = path }
                                .testTag(ModelsTags.model(m.spec.id)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == path, onClick = null, enabled = !running)
                            Spacer(Modifier.padding(start = 8.dp))
                            val where = stringResource(
                                if (m.file.parentFile == ModelFiles.internalDir(context)) R.string.models_bench_where_installed else R.string.models_bench_where_adb,
                            )
                            Text(stringResource(R.string.models_bench_model_line, m.spec.displayName, gigabytes(m.sizeBytes), where))
                        }
                    }
                }
                Text(stringResource(R.string.models_bench_backend), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = backend == BenchBackend.GPU, enabled = !running, onClick = { backend = BenchBackend.GPU },
                        label = { Text(stringResource(R.string.models_bench_gpu)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag(ModelsTags.GPU),
                    )
                    FilterChip(
                        selected = backend == BenchBackend.CPU, enabled = !running, onClick = { backend = BenchBackend.CPU },
                        label = { Text(stringResource(R.string.models_bench_cpu)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag(ModelsTags.CPU),
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val model = list?.firstOrNull { it.file.absolutePath == selected }
                if (running) {
                    Button(onClick = { job?.cancel() }, modifier = Modifier.heightIn(min = 48.dp).testTag(ModelsTags.STOP)) {
                        Text(stringResource(R.string.models_bench_stop))
                    }
                } else {
                    Button(
                        onClick = { model?.let(::run) }, enabled = model != null,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(ModelsTags.RUN),
                    ) { Text(stringResource(R.string.models_bench_run)) }
                }
                OutlinedButton(onClick = { refreshKey++ }, enabled = !running, modifier = Modifier.heightIn(min = 48.dp).testTag(ModelsTags.REFRESH)) {
                    Text(stringResource(R.string.models_bench_refresh))
                }
                OutlinedButton(
                    onClick = { copied = copyToClipboard(context, "RiskMandate LLM bench", BenchReport.text(device(), reports)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(ModelsTags.COPY),
                ) { Text(stringResource(R.string.models_bench_copy)) }
            }

            val statusText = statusText(status, copied)
            if (statusText.isNotEmpty()) {
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (status is Status.Failed || status is Status.WrongHash || status is Status.WrongSize) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(ModelsTags.STATUS).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            if (output.isNotEmpty()) {
                Text(stringResource(R.string.models_bench_output), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                // Model output is shown as plain text only: never parsed as HTML, links or code.
                SelectionContainer { Text(output, modifier = Modifier.testTag(ModelsTags.OUTPUT)) }
            }

            if (reports.isNotEmpty()) {
                Text(stringResource(R.string.models_bench_results), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                SelectionContainer {
                    Text(
                        reports.joinToString("\n"),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(ModelsTags.REPORT),
                    )
                }
            }

            Text(stringResource(R.string.models_bench_how), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val dir = adbDir
            Text(
                if (dir != null) stringResource(R.string.models_bench_folder, dir) else stringResource(R.string.models_bench_no_storage),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(ModelsTags.FOLDER),
            )
            if (dir != null) {
                SelectionContainer {
                    Text(
                        ModelCatalog.ALL.joinToString("\n") { ModelFiles.adbCommand(it, dir) },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(stringResource(R.string.models_bench_files), style = MaterialTheme.typography.bodyMedium)
            SelectionContainer {
                Text(
                    ModelCatalog.ALL.joinToString("\n") { "${it.displayName} (${it.licence}${if (it.downloadable) "" else ", gated"}): ${it.url}" },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun device() = BenchDevice(
    manufacturer = Build.MANUFACTURER ?: "",
    model = Build.MODEL ?: "",
    soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else null,
    android = Build.VERSION.RELEASE ?: "",
    sdk = Build.VERSION.SDK_INT,
    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
)

private fun gigabytes(bytes: Long) = String.format(Locale.ROOT, "%.2f GB", bytes / 1e9)

@Composable
private fun statusText(status: Status, copied: Boolean?): String = when {
    copied == true -> stringResource(R.string.models_bench_copied)
    copied == false -> stringResource(R.string.models_bench_copy_failed)
    else -> when (status) {
        Status.Idle -> ""
        is Status.Verifying -> stringResource(R.string.models_bench_verifying, status.percent)
        Status.Loading -> stringResource(R.string.models_bench_loading)
        is Status.Generating -> stringResource(R.string.models_bench_generating, status.chunks)
        Status.Done -> stringResource(R.string.models_bench_done)
        Status.WrongSize -> stringResource(R.string.models_bench_wrong_size)
        Status.WrongHash -> stringResource(R.string.models_bench_wrong_hash)
        is Status.Failed -> stringResource(R.string.models_bench_failed, status.message)
    }
}
