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
    /** Never throws: on failure the reach is what could be read (an empty list if PackageManager fails). */
    fun collect(context: Context): List<ReachItem> {
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
            val files = storageFileCount(context)
            if (files > 0) add(ReachItem(ReachKind.STORAGE, "files", count = files))
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

    /** Files the app keeps (files, databases, shared preferences). Caches are not data the app keeps. */
    fun storageFileCount(context: Context): Int = try {
        val dirs = listOf(
            context.filesDir,
            File(context.applicationInfo.dataDir, "databases"),
            File(context.applicationInfo.dataDir, "shared_prefs"),
        )
        dirs.sumOf { dir -> dir.walkTopDown().count { it.isFile } }
    } catch (e: RuntimeException) {
        0
    }
}
