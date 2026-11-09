package ai.riskmandate.app.features.stand

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

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

    val hasPin: Boolean get() = prefs.contains(KEY_PIN_HASH)

    /** Sets a staff PIN. Returns false (and changes nothing) if [pin] is not 4 to 8 digits. */
    fun setPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        prefs.edit().putString(KEY_PIN_SALT, salt).putString(KEY_PIN_HASH, hash(salt, pin)).apply()
        return true
    }

    fun clearPin() = prefs.edit().remove(KEY_PIN_SALT).remove(KEY_PIN_HASH).apply()

    /** True if there is no PIN, or [pin] matches. */
    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null)
        val stored = prefs.getString(KEY_PIN_HASH, null)
        if (salt == null || stored == null) return true
        return MessageDigest.isEqual(hash(salt, pin).toByteArray(), stored.toByteArray())
    }

    companion object {
        const val DEFAULT_MINUTES = 3
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 30
        private const val FILE = "stand_mode"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MINUTES = "idle_minutes"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"

        fun of(context: Context) = StandSettings(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

        fun isValidPin(pin: String) = pin.length in 4..8 && pin.all { it in '0'..'9' }

        private fun hash(salt: String, pin: String) =
            MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).toHex()

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}

/**
 * Slows down guessing of the staff PIN: after [maxAttempts] wrong tries, [lockoutMs] must pass.
 * Pure (fake-clock testable); lives in memory only.
 */
class PinGate(private val clock: () -> Long, private val maxAttempts: Int = 5, private val lockoutMs: Long = 60_000) {
    private var failures = 0
    private var lockedUntil = 0L

    fun isLocked(): Boolean = clock() < lockedUntil

    /** Checks via [check] unless locked. Returns true only on a correct PIN. */
    fun attempt(check: () -> Boolean): Boolean {
        if (isLocked()) return false
        if (check()) {
            failures = 0
            return true
        }
        if (++failures >= maxAttempts) {
            failures = 0
            lockedUntil = clock() + lockoutMs
        }
        return false
    }
}
