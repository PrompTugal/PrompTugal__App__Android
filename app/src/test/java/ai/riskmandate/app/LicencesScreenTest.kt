package ai.riskmandate.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LicencesScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun resourceListsAppAndLibraryLicences() {
        val text = readLicences(ApplicationProvider.getApplicationContext())
        assertTrue(text.contains("Apache License"))
        assertTrue(text.contains("androidx.compose.material3:material3"))
        assertTrue(text.contains("org.jetbrains.kotlin:kotlin-stdlib"))
    }

    @Test fun homeOpensLicencesAndShowsApache() {
        val text = readLicences(ApplicationProvider.getApplicationContext())
        compose.setContent {
            MaterialTheme {
                var open = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                if (open.value) LicencesScreen(text = text, onBack = { open.value = false })
                else HomeScreen("0.1.7 (7)", DeviceStatus.from(false, false, null), onOpen = {}, onLicences = { open.value = true })
            }
        }
        compose.onNodeWithTag(HomeTags.LICENCES).performScrollTo().performClick()
        compose.onNodeWithText("Open source licences").assertIsDisplayed()
        compose.onNodeWithTag(LicenceTags.TEXT).assertIsDisplayed()
        compose.onNodeWithText("Apache License", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(LicenceTags.BACK).performClick()
        compose.onNodeWithTag(HomeTags.OPEN).assertIsDisplayed()
    }
}
