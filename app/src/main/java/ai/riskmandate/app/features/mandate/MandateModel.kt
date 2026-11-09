package ai.riskmandate.app.features.mandate

import ai.riskmandate.app.llm.DownloadPolicy
import ai.riskmandate.app.llm.ModelCatalog

/**
 * Pure Kotlin model of this app's own reach, mandate and gap (JVM-testable, no Android types).
 *
 * The vocabulary is riskmandate.ai's (https://riskmandate.ai/, read 9 Nov 2026):
 * - reach: "Everything the agent can actually access in this deployment, including what nobody thought to check."
 * - mandate: "The job, written down: what the business authorised it to do."
 * - gap: "Reach minus mandate. Never written by hand, and recomputed whenever either side moves."
 *
 * Here the "agent" is this app. Reach is read from the device at runtime ([ReachCollector]); the mandate is
 * written down in [Mandate.current]; the gap is computed by [MandateReport.gap] and never written by hand.
 */
enum class ReachKind { PERMISSION, COMPONENT, NETWORK, STORAGE }

enum class ComponentType { ACTIVITY, SERVICE, RECEIVER, PROVIDER }

/**
 * One thing this app can touch. [id] is stable and unique within its [kind] (a permission name, a component
 * class name, "cleartext", "files", "backup").
 */
data class ReachItem(
    val kind: ReachKind,
    val id: String,
    /** Permissions: true when granted now, false when only requested (a dangerous permission not yet allowed). */
    val granted: Boolean = true,
    val componentType: ComponentType? = null,
    /** Components: the permission a caller must hold to reach an exported component, if any. */
    val guardedBy: String? = null,
    /** Storage: how many files were found (1 for a single file). */
    val count: Int = 0,
) {
    val key: String get() = "${kind.name.lowercase()}:$id"
}

/** Why the mandate allows an item. Mapped to a string resource in the UI, so the reasons are translated. */
enum class Reason { OPEN_ALLOWED_PAGES, OFFLINE_HINT, LAUNCHER, INTERNAL_GUARD, STAND_SETTINGS, SITE_STORAGE, SITE_CACHE, SITE_VIEW, SHELL_UI, RELEASES_SEEN, CHAT_HISTORY, MODELS, MODELS_ADB, MODEL_CHOICE, MODEL_DOWNLOAD }

data class MandateEntry(val kind: ReachKind, val id: String, val reason: Reason) {
    val key: String get() = "${kind.name.lowercase()}:$id"
}

/**
 * What the app's policy allows: the written-down job.
 * [data] is what the app collects: none (docs/play/data-safety.md, 8 Nov 2026), so the list is empty.
 */
data class Mandate(
    val entries: List<MandateEntry>,
    val hosts: List<String>,
    val port: Int,
    val data: List<String>,
    val managedKeys: List<String>,
) {
    fun allows(item: ReachItem): Boolean = entries.any { it.key == item.key }

    companion object {
        /** Host list as in Links.ALLOWED_HOSTS; the live check is [Links.isAllowed] (see [HostProbe]). */
        val HOSTS = listOf("riskmandate.ai", "www.riskmandate.ai")

        /**
         * The model files, by exact name, from the app's own catalogue (llm/ModelCatalog): in noBackupFilesDir/models
         * the file, its ".part" while a download is unfinished and its ".verified" marker; in the adb folder on shared
         * storage the file and its marker. Any other file there is gap.
         */
        fun modelFileEntries(): List<MandateEntry> = ModelCatalog.ALL.flatMap { spec ->
            val name = spec.fileName
            listOf(
                MandateEntry(ReachKind.STORAGE, "no_backup/models/$name", Reason.MODELS),
                MandateEntry(ReachKind.STORAGE, "no_backup/models/$name.part", Reason.MODELS),
                MandateEntry(ReachKind.STORAGE, "no_backup/models/.$name.verified", Reason.MODELS),
                MandateEntry(ReachKind.STORAGE, "external/models/$name", Reason.MODELS_ADB),
                MandateEntry(ReachKind.STORAGE, "external/models/.$name.verified", Reason.MODELS_ADB),
            )
        }

        /**
         * The current mandate for [packageName]. The app loads riskmandate.ai pages in its in-app view and opens them in the user's browser (INTERNET),
         * says plainly when it is offline (ACCESS_NETWORK_STATE), has one launcher screen, holds the one
         * signature-level permission AndroidX adds to guard the app's own dynamic receivers, and may keep the settings,
         * chat history and model files of its features, and the in-app view's own web storage and cache, all on this
         * device. Nothing else.
         */
        fun current(packageName: String, managedKeys: List<String>): Mandate = Mandate(
            entries = listOf(
                MandateEntry(ReachKind.PERMISSION, "android.permission.INTERNET", Reason.OPEN_ALLOWED_PAGES),
                MandateEntry(ReachKind.PERMISSION, "android.permission.ACCESS_NETWORK_STATE", Reason.OFFLINE_HINT),
                MandateEntry(ReachKind.PERMISSION, "$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", Reason.INTERNAL_GUARD),
                MandateEntry(ReachKind.COMPONENT, "$packageName.MainActivity", Reason.LAUNCHER),
                // The in-app site view (feature "site") loads riskmandate.ai itself: exactly these two hosts.
                MandateEntry(ReachKind.NETWORK, "connects:${HOSTS[0]}", Reason.SITE_VIEW),
                MandateEntry(ReachKind.NETWORK, "connects:${HOSTS[1]}", Reason.SITE_VIEW),
                // Model setup: the Download button fetches a model from Hugging Face, only when tapped (dev.llm's
                // DownloadPolicy: https, port 443, huggingface.co, hf.co and hosts under hf.co).
                MandateEntry(ReachKind.NETWORK, "connects:huggingface.co", Reason.MODEL_DOWNLOAD),
                MandateEntry(ReachKind.NETWORK, "connects:hf.co", Reason.MODEL_DOWNLOAD),
                MandateEntry(ReachKind.NETWORK, "connects:*.hf.co", Reason.MODEL_DOWNLOAD),
                // Storage the summit features may keep, on this device only. None exists until the feature is used.
                MandateEntry(ReachKind.STORAGE, "shared_prefs/stand_mode.xml", Reason.STAND_SETTINGS),
                MandateEntry(ReachKind.STORAGE, "app_webview", Reason.SITE_STORAGE),
                MandateEntry(ReachKind.STORAGE, "cache", Reason.SITE_CACHE),
                MandateEntry(ReachKind.STORAGE, "shared_prefs/shell.xml", Reason.SHELL_UI),
                MandateEntry(ReachKind.STORAGE, "shared_prefs/feature_releases.xml", Reason.RELEASES_SEEN),
                MandateEntry(ReachKind.STORAGE, "shared_prefs/feature_models.xml", Reason.MODEL_CHOICE),
                MandateEntry(ReachKind.STORAGE, "no_backup/feature_chat", Reason.CHAT_HISTORY),
            ) + modelFileEntries(),
            hosts = HOSTS,
            port = 443,
            data = emptyList(),
            managedKeys = managedKeys,
        )
    }
}

/**
 * Network the app makes itself: when the in-app site view is on, the app (not the browser) connects to the
 * allowed riskmandate.ai hosts; the model download code may connect to Hugging Face, only when Download is tapped in Models. Computed from which features are enabled; pages opened in the user's browser
 * are the browser's traffic and are not counted.
 */
fun networkReach(enabledFeatureIds: Set<String>): List<ReachItem> =
    (if ("site" in enabledFeatureIds) Mandate.HOSTS.map { ReachItem(ReachKind.NETWORK, "connects:$it") } else emptyList()) +
        modelDownloadReach()

/**
 * The hosts the model download code in this build may connect to, asked of its own rule (llm/DownloadPolicy)
 * rather than copied: each candidate is tried with [DownloadPolicy.hostAllowed], so if the policy ever accepts
 * another host it appears here and, not being in the mandate, shows as gap. "*.hf.co" stands for hosts under hf.co.
 * Counted whenever the code is in the build, switched on or not: reach is what the code can do, not what is on.
 */
fun modelDownloadReach(): List<ReachItem> = MODEL_HOST_CANDIDATES
    .filter { (_, sample) -> DownloadPolicy.hostAllowed(sample) }
    .map { (id, _) -> ReachItem(ReachKind.NETWORK, "connects:$id") }

/** Candidate host id to a sample host for the policy's check; the last four should all be refused. */
private val MODEL_HOST_CANDIDATES = listOf(
    "huggingface.co" to "huggingface.co",
    "hf.co" to "hf.co",
    "*.hf.co" to "cdn.hf.co",
    "example.com" to "example.com",
    "huggingface.co.example.com" to "huggingface.co.example.com",
    "evilhf.co" to "evilhf.co",
    "riskmandate.ai" to "riskmandate.ai",
)

/** The comparison. [reach] and [mandate] are inputs; the gap is derived and never stored by hand. */
data class MandateReport(val reach: List<ReachItem>, val mandate: Mandate) {
    /** Reach the mandate does not need: reach minus mandate, recomputed from both sides every time. */
    val gap: List<ReachItem> get() = reach.filterNot { mandate.allows(it) }

    /** Reach the mandate covers. */
    val covered: List<ReachItem> get() = reach.filter { mandate.allows(it) }

    /** Mandate entries nothing in the reach uses (informational: the mandate is wider than what exists). */
    val unused: List<MandateEntry> get() {
        val keys = reach.map { it.key }.toSet()
        return mandate.entries.filter { it.key !in keys }
    }

    val gapIsEmpty: Boolean get() = gap.isEmpty()
}

/**
 * A sample address and whether the app's allow-list lets it through. Computed with the real check.
 * Samples are built from parts (scheme, userinfo, host, port), not written as URL literals, so no feature
 * source holds a URL outside Links.isAllowed (qa's FeatureLinksTest).
 */
data class HostProbe(val url: String, val allowed: Boolean) {
    data class Sample(val scheme: String, val host: String, val port: Int? = null, val userinfo: String? = null) {
        fun url(): String = buildString {
            append(scheme).append("://")
            if (userinfo != null) append(userinfo).append('@')
            append(host)
            if (port != null) append(':').append(port)
            append('/')
        }
    }

    companion object {
        private val SITE = Mandate.HOSTS[0]
        private val WWW = Mandate.HOSTS[1]

        val SAMPLES = listOf(
            Sample("https", SITE),
            Sample("https", WWW),
            Sample("http", SITE),
            Sample("https", SITE, port = 8443),
            Sample("https", "example.com"),
            Sample("https", "example.com", userinfo = SITE),
        ).map { it.url() }

        fun run(isAllowed: (String) -> Boolean, samples: List<String> = SAMPLES): List<HostProbe> =
            samples.map { HostProbe(it, isAllowed(it)) }
    }
}
