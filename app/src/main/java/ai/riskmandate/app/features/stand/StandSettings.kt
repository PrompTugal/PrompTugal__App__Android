package ai.riskmandate.app.features.stand

import android.content.Context
import android.content.SharedPreferences

/** Stand-mode settings, kept in this app's private SharedPreferences on the device and nowhere else. */
class StandSettings(private val prefs: SharedPreferences) {

    /** Whether stand mode is on. Survives rotation and process death, so a restart comes back pinned. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Idle minutes before the app returns to its start screen. */
    var idleMinutes: Int
        get() = prefs.getInt(KEY_MINUTES, DEFAULT_MINUTES).coerceIn(MIN_MINUTES, MAX_MINUTES)
        set(value) = prefs.edit().putInt(KEY_MINUTES, value.coerceIn(MIN_MINUTES, MAX_MINUTES)).apply()

    companion object {
        const val DEFAULT_MINUTES = 3
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 30
        private const val FILE = "stand_mode"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MINUTES = "idle_minutes"

        fun of(context: Context) = StandSettings(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
