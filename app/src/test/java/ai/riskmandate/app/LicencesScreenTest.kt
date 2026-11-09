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
import org.junit.Assert.assertEquals
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

    @Test fun fullApacheTextIsShippedAndMatchesTheRepositoryLicence() {
        val shipped = readApacheLicence(ApplicationProvider.getApplicationContext())
        // Gradle runs unit tests with app/ as the working directory.
        val repoLicence = java.io.File("../LICENSE").readText()
        assertEquals(repoLicence, shipped)
        assertTrue(shipped.contains("4. Redistribution."))
        assertTrue(shipped.contains("END OF TERMS AND CONDITIONS"))
    }

    @Test fun homeOpensLicencesAndShowsApache() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val text = readLicences(context)
        val apache = readApacheLicence(context)
        compose.setContent {
            MaterialTheme {
                var open = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                if (open.value) LicencesScreen(text = text, apacheText = apache)
                else HomeScreen("0.1.7 (7)", DeviceStatus.from(false, false, null), onOpen = {}, onLicences = { open.value = true })
            }
        }
        compose.onNodeWithTag(HomeTags.LICENCES).performScrollTo().performClick()
        compose.onNodeWithTag(LicenceTags.TEXT).assertIsDisplayed()
        compose.onNodeWithTag(LicenceTags.APACHE).assertExists()
    }

    @Test fun modelFilesGroupListsTheDownloadableGemma4ModelsOnly() {
        val lines = modelLicenceLines()
        assertEquals(listOf("Gemma 4 E2B (Apache-2.0)", "Gemma 4 E4B (Apache-2.0)"), lines)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            MaterialTheme { LicencesScreen(text = readLicences(context), apacheText = readApacheLicence(context)) }
        }
        compose.onNodeWithTag(LicenceTags.MODELS).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Gemma 4 E2B (Apache-2.0)", substring = true).assertExists()
        compose.onNodeWithText("Gemma 3", substring = true).assertDoesNotExist()
    }
}
