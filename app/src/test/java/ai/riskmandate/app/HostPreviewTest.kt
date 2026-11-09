package ai.riskmandate.app

import ai.riskmandate.app.features.FEATURES
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diagnostics > Preview in the running activity. Nothing here depends on a debuggable build: the Preview group
 * is in every build (a Firebase release build has Models, Chat and Lab switched off, and Dinis opens them here).
 */
@RunWith(AndroidJUnit4::class)
class HostPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val switchedOff = FEATURES.filter { !it.enabled }

    private fun openDiagnostics() {
        compose.onNodeWithTag(HomeTags.DIAGNOSTICS).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test fun diagnosticsListsEverySwitchedOffFeatureAndTheBench() {
        assertTrue("this test needs at least one switched-off feature", switchedOff.isNotEmpty())
        openDiagnostics()
        compose.onNodeWithTag(DiagnosticsTags.PREVIEW).performScrollTo().assertIsDisplayed()
        for (f in switchedOff) compose.onNodeWithTag(DiagnosticsTags.previewItem(f.id)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(DiagnosticsTags.MODEL_BENCH).performScrollTo().assertIsDisplayed()
    }

    @Test fun switchedOffFeaturesAreNotOnHomeOrInTheSidebar() {
        for (f in switchedOff) {
            compose.onNodeWithTag(HomeTags.feature(f.id)).assertDoesNotExist()
            compose.onNodeWithTag(ShellTags.item(f.id)).assertDoesNotExist()
        }
    }

    @Test fun eachPreviewItemOpensItsOwnScreenAndTheHomeButtonComesBack() {
        for (f in switchedOff) {
            openDiagnostics()
            compose.onNodeWithTag(DiagnosticsTags.previewItem(f.id)).performScrollTo().performClick()
            compose.waitForIdle()
            // On a phone the top bar is the header: Diagnostics is gone and the title is the feature's.
            compose.onNodeWithTag(DiagnosticsTags.PREVIEW).assertDoesNotExist()
            compose.onNodeWithTag(ShellTags.TOP_TITLE).assertIsDisplayed()
            compose.onNodeWithTag(ShellTags.HOME).performClick()
            compose.waitForIdle()
        }
    }

    @Test fun theBenchOpensFromPreview() {
        openDiagnostics()
        compose.onNodeWithTag(DiagnosticsTags.MODEL_BENCH).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(DiagnosticsTags.PREVIEW).assertDoesNotExist()
        compose.onNodeWithTag(ShellTags.TOP_TITLE).assertIsDisplayed()
    }
}

/** Stand mode on from the start (the saved setting): Diagnostics, and with it Preview, is not reachable. */
@RunWith(AndroidJUnit4::class)
class HostPreviewStandModeTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createEmptyComposeRule()

    @Test fun inStandModeDiagnosticsAndPreviewAreHidden() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ai.riskmandate.app.features.stand.StandSettings.of(app).enabled = true
        try {
            androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitForIdle()
                compose.onNodeWithTag(HomeTags.DIAGNOSTICS).assertDoesNotExist()
                compose.onNodeWithTag(ShellTags.item(Routes.DIAGNOSTICS)).assertDoesNotExist()
                for (f in FEATURES.filter { !it.enabled }) compose.onNodeWithTag(ShellTags.item(f.id)).assertDoesNotExist()
            }
        } finally {
            ai.riskmandate.app.features.stand.StandSettings.of(app).enabled = false
        }
    }
}
