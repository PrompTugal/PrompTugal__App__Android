package ai.riskmandate.app.qa

import ai.riskmandate.app.HomeTags
import ai.riskmandate.app.MainActivity
import ai.riskmandate.app.features.FEATURES
import ai.riskmandate.app.features.Feature
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

    /** Waits until exactly [n] nodes carry [tag]. Fails with a clear message after the limit. */
    private fun await(tag: String, n: Int) {
        compose.waitForIdle()
        try {
            compose.waitUntil(LIMIT_MS) { count(tag) == n }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("expected $n node(s) tagged '$tag' but found ${count(tag)}", e)
        }
    }

    private fun awaitHome() { await(HomeTags.OPEN, 1); await(FRAME_TITLE, 0) }

    private fun awaitFeatureScreen() { await(HomeTags.OPEN, 0); await(FRAME_TITLE, 1) }

    private fun open(f: Feature) {
        compose.onNodeWithTag(tag(f)).performScrollTo().assertIsDisplayed().performClick()
        awaitFeatureScreen()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        awaitHome()
    }

    private fun rotate() {
        compose.activityRule.scenario.recreate()
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
            compose.onNodeWithTag(FRAME_BACK).assertIsDisplayed().performClick()
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
