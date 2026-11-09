package ai.riskmandate.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Indicative only: the time to create MainActivity on the JVM under Robolectric. It is not a cold
 * start on a device (no process start, no class loading from the APK, no real GPU); it catches
 * someone adding slow work to onCreate. Printed to the test output.
 */
@RunWith(AndroidJUnit4::class)
class StartupTest {
    @Test fun mainActivityCreatesQuickly() {
        ActivityScenario.launch(MainActivity::class.java).close() // warm the JVM and Robolectric
        val t0 = System.nanoTime()
        ActivityScenario.launch(MainActivity::class.java).use { }
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("STARTUP_JVM_MS=$ms")
        assertTrue("MainActivity took ${ms}ms to create", ms < 3000)
    }
}
