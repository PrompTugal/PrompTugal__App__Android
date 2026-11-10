package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.R
import ai.riskmandate.app.copyToClipboard
import ai.riskmandate.app.llm.LlmLocation
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.LlmState
import ai.riskmandate.app.llm.knowledge.Bm25Index
import ai.riskmandate.app.llm.knowledge.EvalQuestion
import ai.riskmandate.app.llm.knowledge.EvalResult
import ai.riskmandate.app.llm.knowledge.GroundedAnswer
import ai.riskmandate.app.llm.knowledge.GroundedAnswerer
import ai.riskmandate.app.llm.knowledge.Knowledge
import ai.riskmandate.app.llm.knowledge.KnowledgeEval
import ai.riskmandate.app.llm.knowledge.KnowledgeKind
import ai.riskmandate.app.ui.ChipKind
import ai.riskmandate.app.ui.StatRow
import ai.riskmandate.app.ui.StatusChip
import ai.riskmandate.app.ui.TitledCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AskTags {
    const val QUESTION = "ask_question"
    const val ASK = "ask_go"
    const val STOP = "ask_stop"
    const val ANSWER = "ask_answer"
    const val SOURCES = "ask_sources"
    const val TTFT = "ask_ttft"
    const val EVAL = "ask_eval"
    const val EVAL_SUMMARY = "ask_eval_summary"
    const val NO_MODEL = "ask_no_model"
    const val LOADING = "ask_loading"
    fun k(n: Int) = "ask_k_$n"
    fun source(n: Int) = "ask_source_$n"
}

/** What the tab needs from the bundle: the index, the system block and the eval questions. */
internal class AskData(val index: Bm25Index, val system: String, val eval: List<EvalQuestion>)

/**
 * Tab 4, "Our data": the on-device model answers over the bundled data (llm/knowledge: RiskMandate's riskmandate.ai
 * quotes and a pt.news snapshot), with numbered sources and the items it cited, and no network. Always the
 * on-device [llm], whatever the Run tab chose: this is the offline path. Measures time to first token, and runs the
 * bundled eval (sources found, answers right, median time to first token) for the tablet's numbers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AskTab(
    llm: LlmService?,
    io: CoroutineDispatcher,
    onOpenModels: () -> Unit,
    onRunning: (Boolean) -> Unit,
    load: (suspend () -> AskData)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<AskData?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            data = load?.invoke() ?: withContext(io) {
                val k = Knowledge.load(context.assets)
                AskData(Bm25Index(k), GroundedAnswerer.systemBlock(context.assets), KnowledgeEval.load(context.assets, k))
            }
        } catch (e: java.io.IOException) {
            loadError = e.message ?: "the bundled data did not load"
        }
    }
    var question by rememberSaveable { mutableStateOf("") }
    var k by rememberSaveable { mutableIntStateOf(4) }
    var streaming by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<GroundedAnswer?>(null) }
    var evalDone by remember { mutableStateOf<List<EvalResult>>(emptyList()) }
    var evalTotal by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    val running = job != null
    val state = llm?.state?.collectAsState()?.value
    val noModel = llm == null || state is LlmState.NoModel
    // Offline means the device: a cloud service here would break the promise, so it is never used (R-wb-9's rule).
    val service = llm?.takeIf { it.location == LlmLocation.LOCAL }

    fun launchTracked(block: suspend () -> Unit) {
        onRunning(true)
        val j = scope.launch { block() }
        job = j
        j.invokeOnCompletion { if (job === j) job = null; onRunning(false) }
    }

    fun answerer(d: AskData) = GroundedAnswerer(service!!, d.index, d.system)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TitledCard(stringResource(R.string.ask_title), kicker = stringResource(R.string.ask_kicker), accessory = {
            StatusChip(stringResource(R.string.ask_offline), ChipKind.Allowed)
        }) {
            Text(stringResource(R.string.ask_about), style = MaterialTheme.typography.bodyMedium)
            val d = data
            when {
                loadError != null -> Text(loadError!!, color = MaterialTheme.colorScheme.error)
                d == null -> Text(stringResource(R.string.ask_loading), modifier = Modifier.testTag(AskTags.LOADING))
                else -> Text(stringResource(R.string.ask_bundle, d.index.knowledge.items.count { it.kind == KnowledgeKind.ARTICLE },
                    d.index.knowledge.items.count { it.kind == KnowledgeKind.COMPANY }, d.index.size), style = MaterialTheme.typography.bodySmall)
            }
            if (noModel || service == null) {
                Text(stringResource(R.string.ask_no_model), modifier = Modifier.testTag(AskTags.NO_MODEL))
                TextButton(onClick = onOpenModels, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.workbench_model_change)) }
            }
            OutlinedTextField(
                value = question, onValueChange = { question = it.take(500) }, enabled = !running,
                label = { Text(stringResource(R.string.ask_question)) }, modifier = Modifier.fillMaxWidth().testTag(AskTags.QUESTION),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 4, 6).forEach { n ->
                    FilterChip(selected = k == n, enabled = !running, onClick = { k = n }, label = { Text(stringResource(R.string.ask_k, n)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag(AskTags.k(n)))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) {
                    OutlinedButton(onClick = { job?.cancel() }, modifier = Modifier.heightIn(min = 48.dp).testTag(AskTags.STOP)) { Text(stringResource(R.string.workbench_stop)) }
                } else {
                    val ready = d != null && service != null && !noModel
                    Button(
                        onClick = {
                            val dd = d ?: return@Button
                            streaming = ""; answer = null
                            launchTracked { answer = answerer(dd).ask(question, k) { t, _ -> streaming = t } }
                        },
                        enabled = ready && question.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp).testTag(AskTags.ASK),
                    ) { Text(stringResource(R.string.ask_go)) }
                    OutlinedButton(
                        onClick = {
                            val dd = d ?: return@OutlinedButton
                            evalDone = emptyList(); evalTotal = dd.eval.size
                            launchTracked {
                                val a = answerer(dd)
                                for (q in dd.eval) {
                                    val r = a.ask(q.q, k)
                                    evalDone = evalDone + EvalResult(q, r)
                                }
                            }
                        },
                        enabled = ready, modifier = Modifier.heightIn(min = 48.dp).testTag(AskTags.EVAL),
                    ) { Text(stringResource(R.string.ask_eval, data?.eval?.size ?: 0)) }
                }
            }
        }

        val shown = answer
        if (shown != null || streaming.isNotEmpty()) {
            TitledCard(stringResource(R.string.ask_answer_title), kicker = stringResource(R.string.workbench_where_device)) {
                Text(shown?.text ?: streaming, modifier = Modifier.testTag(AskTags.ANSWER).semantics { liveRegion = LiveRegionMode.Polite })
                if (shown != null) {
                    StatRow(stringResource(R.string.ask_ttft), shown.firstTokenMs?.let { "$it ms" } ?: "-", source = stringResource(R.string.workbench_src_app), modifier = Modifier.testTag(AskTags.TTFT))
                    StatRow(stringResource(R.string.ask_search), "${shown.retrievalMs} ms", source = stringResource(R.string.workbench_src_app))
                    StatRow(stringResource(R.string.ask_total), "${shown.totalMs} ms", source = stringResource(R.string.workbench_src_app))
                    shown.stats?.decodeTokensPerSecond?.let { StatRow(stringResource(R.string.ask_decode), String.format(Locale.ROOT, "%.1f tok/s", it)) }
                    if (shown.citations.invalid.isNotEmpty()) Text(stringResource(R.string.ask_bad_citations, shown.citations.invalid.joinToString()), color = MaterialTheme.colorScheme.error)
                    if (shown.citations.valid.isEmpty()) Text(stringResource(R.string.ask_no_citations), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (shown != null) {
                TitledCard(stringResource(R.string.ask_sources_title), modifier = Modifier.testTag(AskTags.SOURCES)) {
                    if (shown.sources.size == 0) Text(stringResource(R.string.ask_no_sources))
                    shown.sources.hits.forEachIndexed { i, h ->
                        val n = i + 1
                        val cited = n in shown.citations.valid
                        Column(Modifier.testTag(AskTags.source(n))) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.ask_source_line, n, h.item.title), style = MaterialTheme.typography.titleSmall)
                                if (cited) StatusChip(stringResource(R.string.ask_cited), ChipKind.Allowed)
                            }
                            // Plain text: the source is shown, never opened (no network on this path).
                            Text(stringResource(kindLabel(h.item.kind)) + " · " + h.item.source + (h.item.date?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (evalTotal > 0) {
            TitledCard(stringResource(R.string.ask_eval_title), accessory = {
                TextButton(onClick = { copyToClipboard(context, "eval", KnowledgeEval.report(evalDone, k, (state as? LlmState.Ready)?.modelId)) }, enabled = evalDone.isNotEmpty()) {
                    Text(stringResource(R.string.ask_copy))
                }
            }) {
                val firsts = evalDone.mapNotNull { it.answer.firstTokenMs }.sorted()
                Text(
                    stringResource(R.string.ask_eval_summary, evalDone.size, evalTotal, evalDone.count { it.found }, evalDone.count { it.right },
                        firsts.getOrNull(firsts.size / 2)?.toString() ?: "-"),
                    modifier = Modifier.testTag(AskTags.EVAL_SUMMARY).semantics { liveRegion = LiveRegionMode.Polite },
                )
                evalDone.forEachIndexed { i, r ->
                    Text(stringResource(R.string.ask_eval_line, i + 1, stringResource(if (r.right) R.string.ask_right else R.string.ask_wrong), r.answer.firstTokenMs?.toString() ?: "-", r.question.q), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun kindLabel(k: KnowledgeKind) = when (k) {
    KnowledgeKind.RISKMANDATE -> R.string.ask_kind_riskmandate
    KnowledgeKind.ARTICLE -> R.string.ask_kind_article
    KnowledgeKind.COMPANY -> R.string.ask_kind_company
}
