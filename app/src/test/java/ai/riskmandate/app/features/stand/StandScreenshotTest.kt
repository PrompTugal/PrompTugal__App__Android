package ai.riskmandate.app.features.stand

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import ai.riskmandate.app.RiskMandateTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Stand-mode settings dialog on a 10" tablet (1600x2560 px portrait, landscape too), light and dark, in
 * English and pt-PT. Writes app/build/screenshots/stand-<state>-<orientation>-<theme>[-pt].png.
 * A dialog is its own window and would not be drawn, so the same contents are drawn inline
 * ([SettingsDialogPreview]) over a dimmed background.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StandScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val outDir = File("build/screenshots")

    private fun shoot(name: String, enabled: Boolean, pinned: Boolean, minutes: Int) {
        outDir.mkdirs()
        for (landscape in listOf(false, true)) for (dark in listOf(false, true)) for (pt in listOf(false, true)) {
            val (w, h) = if (landscape) 1280 to 800 else 800 to 1280
            RuntimeEnvironment.setQualifiers(
                "${if (pt) "pt-rPT-" else ""}w${w}dp-h${h}dp-${if (landscape) "land" else "port"}-" +
                    "${if (dark) "night" else "notnight"}-xhdpi",
            )
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.setContent {
                        RiskMandateTheme(darkTheme = dark) {
                            Surface(Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    SettingsDialogPreview(enabled, pinned, minutes)
                                }
                            }
                        }
                    }
                }
                compose.waitForIdle()
                var bitmap: Bitmap? = null
                scenario.onActivity { activity ->
                    val view = activity.window.decorView
                    bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap!!))
                }
                val file = File(
                    outDir,
                    "stand-$name-${if (landscape) "landscape" else "portrait"}-${if (dark) "dark" else "light"}${if (pt) "-pt" else ""}.png",
                )
                file.outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue("${file.name} is empty", file.length() > 1000)
            }
        }
    }

    @Test fun settingsOnAndPinned() = shoot("settings-on", enabled = true, pinned = true, minutes = 3)

    @Test fun settingsOff() = shoot("settings-off", enabled = false, pinned = false, minutes = 3)
}
