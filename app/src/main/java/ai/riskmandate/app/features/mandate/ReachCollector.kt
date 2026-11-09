package ai.riskmandate.app.features.mandate

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/** Reads this app's reach from the device: PackageManager, the application flags and its own storage. */
object ReachCollector {
    /** [enabledFeatureIds]: the features that are on, to know whether the app itself connects anywhere. Never throws: on failure the reach is what could be read (an empty list if PackageManager fails). */
    fun collect(context: Context, enabledFeatureIds: Set<String> = emptySet()): List<ReachItem> {
        val pm = context.packageManager
        val info = packageInfo(pm, context.packageName) ?: return emptyList()
        return buildList {
            addAll(permissions(info))
            addAll(components(info))
            val appFlags = info.applicationInfo?.flags ?: 0
            if (appFlags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0) {
                add(ReachItem(ReachKind.NETWORK, "cleartext"))
            }
            if (appFlags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0) {
                add(ReachItem(ReachKind.STORAGE, "backup"))
            }
            addAll(networkReach(enabledFeatureIds))
            addAll(storage(context))
        }
    }

    private fun permissions(info: PackageInfo): List<ReachItem> {
        val names = info.requestedPermissions ?: return emptyList()
        val flags = info.requestedPermissionsFlags
        return names.mapIndexed { i, name ->
            val granted = flags != null && i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            ReachItem(ReachKind.PERMISSION, name, granted = granted)
        }
    }

    private fun components(info: PackageInfo): List<ReachItem> {
        fun exported(list: Array<out ComponentInfo>?, type: ComponentType, guard: (ComponentInfo) -> String?) =
            list.orEmpty().filter { it.exported }
                .map { ReachItem(ReachKind.COMPONENT, it.name, componentType = type, guardedBy = guard(it)) }
        return exported(info.activities, ComponentType.ACTIVITY) { (it as android.content.pm.ActivityInfo).permission } +
            exported(info.services, ComponentType.SERVICE) { (it as android.content.pm.ServiceInfo).permission } +
            exported(info.receivers, ComponentType.RECEIVER) { (it as android.content.pm.ActivityInfo).permission } +
            exported(info.providers, ComponentType.PROVIDER) {
                val p = it as android.content.pm.ProviderInfo
                p.readPermission ?: p.writePermission
            }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(pm: PackageManager, pkg: String): PackageInfo? {
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pm.getPackageInfo(pkg, flags)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }

    /**
     * What the app keeps on the device: each file in files/, databases/ and shared_prefs/ (id is the path under
     * the data directory), and app_webview/ and cache/ as directories (the in-app view's web storage and cache),
     * each only when it holds a file. Never throws.
     */
    fun storage(context: Context): List<ReachItem> = try {
        val data = File(context.applicationInfo.dataDir)
        val perFile = listOf(context.filesDir, File(data, "databases"), File(data, "shared_prefs"))
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile }.take(MAX_FILES).toList() }
            .map { ReachItem(ReachKind.STORAGE, it.relativeTo(data).invariantSeparatorsPath, count = 1) }
        val dirs = listOf(File(data, "app_webview"), context.cacheDir).mapNotNull { dir ->
            val n = dir.walkTopDown().filter { it.isFile }.take(MAX_FILES).count()
            if (n > 0) ReachItem(ReachKind.STORAGE, dir.relativeTo(data).invariantSeparatorsPath, count = n) else null
        }
        perFile + dirs
    } catch (e: RuntimeException) {
        emptyList()
    }

    private const val MAX_FILES = 500
}
