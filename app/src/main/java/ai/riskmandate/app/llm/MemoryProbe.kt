package ai.riskmandate.app.llm

import android.app.ActivityManager
import android.content.Context
import java.io.File

/** Reads memory figures for this process only (its own /proc/self/status) and the system totals. */
object MemoryProbe {
    fun sample(context: Context): MemorySample {
        val status = try {
            parseStatus(File("/proc/self/status").readText())
        } catch (e: Exception) {
            emptyMap()
        }
        val info = ActivityManager.MemoryInfo()
        val ok = try {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            true
        } catch (e: RuntimeException) {
            false
        }
        return MemorySample(
            rssMb = status["VmRSS"]?.div(1024),
            peakRssMb = status["VmHWM"]?.div(1024),
            availMb = if (ok) info.availMem / MIB else null,
            totalMb = if (ok) info.totalMem / MIB else null,
        )
    }

    /** `Name:   1234 kB` lines to name -> kB. Other lines are skipped. */
    fun parseStatus(text: String): Map<String, Long> = text.lineSequence().mapNotNull { line ->
        val name = line.substringBefore(':', "").trim()
        val parts = line.substringAfter(':', "").trim().split(Regex("\\s+"))
        val kb = parts.firstOrNull()?.toLongOrNull()
        if (name.isEmpty() || kb == null || parts.getOrNull(1) != "kB") null else name to kb
    }.toMap()

    private const val MIB = 1024L * 1024L
}
