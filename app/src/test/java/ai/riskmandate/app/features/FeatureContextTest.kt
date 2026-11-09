package ai.riskmandate.app.features

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.ManagedConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The FeatureContext contract (architect owns this file). */
@RunWith(AndroidJUnit4::class)
class FeatureContextTest {
    @get:Rule val compose = createComposeRule()

    private val device = DeviceStatus.from(hasDeviceOwner = false, inWorkProfile = false, label = null)

    @Test fun holdsAreCountedAndReleasedOnce() {
        val holds = IdleHolds()
        assertFalse(holds.held)
        val a = holds.hold()
        val b = holds.hold()
        a(); a() // a second release of the same hold changes nothing
        assertTrue(holds.held)
        b()
        assertFalse(holds.held)
    }

    @Test fun clearDropsEveryHoldAndLateReleasesAreHarmless() {
        val holds = IdleHolds()
        val a = holds.hold()
        holds.clear()
        assertFalse(holds.held)
        a()
        val b = holds.hold()
        assertTrue(holds.held)
        b()
        assertFalse(holds.held)
    }

    @Test fun holdIdleWhileFollowsTheConditionAndTheScreen() {
        val holds = IdleHolds()
        val context = FeatureContext(onBack = {}, config = ManagedConfig.DEFAULT, device = device, holdIdle = holds::hold)
        var active by mutableStateOf(false)
        var shown by mutableStateOf(true)
        compose.setContent { if (shown) context.HoldIdleWhile(active) }
        compose.waitForIdle(); assertFalse(holds.held)
        active = true
        compose.waitForIdle(); assertTrue(holds.held)
        active = false
        compose.waitForIdle(); assertFalse(holds.held)
        active = true
        compose.waitForIdle(); assertTrue(holds.held)
        shown = false // the feature leaves the screen while holding
        compose.waitForIdle(); assertFalse(holds.held)
    }

    @Test fun theDefaultHoldDoesNothing() {
        val context = FeatureContext(onBack = {}, config = ManagedConfig.DEFAULT, device = device)
        context.holdIdle()() // no host: a no-op, never throws
    }
}

/** Feature.homeBadge (P6): optional, generic, null by default. */
@RunWith(AndroidJUnit4::class)
class FeatureBadgeTest {
    @get:Rule val compose = createComposeRule()

    private object Plain : Feature {
        override val id = "plain"
        override val title = ai.riskmandate.app.R.string.app_name
        @androidx.compose.runtime.Composable override fun Content(context: FeatureContext) = Unit
    }

    private object Badged : Feature {
        override val id = "badged"
        override val title = ai.riskmandate.app.R.string.app_name
        @androidx.compose.runtime.Composable override fun Content(context: FeatureContext) = Unit
        @androidx.compose.runtime.Composable override fun homeBadge(): Int = ai.riskmandate.app.R.string.app_name
    }

    @Test fun defaultIsNoBadgeAndAFeatureCanOfferOne() {
        var plain: Int? = -1
        var badged: Int? = -1
        compose.setContent { plain = Plain.homeBadge(); badged = Badged.homeBadge() }
        compose.waitForIdle()
        org.junit.Assert.assertNull(plain)
        org.junit.Assert.assertEquals(ai.riskmandate.app.R.string.app_name, badged)
    }

    @Test fun everyListedFeatureAnswersWithoutThrowing() {
        compose.setContent { FEATURES.filter { it.enabled }.forEach { it.homeBadge() } }
        compose.waitForIdle()
    }
}

/** Feature.icon, kicker, summary (P7): optional, null by default; every listed feature's strings resolve. */
@RunWith(AndroidJUnit4::class)
class FeaturePresentationTest {
    private object Plain : Feature {
        override val id = "plain"
        override val title = ai.riskmandate.app.R.string.app_name
        @androidx.compose.runtime.Composable override fun Content(context: FeatureContext) = Unit
    }

    @Test fun defaultsAreNull() {
        org.junit.Assert.assertNull(Plain.icon)
        org.junit.Assert.assertNull(Plain.kicker)
        org.junit.Assert.assertNull(Plain.summary)
    }

    @Test fun everyListedKickerAndSummaryResolvesAndIsNotBlank() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        for (f in FEATURES) {
            f.kicker?.let { org.junit.Assert.assertTrue("${f.id} kicker", app.getString(it).isNotBlank()) }
            f.summary?.let { org.junit.Assert.assertTrue("${f.id} summary", app.getString(it).isNotBlank()) }
        }
    }
}
