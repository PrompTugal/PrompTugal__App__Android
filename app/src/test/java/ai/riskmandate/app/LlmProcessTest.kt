package ai.riskmandate.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmProcessTest {
    @Before @After fun reset() = LlmProcess.resetForTest()

    @Test fun oneHostPerProcessCreatedOnFirstUse() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertNull("peek never creates", LlmProcess.peek())
        val first = LlmProcess.get(context)
        assertNotNull(first)
        assertSame(first, LlmProcess.get(context))
        assertSame(first, LlmProcess.peek())
    }

    @Test fun trimMemoryReachesAnExistingHostAndNeverCreatesOne() {
        // The activity forwards through peek(): with no host there is nothing to do, and no host appears.
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) }
        }
        assertNull(LlmProcess.peek())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        LlmProcess.get(context)
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) } // must not throw
        }
        assertNotNull(LlmProcess.peek())
    }
}

@RunWith(AndroidJUnit4::class)
class HostFeatureContextTest {
    @Before @After fun reset() = LlmProcess.resetForTest()

    @Test fun aFeatureSeesTheProcessWideModelService() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val a = hostFeatureContext(context, {}, ManagedConfig.DEFAULT, DeviceStatus.from(false, false, null), { {} }, {})
        val b = hostFeatureContext(context, {}, ManagedConfig.DEFAULT, DeviceStatus.from(false, false, null), { {} }, {})
        assertNotNull("a feature must see a non-null llm", a.llm)
        assertSame("one service per process", a.llm, b.llm)
        assertSame(LlmProcess.peek(), a.llm)
    }
}
