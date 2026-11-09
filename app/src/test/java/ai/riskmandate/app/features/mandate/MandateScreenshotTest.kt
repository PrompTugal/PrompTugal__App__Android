package ai.riskmandate.app.features.mandate

import ai.riskmandate.app.DeviceStatus
import ai.riskmandate.app.FeatureFrame
import ai.riskmandate.app.Links
import ai.riskmandate.app.ManagedConfig
import ai.riskmandate.app.RiskMandateTheme
import ai.riskmandate.app.features.FEATURES
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
 * Writes app/build/screenshots/mandate-<size>-<theme>.png: a phone and a 10" tablet, light and dark, the
 * viewport and the whole screen ("full": a tall window, since the screen scrolls). The reach is a fixed,
 * clean one with the in-app site view on, so the picture does not depend on the machine that renders it;
 * the live reading is covered by MandateScreenTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MandateScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val pkg = "ai.riskmandate.app"
    private val mandate = Mandate.current(pkg, listOf(ManagedConfig.KEY_START_URL, ManagedConfig.KEY_DEVICE_LABEL))
    private val reach = listOf(
        ReachItem(ReachKind.PERMISSION, "android.permission.INTERNET"),
        ReachItem(ReachKind.PERMISSION, "android.permission.ACCESS_NETWORK_STATE"),
        ReachItem(ReachKind.PERMISSION, "$pkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"),
        ReachItem(ReachKind.COMPONENT, "$pkg.MainActivity", componentType = ComponentType.ACTIVITY),
    ) + networkReach(setOf("site"))
    private val config = ManagedConfig.fromMap(mapOf("device_label" to "Stand tablet 2"))
    private val state = MandateState(
        MandateReport(reach, mandate), HostProbe.run(Links::isAllowed), config, DeviceStatus.from(true, false, config.deviceLabel),
    )

    private data class Size(val name: String, val size: String, val density: String)

    private val sizes = listOf(
        Size("phone", "w360dp-h800dp-port", "xxhdpi"),
        Size("phone-full", "w360dp-h2600dp-port", "xxhdpi"),
        Size("tablet10", "w1280dp-h800dp-land", "xhdpi"),
        Size("tablet10-full", "w800dp-h2400dp-port", "xhdpi"),
    )

    @Test fun mandateScreens() {
        val out = File("build/screenshots").apply { mkdirs() }
        val title = MandateFeature.title
        for (size in sizes) for (dark in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers("${size.size}-${if (dark) "night" else "notnight"}-${size.density}")
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.setContent {
                        RiskMandateTheme(darkTheme = dark) {
                            Surface { FeatureFrame(title = title, onBack = {}) { MandateScreen(state) } }
                        }
                    }
                }
                compose.waitForIdle()
                var bitmap: Bitmap? = null
                scenario.onActivity { a ->
                    val v = a.window.decorView
                    bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
                }
                val f = File(out, "mandate-${size.name}-${if (dark) "dark" else "light"}.png")
                f.outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue("${f.name} is empty", f.length() > 1000)
            }
        }
        assertTrue(FEATURES.any { it.id == "mandate" })
    }
}
