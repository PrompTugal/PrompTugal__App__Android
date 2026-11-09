package ai.riskmandate.app.llm

import android.content.ComponentCallbacks2
import android.content.Context
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one [LlmService] per process (architect: "the one sanctioned process-wide object"). Held by app, handed to
 * features through `FeatureContext.llm`. It:
 * - picks the installed model (only files in the internal folder, [ModelFiles.internalDir], that pass the pinned
 *   size and SHA-256; a valid verified marker skips re-hashing, security G.3);
 * - loads lazily on the first request, GPU first and CPU if the GPU load fails;
 * - serves one request at a time (a Mutex) on its own single-threaded dispatcher, so every call is main-safe;
 * - unloads after [idleMs] without requests and on memory pressure ([onTrimMemory]); the next request reloads.
 *
 * Construct with [create] in the app; the primary constructor is for tests (fake runtime, temp folders).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LlmHost(
    private val modelDir: () -> File,
    private val runtime: LlmRuntime,
    private val catalog: List<ModelSpec> = ModelCatalog.ALL.filter { it.downloadable },
    private val idleMs: Long = IDLE_MS,
    private val maxTokens: Int = CONTEXT_TOKENS,
    dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : LlmService {
    private val dispatcher = dispatcher
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lock = Mutex()
    private val _state = MutableStateFlow<LlmState>(LlmState.NoModel)
    override val state: StateFlow<LlmState> = _state.asStateFlow()

    private var loaded: LoadedModel? = null
    private var loadedId: String? = null
    private var idleJob: Job? = null

    /** The model to use when more than one is installed (feature G's choice). Null: the first in catalogue order. */
    @Volatile var preferredModelId: String? = null

    /** Rescans installed models and updates [state]. Call after feature G installs or deletes a file. */
    suspend fun refresh() = withContext(dispatcher) {
        lock.withLock {
            val pick = installed()
            if (loadedId != null && loadedId != pick?.spec?.id) unloadLocked()
            if (loaded == null) _state.value = pick?.let { LlmState.Idle(it.spec.id) } ?: LlmState.NoModel
        }
    }

    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        lock.withLock {
            idleJob?.cancel()
            try {
                if (request.messages.sumOf { it.text.length } > maxTokens * CHARS_PER_TOKEN_CEILING) {
                    emit(LlmEvent.Error(LlmError.TOO_LONG, "the conversation is longer than the model's context"))
                    return@withLock
                }
                val loadStart = System.nanoTime()
                val wasLoaded = loaded != null
                val (model, id) = ensureLoaded() ?: run {
                    val s = _state.value
                    emit(
                        if (s is LlmState.Failed) LlmEvent.Error(LlmError.LOAD_FAILED, s.reason)
                        else LlmEvent.Error(LlmError.NO_MODEL, "no verified model is installed"),
                    )
                    return@withLock
                }
                val loadMs = if (wasLoaded) null else (System.nanoTime() - loadStart) / 1_000_000
                try {
                    model.stream(request, id).collect { e ->
                        emit(if (e is LlmEvent.Done) LlmEvent.Done(e.stats.copy(loadedModel = loadMs != null, loadMs = loadMs)) else e)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    // Throwable, not Exception: the native runtime can throw an Error (qa Q-llm-1).
                    emit(LlmEvent.Error(LlmError.INFERENCE_FAILED, describe(t)))
                }
            } finally {
                scheduleIdleUnload()
            }
        }
    }.flowOn(dispatcher)

    /** From the app's `onTrimMemory`: unloads when the app is in the background or memory is critical. */
    fun onTrimMemory(level: Int) {
        @Suppress("DEPRECATION")
        val critical = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (critical) scope.launch { lock.withLock { unloadLocked() } }
    }

    /** Unloads now (waits for a running request to finish). */
    suspend fun unload() = withContext(dispatcher) { lock.withLock { unloadLocked() } }

    private fun scheduleIdleUnload() {
        idleJob?.cancel()
        if (loaded == null) return
        idleJob = scope.launch {
            delay(idleMs)
            lock.withLock { unloadLocked() }
        }
    }

    private fun unloadLocked() {
        val m = loaded ?: return
        val id = loadedId
        loaded = null
        loadedId = null
        try { m.close() } catch (_: Throwable) {}
        _state.value = id?.let { LlmState.Idle(it) } ?: LlmState.NoModel
    }

    /** Under [lock]. Returns the loaded model, or null with [state] set to NoModel or Failed. */
    private fun ensureLoaded(): Pair<LoadedModel, String>? {
        val pick = installed()
        if (pick == null) {
            unloadLocked()
            _state.value = LlmState.NoModel
            return null
        }
        val current = loaded
        if (current != null && loadedId == pick.spec.id) return current to pick.spec.id
        unloadLocked()
        if (ModelFiles.verifyOnce(pick.file, pick.spec) != Verdict.OK) {
            _state.value = LlmState.Failed(pick.spec.id, "the model file failed its SHA-256 check and was removed; set it up again")
            return null
        }
        _state.value = LlmState.Loading(pick.spec.id)
        var lastError: Throwable? = null
        for (backend in listOf(BenchBackend.GPU, BenchBackend.CPU)) {
            try {
                val m = runtime.load(pick.file, backend, maxTokens)
                loaded = m
                loadedId = pick.spec.id
                _state.value = LlmState.Ready(pick.spec.id, m.backend.name)
                return m to pick.spec.id
            } catch (t: Throwable) {
                // Creating an engine loads the native library: UnsatisfiedLinkError is an Error (qa Q-llm-1).
                lastError = t
            }
        }
        _state.value = LlmState.Failed(pick.spec.id, lastError?.let(::describe) ?: "could not load the model")
        return null
    }

    /** The model to use: the preferred one if installed, else the first installed in catalogue order. */
    private fun installed(): FoundModel? {
        val dir = modelDir()
        val found = catalog.mapNotNull { spec -> File(dir, spec.fileName).takeIf { it.isFile }?.let { FoundModel(spec, it) } }
        return found.firstOrNull { it.spec.id == preferredModelId } ?: found.firstOrNull()
    }

    private fun describe(t: Throwable) = "${t.javaClass.simpleName}: ${t.message ?: "no message"}".take(300)

    companion object {
        /** Unload after this long without a request. */
        const val IDLE_MS = 5 * 60_000L
        /** The context the engine is sized for (brief: aim for 4K to 8K tokens). */
        const val CONTEXT_TOKENS = 8192
        /** Refuse up front only what cannot possibly fit; the runtime refuses the rest. */
        private const val CHARS_PER_TOKEN_CEILING = 6

        /** The app's instance: internal model folder, LiteRT-LM, the downloadable catalogue. */
        fun create(context: Context): LlmHost {
            val app = context.applicationContext
            return LlmHost(
                modelDir = { ModelFiles.internalDir(app) },
                runtime = LiteRtRuntime(File(app.cacheDir, ModelFiles.DIR)),
            )
        }
    }
}
