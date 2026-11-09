package ai.riskmandate.app

import ai.riskmandate.app.features.FEATURES
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun rotatingKeepsTheScreenOpen() {
        compose.onNodeWithTag(HomeTags.DIAGNOSTICS).performScrollTo().performClick()
        compose.onNodeWithTag(DiagnosticsTags.TEXT).assertIsDisplayed()
        compose.activityRule.scenario.recreate() // what a tablet turn does
        compose.onNodeWithTag(DiagnosticsTags.TEXT).assertIsDisplayed()
        compose.onNodeWithTag(DiagnosticsTags.BACK).performClick()
        compose.onNodeWithTag(HomeTags.OPEN).assertIsDisplayed()
    }

    @Test fun featureIdsAreUniqueLowercaseAndDoNotClashWithBuiltInRoutes() {
        val ids = FEATURES.map { it.id }
        assertEquals(ids.distinct(), ids)
        val builtIn = setOf(Routes.HOME, Routes.LICENCES, Routes.DIAGNOSTICS)
        for (id in ids) {
            assertTrue("$id must be lowercase letters", id.matches(Regex("[a-z]+")))
            assertTrue("$id clashes with a built-in route", id !in builtIn)
        }
    }
}

@RunWith(AndroidJUnit4::class)
class FeatureFrameTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()

    @Test fun frameShowsTitleContentAndABackButtonThatWorks() {
        var back = 0
        compose.setContent {
            MaterialTheme { FeatureFrame(title = R.string.home_diagnostics, onBack = { back++ }) { Text("inside") } }
        }
        compose.onNodeWithTag(FeatureFrameTags.TITLE).assertIsDisplayed()
        compose.onNodeWithTag("feature_back").assertIsDisplayed().performClick()
        assertEquals(1, back)
        compose.onNodeWithTag(FeatureFrameTags.TITLE).fetchSemanticsNode()
    }
}
