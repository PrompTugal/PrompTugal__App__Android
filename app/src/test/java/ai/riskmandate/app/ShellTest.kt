package ai.riskmandate.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
        NavSection(R.string.nav_section_now, listOf(NavItem("home", R.string.nav_home, Icons.Filled.Home))),
        NavSection(R.string.nav_section_features, listOf(NavItem("site", R.string.home_diagnostics, Icons.Filled.Star, badge = R.string.nav_home))),
    )

    private var selected by mutableStateOf("home")
    private var homeCalls = 0

    private fun show() = compose.setContent {
        RiskMandateTheme {
            AppShell(
                sections = sections, selected = selected, currentTitle = R.string.nav_home, atHome = selected == "home",
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
}
