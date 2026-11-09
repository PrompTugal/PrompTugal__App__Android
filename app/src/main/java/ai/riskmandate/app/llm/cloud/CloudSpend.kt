package ai.riskmandate.app.llm.cloud

import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * The spending cap, per install, kept in `noBackupFilesDir/cloud/spend.json`. Amounts are whole micro-dollars
 * (1,000,000 = US$1), so there is no floating-point drift. The default cap is US$1 until Dinis sets another.
 * A damaged file counts as "cap reached" (fail closed) until [reset]; a missing file is a fresh start.
 */
class CloudSpend(private val dir: File, private val defaultCapMicros: Long = DEFAULT_CAP_MICROS) {
    data class Snapshot(val spentMicros: Long, val capMicros: Long) {
        val remainingMicros: Long get() = (capMicros - spentMicros).coerceAtLeast(0)
    }

    private val file get() = File(dir, FILE)

    @Synchronized
    fun snapshot(): Snapshot = load()

    /** True if a call that could cost up to [worstCaseMicros] would take the total past the cap. */
    @Synchronized
    fun wouldExceed(worstCaseMicros: Long): Boolean = load().let { it.spentMicros + worstCaseMicros > it.capMicros }

    @Synchronized
    fun record(costMicros: Long) {
        require(costMicros >= 0) { "cost cannot be negative" }
        val s = load()
        store(s.copy(spentMicros = (s.spentMicros + costMicros).coerceAtMost(Long.MAX_VALUE / 2)))
    }

    @Synchronized
    fun setCap(capMicros: Long) {
        require(capMicros in 0..MAX_CAP_MICROS) { "cap out of range" }
        store(load().copy(capMicros = capMicros))
    }

    /** Sets the counter back to zero (staff, on purpose), keeping the cap. */
    @Synchronized
    fun reset() = store(Snapshot(0, load().capMicros))

    private fun load(): Snapshot {
        if (!file.isFile) return Snapshot(0, defaultCapMicros)
        return try {
            val o = JSONObject(file.readText(Charsets.UTF_8))
            if (o.getInt("v") != 1) throw IOException("version")
            val cap = o.getLong("cap").coerceIn(0, MAX_CAP_MICROS)
            val spent = o.getLong("spent")
            if (spent < 0) throw IOException("negative")
            Snapshot(spent, cap)
        } catch (e: Exception) {
            Snapshot(defaultCapMicros, defaultCapMicros) // damaged: closed, never silently reset
        }
    }

    private fun store(s: Snapshot) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create the cloud folder")
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(JSONObject().put("v", 1).put("spent", s.spentMicros).put("cap", s.capMicros).toString(), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("cannot replace the spend file")
        }
    }

    companion object {
        const val FILE = "spend.json"
        const val DEFAULT_CAP_MICROS = 1_000_000L
        const val MAX_CAP_MICROS = 100_000_000L

        fun usd(micros: Long): String = String.format(java.util.Locale.US, "%.2f", micros / 1_000_000.0)
    }
}
