package ai.riskmandate.app.features.workbench

import ai.riskmandate.app.llm.FakeLlmService
import ai.riskmandate.app.llm.FakeReply
import ai.riskmandate.app.llm.LlmRunner
import ai.riskmandate.app.llm.RunSettings
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RunLogTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun entry(prompt: String = "Say hi", answer: String = "Hello") = runBlocking {
        val r = LlmRunner(FakeLlmService { FakeReply(answer) }).run(RunSettings(userPrompt = prompt, systemPrompt = "S")) {}
        LogEntry.of("run", r)
    }

    @Test fun anEntryRoundTripsThroughStrictJson() {
        val e = entry(prompt = "Quote \" and \\ and \n newline", answer = "é ünïcode")
        assertEquals(e, LogEntry.parse(e.toJson()))
        assertFalse(e.toJson().contains('\n'))
    }

    @Test fun promptAndAnswerAreKeptShort() {
        val e = entry(prompt = "p".repeat(5000), answer = "a ".repeat(2000))
        assertEquals(LogEntry.PROMPT_CHARS, e.prompt.length)
        assertEquals(LogEntry.ANSWER_CHARS, e.answer.length)
    }

    @Test fun badLinesAreDroppedNotHalfRead() {
        val good = entry().toJson()
        listOf("", "{}", "not json", good.replace("\"v\":1", "\"v\":2"), good.replace("\"topk\":64", "\"topk\":\"64\""), "{'v':1}", good + " x").forEach {
            assertNull(it, LogEntry.parse(it))
        }
    }

    @Test fun storeKeepsNewestFirstBoundedAndClears() {
        val store = RunLogStore(File(tmp.root, RunLogStore.DIR))
        assertTrue(store.load().isEmpty())
        val e1 = entry(prompt = "first")
        val e2 = entry(prompt = "second")
        store.append(e1)
        store.append(e2)
        File(tmp.root, "${RunLogStore.DIR}/${RunLogStore.FILE}").appendText("garbage line\n")
        assertEquals(listOf("second", "first"), store.load().map { it.prompt })
        repeat(RunLogStore.MAX_ENTRIES + 60) { store.append(e1) }
        assertTrue(File(tmp.root, "${RunLogStore.DIR}/${RunLogStore.FILE}").readLines().size <= RunLogStore.MAX_ENTRIES + 50)
        assertEquals(RunLogStore.MAX_ENTRIES, store.load().size)
        store.clear()
        assertTrue(store.load().isEmpty())
    }

    @Test fun aLogLineShowsSettingsAndFigures() {
        val l = logLine(entry())
        assertTrue(l, l.contains("run | fake fake t=1.0 p=0.95 max=256 sys | done | warm ttft=0ms"))
    }
}
