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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import ai.riskmandate.app.features.FEATURES
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.features.IdleHolds
import ai.riskmandate.app.features.stand.StandHost
import ai.riskmandate.app.llm.ModelFiles
import ai.riskmandate.app.llm.bench.ModelBenchScreen

/** Routes: the built-in screens, or a summit feature's id (features/Features.kt). */
object Routes {
    const val HOME = "home"
    const val LICENCES = "licences"
    const val DIAGNOSTICS = "diagnostics"

    /** The on-device LLM bench (dev.llm), reached from Diagnostics only on a debuggable build or once a model file is pushed. */
    const val MODEL_BENCH = "modelbench"
}

/**
 * The route to show for a saved [screen]: itself if it is a built-in or an enabled feature, else Home;
 * and Home for the staff-only screens (Diagnostics, the model bench) while stand mode is on.
 */
fun resolveRoute(screen: String, standOn: Boolean, featureEnabled: Boolean): String = when {
    standOn && (screen == Routes.DIAGNOSTICS || screen == Routes.MODEL_BENCH) -> Routes.HOME
    screen in BUILT_IN || featureEnabled -> screen
    else -> Routes.HOME
}

private val BUILT_IN = setOf(Routes.HOME, Routes.LICENCES, Routes.DIAGNOSTICS, Routes.MODEL_BENCH)

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
                    // A route that no longer exists or is switched off (restored after an app update or
                    // process death) shows Home, and the saved route is reset after composition.
                    val feature = FEATURES.firstOrNull { it.id == screen && it.enabled }
                    // Stand mode (a visitor at a stand): Diagnostics and the model bench are staff tools, so they
                    // are hidden and unreachable while it is on (R-shell-1). Set from StandHost, see standOn below.
                    var standOn by remember { mutableStateOf(false) }
                    val route = resolveRoute(screen, standOn, featureEnabled = feature != null)
                    LaunchedEffect(route, screen) { if (route != screen) screen = route }
                    // Features that run unattended on purpose (abpdemo's auto-play) hold stand mode's idle
                    // reset off through this. HoldIdleWhile releases when the feature leaves the screen; the
                    // host does not clear holds itself (that wiped a feature's hold after rotation, A-app-7).
                    val idle = remember { IdleHolds() }
                    // Destinations: Home, then each enabled feature (with its badge), then the about screens.
                    val featureItems = FEATURES.filter { it.enabled }.map { f ->
                        NavItem(f.id, f.title, Icons.Filled.Star, f.homeBadge())
                    }
                    val sections = listOf(
                        NavSection(R.string.nav_section_now, listOf(NavItem(Routes.HOME, R.string.nav_home, Icons.Filled.Home))),
                        NavSection(R.string.nav_section_features, featureItems),
                        NavSection(
                            R.string.home_about_heading,
                            listOfNotNull(
                                if (standOn) null else NavItem(Routes.DIAGNOSTICS, R.string.home_diagnostics, Icons.Filled.Build),
                                NavItem(Routes.LICENCES, R.string.home_licences, Icons.Filled.Info),
                            ),
                        ),
                    )
                    val currentTitle = when (route) {
                        Routes.HOME -> R.string.nav_home
                        Routes.LICENCES -> R.string.home_licences
                        Routes.DIAGNOSTICS -> R.string.home_diagnostics
                        Routes.MODEL_BENCH -> R.string.feature_models_title
                        else -> feature?.title ?: R.string.nav_home
                    }
                    // standOn must follow stand mode: wire `onEnabledChange = { standOn = it }` here once StandHost reports it
                    // (a small additive parameter owned by dev.stand; asked of the conductor). Until then it stays false.
                    StandHost(activity = this@MainActivity, atHome = route == Routes.HOME, goHome = goHome, idleHeld = idle.held) {
                        AppShell(
                            sections = sections,
                            selected = route,
                            currentTitle = currentTitle,
                            atHome = route == Routes.HOME,
                            onSelect = { screen = it },
                            onBackHome = goHome,
                        ) {
                            when {
                                route == Routes.LICENCES -> LicencesScreen(
                                    text = remember { readLicences(this@MainActivity) },
                                    apacheText = remember { readApacheLicence(this@MainActivity) },
                                    onBack = goHome,
                                )
                                route == Routes.DIAGNOSTICS -> {
                                    val text = remember { DiagnosticsInfo.collect(this@MainActivity, config, device).toText() }
                                    // Only on a debuggable build or once a model file is pushed (architect A-llm-5).
                                    val bench = remember { ModelFiles.benchAvailable(this@MainActivity, BuildConfig.DEBUG) }
                                    DiagnosticsScreen(
                                        text = text, onCopy = rememberCopyAction(text), onBack = goHome,
                                        onModelBench = if (bench) ({ screen = Routes.MODEL_BENCH }) else null,
                                    )
                                }
                                route == Routes.MODEL_BENCH -> FeatureFrame(title = R.string.feature_models_title, onBack = goHome) {
                                    ModelBenchScreen()
                                }
                                feature != null -> FeatureFrame(title = feature.title, onBack = goHome) {
                                    feature.Content(remember(config, device) { FeatureContext(onBack = goHome, config = config, device = device, holdIdle = idle::hold) })
                                }
                                else -> HomeScreen(
                                    version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                    device = device,
                                    message = message,
                                    onOpen = { message = open() },
                                    onLicences = { screen = Routes.LICENCES },
                                    onDiagnostics = { screen = Routes.DIAGNOSTICS },
                                    showDiagnostics = !standOn,
                                    features = featureItems.map { n -> FeatureButton(n.route, n.title, n.badge) { screen = n.route } },
                                )
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
