package ai.riskmandate.app.cloud

import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * What the cloud client may still spend. Two caps, both constants in [CloudLimits] that no screen can raise:
 * a per-session cap (in memory, gone at restart) and a per-tablet total kept in a ledger file in the folder it is
 * given (`noBackupFilesDir/feature_cloud/`, a number and not a secret). Amounts are whole micro-dollars.
 *
 * A missing or damaged ledger **refuses** to spend, never assumes zero: staff create it when they save the key
 * ([initialise]) or on purpose ([reset]). Tests build it with other caps through the internal constructor only.
 */
class CloudSpend internal constructor(
    private val dir: File,
    private val sessionCap: Long,
    private val totalCap: Long,
) {
    constructor(dir: File) : this(dir, CloudLimits.SESSION_CAP_MICROS, CloudLimits.TOTAL_CAP_MICROS)

    data class Snapshot(
        val ledgerOk: Boolean,
        val totalSpentMicros: Long,
        val totalCapMicros: Long,
        val sessionSpentMicros: Long,
        val sessionCapMicros: Long,
    ) {
        val remainingMicros: Long get() = if (!ledgerOk) 0 else minOf(totalCapMicros - totalSpentMicros, sessionCapMicros - sessionSpentMicros).coerceAtLeast(0)
    }

    private sealed interface Ledger {
        data object Missing : Ledger
        data object Damaged : Ledger
        data class Ok(val spent: Long) : Ledger
    }

    private var session = 0L

    /**
     * One request at a time across every service that shares this ledger (one per model): the cap check and the
     * call run under it, so two services cannot both pass the check and then both spend (security R-cloud-5).
     * The host makes one [CloudSpend] per tablet and gives it to every [CloudLlmService].
     */
    val gate = kotlinx.coroutines.sync.Mutex()
    private val file get() = File(dir, FILE)

    @Synchronized
    fun snapshot(): Snapshot {
        val l = load()
        return Snapshot(l is Ledger.Ok, (l as? Ledger.Ok)?.spent ?: 0, totalCap, session, sessionCap)
    }

    /** True if a call that could cost up to [worstCaseMicros] must not be sent: a ledger that cannot be trusted, or either cap. */
    @Synchronized
    fun wouldExceed(worstCaseMicros: Long): Boolean {
        val l = load() as? Ledger.Ok ?: return true
        return l.spent + worstCaseMicros > totalCap || session + worstCaseMicros > sessionCap
    }

    /** Adds [costMicros] to this session and, if the ledger is sound, to the total. A ledger that cannot be written stays refusing. */
    @Synchronized
    fun record(costMicros: Long) {
        require(costMicros >= 0) { "cost cannot be negative" }
        session = (session + costMicros).coerceAtMost(Long.MAX_VALUE / 2)
        val l = load() as? Ledger.Ok ?: return
        try { store((l.spent + costMicros).coerceAtMost(Long.MAX_VALUE / 2)) } catch (e: IOException) { discard() }
    }

    /** Creates the ledger at zero when there is none (staff saving the key). A damaged one is left alone: [reset] is the deliberate way out. */
    @Synchronized
    fun initialise() {
        if (load() is Ledger.Missing) store(0)
    }

    /**
     * Staff, on purpose: the total and the session counter go back to zero, and a damaged ledger is replaced.
     * [confirmed] must be true: a screen may only call this from a confirmed action on the staff-only key screen,
     * never from a plain button and never from anything a visitor can reach (security R-cloud-4). The credit limit
     * on the key itself, set at OpenRouter, stays the real backstop.
     */
    @Synchronized
    fun reset(confirmed: Boolean) {
        require(confirmed) { "the ledger is reset only after an explicit confirmation" }
        session = 0
        store(0)
    }

    private fun load(): Ledger {
        if (!file.isFile) return Ledger.Missing
        return try {
            val o = JSONObject(file.readText(Charsets.UTF_8))
            if (o.getInt("v") != 1) throw IOException("version")
            val spent = o.getLong("spent")
            if (spent < 0) throw IOException("negative")
            Ledger.Ok(spent)
        } catch (e: Exception) {
            Ledger.Damaged
        }
    }

    private fun store(spent: Long) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create the cloud folder")
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(JSONObject().put("v", 1).put("spent", spent).toString(), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("cannot replace the ledger")
        }
    }

    private fun discard() {
        file.delete() // could not be written: refuse from now on rather than let the total fall behind
    }

    companion object {
        const val FILE = "ledger.json"

        fun usd(micros: Long): String = String.format(java.util.Locale.US, "%.2f", micros / 1_000_000.0)
    }
}
