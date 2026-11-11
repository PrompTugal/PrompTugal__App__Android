package ai.riskmandate.app.features.xray

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
 * The kiosk's own screen, the offline cover (Robolectric draws no web content, so the page itself needs a device),
 * on a phone (portrait) and a 10" tablet (landscape). PNGs go to app/build/screenshots/xray-cover-<device>-<orientation>.png.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class XrayScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun theCover() {
        val outDir = File("build/screenshots").apply { mkdirs() }
        for ((name, qualifiers) in listOf(
            "phone-portrait" to "w360dp-h800dp-port-xxhdpi",
            "tablet10-landscape" to "w1280dp-h800dp-land-xhdpi",
        )) {
            RuntimeEnvironment.setQualifiers(qualifiers)
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.setContent {
                        RiskMandateTheme(darkTheme = true) {
                            Surface { XrayKiosk(onExit = {}, startUrl = XrayPolicy.START_URL, exitHoldMs = 3_000, clock = { 0L }, showWebView = false, startFailed = true) }
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
                val file = File(outDir, "xray-cover-$name.png")
                file.outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue("${file.name} is empty", file.length() > 1000)
            }
        }
    }
}
