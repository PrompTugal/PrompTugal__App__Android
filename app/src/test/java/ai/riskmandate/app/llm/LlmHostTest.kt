package ai.riskmandate.app.llm

import android.content.ComponentCallbacks2
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LlmHostTest {
    @get:Rule val tmp = TemporaryFolder()

    private val bytesA = ByteArray(4096) { 1 }
    private val bytesB = ByteArray(2048) { 2 }
    private fun spec(id: String, file: String, b: ByteArray) =
        ModelCatalog.GEMMA4_E2B.copy(id = id, fileName = file, sizeBytes = b.size.toLong(), sha256 = ModelFiles.sha256(b.inputStream()))
    private val specA = spec("modela", "a.litertlm", bytesA)
    private val specB = spec("modelb", "b.litertlm", bytesB)

    /** Records loads and closes; the GPU can be made to fail; answers "hi" token by token. */
    private class FakeRuntime(var gpuFails: Boolean = false, var allFail: Boolean = false, val tokenDelay: Long = 0) : LlmRuntime {
        val loads = mutableListOf<Pair<String, BenchBackend>>()
        var open = 0
        var cancelled = 0
        override fun load(file: File, backend: BenchBackend, maxTokens: Int): LoadedModel {
            loads += file.name to backend
            if (allFail || (gpuFails && backend == BenchBackend.GPU)) throw IllegalStateException("no $backend")
            open++
            return object : LoadedModel {
                override val backend = backend
                override fun stream(request: LlmRequest, modelId: String): Flow<LlmEvent> = flow {
                    try {
                        repeat(if (tokenDelay > 0) 1000 else 2) {
                            if (tokenDelay > 0) delay(tokenDelay)
                            emit(LlmEvent.Token("hi"))
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        cancelled++
                        throw e
                    }
                    emit(LlmEvent.Done(LlmStats(modelId, backend.name, 3, 2, null, null, 1, 2)))
                }
                override fun close() { open-- }
            }
        }
    }

    private val ask = LlmRequest(listOf(LlmMessage.user("hello")))
    private fun install(spec: ModelSpec, b: ByteArray) = File(tmp.root, spec.fileName).apply { writeBytes(b) }
    private fun host(rt: LlmRuntime, idleMs: Long = 60_000) = LlmHost({ tmp.root }, rt, listOf(specA, specB), idleMs)

    @Test fun noModelMeansAnErrorAndTheNoModelState() = runBlocking {
        val h = host(FakeRuntime())
        h.refresh()
        assertEquals(LlmState.NoModel, h.state.value)
        assertEquals(LlmError.NO_MODEL, h.generate(ask).error?.reason)
    }

    @Test fun loadsLazilyOnGpuThenAnswers() = runBlocking {
        install(specA, bytesA)
        val rt = FakeRuntime()
        val h = host(rt)
        h.refresh()
        assertEquals(LlmState.Idle("modela"), h.state.value)
        assertTrue(rt.loads.isEmpty())
        val r = h.generate(ask)
        assertEquals("hihi", r.text)
        assertEquals(true, r.stats?.loadedModel)
        assertEquals(LlmState.Ready("modela", "GPU"), h.state.value)
        assertEquals(false, h.generate(ask).stats?.loadedModel)
        assertEquals(listOf("a.litertlm" to BenchBackend.GPU), rt.loads)
        assertTrue(ModelFiles.isMarkedVerified(File(tmp.root, "a.litertlm"), specA))
    }

    @Test fun fallsBackToCpuWhenTheGpuFails() = runBlocking {
        install(specA, bytesA)
        val h = host(FakeRuntime(gpuFails = true))
        assertEquals("hihi", h.generate(ask).text)
        assertEquals(LlmState.Ready("modela", "CPU"), h.state.value)
    }

    @Test fun theFallbackToCpuIsReported() = runBlocking {
        install(specA, bytesA)
        val r = host(FakeRuntime(gpuFails = true)).generate(ask)
        assertEquals("CPU", r.stats?.backend)
        assertEquals("AUTO", r.stats?.requestedBackend)
        assertEquals(true, r.stats?.fellBackToCpu)
        assertEquals(true, r.stats?.loadedModel)
    }

    @Test fun theRequestsBackendIsHonouredAndReloadsOnlyWhenItMust() = runBlocking {
        install(specA, bytesA)
        val rt = FakeRuntime()
        val h = host(rt)
        h.generate(ask)
        assertEquals(listOf("a.litertlm" to BenchBackend.GPU), rt.loads)
        assertEquals(false, h.generate(ask.copy(backend = BackendPreference.GPU)).stats?.loadedModel)
        val cpu = h.generate(ask.copy(backend = BackendPreference.CPU))
        assertEquals(true, cpu.stats?.coldLoad)
        assertEquals("CPU", cpu.stats?.backendUsed)
        assertEquals("CPU", cpu.stats?.requestedBackend)
        assertEquals(false, cpu.stats?.fellBack)
        assertEquals(1, rt.open)
        // AUTO after an explicit CPU load tries the GPU again (no false "fell back").
        val auto = h.generate(ask)
        assertEquals(true, auto.stats?.coldLoad)
        assertEquals("GPU", auto.stats?.backendUsed)
        assertEquals(false, auto.stats?.fellBack)
        assertEquals(listOf(BenchBackend.GPU, BenchBackend.CPU, BenchBackend.GPU), rt.loads.map { it.second })
    }

    @Test fun aFallenBackModelIsReusedAndStillSaysSo() = runBlocking {
        install(specA, bytesA)
        val rt = FakeRuntime(gpuFails = true)
        val h = host(rt)
        h.generate(ask)
        val again = h.generate(ask.copy(backend = BackendPreference.GPU))
        assertEquals(false, again.stats?.coldLoad)
        assertEquals(true, again.stats?.fellBack)
        assertEquals(2, rt.loads.size)
    }

    @Test fun unloadMakesTheNextRunCold() = runBlocking {
        install(specA, bytesA)
        val h = host(FakeRuntime())
        h.generate(ask)
        val service: LlmService = h
        service.unload()
        assertEquals(true, h.generate(ask).stats?.coldLoad)
    }

    @Test fun loadFailureIsAStateAndAnErrorNotACrash() = runBlocking {
        install(specA, bytesA)
        val h = host(FakeRuntime(allFail = true))
        assertEquals(LlmError.LOAD_FAILED, h.generate(ask).error?.reason)
        assertTrue(h.state.value is LlmState.Failed)
    }

    @Test fun aTamperedFileIsDeletedAndNeverLoaded() = runBlocking {
        install(specA, ByteArray(bytesA.size) { 9 })
        val rt = FakeRuntime()
        val h = host(rt)
        assertEquals(LlmError.LOAD_FAILED, h.generate(ask).error?.reason)
        assertTrue(rt.loads.isEmpty())
        assertFalse(File(tmp.root, "a.litertlm").exists())
    }

    @Test fun aChangedFileIsHashedAgainDespiteItsMarker() = runBlocking {
        val f = install(specA, bytesA)
        assertEquals(Verdict.OK, ModelFiles.verifyOnce(f, specA))
        f.writeBytes(ByteArray(bytesA.size) { 7 })
        f.setLastModified(f.lastModified() + 5_000)
        assertFalse(ModelFiles.isMarkedVerified(f, specA))
        assertEquals(Verdict.WRONG_HASH, ModelFiles.verifyOnce(f, specA))
    }

    @Test fun preferredModelWinsAndSwitchingUnloadsTheOld() = runBlocking {
        install(specA, bytesA)
        install(specB, bytesB)
        val rt = FakeRuntime()
        val h = host(rt)
        h.generate(ask)
        assertEquals(LlmState.Ready("modela", "GPU"), h.state.value)
        h.preferredModelId = "modelb"
        h.refresh()
        assertEquals(0, rt.open)
        h.generate(ask)
        assertEquals(LlmState.Ready("modelb", "GPU"), h.state.value)
        assertEquals(1, rt.open)
    }

    @Test fun unloadsWhenIdleAndOnMemoryPressure() = runBlocking {
        install(specA, bytesA)
        val rt = FakeRuntime()
        val h = host(rt, idleMs = 50)
        h.generate(ask)
        withTimeout(2_000) { h.state.first { it is LlmState.Idle } }
        assertEquals(0, rt.open)
        h.generate(ask)
        assertEquals(1, rt.open)
        h.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        withTimeout(2_000) { h.state.first { it is LlmState.Idle } }
        assertEquals(0, rt.open)
    }

    @Test fun cancellingTheCollectorCancelsTheAnswerAndFreesTheLock() = runBlocking {
        install(specA, bytesA)
        val rt = FakeRuntime(tokenDelay = 5)
        val h = host(rt)
        assertEquals(3, withTimeout(5_000) { h.stream(ask).take(3).toList() }.size)
        withTimeout(2_000) { while (rt.cancelled == 0) delay(5) }
        // The next request is served, so the lock was released.
        assertEquals(2, withTimeout(5_000) { h.stream(ask).take(2).toList() }.size)
    }

    @Test fun refusesWhatCannotFit() = runBlocking {
        install(specA, bytesA)
        val h = LlmHost({ tmp.root }, FakeRuntime(), listOf(specA), maxTokens = 10)
        assertEquals(LlmError.TOO_LONG, h.generate(LlmRequest(listOf(LlmMessage.user("x".repeat(61))))).error?.reason)
    }

    @Test fun runtimeContextOverflowIsTooLong() = runBlocking {
        install(specA, bytesA)
        val rt = object : LlmRuntime {
            override fun load(file: File, backend: BenchBackend, maxTokens: Int) = object : LoadedModel {
                override val backend = backend
                override fun stream(request: LlmRequest, modelId: String): Flow<LlmEvent> = flow {
                    throw IllegalStateException("Input token ids are too long. Exceeding the maximum number of tokens allowed: 9000 >= 8192")
                }
                override fun close() {}
            }
        }
        val h = LlmHost({ tmp.root }, rt, listOf(specA))
        assertEquals(LlmError.TOO_LONG, h.generate(ask).error?.reason)
        assertFalse(h.isContextOverflow(IllegalStateException("GPU delegate failed")))
        assertFalse(h.isContextOverflow(UnsatisfiedLinkError("dlopen failed: liblitertlm_jni.so")))
    }

    @Test fun onlyTheInternalDownloadableCatalogueIsServedByDefault() {
        assertTrue(ModelCatalog.ALL.filter { it.downloadable }.all { it.licence == "apache-2.0" })
    }
}
