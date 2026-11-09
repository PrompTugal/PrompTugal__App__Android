package ai.riskmandate.app

import android.graphics.Bitmap
import android.graphics.Canvas
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
 * The real app, not samples: launches [MainActivity] with the features enabled in `Features.kt` and their
 * kickers, summaries and badges, and writes PNGs to `app/build/screenshots/app-<device>-<orientation>-<theme>.png`.
 * For the release article and the store listing. What shows is whatever is enabled on this branch.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RealAppScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    private data class Shot(val device: String, val wDp: Int, val hDp: Int, val density: String, val landscape: Boolean, val dark: Boolean)

    private val shots = listOf(
        Shot("tablet10", 800, 1280, "xhdpi", landscape = true, dark = false),
        Shot("tablet10", 800, 1280, "xhdpi", landscape = true, dark = true),
        Shot("tablet10", 800, 1280, "xhdpi", landscape = false, dark = false),
        Shot("tablet7", 600, 960, "xhdpi", landscape = true, dark = false),
        Shot("tablet7", 600, 960, "xhdpi", landscape = true, dark = true),
        Shot("phone", 360, 800, "xxhdpi", landscape = false, dark = false),
        Shot("phone", 360, 800, "xxhdpi", landscape = false, dark = true),
    )

    @Test fun theRealAppHome() {
        val out = File("build/screenshots").apply { mkdirs() }
        for (s in shots) {
            val (w, h) = if (s.landscape) s.hDp to s.wDp else s.wDp to s.hDp
            RuntimeEnvironment.setQualifiers(
                "w${w}dp-h${h}dp-${if (s.landscape) "land" else "port"}-${if (s.dark) "night" else "notnight"}-${s.density}",
            )
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitForIdle()
                var bitmap: Bitmap? = null
                scenario.onActivity { activity ->
                    val view = activity.window.decorView
                    bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap!!))
                }
                val file = File(out, "app-${s.device}-${if (s.landscape) "landscape" else "portrait"}-${if (s.dark) "dark" else "light"}.png")
                file.outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue("${file.name} is empty", file.length() > 1000)
            }
        }
    }
}
