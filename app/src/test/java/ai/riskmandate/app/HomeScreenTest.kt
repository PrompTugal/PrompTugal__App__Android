package ai.riskmandate.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
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

@RunWith(AndroidJUnit4::class)
class HomeFeatureOrderTest {
    @get:Rule val compose = createComposeRule()

    /** TalkBack reads in this order: title, Open, the message about opening, then the feature buttons. */
    @Test fun messageComesRightAfterOpenAndBeforeFeatures() {
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    "0.1.7 (7)", DeviceStatus.from(false, false, null), onOpen = {},
                    message = R.string.message_offline,
                    features = listOf(FeatureButton("site", R.string.home_diagnostics, onClick = {})),
                )
            }
        }
        fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
        assert(top(HomeTags.OPEN) < top(HomeTags.MESSAGE))
        assert(top(HomeTags.MESSAGE) < top(HomeTags.feature("site")))
    }
}

@RunWith(AndroidJUnit4::class)
class HomeCornerTest {
    @get:Rule val compose = createComposeRule()

    /** Stand mode (features/stand) listens for a long press in the bottom-end 48dp corner: nothing clickable may sit there. */
    @Test fun bottomEndCornerHasNoClickableControl() {
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    "0.1.7 (7)", DeviceStatus.from(true, false, "Lisbon kiosk 3"), onOpen = {},
                    message = R.string.message_offline,
                    features = listOf(FeatureButton("a", R.string.home_diagnostics) {}, FeatureButton("b", R.string.home_licences) {}),
                )
            }
        }
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val dp = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        val corner = androidx.compose.ui.geometry.Rect(root.right - 48 * dp, root.bottom - 48 * dp, root.right, root.bottom)
        compose.onAllNodes(androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes().forEach {
            assert(!it.boundsInRoot.overlaps(corner)) { "a control overlaps the bottom-end corner: ${it.boundsInRoot}" }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class HomeBadgeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun badgeShowsOnlyOnTheButtonThatHasOne() {
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    "0.1.7 (7)", DeviceStatus.from(false, false, null), onOpen = {},
                    features = listOf(
                        FeatureButton("a", R.string.home_diagnostics, badge = R.string.home_licences) {},
                        FeatureButton("b", R.string.home_diagnostics) {},
                    ),
                )
            }
        }
        compose.onNodeWithTag(HomeTags.featureBadge("a"), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(HomeTags.featureBadge("b"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun badgedButtonStillClicksAndIsBigEnough() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    "0.1.7 (7)", DeviceStatus.from(false, false, null), onOpen = {},
                    features = listOf(FeatureButton("a", R.string.home_diagnostics, badge = R.string.home_licences) { clicks++ }),
                )
            }
        }
        compose.onNodeWithTag(HomeTags.feature("a")).performClick()
        assertEquals(1, clicks)
        val dp = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        assert(compose.onNodeWithTag(HomeTags.feature("a")).fetchSemanticsNode().boundsInRoot.height >= 48 * dp - 1)
    }
}

@RunWith(AndroidJUnit4::class)
class HomeDiagnosticsVisibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun diagnosticsButtonIsHiddenWhenAsked() {
        compose.setContent {
            MaterialTheme { HomeScreen("0.1.7 (7)", DeviceStatus.from(false, false, null), onOpen = {}, showDiagnostics = false) }
        }
        compose.onNodeWithTag(HomeTags.DIAGNOSTICS).assertDoesNotExist()
        compose.onNodeWithTag(HomeTags.LICENCES).assertExists()
    }
}
