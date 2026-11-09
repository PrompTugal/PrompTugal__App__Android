package ai.riskmandate.app.features.site

import ai.riskmandate.app.FeatureFrame
import ai.riskmandate.app.R
import ai.riskmandate.app.RiskMandateTheme
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
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
 * The site screen in the offline-copy state (banner showing, so no network is needed), inside the host's
 * FeatureFrame, on a phone (portrait) and a 10" tablet (landscape), light and dark. PNGs go to
 * app/build/screenshots/site-<device>-<orientation>-<theme>.png.
 *
 * Robolectric does not render web content: the area under the tabs is the WebView, which stays blank here.
 * What the pictures show is the native chrome: tabs, the "offline copy from <date>" banner, the privacy line.
 * The page itself needs a real device.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SiteScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    private data class Shot(val name: String, val wDp: Int, val hDp: Int, val density: String, val landscape: Boolean)

    private val shots = listOf(
        Shot("phone", 360, 800, "xxhdpi", landscape = false),
        Shot("tablet10", 800, 1280, "xhdpi", landscape = true),
    )

    @Test fun siteScreens() {
        val outDir = File("build/screenshots").apply { mkdirs() }
        for (s in shots) for (dark in listOf(false, true)) {
            val (w, h) = if (s.landscape) s.hDp to s.wDp else s.wDp to s.hDp
            RuntimeEnvironment.setQualifiers(
                "w${w}dp-h${h}dp-${if (s.landscape) "land" else "port"}-${if (dark) "night" else "notnight"}-${s.density}",
            )
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.setContent {
                        RiskMandateTheme(darkTheme = dark) {
                            Surface {
                                FeatureFrame(title = R.string.feature_site_title, onBack = {}) {
                                    SiteScreen(OfflineSwitch(true), onBack = {}, isSystemOffline = { true }, openExternal = { _, _ -> null })
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
                val file = File(outDir, "site-${s.name}-${if (s.landscape) "landscape" else "portrait"}-${if (dark) "dark" else "light"}.png")
                file.outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue("${file.name} is empty", file.length() > 1000)
            }
        }
    }
}
