package ai.riskmandate.app

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ShellTest {
    @get:Rule val compose = createComposeRule()

    private val sections = listOf(
        NavSection(R.string.nav_section_today, listOf(NavItem("home", R.string.home_now_title, R.drawable.ic_nav_home))),
        NavSection(R.string.nav_section_explore, listOf(NavItem("site", R.string.home_diagnostics, R.drawable.ic_nav_globe, badge = R.string.nav_menu))),
    )

    private var selected by mutableStateOf("home")
    private var homeCalls = 0

    private var collapsed by mutableStateOf(false)

    private fun show() = compose.setContent {
        RiskMandateTheme {
            AppShell(
                sidebarCollapsed = collapsed, onSidebarCollapsedChange = { collapsed = it },
                sections = sections, selected = selected, currentTitle = R.string.home_now_title, atHome = selected == "home",
                onSelect = { selected = it }, onBackHome = { homeCalls++; selected = "home" },
            ) { Text("content:$selected", modifier = androidx.compose.ui.Modifier.run { this }) }
        }
    }

    @Test @Config(qualifiers = "w900dp-h600dp")
    fun wideWindowHasAPermanentSidebarAndNoMenuButton() {
        show()
        compose.onNodeWithTag(ShellTags.SIDEBAR).assertIsDisplayed()
        compose.onNodeWithTag(ShellTags.item("home")).assertIsDisplayed()
        compose.onNodeWithTag(ShellTags.item("site")).assertIsDisplayed()
        compose.onNodeWithTag(ShellTags.pill("site"), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(ShellTags.pill("home"), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(ShellTags.MENU).assertDoesNotExist()
        compose.onNodeWithTag(ShellTags.item("site")).performClick()
        assertEquals("site", selected)
    }

    @Test
    fun narrowWindowHasATopBarWithAMenuThatOpensTheSameDestinations() {
        show()
        compose.onNodeWithTag(ShellTags.SIDEBAR).assertDoesNotExist()
        compose.onNodeWithTag(ShellTags.TOP_TITLE).assertIsDisplayed()
        compose.onNodeWithTag(ShellTags.MENU).assertIsDisplayed().performClick()
        compose.waitForIdle() // the drawer finishes sliding in
        compose.onNodeWithTag(ShellTags.item("site")).assertIsDisplayed()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals("site", selected)
    }

    @Test @Config(qualifiers = "w900dp-h600dp")
    fun menuTouchTargetsAreAtLeast48dp() {
        show()
        val dp = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        for (r in listOf("home", "site"))
            assert(compose.onNodeWithTag(ShellTags.item(r)).fetchSemanticsNode().boundsInRoot.height >= 48 * dp - 1)
    }

    @Test @Config(qualifiers = "w900dp-h600dp")
    fun sidebarFoldsToARailAndBackAndKeepsEveryDestinationReachable() {
        show()
        compose.onNodeWithTag(ShellTags.pill("site"), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(ShellTags.COLLAPSE).performClick()
        assertEquals(true, collapsed)
        compose.waitForIdle()
        compose.onNodeWithTag(ShellTags.pill("site"), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(ShellTags.item("site")).assertIsDisplayed().performClick()
        assertEquals("site", selected)
        compose.onNodeWithTag(ShellTags.COLLAPSE).performClick()
        assertEquals(false, collapsed)
    }

    @Test @Config(qualifiers = "w900dp-h600dp")
    fun theCloudBadgeShowsOnEveryScreenOnlyWhileTheCloudIsOn() {
        var cloud by mutableStateOf(false)
        compose.setContent {
            RiskMandateTheme {
                AppShell(
                    sections = sections, selected = "home", currentTitle = R.string.home_now_title, atHome = true,
                    onSelect = {}, onBackHome = {}, cloudOn = cloud, cloudModel = "google/gemma-4-26b-a4b-it",
                ) { Text("content") }
            }
        }
        compose.onNodeWithTag(ShellTags.CLOUD_BADGE).assertDoesNotExist()
        cloud = true
        compose.waitForIdle()
        compose.onNodeWithTag(ShellTags.CLOUD_BADGE).assertIsDisplayed()
        compose.onNodeWithTag(ShellTags.CLOUD_MODEL).assertIsDisplayed() // the model is shown with the badge
    }

    @Test
    fun theCloudBadgeShowsInThePhoneTopBarToo() {
        // (the closed drawer holds a second copy of the sidebar, so look at the first: the top bar's)
        compose.setContent {
            RiskMandateTheme {
                AppShell(
                    sections = sections, selected = "home", currentTitle = R.string.home_now_title, atHome = true,
                    onSelect = {}, onBackHome = {}, cloudOn = true,
                ) { Text("content") }
            }
        }
        compose.onAllNodesWithTag(ShellTags.CLOUD_BADGE).onFirst().assertIsDisplayed()
    }
}
