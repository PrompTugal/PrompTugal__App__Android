package ai.riskmandate.app.qa

import ai.riskmandate.app.HomeTags
import ai.riskmandate.app.MainActivity
import ai.riskmandate.app.features.FEATURES
import ai.riskmandate.app.features.Feature
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
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
 */
@RunWith(AndroidJUnit4::class)
class FeatureNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val enabled: List<Feature> get() = FEATURES.filter { it.enabled }

    private fun tag(f: Feature) = HomeTags.feature(f.id)

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun rotate() {
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    // Home is the "Now" page of cards (shell step 2): its stat grid is the marker that Home shows.
    private fun assertHome() = compose.onNodeWithTag(HomeTags.STATS).performScrollTo().assertIsDisplayed()

    private fun assertNotHome() = compose.onNodeWithTag(HomeTags.STATS).assertDoesNotExistOrNotDisplayed()

    @Test fun everyEnabledFeatureOpensFromHomeAndBackReturns() {
        for (f in enabled) {
            assertHome()
            compose.onNodeWithTag(tag(f)).performScrollTo().assertIsDisplayed().performClick()
            compose.waitForIdle()
            assertNotHome()
            back()
            assertHome()
        }
    }

    /**
     * Shell step 2: the frame no longer draws a Back button. On a phone (this test's window) the shell's
     * top bar is the header and the menu opens the destinations, where Now is the way home; the system
     * Back also goes home. (With the sidebar on a tablet the frame draws one ScreenHeader: HostTest.)
     */
    @Test fun frameHasNoBackButtonAndTheMenuLeadsHome() {
        for (f in enabled) {
            compose.onNodeWithTag(tag(f)).performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("feature_back").assertDoesNotExist()
            compose.onNodeWithTag("shell_top_title").assertIsDisplayed()
            compose.onNodeWithTag("shell_menu").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("nav_home").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            compose.waitForIdle()
            assertHome()
        }
    }

    @Test fun rotationKeepsTheFeatureScreen() {
        for (f in enabled) {
            compose.onNodeWithTag(tag(f)).performScrollTo().performClick()
            compose.waitForIdle()
            assertNotHome()
            rotate()
            assertNotHome() // still on the feature, not thrown back to home
            rotate()
            assertNotHome()
            back()
            assertHome()
        }
    }

    @Test fun rotationOnHomeStaysOnHome() {
        rotate()
        assertHome()
    }

    @Test fun backOnHomeDoesNotCrashTheFeatureList() {
        // A feature button must exist on home for every enabled feature, and only for those.
        for (f in enabled) compose.onNodeWithTag(tag(f)).performScrollTo().assertIsDisplayed()
        for (f in FEATURES.filter { !it.enabled }) compose.onNodeWithTag(tag(f)).assertDoesNotExist()
    }

    @Test fun featureIdsAreUniqueLowercaseLettersAndNotRoutes() {
        val ids = FEATURES.map { it.id }
        assertEquals("duplicate feature ids", ids.size, ids.toSet().size)
        for (id in ids) assertTrue("id '$id' must be lowercase letters only", id.matches(Regex("[a-z]+")))
        val reserved = setOf("home", "licences", "diagnostics")
        assertEquals("feature id clashes with a built-in route", emptySet<String>(), ids.toSet() intersect reserved)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistOrNotDisplayed() {
    // Home's Open button leaves the composition when a feature screen is shown.
    assertDoesNotExist()
}
