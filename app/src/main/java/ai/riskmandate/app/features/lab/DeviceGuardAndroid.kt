package ai.riskmandate.app.features.lab

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/** Battery and thermal readings for the loop's guards. Read-only system services; no broadcast, no permission. */
class AndroidDeviceGuard(context: Context) : DeviceGuard {
    private val battery = context.getSystemService(BatteryManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    override fun batteryPct(): Int? = runCatching {
        battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
    }.getOrNull()

    override fun charging(): Boolean = runCatching { battery?.isCharging == true }.getOrDefault(false)

    /** Thermal status exists from Android 10 (API 29); on older systems the guard cannot tell, so it says no. */
    override fun hot(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        runCatching { (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE }.getOrDefault(false)
}
