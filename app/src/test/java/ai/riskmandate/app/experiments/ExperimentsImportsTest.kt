package ai.riskmandate.app.experiments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Security's checklist L (the Workbench): `experiments/` and the tool-test tab run fake tools and nothing else, so
 * they may not reach a network, a file, an intent, the clipboard, a process or reflection. Reads the sources and
 * fails on a banned import or call. The only Android system service is the battery and thermal reading in
 * AndroidDeviceGuard, and the only asset reader is the catalogue loader.
 */
class ExperimentsImportsTest {
    private val dirs = listOf(File("src/main/java/ai/riskmandate/app/experiments"), File("src/main/java/ai/riskmandate/app/features/workbench/tooltests"))
    private val sources = dirs.flatMap { d -> d.listFiles { f -> f.name.endsWith(".kt") }!!.toList() }

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

    @Test fun noNetworkAtAllAndAndroidOsOnlyInTheDeviceGuard() {
        for (f in sources) for (line in f.readLines().filter { it.startsWith("import android.net") }) throw AssertionError("${f.name} imports ${line.trim()}: no network API here")
        for (f in sources) for (line in f.readLines().filter { it.startsWith("import android.os") }) {
            assertEquals("only AndroidDeviceGuard may read battery and thermal state", "AndroidDeviceGuard.kt", f.name)
            assertTrue(line, line.trim() in setOf("import android.os.BatteryManager", "import android.os.Build", "import android.os.PowerManager"))
        }
    }

    @Test fun theOnlyAssetReaderIsTheCatalogueLoaderAndNoBannedCallAppears() {
        for (f in sources) {
            val code = f.readLines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") || it.trim().startsWith("/*") }.joinToString("\n")
            for (b in bannedCalls) assertTrue("${f.name} uses $b", !code.contains(b))
            if (f.name != "ToolTestAssets.kt") assertTrue("${f.name} touches assets", !code.contains("assets.open") && !code.contains("AssetManager"))
        }
        assertTrue(File("src/main/java/ai/riskmandate/app/experiments/ToolTestAssets.kt").readText().contains("assets.open(\"\$ROOT/\$f\")"))
    }

    @Test fun theNewPackagesAreInScope() {
        val names = sources.map { it.name }
        assertTrue(names.containsAll(listOf("ToolProtocol.kt", "ToolTestRunner.kt", "ToolChecks.kt", "RunCaps.kt", "AndroidDeviceGuard.kt", "ToolTestsTab.kt")))
    }
}
