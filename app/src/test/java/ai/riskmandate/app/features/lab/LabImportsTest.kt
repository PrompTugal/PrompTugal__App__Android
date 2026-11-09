package ai.riskmandate.app.features.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Security's checklist I.1 (also asked for by qa): the Lab's tools are fakes, so the package may not reach
 * a network, a file, an intent, the clipboard, a process or reflection. Reads the sources and fails on a
 * banned import or call. The one Android use is the connectivity hint in NetworkReader.
 */
class LabImportsTest {
    private val dir = File("src/main/java/ai/riskmandate/app/features/lab")
    private val sources = dir.listFiles { f -> f.name.endsWith(".kt") }!!.toList()

    private val bannedImports = listOf(
        "android.content.Intent", "android.content.ClipboardManager", "android.content.ContentResolver", "android.content.ContentProvider",
        "android.app.Activity", "android.app.Service", "android.webkit", "android.os.Environment", "android.provider",
        "java.net", "javax.net", "okhttp3", "okio", "retrofit2", "org.apache.http", "java.io.File", "java.nio.file", "java.io.FileInputStream",
        "java.io.FileOutputStream", "java.io.FileReader", "java.io.FileWriter", "java.lang.reflect", "kotlin.reflect", "java.lang.ProcessBuilder",
        "android.net.Uri", "android.net.http", "android.net.wifi", "android.net.nsd",
    )
    private val bannedCalls = listOf("Class.forName", "Runtime.getRuntime", "ProcessBuilder", "System.exit", "Thread(", "GlobalScope", "runBlocking", "addJavascriptInterface", "startActivity", "sendBroadcast", "HttpURLConnection")

    @Test fun thePackageHasNoBannedImports() {
        assertTrue(sources.size >= 10)
        for (f in sources) for (line in f.readLines().filter { it.startsWith("import ") }) {
            val name = line.removePrefix("import ").trim()
            for (b in bannedImports) assertTrue("${f.name} imports $name (banned: $b)", !(name == b || name.startsWith("$b.")))
        }
    }

    @Test fun onlyTheConnectivityHintTouchesAndroidNet() {
        for (f in sources) for (line in f.readLines().filter { it.startsWith("import android.net") }) {
            assertEquals("only NetworkReader may import android.net", "NetworkReader.kt", f.name)
            assertTrue(line, line.trim() in setOf("import android.net.ConnectivityManager", "import android.net.NetworkCapabilities"))
        }
    }

    @Test fun theOnlyAssetReaderIsTheBundledFolderAndNoBannedCallAppears() {
        for (f in sources) {
            val code = f.readLines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") || it.trim().startsWith("/*") }.joinToString("\n")
            for (b in bannedCalls) assertTrue("${f.name} uses $b", !code.contains(b))
            if (f.name != "LabAssets.kt") assertTrue("${f.name} touches assets", !code.contains("assets.open") && !code.contains("AssetManager"))
        }
        assertTrue(File(dir, "LabAssets.kt").readText().contains("assets.open(\"\$ROOT/\$path\")"))
    }
}
