package ai.riskmandate.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * What a tester or the M2 rehearsal needs to know about this install, as plain text.
 * Deliberately no personal data and no identifiers: no IMEI, serial number, Android ID, account or
 * network address. The device model is the make and model of the hardware, shared by every unit.
 */
data class DiagnosticsInfo(
    val versionName: String,
    val versionCode: Long,
    val applicationId: String,
    val buildType: String,
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val device: DeviceStatus,
    val config: ManagedConfig,
    /** Package that installed this app (PackageManager), or null when unknown (adb, some sideloads). */
    val installerPackage: String?,
) {
    /**
     * The text that the Copy button puts on the clipboard. English on purpose: it is pasted into
     * tickets and read by people on other teams, and field names must stay stable.
     */
    fun toText(): String = buildString {
        appendLine("RiskMandate diagnostics")
        appendLine("App version: $versionName")
        appendLine("Version code: $versionCode")
        appendLine("Application ID: $applicationId")
        appendLine("Build type: $buildType")
        appendLine("Android: $androidRelease (API $sdkInt)")
        appendLine("Device: ${"$manufacturer $model".trim()}")
        appendLine("Management: ${device.management.name.lowercase().replace('_', ' ')}")
        appendLine("Managed configuration:")
        appendLine("  start_url: ${config.startUrl}")
        appendLine("  start_url refused: ${if (config.startUrlRefused) "yes (fell back to the default)" else "no"}")
        appendLine("  device_label: ${config.deviceLabel ?: "(not set)"}")
        append("Installer: ${installerLabel(installerPackage)}")
    }

    companion object {
        /** Human name for the usual installers; anything else is shown as its package name. */
        fun installerLabel(pkg: String?): String = when (pkg) {
            null, "" -> "unknown (adb or a sideload)"
            "com.android.vending" -> "Google Play ($pkg)"
            "dev.firebase.appdistribution" -> "Firebase App Distribution ($pkg)"
            "com.google.android.packageinstaller", "com.android.packageinstaller" -> "package installer, a file install ($pkg)"
            else -> pkg
        }

        fun collect(context: Context, config: ManagedConfig, device: DeviceStatus): DiagnosticsInfo =
            DiagnosticsInfo(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                applicationId = BuildConfig.APPLICATION_ID,
                buildType = BuildConfig.BUILD_TYPE,
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                device = device,
                config = config,
                installerPackage = installer(context),
            )

        @Suppress("DEPRECATION")
        private fun installer(context: Context): String? = try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info = pm.getInstallSourceInfo(context.packageName)
                info.installingPackageName ?: info.initiatingPackageName
            } else {
                pm.getInstallerPackageName(context.packageName)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }
}
