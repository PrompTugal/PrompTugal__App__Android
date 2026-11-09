package ai.riskmandate.app

import ai.riskmandate.app.cloud.CloudKey
import ai.riskmandate.app.cloud.CloudLlmService
import ai.riskmandate.app.cloud.CloudModel
import ai.riskmandate.app.cloud.CloudModels
import ai.riskmandate.app.cloud.CloudSpend
import ai.riskmandate.app.cloud.CloudSwitch
import ai.riskmandate.app.cloud.KeyCipher
import ai.riskmandate.app.cloud.KeystoreCipher
import ai.riskmandate.app.llm.LlmService
import android.content.Context
import java.io.File

/**
 * The host's cloud model state (OpenRouter, staff only; docs/security/summit-checklist.md section M): the key
 * (encrypted, `noBackupFilesDir/feature_cloud/`), the spending ledger, the staff switch and the chosen model.
 * One per process. Nothing here is saved except the key and the ledger: the switch and the model are memory only,
 * so after a restart the cloud is off. [standMode] is read by the switch, which falls back to off for good when
 * stand mode is on.
 */
class CloudHost(dir: File, cipher: KeyCipher, standMode: () -> Boolean) {
    val key = CloudKey(dir, cipher)
    val spend = CloudSpend(dir)
    val switch = CloudSwitch(standMode)

    @Volatile var model: CloudModel = CloudModels.GEMMA4
        private set

    private var service: CloudLlmService? = null

    /** Picks a model from the pinned list (never free text). */
    @Synchronized
    fun choose(id: String) {
        val m = CloudModels.byId(id) ?: return
        if (m != model) { model = m; service = null }
    }

    /** The service for the chosen model: one per model, reused. */
    @Synchronized
    fun service(): CloudLlmService = service ?: CloudLlmService(model, key, spend, switch).also { service = it }

    /** Saves a pasted key and creates the ledger; see [CloudKey.save]. */
    fun saveKey(pasted: String) = key.save(pasted).also { if (it == ai.riskmandate.app.cloud.KeyCheck.OK) { spend.initialise(); service?.refresh() } }

    /**
     * Staff, after a confirmation that showed the amount (the only caller is the key screen's dialog): the total and
     * the session counter go back to zero. One place, so dev.chat's `reset(confirmed = true)` is a one-line change.
     */
    fun resetSpend() = spend.reset()

    /** Removes the key and its encryption key, and switches the cloud off. Needs no network. */
    fun removeKey() {
        switch.turnOff()
        key.clear()
        service?.refresh()
    }
}

/**
 * What the host passes a feature as `FeatureContext.cloudLlm`: the cloud service, but only to a feature that
 * declares `usesCloud`, only while a key is stored, the staff switch is on, and stand mode is off. Otherwise null.
 */
fun cloudLlmFor(usesCloud: Boolean, keySet: Boolean, switchOn: Boolean, standOn: Boolean, service: () -> LlmService): LlmService? =
    if (usesCloud && keySet && switchOn && !standOn) service() else null

object CloudProcess {
    @Volatile private var host: CloudHost? = null

    /** True while stand mode is on (set by the host); read by the cloud switch. */
    @Volatile var standMode: Boolean = false

    fun get(context: Context): CloudHost =
        host ?: synchronized(this) {
            host ?: CloudHost(File(context.applicationContext.noBackupFilesDir, "feature_cloud"), KeystoreCipher()) { standMode }
                .also { host = it }
        }

    /** For tests (the Keystore does not exist on the JVM): use this host. */
    internal fun setForTest(h: CloudHost?) { host = h; standMode = false }
}
