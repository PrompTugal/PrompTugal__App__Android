package ai.riskmandate.app.qa

import ai.riskmandate.app.HomeTags
import ai.riskmandate.app.MainActivity
import ai.riskmandate.app.features.FEATURES
import ai.riskmandate.app.features.Feature
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import android.os.Looper

/**
 * Cross-feature navigation (QA owns this file). Every ENABLED feature must open from the home screen,
 * survive a rotation (activity recreation) on its own screen, and return home with Back.
 * A feature still disabled is not tested here; the owner turns it on when its screen works end to end,
 * and from then on this test covers it with no change.
 *
 * Deterministic by construction: no step trusts that one `waitForIdle()` was enough. After every action the test
 * waits for the state it expects (exactly one node of the expected screen, none of the other), so a recreation or
 * a back press that is still settling on a loaded machine cannot make it flaky. The upper bound of each wait is
 * only a failure limit, never a delay the test depends on.
 */
@RunWith(AndroidJUnit4::class)
class FeatureNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val enabled: List<Feature> get() = FEATURES.filter { it.enabled }

    private fun tag(f: Feature) = HomeTags.feature(f.id)

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    /**
     * Waits until exactly [n] nodes carry [tag]. Idles Robolectric's main looper as well as Compose, because state that
     * arrives from another thread (for example the What's new badge, read on `Dispatchers.IO`) resumes on that looper.
     * The limit is only a failure bound.
     */
    private fun await(tag: String, n: Int) {
        val end = System.currentTimeMillis() + LIMIT_MS
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (count(tag) == n) return
            if (System.currentTimeMillis() > end) throw AssertionError("expected $n node(s) tagged '$tag' but found ${count(tag)}")
            Thread.sleep(5)
        }
    }

    private fun awaitHome() { await(HomeTags.OPEN, 1); await(FRAME_TITLE, 0) }

    private fun awaitFeatureScreen() { await(HomeTags.OPEN, 0); await(FRAME_TITLE, 1) }

    /**
     * Opens a feature. The click is the node's own click action, not a touch at coordinates: a home screen whose layout
     * changes a moment after it appears (a badge that pops in after an asynchronous read resizes a button and moves the
     * ones below) would otherwise send a coordinate click to the wrong place. That shift is reported separately
     * (docs/qa/reviews/2026-11-09-app-*). The button must still be on screen: that is asserted just before.
     */
    private fun open(f: Feature) {
        settle()
        compose.onNodeWithTag(tag(f)).performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        awaitFeatureScreen()
    }

    /** Lets pending work on the main looper finish so the home screen is in its final layout. */
    private fun settle() {
        repeat(5) { shadowOf(Looper.getMainLooper()).idle(); compose.waitForIdle() }
    }

    /**
     * Presses Back once the screen can take it. With no enabled back callback the dispatcher finishes the activity,
     * which is what a Back on Home does: so the test never presses Back until the host's own handler is registered
     * (it is not, for an instant, in a freshly recreated activity), or the press would close the app and the test
     * would wait for a Home that no longer exists.
     */
    private fun back() {
        val end = System.currentTimeMillis() + LIMIT_MS
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            var ready = false
            compose.runOnUiThread { ready = compose.activity.onBackPressedDispatcher.hasEnabledCallbacks() }
            if (ready) break
            if (System.currentTimeMillis() > end) throw AssertionError("no back callback is enabled on the feature screen")
            Thread.sleep(5)
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        awaitHome()
    }

    /**
     * Recreates the activity (what a rotation does) and waits for the NEW activity: right after `recreate()` the old
     * activity's tree can still be registered, so "exactly one title" alone could be satisfied by the old screen.
     */
    private fun rotate() {
        val old = compose.activity
        compose.activityRule.scenario.recreate()
        val end = System.currentTimeMillis() + LIMIT_MS
        while (compose.activity === old) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (System.currentTimeMillis() > end) throw AssertionError("the activity was not recreated")
            Thread.sleep(5)
        }
        compose.waitForIdle()
    }

    @Test fun everyEnabledFeatureOpensFromHomeAndBackReturns() {
        awaitHome()
        for (f in enabled) {
            open(f)
            back()
            compose.onNodeWithTag(HomeTags.OPEN).assertIsDisplayed()
        }
    }

    /** The host's visible Back button (FeatureFrame): needed in stand mode, where the back gesture may be pinned off. */
    @Test fun frameBackButtonReturnsHomeAndTitleShowsOnce() {
        awaitHome()
        for (f in enabled) {
            open(f)
            compose.onNodeWithTag(FRAME_TITLE).assertIsDisplayed()
            compose.onNodeWithTag(FRAME_BACK).assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
            awaitHome()
        }
    }

    @Test fun rotationKeepsTheFeatureScreen() {
        awaitHome()
        for (f in enabled) {
            open(f)
            rotate()
            awaitFeatureScreen() // still on the feature, not thrown back to home
            rotate()
            awaitFeatureScreen()
            back()
        }
    }

    @Test fun rotationOnHomeStaysOnHome() {
        awaitHome()
        rotate()
        awaitHome()
    }

    @Test fun featureButtonsExistForEnabledFeaturesOnly() {
        awaitHome()
        for (f in enabled) compose.onNodeWithTag(tag(f)).performScrollTo().assertIsDisplayed()
        for (f in FEATURES.filter { !it.enabled }) assertEquals("disabled feature ${f.id} has a button", 0, count(tag(f)))
    }

    @Test fun featureIdsAreUniqueLowercaseLettersAndNotRoutes() {
        val ids = FEATURES.map { it.id }
        assertEquals("duplicate feature ids", ids.size, ids.toSet().size)
        for (id in ids) assertTrue("id '$id' must be lowercase letters only", id.matches(Regex("[a-z]+")))
        val reserved = setOf("home", "licences", "diagnostics", "modelbench")
        assertEquals("feature id clashes with a built-in route", emptySet<String>(), ids.toSet() intersect reserved)
    }

    private companion object {
        const val FRAME_TITLE = "feature_title"
        const val FRAME_BACK = "feature_back"
        const val LIMIT_MS = 15_000L
    }
}
