package ai.riskmandate.app.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * On-device LLM side team, plan I-6, H-2 and WB-X (QA owns this file): nothing the model proposes can reach a real
 * network, file, intent, process or account. The Lab's tools are fakes, the chat only talks to `LlmService`, the
 * Workbench and the tool-test runner use the same fakes. A static scan of the packages that hold those screens and
 * fake tools. A root that does not exist yet is skipped (it starts to bite with its first line of code).
 *
 * Two places are reviewed exceptions, each spelled out here and pinned by its own test, never a pattern:
 * - the model download (`llm/`) is guarded separately (DownloadPolicy, pinned hosts);
 * - `cloud/` (the OpenRouter client): only `cloud/CloudTransport.kt` may touch `java.net`, only through
 *   `HttpsURLConnection`, only after `CloudEndpoint.isAllowed(url)`, and it re-checks the host after the response.
 *   Every other file in `cloud/` is held to the full sandbox (architect's ruling, 9 Nov 2026).
 */
class LlmSandboxTest {
    private val app = File("src/main/java/ai/riskmandate/app")

    /** Packages that must stay entirely inside the sandbox. */
    private val roots = listOf(
        "features/chat", "features/lab", "features/workbench", "features/workbench/tooltests", "experiments",
    ).map { File(app, it) }

    private val network = Regex(
        """\bjava\.net\b|\bjavax\.net\b|\bHttpURLConnection\b|\bHttpsURLConnection\b|\bokhttp3\b|\bURL\(|\bSocket\(|\bServerSocket\b|\bDatagramSocket\b""",
    )
    private val others = Regex(
        """\bstartActivity(?:ForResult)?\(|\bACTION_VIEW\b|\bContentResolver\b|\bRuntime\.getRuntime\(\)|\bProcessBuilder\b|\bDexClassLoader\b|""" +
            """\bandroid\.webkit\b|\baddJavascriptInterface\b|\bSharedPreferences\b.*\bMODE_WORLD|\bFileOutputStream\(|\bsendBroadcast\(|\bstartService\(""",
    )

    private fun code(dir: File): Sequence<Pair<File, List<Pair<Int, String>>>> =
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { f ->
            f to f.readLines().mapIndexed { i, l -> (i + 1) to l.trim() }
                .filterNot { (_, c) -> c.startsWith("//") || c.startsWith("*") || c.startsWith("/*") }
        }

    @Test fun theScreensAndFakeToolsNeverTouchTheNetworkFilesIntentsOrProcesses() {
        val offenders = mutableListOf<String>()
        for (root in roots.filter { it.isDirectory }) for ((f, lines) in code(root)) for ((n, c) in lines) {
            if (network.containsMatchIn(c) || others.containsMatchIn(c)) offenders += "${f.path}:$n: $c"
        }
        assertEquals("these packages must stay in the sandbox (use LlmService and the fake tools)", emptyList<String>(), offenders)
    }

    @Test fun onlyTheCloudTransportTouchesTheNetworkAndOnlyThroughHttpsToThePinnedHost() {
        val cloud = File(app, "cloud")
        if (!cloud.isDirectory) return // not merged to this branch yet
        val offenders = mutableListOf<String>()
        for ((f, lines) in code(cloud)) for ((n, c) in lines) {
            val isTransport = f.name == "CloudTransport.kt"
            if (others.containsMatchIn(c)) offenders += "${f.path}:$n: $c"
            // Everywhere in cloud/: no cleartext, no raw sockets, no OkHttp, no plain HttpURLConnection.
            if (Regex("""\"http://|\bokhttp3\b|\bSocket\(|\bServerSocket\b|\bDatagramSocket\b|\bHttpURLConnection\b""").containsMatchIn(c)) offenders += "${f.path}:$n: $c"
            // The rest of java.net is allowed in the transport file only.
            if (!isTransport && network.containsMatchIn(c)) offenders += "${f.path}:$n: $c"
        }
        assertEquals("cloud/ breaks its reviewed allowance", emptyList<String>(), offenders)
    }

    @Test fun theCloudTransportChecksThePinnedHostBeforeAndAfterTheRequest() {
        val transport = File(app, "cloud/CloudTransport.kt")
        val endpoint = File(app, "cloud/CloudEndpoint.kt")
        if (!transport.isFile) return
        val t = transport.readText()
        assertTrue("the transport must check CloudEndpoint.isAllowed(url) before it connects", t.contains("CloudEndpoint.isAllowed("))
        assertTrue("the transport must check the host again after the response", Regex("""connection\.url\.host\s*!=\s*CloudEndpoint\.HOST""").containsMatchIn(t))
        assertTrue("the transport must not follow redirects", t.contains("instanceFollowRedirects = false"))
        assertTrue("the transport must use HttpsURLConnection", t.contains("HttpsURLConnection"))
        assertTrue("CloudEndpoint.kt is missing", endpoint.isFile)
        assertTrue("the pinned host must stay openrouter.ai", Regex("""const val HOST\s*=\s*"openrouter\.ai"""").containsMatchIn(endpoint.readText()))
    }
}
