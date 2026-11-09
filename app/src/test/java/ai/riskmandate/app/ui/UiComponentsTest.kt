package ai.riskmandate.app.ui

import ai.riskmandate.app.RiskMandateTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class UiComponentsTest {
    @get:Rule val compose = createComposeRule()

    private val density get() = ApplicationProvider.getApplicationContext<android.app.Application>().resources.displayMetrics.density

    @Test fun layerRowSpeaksItsState() {
        compose.setContent {
            RiskMandateTheme {
                Column {
                    LayerRow(1, "Browser", LayerState.Waiting, "Stopped here")
                    LayerRow(2, "Model", LayerState.Stopped, "Stopped here")
                }
            }
        }
        val state = { i: Int -> compose.onNodeWithTag(UiTags.layer(i)).fetchSemanticsNode().config[SemanticsProperties.StateDescription] }
        assertEquals("Not reached yet", state(1))
        assertEquals("Stopped the instruction here", state(2))
        // The chip only appears on a lit row.
        compose.onNodeWithText("Stopped here").assertIsDisplayed()
    }

    @Test fun chipSpeaksItsWordOnlyNotItsGlyph() {
        compose.setContent { RiskMandateTheme { Column { StatusChip("Blocked", ChipKind.Blocked) } } }
        val text = compose.onNodeWithText("Blocked").assertIsDisplayed().fetchSemanticsNode().config[SemanticsProperties.Text]
        assertEquals(listOf("Blocked"), text.map { it.text })
    }

    @Test fun sixLargeLayersSurvive200PercentFontScale() {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                RiskMandateTheme { LayersSample(List(6) { LayerState.Waiting }, large = true) }
            }
        }
        compose.onNodeWithTag(UiTags.BANNER).assertIsDisplayed()
        compose.onNodeWithTag(UiTags.layer(6)).performScrollTo().assertIsDisplayed()
    }

    @Test fun featureContentFlagsWideLayoutOnlyWhenWide() {
        var wide: Boolean? = null
        compose.setContent {
            RiskMandateTheme {
                FeatureContent { wide = LocalWideLayout.current; androidx.compose.material3.Text("body") }
            }
        }
        compose.onNodeWithText("body").assertIsDisplayed()
        assertEquals(false, wide) // the default Robolectric screen is a phone
    }
}
