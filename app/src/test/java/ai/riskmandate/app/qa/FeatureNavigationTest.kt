package ai.riskmandate.app.qa

import ai.riskmandate.app.HomeTags
import ai.riskmandate.app.MainActivity
import ai.riskmandate.app.features.FEATURES
import ai.riskmandate.app.features.Feature
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Cross-feature navigation (QA owns this file). Every ENABLED feature must open from the home screen, survive a
 * rotation (activity recreation) on its own screen, and return home (system Back, and the visible way home).
 * A disabled feature is not tested here; from the day its owner enables it, this test covers it with no change.
 *
 * Two properties on purpose:
 *
 * 1. **Deterministic.** No step trusts that one `waitForIdle()` was enough. After every action the test waits for the
 *    state it expects and idles Robolectric's main looper as well (state that arrives from another thread resumes
 *    there). Clicks are the node's own click action, not a touch at coordinates: a home screen whose layout changes a
 *    moment after it appears (a badge that pops in after an asynchronous read) would send a coordinate click to the
 *    wrong place. After `recreate()` the test waits for the NEW activity, and Back is only pressed once the host's back
 *    callback is enabled (with none enabled the dispatcher finishes the activity, as a Back on Home does). The limits
 *    are only failure bounds, never a delay the test depends on.
 * 2. **Shell-agnostic.** The home marker is either the old home's Open button (`open`) or the new "Now" page's stats
 *    grid (`home_stats`); the feature header is either the frame's (`feature_title`) or the shell's (`shell_top_title`);
 *    the visible way home is the frame's Back button when there is one, else the shell's menu then Now. So the same
 *    file passes before and after the app shell lands and covers both.
 */
@RunWith(AndroidJUnit4::class)
class FeatureNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val enabled: List<Feature> get() = FEATURES.filter { it.enabled }

    private fun tag(f: Feature) = HomeTags.feature(f.id)

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun homeShown() = HOME_MARKERS.sumOf { count(it) } >= 1

    private fun headerCount() = HEADER_TAGS.sumOf { count(it) }

    /** Idles the looper and Compose until [done] holds, or fails with [what] after the limit. */
    private fun until(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + LIMIT_MS
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (done()) return
            if (System.currentTimeMillis() > end) throw AssertionError("timed out waiting for: $what")
            Thread.sleep(5)
        }
    }

    private fun awaitHome() = until("the home screen") { homeShown() && headerCount() == 0 }

    private fun awaitFeatureScreen() = until("a feature screen with one header") { !homeShown() && headerCount() == 1 }

    private fun open(f: Feature) {
        // Let pending main-looper work finish so the home screen is in its final layout, then click the node's own action.
        repeat(5) { shadowOf(Looper.getMainLooper()).idle(); compose.waitForIdle() }
        compose.onNodeWithTag(tag(f)).performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        awaitFeatureScreen()
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)

    /** Presses system Back once the screen can take it (see the class comment), then waits for Home. */
    private fun back() {
        until("an enabled back callback") {
            var ready = false
            compose.runOnUiThread { ready = compose.activity.onBackPressedDispatcher.hasEnabledCallbacks() }
            ready
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        awaitHome()
    }

    /** Recreates the activity (what a rotation does) and waits for the NEW one: the old tree can stay registered for an instant. */
    private fun rotate() {
        val old = compose.activity
        compose.activityRule.scenario.recreate()
        until("a recreated activity") { compose.activity !== old }
    }

    @Test fun everyEnabledFeatureOpensFromHomeAndBackReturns() {
        awaitHome()
        for (f in enabled) {
            open(f)
            back()
        }
    }

    /** The visible way home: the frame's Back button on the old shell, the menu then Now on the new one. One header only. */
    @Test fun theVisibleWayHomeWorksAndTheHeaderShowsOnce() {
        awaitHome()
        for (f in enabled) {
            open(f)
            assertEquals("exactly one header on a feature screen", 1, headerCount())
            if (count("feature_back") > 0) {
                click("feature_back")
            } else {
                click("shell_menu")
                until("the navigation menu") { count("nav_home") >= 1 }
                click("nav_home")
            }
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
        val HOME_MARKERS = listOf("open", "home_stats")
        val HEADER_TAGS = listOf("feature_title", "shell_top_title")
        const val LIMIT_MS = 15_000L
    }
}
