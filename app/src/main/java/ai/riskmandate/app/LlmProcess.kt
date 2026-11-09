package ai.riskmandate.app

import ai.riskmandate.app.llm.LlmHost
import android.content.Context

/**
 * The one [LlmHost] of this process (the contract in llm/LlmService.kt: one per process, owned by the host).
 * Created on first use, so an app that never touches a model never builds it. [peek] is for callers that
 * only want to tell an existing host something (trim memory, unload) and must not create one.
 */
object LlmProcess {
    @Volatile private var host: LlmHost? = null

    fun get(context: Context): LlmHost =
        host ?: synchronized(this) { host ?: LlmHost.create(context.applicationContext).also { host = it } }

    fun peek(): LlmHost? = host

    /** For tests: forget the instance. */
    internal fun resetForTest() { host = null }
}
