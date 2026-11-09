package ai.riskmandate.app.features.stand

/**
 * Pure idle-timer logic for stand mode: no Android types, so a JVM test drives it with a fake clock.
 *
 * [clock] must be monotonic (the app passes `SystemClock.elapsedRealtime`, which also counts while
 * the screen is off). [poll] returns true once per idle period; the next [touch] arms it again.
 */
class IdleTimer(private val clock: () -> Long, timeoutMs: Long) {
    var timeoutMs: Long = timeoutMs.coerceAtLeast(1)
        set(value) { field = value.coerceAtLeast(1) }

    private var lastTouch = clock()
    private var fired = false

    /** Any user input: restarts the countdown and re-arms the timer. */
    fun touch() {
        lastTouch = clock()
        fired = false
    }

    fun idleMs(): Long = (clock() - lastTouch).coerceAtLeast(0)

    /** True exactly once when the idle time first reaches the timeout; false until the next [touch]. */
    fun poll(): Boolean {
        if (fired || idleMs() < timeoutMs) return false
        fired = true
        return true
    }
}
