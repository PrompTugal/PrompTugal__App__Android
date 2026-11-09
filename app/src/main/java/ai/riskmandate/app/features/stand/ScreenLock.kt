package ai.riskmandate.app.features.stand

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.view.WindowManager

/** What stand mode needs from the screen. An interface so tests can fake it. Nothing here may throw. */
interface ScreenLock {
    /** True while the task is pinned (screen pinning or lock task). */
    fun isLocked(): Boolean

    /** Asks Android to pin. Returns false if it could not even ask; a refusal by the user shows up in [isLocked]. */
    fun lock(): Boolean

    fun unlock()

    fun keepScreenOn(on: Boolean)
}

/** Screen pinning on an unmanaged device: Android shows its own prompt once; we handle every failure. */
class ActivityScreenLock(private val activity: Activity) : ScreenLock {
    override fun isLocked(): Boolean = try {
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am != null && am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    } catch (e: RuntimeException) {
        false
    }

    override fun lock(): Boolean = try {
        activity.startLockTask()
        true
    } catch (e: RuntimeException) {
        false
    }

    override fun unlock() {
        try {
            activity.stopLockTask()
        } catch (e: RuntimeException) {
            // Not pinned, or the system refused: nothing to undo.
        }
    }

    override fun keepScreenOn(on: Boolean) {
        val window = activity.window
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
