package ai.riskmandate.app.qa

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * On-device LLM side team, plan I-6 and H-2 (QA owns this file): nothing the model proposes can reach a real
 * network, file, intent, process or account. The Lab's tools are fakes, the chat only talks to `LlmService`.
 * A static scan of the packages that hold the screens and the fake tools. Vacuous while the packages are
 * placeholders; it starts to bite with the first line of real code. The model download (llm/) is the one place with
 * a network call and is guarded separately.
 */
class LlmSandboxTest {
    private val roots = listOf("chat", "lab").map { File("src/main/java/ai/riskmandate/app/features/$it") }

    private val forbidden = Regex(
        """\bjava\.net\b|\bHttpURLConnection\b|\bHttpsURLConnection\b|\bokhttp3\b|\bjava\.net\.URL\b|\bURL\(|\bSocket\(|\bServerSocket\b|""" +
            """\bstartActivity(?:ForResult)?\(|\bACTION_VIEW\b|\bContentResolver\b|\bRuntime\.getRuntime\(\)|\bProcessBuilder\b|\bDexClassLoader\b|""" +
            """\bandroid\.webkit\b|\baddJavascriptInterface\b|\bSharedPreferences\b.*\bMODE_WORLD|\bFileOutputStream\(|\bsendBroadcast\(|\bstartService\(""",
    )

    @Test fun chatAndLabNeverTouchTheNetworkFilesIntentsOrProcesses() {
        val offenders = mutableListOf<String>()
        for (root in roots) {
            if (!root.isDirectory) continue
            for (f in root.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
                f.readLines().forEachIndexed { i, line ->
                    val code = line.trim()
                    if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return@forEachIndexed
                    if (forbidden.containsMatchIn(code)) offenders += "${f.path}:${i + 1}: $code"
                }
            }
        }
        assertEquals("chat and lab must stay in the sandbox (use LlmService and the fake tools)", emptyList<String>(), offenders)
    }
}
