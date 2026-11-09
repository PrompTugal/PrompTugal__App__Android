package ai.riskmandate.app.features.releases

import ai.riskmandate.app.RiskMandateTheme
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The What's new screen itself, for the release articles and the store: writes
 * `app/build/screenshots/releases-<screen>-<device>-<orientation>-<theme>.png`, same folder and naming as
 * `ScreenshotTest`. Screens: `list` and `reader` (on a tablet in landscape the reader also shows the list).
 * Contract for CI and the editor: README.md in this package.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReleasesScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    private data class Shot(val device: String, val wDp: Int, val hDp: Int, val density: String, val landscape: Boolean)

    private val shots = listOf(
        Shot("phone", 360, 800, "xxhdpi", landscape = false),
        Shot("tablet10", 1280, 800, "xhdpi", landscape = true),
    )
    private val outDir = File("build/screenshots")

    private fun shoot(screen: String, content: @Composable () -> Unit) {
        outDir.mkdirs()
        for (s in shots) for (dark in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers(
                "w${s.wDp}dp-h${s.hDp}dp-${if (s.landscape) "land" else "port"}-${if (dark) "night" else "notnight"}-${s.density}",
            )
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { it.setContent { RiskMandateTheme(darkTheme = dark) { Surface { content() } } } }
                compose.waitForIdle()
                // Images decode off the main thread: give them a moment before the frame is drawn.
                repeat(5) {
                    Thread.sleep(200)
                    // Advance Robolectric's clock so Choreographer frames run: decoded images then get laid out.
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
                    compose.waitForIdle()
                }
                var bitmap: Bitmap? = null
                scenario.onActivity { activity ->
                    val view = activity.window.decorView
                    // Images arrive after the first frame and nothing drives a frame here: lay out again.
                    val w = view.width
                    val h = view.height
                    view.measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY),
                    )
                    view.layout(0, 0, w, h)
                    bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap!!))
                }
                val file = File(outDir, "releases-$screen-${s.device}-${if (s.landscape) "landscape" else "portrait"}-${if (dark) "dark" else "light"}.png")
                file.outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue("${file.name} is empty", file.length() > 1000)
            }
        }
    }

    /** The bundled articles (their images come from CI), else the fixture, so the shots exist on a bare checkout. */
    private fun articles(): List<Article> =
        ReleaseStore.load(ApplicationProvider.getApplicationContext<android.app.Application>(), "en").ifEmpty { listOf(Fixture.sample) }

    private fun images(all: List<Article>): ArticleImages? = if (all.singleOrNull() === Fixture.sample) Fixture.images else null

    @Test fun listScreens() = shoot("list") {
        // Phone: the list. Tablet landscape draws the list and the reader, so the list shot uses the same screen.
        val all = articles()
        ReleasesScreen(all, selectedDir = null, onSelect = {}, onLink = {}, images = images(all))
    }

    @Test fun readerScreens() = shoot("reader") {
        val all = articles()
        ReleasesScreen(all, selectedDir = all.first().dir, onSelect = {}, onLink = {}, images = images(all))
    }
}
