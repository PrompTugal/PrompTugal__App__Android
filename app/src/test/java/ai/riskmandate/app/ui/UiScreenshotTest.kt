package ai.riskmandate.app.ui

import ai.riskmandate.app.RiskMandateTheme
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
 * Renders the shared components as the feature screens will use them, on the stand tablet first
 * (10" and 7" landscape), then phones, light and dark. PNGs go to
 * app/build/screenshots/ui-<screen>-<device>-<orientation>-<theme>.png, same recipe as ScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    private data class Device(val name: String, val wDp: Int, val hDp: Int, val density: String)

    // Portrait sizes in dp, as in ScreenshotTest.
    private val devices = listOf(
        Device("tablet10", 800, 1280, "xhdpi"),
        Device("tablet7", 600, 960, "xhdpi"),
        Device("phone", 360, 800, "xxhdpi"),
    )
    private val outDir = File("build/screenshots")

    private fun shoot(screen: String, content: @Composable () -> Unit) {
        outDir.mkdirs()
        for (d in devices) for (landscape in listOf(true, false)) for (dark in listOf(false, true)) {
            val (w, h) = if (landscape) d.hDp to d.wDp else d.wDp to d.hDp
            RuntimeEnvironment.setQualifiers(
                "w${w}dp-h${h}dp-${if (landscape) "land" else "port"}-${if (dark) "night" else "notnight"}-${d.density}",
            )
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { it.setContent { RiskMandateTheme(darkTheme = dark) { content() } } }
                compose.waitForIdle()
                var bitmap: Bitmap? = null
                scenario.onActivity { activity ->
                    val view = activity.window.decorView
                    bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap!!))
                }
                val file = File(outDir, "ui-$screen-${d.name}-${if (landscape) "landscape" else "portrait"}-${if (dark) "dark" else "light"}.png")
                file.outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue("${file.name} is empty", file.length() > 1000)
            }
        }
    }

    private val midStory = listOf(
        LayerState.Passed, LayerState.Passed, LayerState.Passed, LayerState.Stopped, LayerState.Waiting, LayerState.Waiting,
    )

    @Test fun layersStand() = shoot("layers-large") { LayersSample(midStory, large = true) }

    @Test fun layersWithDescriptions() = shoot("layers") {
        LayersSample(
            listOf(LayerState.Passed, LayerState.Checking, LayerState.Waiting, LayerState.Waiting, LayerState.Waiting, LayerState.Waiting),
            large = false,
        )
    }

    @Test fun cards() = shoot("cards") { CardsSample() }

    @Test fun lab() = shoot("lab") { LabSample() }

    @Test fun now() = shoot("now") { Surface(color = ai.riskmandate.app.Pt.colors.paper) { NowSample() } }

    @Test fun shell() = shoot("shell") { ShellSample() }
}
