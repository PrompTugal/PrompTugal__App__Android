package ai.riskmandate.app.cloud

/**
 * The staff member's "cloud on" switch (checklist M.4). Off by default, kept in memory only so it is off again
 * after a restart, and it falls back to off when stand mode turns on, whatever was set before: a visitor's text never
 * goes to a third party. The host creates one, passes the stand-mode reader, and gives the service this switch.
 */
class CloudSwitch(private val standMode: () -> Boolean = { false }) {
    @Volatile private var requested = false

    /** Reading it while stand mode is on switches it off for good: it does not come back when stand mode ends. */
    val isOn: Boolean
        get() {
            if (standMode()) requested = false
            return requested
        }

    /** Turns it on unless stand mode is on; returns whether it is now on. */
    fun turnOn(): Boolean {
        if (standMode()) return false
        requested = true
        return true
    }

    fun turnOff() {
        requested = false
    }
}
