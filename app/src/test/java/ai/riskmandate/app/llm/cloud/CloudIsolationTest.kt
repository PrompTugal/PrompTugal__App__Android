package ai.riskmandate.app.llm.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Static guards for llm/cloud/ (security checklist: one network destination, a secret that is never logged). */
class CloudIsolationTest {
    private val files = File("src/main/java/ai/riskmandate/app/llm/cloud").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun code(f: File) = f.readLines().map { it.trim() }.filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    @Test fun onlyTheTransportFileTouchesTheNetwork() {
        assertTrue("llm/cloud files not found: wrong working directory?", files.size >= 5)
        val net = Regex("""\bjava\.net\b|\bHttpsURLConnection\b|\bHttpURLConnection\b|\bURL\(|\bSocket\(|\bokhttp3\b|\bandroid\.net\b""")
        val offenders = files.filter { it.name != "CloudTransport.kt" }.flatMap { f -> code(f).filter { net.containsMatchIn(it) }.map { "${f.name}: $it" } }
        assertEquals("only CloudTransport.kt may use the network", emptyList<String>(), offenders)
    }

    @Test fun nothingInThePackageLogsOrPrints() {
        val bad = Regex("""\bLog\.|\bprintln\(|\bprintStackTrace|\bTimber\b|\bLogger\b""")
        val offenders = files.flatMap { f -> code(f).filter { bad.containsMatchIn(it) }.map { "${f.name}: $it" } }
        assertEquals("the package must not log (the key, prompts or answers)", emptyList<String>(), offenders)
    }

    @Test fun onlyTheServiceBuildsTheAuthorizationHeaderAndNoRedirectsAreFollowed() {
        val users = files.filter { f -> code(f).any { it.contains("\"Authorization\"") } }.map { it.name }
        assertEquals(listOf("CloudLlmService.kt"), users)
        assertTrue(File("src/main/java/ai/riskmandate/app/llm/cloud/CloudTransport.kt").readText().contains("instanceFollowRedirects = false"))
    }

    @Test fun noFileOutsideThePackagesOwnFoldersIsWritten() {
        val mentions = files.flatMap { f -> code(f).filter { it.contains("filesDir") || it.contains("getExternal") || it.contains("cacheDir") }.map { "${f.name}: $it" } }
        assertEquals(emptyList<String>(), mentions) // the folder is handed in (noBackupFilesDir/cloud), never chosen here
    }
}
