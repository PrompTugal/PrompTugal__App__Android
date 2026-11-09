package ai.riskmandate.app

import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val info = DiagnosticsInfo(
        versionName = "0.1.7", versionCode = 7, applicationId = "ai.riskmandate.app", buildType = "release",
        androidRelease = "14", sdkInt = 34, manufacturer = "Lenovo", model = "TB-X606F",
        device = DeviceStatus.from(true, false, "Lisbon tablet 3"),
        config = ManagedConfig(Links.HOME, "Lisbon tablet 3"),
        installerPackage = "com.android.vending",
    )

    @Test fun textHasEveryField() {
        assertEquals(
            """
            RiskMandate diagnostics
            App version: 0.1.7
            Version code: 7
            Application ID: ai.riskmandate.app
            Build type: release
            Android: 14 (API 34)
            Device: Lenovo TB-X606F
            Management: fully managed
            Managed configuration:
              start_url: https://riskmandate.ai/
              start_url refused: no
              device_label: Lisbon tablet 3
            Installer: Google Play (com.android.vending)
            """.trimIndent(),
            info.toText(),
        )
    }

    @Test fun refusedUrlAndMissingLabelAndUnknownInstaller() {
        val text = info.copy(
            config = ManagedConfig(Links.HOME, null, startUrlRefused = true),
            device = DeviceStatus.from(false, false, null),
            installerPackage = null,
        ).toText()
        assertTrue(text.contains("start_url refused: yes (fell back to the default)"))
        assertTrue(text.contains("device_label: (not set)"))
        assertTrue(text.contains("Management: not detected"))
        assertTrue(text.contains("Installer: unknown (adb or a sideload)"))
    }

    @Test fun installerNames() {
        assertTrue(DiagnosticsInfo.installerLabel("dev.firebase.appdistribution").startsWith("Firebase App Distribution"))
        assertEquals("org.example.store", DiagnosticsInfo.installerLabel("org.example.store"))
    }

    @Test fun collectedTextHoldsNoIdentifiers() {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val text = DiagnosticsInfo.collect(context, ManagedConfig.DEFAULT, DeviceStatus.from(false, false, null)).toText()
        assertTrue(text.contains("Application ID: ai.riskmandate.app"))
        assertTrue(text.contains("Version code: "))
        if (!androidId.isNullOrEmpty()) assertFalse(text.contains(androidId))
        assertFalse(text.contains(android.os.Build.SERIAL.takeIf { it.length > 3 && it != "unknown" } ?: "\u0000none"))
        assertFalse(Regex("""\b\d{15}\b""").containsMatchIn(text)) // IMEI shape
    }

    @Test fun copyButtonPutsPlainTextOnTheClipboard() {
        val text = info.toText()
        compose.setContent {
            MaterialTheme { DiagnosticsScreen(text = text, onCopy = { copyToClipboard(context, "Diagnostics", text) }) }
        }
        compose.onNodeWithTag(DiagnosticsTags.TEXT).assertTextContains("Version code: 7", substring = true)
        compose.onNodeWithTag(DiagnosticsTags.COPY).performClick()
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals(text, clip.getItemAt(0).text.toString())
        compose.onNodeWithTag(DiagnosticsTags.COPIED).assertTextContains("Copied", substring = true)
    }

    @Test fun modelBenchButtonOpensTheBenchAndIsHiddenWithoutIt() {
        var opened = 0
        var withBench by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                DiagnosticsScreen(text = "x", onCopy = { true }, onModelBench = if (withBench) ({ opened++ }) else null)
            }
        }
        compose.onNodeWithTag(DiagnosticsTags.MODEL_BENCH).performScrollTo().performClick()
        assertEquals(1, opened)
        withBench = false
        compose.onNodeWithTag(DiagnosticsTags.MODEL_BENCH).assertDoesNotExist()
    }
}
