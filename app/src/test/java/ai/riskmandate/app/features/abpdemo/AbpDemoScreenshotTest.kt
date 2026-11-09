package ai.riskmandate.app.features.abpdemo

import ai.riskmandate.app.RiskMandateTheme
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

/** Writes app/build/screenshots/abpdemo-<step>-<tablet>-<theme>.png for the stand and for review. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AbpDemoScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun stepsOnALandscapeTablet() {
        val out = File("build/screenshots").apply { mkdirs() }
        for (dark in listOf(false, true)) for (step in listOf(0, 1, 4, 6)) {
            RuntimeEnvironment.setQualifiers("w1280dp-h800dp-land-${if (dark) "night" else "notnight"}-xhdpi")
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.setContent { RiskMandateTheme(darkTheme = dark) { AbpDemoScreen(onBack = {}, initial = DemoPlayer(step)) } }
                }
                compose.waitForIdle()
                var bitmap: Bitmap? = null
                scenario.onActivity { a ->
                    val v = a.window.decorView
                    bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
                }
                val f = File(out, "abpdemo-step${step + 1}-tablet-${if (dark) "dark" else "light"}.png")
                f.outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue(f.length() > 1000)
            }
        }
    }
}
