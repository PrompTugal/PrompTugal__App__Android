package ai.riskmandate.app.qa

import ai.riskmandate.app.features.workbench.LogEntry
import ai.riskmandate.app.features.workbench.RunLogStore
import ai.riskmandate.app.llm.StrictJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The Workbench run log (QA; plan WB-L): bounded, strict, app-private, no identifiers, nothing half-read. */
class WorkbenchRunLogTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun dir() = File(tmp.root, RunLogStore.DIR)

    private fun entry(t: Long, prompt: String = "p", answer: String = "a", label: String = "run") = LogEntry(
        t, label, "gemma4e2b", "gpu", false, 0.7, 40L, 0.95, 256L, 12L, 0L, "DONE", "", true, 1200L, 300L, 4000L, 50L, 80L, 5.5, 120.0,
        "runtime/runtime", 0L, 0L, prompt, answer,
    )

    @Test fun anEntryRoundTripsWithEveryField() {
        val e = entry(7L, prompt = "Say hello", answer = "Hello there")
        assertEquals(e, LogEntry.parse(e.toJson()))
        val nulls = e.copy(loadMs = null, firstTokenMs = null, totalMs = null, inputTokens = null, outputTokens = null, decodeTps = null, prefillTps = null)
        assertEquals(nulls, LogEntry.parse(nulls.toJson()))
    }

    @Test fun nothingThatIdentifiesTheDeviceOrPersonIsInTheRecord() {
        val keys = (StrictJson.parse(entry(1L).toJson()) as Map<*, *>).keys.map { it.toString() }.toSet()
        assertEquals(
            setOf("v", "t", "label", "model", "backend", "fell", "temp", "topk", "topp", "max", "sys", "tools", "stop", "err", "cold", "load", "ttft", "total", "in", "out", "dec", "pre", "src", "calls", "ok", "prompt", "answer"),
            keys,
        )
    }

    @Test fun promptAnswerAndLabelAreCutOnReadAndAnOversizeLineIsDropped() {
        val e = LogEntry.parse(entry(1L, prompt = "p".repeat(900), answer = "a".repeat(900), label = "l".repeat(200)).toJson())!!
        assertEquals(LogEntry.PROMPT_CHARS, e.prompt.length); assertEquals(LogEntry.ANSWER_CHARS, e.answer.length); assertEquals(40, e.label.length)
        assertNull("a line over the read limit is dropped, not truncated", LogEntry.parse(entry(1L, prompt = "p".repeat(9000)).toJson()))
    }

    @Test fun oneLinePerEntryWhateverTheTextHolds() {
        val store = RunLogStore(dir())
        val nasty = "line1\nline2\r\n{\"v\":1,\"t\":99}\u0000  \"quoted\" \\ back 😀"
        store.append(entry(1L, prompt = nasty, answer = nasty))
        assertEquals("one entry is one line: a newline in the text cannot forge a second entry", 1, File(dir(), RunLogStore.FILE).readLines().size)
        val back = store.load().single()
        assertEquals(nasty.take(LogEntry.PROMPT_CHARS), back.prompt)
    }

    @Test fun newestFirstOnScreenAndBoundedOnDisk() {
        val store = RunLogStore(dir())
        for (i in 1..(RunLogStore.MAX_ENTRIES + 120)) store.append(entry(i.toLong()))
        val loaded = store.load()
        assertEquals(RunLogStore.MAX_ENTRIES, loaded.size)
        assertEquals("newest first", (RunLogStore.MAX_ENTRIES + 120).toLong(), loaded.first().startedAtMs)
        assertEquals("the oldest were dropped", 121L, loaded.last().startedAtMs)
        assertTrue("the file stays bounded", File(dir(), RunLogStore.FILE).readLines().size <= RunLogStore.MAX_ENTRIES + 60)
    }

    @Test fun damagedLinesAreDroppedNeverHalfReadAndNeverCrash() {
        val store = RunLogStore(dir())
        store.append(entry(1L)); store.append(entry(2L))
        val f = File(dir(), RunLogStore.FILE)
        val good = entry(3L).toJson()
        f.appendText(good.take(good.length / 2) + "\n")                  // a line cut by a crash
        f.appendText("not json at all\n\n   \n")
        f.appendText(good.replace("\"v\":1", "\"v\":2") + "\n")           // another version
        f.appendText(good.replace("\"temp\":0.7", "\"temp\":\"hot\"") + "\n") // the wrong type
        f.appendText(good + "\n")
        assertEquals(listOf(3L, 2L, 1L), store.load().map { it.startedAtMs })
    }

    @Test fun aMissingOrEmptyOrHugeFileReadsAsAnEmptyLog() {
        val store = RunLogStore(dir())
        assertEquals(emptyList<LogEntry>(), store.load())
        dir().mkdirs(); File(dir(), RunLogStore.FILE).writeText("")
        assertEquals(emptyList<LogEntry>(), store.load())
        File(dir(), RunLogStore.FILE).writeBytes(ByteArray(5 * 1024 * 1024) { '{'.code.toByte() })
        assertEquals("an oversize file is not read at all", emptyList<LogEntry>(), store.load())
    }

    @Test fun clearDeletesTheFileAndATwiceClearIsHarmless() {
        val store = RunLogStore(dir())
        store.append(entry(1L))
        assertTrue(File(dir(), RunLogStore.FILE).isFile)
        store.clear(); store.clear()
        assertFalse(File(dir(), RunLogStore.FILE).exists())
        assertEquals(emptyList<LogEntry>(), store.load())
        store.append(entry(2L)); assertEquals(1, store.load().size)
    }

    @Test fun theLogLivesInTheNoBackupFolderAndNothingElseIsWritten() {
        val feature = File("src/main/java/ai/riskmandate/app/features/workbench/WorkbenchFeature.kt").readText()
        assertTrue(feature.contains("noBackupFilesDir") && feature.contains("RunLogStore.DIR"))
        val store = RunLogStore(dir()); store.append(entry(1L))
        assertEquals(listOf(RunLogStore.FILE), dir().list()!!.toList())
        assertEquals("feature_workbench", RunLogStore.DIR)
    }
}
