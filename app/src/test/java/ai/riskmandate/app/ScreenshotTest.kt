package ai.riskmandate.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the home and licences screens at phone, 7" and 10" sizes, portrait and landscape, light and
 * dark, and writes PNGs to app/build/screenshots/<screen>-<device>-<orientation>-<theme>.png
 * (Robolectric native graphics, no extra plugin). Used for the Play Store listing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    private data class Device(val name: String, val wDp: Int, val hDp: Int, val density: String)

    // Portrait sizes in dp. Phone 1080x2400 px; 7" tablet 1200x1920 px; 10" tablet 1600x2560 px.
    private val devices = listOf(
        Device("phone", 360, 800, "xxhdpi"),
        Device("tablet7", 600, 960, "xhdpi"),
        Device("tablet10", 800, 1280, "xhdpi"),
    )

    private val outDir = File("build/screenshots")

    private fun shoot(screen: String, content: @Composable () -> Unit) {
        outDir.mkdirs()
        for (d in devices) for (landscape in listOf(false, true)) for (dark in listOf(false, true)) {
            val (w, h) = if (landscape) d.hDp to d.wDp else d.wDp to d.hDp
            RuntimeEnvironment.setQualifiers(
                "w${w}dp-h${h}dp-${if (landscape) "land" else "port"}-${if (dark) "night" else "notnight"}-${d.density}",
            )
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { it.setContent { RiskMandateTheme(darkTheme = dark) { Surface { content() } } } }
                compose.waitForIdle()
                // Compose's captureToImage waits for a real frame, which never comes under Robolectric's
                // paused looper; drawing the window's view tree into a bitmap (native graphics) does the job.
                var bitmap: Bitmap? = null
                scenario.onActivity { activity ->
                    val view = activity.window.decorView
                    bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap!!))
                }
                val file = File(outDir, "$screen-${d.name}-${if (landscape) "landscape" else "portrait"}-${if (dark) "dark" else "light"}.png")
                file.outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue("${file.name} is empty", file.length() > 1000)
            }
        }
    }

    @Test fun homeScreens() = shoot("home") {
        HomeScreen(
            version = "0.1.7 (7)",
            device = DeviceStatus.from(hasDeviceOwner = true, inWorkProfile = false, label = "Lisbon tablet 3"),
            onOpen = {},
        )
    }

    @Test fun licencesScreens() {
        val context = RuntimeEnvironment.getApplication()
        val text = readLicences(context)
        val apache = readApacheLicence(context)
        shoot("licences") { LicencesScreen(text = text, apacheText = apache, onBack = {}) }
    }
}
