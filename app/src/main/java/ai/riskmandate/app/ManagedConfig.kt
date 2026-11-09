package ai.riskmandate.app

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle

/**
 * The managed configuration (app restrictions) an organisation's EMM sets for this app (M2).
 * Keys are declared in res/xml/app_restrictions.xml.
 */
data class ManagedConfig(
    /** The page the app opens: always a riskmandate.ai HTTPS URL (Links.isAllowed). */
    val startUrl: String,
    /** A label for this device, or null when none is set. */
    val deviceLabel: String?,
    /** True when the organisation set a start_url that was refused, so the app fell back to Links.HOME. */
    val startUrlRefused: Boolean = false,
) {
    companion object {
        const val KEY_START_URL = "start_url"
        const val KEY_DEVICE_LABEL = "device_label"
        private const val MAX_LABEL_LENGTH = 64

        val DEFAULT = ManagedConfig(startUrl = Links.HOME, deviceLabel = null)

        /** Pure parsing, testable on the JVM: takes the restrictions as a map of key to value. */
        fun fromMap(values: Map<String, Any?>): ManagedConfig {
            val rawUrl = (values[KEY_START_URL] as? String)?.trim().orEmpty()
            val urlAllowed = rawUrl.isNotEmpty() && Links.isAllowed(rawUrl)
            val label = (values[KEY_DEVICE_LABEL] as? String)
                ?.trim()
                ?.filter { !it.isISOControl() }
                ?.take(MAX_LABEL_LENGTH)
                ?.takeIf { it.isNotEmpty() }
            return ManagedConfig(
                startUrl = if (urlAllowed) rawUrl else Links.HOME,
                deviceLabel = label,
                startUrlRefused = rawUrl.isNotEmpty() && !urlAllowed,
            )
        }

        /** Reads the current restrictions from the system. Never throws: falls back to DEFAULT. */
        fun read(context: Context): ManagedConfig {
            val bundle = try {
                context.getSystemService(RestrictionsManager::class.java)?.applicationRestrictions
            } catch (e: RuntimeException) {
                null
            } ?: return DEFAULT
            return fromMap(bundle.toMap())
        }

        private fun Bundle.toMap(): Map<String, Any?> =
            keySet().associateWith { key -> getString(key) }
    }
}
