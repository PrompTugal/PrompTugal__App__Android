package ai.riskmandate.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(device: DeviceStatus, message: Int? = null, onOpen: () -> Unit = {}) =
        compose.setContent {
            MaterialTheme { HomeScreen(version = "0.1.7 (7)", device = device, onOpen = onOpen, message = message) }
        }

    @Test fun showsButtonAndOpens() {
        var clicks = 0
        show(DeviceStatus.from(false, false, null)) { clicks++ }
        compose.onNodeWithText("Open riskmandate.ai").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag(HomeTags.OPEN).performClick()
        assertEquals(1, clicks)
    }

    @Test fun showsVersion() {
        show(DeviceStatus.from(false, false, null))
        compose.onNodeWithTag(HomeTags.VERSION).assertTextEquals("Build 0.1.7 (7)")
    }

    @Test fun showsUnmanagedDevice() {
        show(DeviceStatus.from(false, false, null))
        compose.onNodeWithText("Device").assertExists()
        compose.onNodeWithTag(HomeTags.DEVICE_MANAGEMENT)
            .assertTextEquals("Not managed (no device owner or work profile detected)")
        compose.onNodeWithTag(HomeTags.DEVICE_LABEL).assertDoesNotExist()
    }

    @Test fun showsManagedDeviceAndLabel() {
        show(DeviceStatus.from(true, false, "Lisbon kiosk 3"))
        compose.onNodeWithTag(HomeTags.DEVICE_MANAGEMENT)
            .assertTextEquals("Managed by an organisation (fully managed device)")
        compose.onNodeWithTag(HomeTags.DEVICE_LABEL).assertTextEquals("Label: Lisbon kiosk 3")
    }

    @Test fun showsMessageWhenNoBrowser() {
        show(DeviceStatus.from(false, false, null), message = Browser.message(OpenResult.NO_BROWSER))
        compose.onNodeWithTag(HomeTags.MESSAGE).assertExists()
    }

    @Test fun diagnosticsButtonIsThere() {
        var opened = 0
        compose.setContent {
            MaterialTheme { HomeScreen("0.1.7 (7)", DeviceStatus.from(false, false, null), onOpen = {}, onDiagnostics = { opened++ }) }
        }
        compose.onNodeWithTag(HomeTags.DIAGNOSTICS).performClick()
        assertEquals(1, opened)
    }
}
