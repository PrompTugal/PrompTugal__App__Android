package ai.riskmandate.app

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.UserManager

/** What the app can tell, through public APIs, about whether an organisation manages this device. */
enum class Management {
    /** A device owner (fully managed device) was found. */
    FULLY_MANAGED,
    /** The app runs in a work profile. */
    WORK_PROFILE,
    /** No device owner or work profile was detected (it may still be managed in a way the app cannot see). */
    NOT_DETECTED,
}

data class DeviceStatus(val management: Management, val label: String?) {
    val managementText: String
        get() = when (management) {
            Management.FULLY_MANAGED -> "Managed by an organisation (fully managed device)"
            Management.WORK_PROFILE -> "Managed by an organisation (work profile)"
            Management.NOT_DETECTED -> "Not managed (no device owner or work profile detected)"
        }

    companion object {
        /** Pure decision, testable on the JVM. */
        fun from(hasDeviceOwner: Boolean, inWorkProfile: Boolean, label: String?): DeviceStatus =
            DeviceStatus(
                management = when {
                    hasDeviceOwner -> Management.FULLY_MANAGED
                    inWorkProfile -> Management.WORK_PROFILE
                    else -> Management.NOT_DETECTED
                },
                label = label,
            )

        /**
         * There is no public "is this device managed?" call (DevicePolicyManager.isDeviceManaged is a
         * system API). From API 26 to 35 the app asks, for each active device admin, whether that
         * package is the device owner or a profile owner; from API 30 it also asks UserManager whether
         * it runs in a managed profile. Any failure counts as "not detected".
         */
        fun detect(context: Context, label: String?): DeviceStatus {
            val dpm = context.getSystemService(DevicePolicyManager::class.java)
            val admins = try {
                dpm?.activeAdmins.orEmpty()
            } catch (e: RuntimeException) {
                emptyList()
            }
            val packages = admins.map { it.packageName }.distinct()
            val hasDeviceOwner = dpm != null && packages.any { pkg ->
                try { dpm.isDeviceOwnerApp(pkg) } catch (e: RuntimeException) { false }
            }
            val hasProfileOwner = dpm != null && packages.any { pkg ->
                try { dpm.isProfileOwnerApp(pkg) } catch (e: RuntimeException) { false }
            }
            val managedProfile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    context.getSystemService(UserManager::class.java)?.isManagedProfile ?: false
                } catch (e: RuntimeException) {
                    false
                }
            } else {
                false
            }
            return from(hasDeviceOwner, managedProfile || hasProfileOwner, label)
        }
    }
}
