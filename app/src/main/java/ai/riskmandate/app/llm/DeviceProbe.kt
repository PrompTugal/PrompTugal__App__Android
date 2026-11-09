package ai.riskmandate.app.llm

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/** Thermal status as Android reports it (API 29+); UNKNOWN on older systems or when it cannot be read. */
enum class Thermal { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN, UNKNOWN;
    /** The Workbench and the Lab stop a loop at SEVERE or worse. */
    val severeOrWorse: Boolean get() = this in setOf(SEVERE, CRITICAL, EMERGENCY, SHUTDOWN)

    companion object {
        fun of(status: Int?): Thermal = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> NONE
            PowerManager.THERMAL_STATUS_LIGHT -> LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> SHUTDOWN
            else -> UNKNOWN
        }
    }
}

/** One reading of this app's memory, the battery and the thermal status. No identifiers. */
data class DeviceSample(
    val memory: MemorySample,
    /** 0 to 100, or null when the device does not say. */
    val batteryPct: Int?,
    val charging: Boolean?,
    val thermal: Thermal,
)

/**
 * Reads [DeviceSample]s for the Workbench's runs and caps. Read-only system services (BatteryManager properties,
 * PowerManager.currentThermalStatus, this process's /proc/self/status); no broadcast receiver, no permission.
 */
object DeviceProbe {
    fun sample(context: Context): DeviceSample {
        val battery = context.getSystemService(BatteryManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val pct = try {
            battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        } catch (e: RuntimeException) {
            null
        }
        val charging = try {
            battery?.isCharging
        } catch (e: RuntimeException) {
            null
        }
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Thermal.of(power?.currentThermalStatus)
            } catch (e: RuntimeException) {
                Thermal.UNKNOWN
            }
        } else Thermal.UNKNOWN
        return DeviceSample(MemoryProbe.sample(context), pct, charging, thermal)
    }
}
