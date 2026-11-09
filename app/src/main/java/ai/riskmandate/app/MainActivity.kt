package ai.riskmandate.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import ai.riskmandate.app.features.FEATURES
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.features.featureById
import ai.riskmandate.app.features.stand.StandHost

/** Routes: the built-in screens, or a summit feature's id (features/Features.kt). */
object Routes {
    const val HOME = "home"
    const val LICENCES = "licences"
    const val DIAGNOSTICS = "diagnostics"
}

class MainActivity : ComponentActivity() {
    private var config by mutableStateOf(ManagedConfig.DEFAULT)
    private var device by mutableStateOf(DeviceStatus(Management.NOT_DETECTED, null))
    private var message by mutableStateOf<Int?>(null)

    private val restrictionsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refresh()
        setContent {
            RiskMandateTheme {
                Surface(Modifier.fillMaxSize()) {
                    // Saved, so turning a tablet round does not drop the user back on the home screen.
                    var screen by rememberSaveable { mutableStateOf(Routes.HOME) }
                    val goHome = { screen = Routes.HOME }
                    StandHost(activity = this@MainActivity, atHome = screen == Routes.HOME, goHome = goHome) {
                    when (screen) {
                        Routes.LICENCES -> {
                            BackHandler { screen = Routes.HOME }
                            LicencesScreen(
                                text = remember { readLicences(this@MainActivity) },
                                apacheText = remember { readApacheLicence(this@MainActivity) },
                                onBack = { screen = Routes.HOME },
                            )
                        }
                        Routes.DIAGNOSTICS -> {
                            BackHandler { screen = Routes.HOME }
                            // Read again each time the screen opens, so it shows the current state.
                            val text = remember { DiagnosticsInfo.collect(this@MainActivity, config, device).toText() }
                            DiagnosticsScreen(text = text, onCopy = rememberCopyAction(text), onBack = { screen = Routes.HOME })
                        }
                        Routes.HOME -> HomeScreen(
                            version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            device = device,
                            message = message,
                            onOpen = { message = open() },
                            onLicences = { screen = Routes.LICENCES },
                            onDiagnostics = { screen = Routes.DIAGNOSTICS },
                            features = FEATURES.filter { it.enabled }.map { f -> FeatureButton(f.id, f.title) { screen = f.id } },
                        )
                        else -> {
                            val feature = featureById(screen)
                            if (feature == null) {
                                screen = Routes.HOME
                            } else {
                                BackHandler { screen = Routes.HOME }
                                feature.Content(FeatureContext(activity = this@MainActivity, onBack = goHome))
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    /** Opens the start page; returns the message to show, if any. Never throws. */
    private fun open(): Int? {
        val result = try {
            Browser.open(this, config.startUrl)
        } catch (e: RuntimeException) {
            OpenResult.FAILED
        }
        // The browser opens anyway and shows its own error page; this tells the user why it may fail.
        return Browser.message(result) ?: if (Browser.isOffline(this)) R.string.message_offline else null
    }

    override fun onResume() {
        super.onResume()
        // Restrictions can change while the app is stopped, so read them again on every resume,
        // and listen for changes while the app is in front.
        refresh()
        ContextCompat.registerReceiver(
            this,
            restrictionsReceiver,
            IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onPause() {
        unregisterReceiver(restrictionsReceiver)
        super.onPause()
    }

    private fun refresh() {
        config = ManagedConfig.read(this)
        device = DeviceStatus.detect(this, config.deviceLabel)
    }
}
