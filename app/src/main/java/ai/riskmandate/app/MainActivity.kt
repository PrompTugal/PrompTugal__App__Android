package ai.riskmandate.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
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

/** How many of an app's [requested] permissions are Android's own: the app's own custom ones (from `<packageName>.`) do not count. */
fun countRequestedPermissions(requested: Array<String>?, packageName: String): Int =
    requested.orEmpty().count { !it.startsWith("$packageName.") }

/** The permissions this app asks for in its manifest (derived, shown on Home). A failure counts as zero. */
fun requestedPermissionCount(context: android.content.Context): Int = try {
    val pm = context.packageManager
    val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
        pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(android.content.pm.PackageManager.GET_PERMISSIONS.toLong()))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
    }
    countRequestedPermissions(info.requestedPermissions, context.packageName)
} catch (e: Exception) {
    0
}

/**
 * The route to show for a saved [screen]: itself if it is a built-in or an enabled feature, else Home;
 * and Home for the staff-only screens (Diagnostics, the model bench) while stand mode is on, and for a restored
 * model-bench route when the bench is not available (release build with no model file pushed).
 */
fun resolveRoute(screen: String, standOn: Boolean, featureEnabled: Boolean, benchAvailable: Boolean = true): String = when {
    standOn && (screen == Routes.DIAGNOSTICS || screen == Routes.MODEL_BENCH) -> Routes.HOME
    screen == Routes.MODEL_BENCH && !benchAvailable -> Routes.HOME
    screen in BUILT_IN || featureEnabled -> screen
    else -> Routes.HOME
}

/** Which sidebar section a feature sits in, by id (design spec table); unknown ids go to Explore. */
fun sectionOf(id: String): Int = when (id) {
    "site", "releases" -> R.string.nav_section_today
    "mandate", "models" -> R.string.nav_section_device
    else -> R.string.nav_section_explore
}

/** The order of features inside their sections (design spec table). */
private val NAV_ORDER = listOf("site", "releases", "abpdemo", "lab", "chat", "mandate", "models")

/** A line icon per known feature id, until each feature gives its own (Feature.icon). */
fun iconFor(id: String): Int = when (id) {
    "site" -> R.drawable.ic_nav_globe
    "releases" -> R.drawable.ic_nav_news
    "abpdemo" -> R.drawable.ic_nav_layers
    "lab" -> R.drawable.ic_nav_lab
    "chat" -> R.drawable.ic_nav_chat
    "mandate" -> R.drawable.ic_nav_shield
    "models" -> R.drawable.ic_nav_chip
    else -> R.drawable.ic_nav_star
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
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Saved, so turning a tablet round does not drop the user back on the home screen.
                    var screen by rememberSaveable { mutableStateOf(Routes.HOME) }
                    val goHome = { screen = Routes.HOME }
                    // A route that no longer exists or is switched off (restored after an app update or
                    // process death) shows Home, and the saved route is reset after composition.
                    val feature = FEATURES.firstOrNull { it.id == screen && it.enabled }
                    // Stand mode (a visitor at a stand): Diagnostics and the model bench are staff tools, so they
                    // are hidden and unreachable while it is on (R-shell-1). Set from StandHost, see standOn below.
                    var standOn by remember { mutableStateOf(false) }
                    val benchAvailable = remember { ModelFiles.benchAvailable(this@MainActivity, BuildConfig.DEBUG) }
                    val route = resolveRoute(screen, standOn, featureEnabled = feature != null, benchAvailable = benchAvailable)
                    LaunchedEffect(route, screen) { if (route != screen) screen = route }
                    // Features that run unattended on purpose (abpdemo's auto-play) hold stand mode's idle
                    // reset off through this. HoldIdleWhile releases when the feature leaves the screen; the
                    // host does not clear holds itself (that wiped a feature's hold after rotation, A-app-7).
                    val idle = remember { IdleHolds() }
                    // Destinations, grouped as in docs/design/android-theme.md. Features are grouped by id (a
                    // `section` on Feature is the architect's to add) and use their own icon when they give one.
                    val featureItems = FEATURES.filter { it.enabled }.map { f ->
                        NavItem(f.id, f.title, iconFor(f.id), f.homeBadge(), vector = f.icon)
                    }
                    fun inSection(section: Int) = featureItems.filter { sectionOf(it.route) == section }
                        .sortedBy { NAV_ORDER.indexOf(it.route).let { i -> if (i < 0) NAV_ORDER.size else i } }
                    val sections = listOf(
                        NavSection(
                            R.string.nav_section_today,
                            listOf(NavItem(Routes.HOME, R.string.home_now_title, R.drawable.ic_nav_home)) + inSection(R.string.nav_section_today),
                        ),
                        NavSection(R.string.nav_section_explore, inSection(R.string.nav_section_explore)),
                        NavSection(
                            R.string.nav_section_device,
                            inSection(R.string.nav_section_device) + listOfNotNull(
                                NavItem(Routes.LICENCES, R.string.home_licences, R.drawable.ic_nav_doc),
                                if (standOn) null else NavItem(Routes.DIAGNOSTICS, R.string.home_diagnostics, R.drawable.ic_nav_pulse),
                            ),
                        ),
                    )
                    val currentTitle = when (route) {
                        Routes.HOME -> R.string.home_now_title
                        Routes.LICENCES -> R.string.licences_title
                        Routes.DIAGNOSTICS -> R.string.diagnostics_title
                        Routes.MODEL_BENCH -> R.string.feature_models_title
                        else -> feature?.title ?: R.string.home_now_title
                    }
                    val permissionCount = remember { requestedPermissionCount(this@MainActivity) }
                    val prefs = remember { getSharedPreferences("shell", MODE_PRIVATE) }
                    var sidebarCollapsed by remember { mutableStateOf(prefs.getBoolean("sidebar_collapsed", false)) }
                    StandHost(
                        activity = this@MainActivity,
                        atHome = route == Routes.HOME,
                        goHome = goHome,
                        idleHeld = idle.held,
                        // Called with the saved value at start, then on each change. In stand mode the sidebar
                        // starts folded to the rail so the content is the screen (staff can open it; not saved,
                        // so it does not stay folded after stand mode ends).
                        onEnabledChange = { on ->
                            standOn = on
                            if (on) sidebarCollapsed = true
                        },
                    ) {
                        AppShell(
                            sections = sections,
                            selected = route,
                            currentTitle = currentTitle,
                            atHome = route == Routes.HOME,
                            onSelect = { screen = it },
                            onBackHome = goHome,
                            brandLine = device.label ?: stringResource(R.string.shell_brand_line),
                            sidebarCollapsed = sidebarCollapsed,
                            onSidebarCollapsedChange = {
                                sidebarCollapsed = it
                                prefs.edit().putBoolean("sidebar_collapsed", it).apply()
                            },
                        ) {
                            when {
                                route == Routes.LICENCES -> FeatureFrame(title = R.string.licences_title, kicker = R.string.nav_section_device) {
                                    LicencesScreen(
                                        text = remember { readLicences(this@MainActivity) },
                                        apacheText = remember { readApacheLicence(this@MainActivity) },
                                    )
                                }
                                route == Routes.DIAGNOSTICS -> {
                                    val text = remember { DiagnosticsInfo.collect(this@MainActivity, config, device).toText() }
                                    // Only on a debuggable build or once a model file is pushed (architect A-llm-5).
                                    FeatureFrame(title = R.string.diagnostics_title, kicker = R.string.nav_section_device) {
                                        DiagnosticsScreen(
                                            text = text, onCopy = rememberCopyAction(text),
                                            onModelBench = if (benchAvailable) ({ screen = Routes.MODEL_BENCH }) else null,
                                        )
                                    }
                                }
                                route == Routes.MODEL_BENCH -> FeatureFrame(title = R.string.feature_models_title, kicker = R.string.nav_section_device) {
                                    ModelBenchScreen()
                                }
                                feature != null -> FeatureFrame(title = feature.title, kicker = feature.kicker ?: sectionOf(feature.id)) {
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
                                    permissions = permissionCount,
                                    attention = (if (config.startUrlRefused) 1 else 0) + (if (message != null) 1 else 0),
                                    features = FEATURES.filter { it.enabled }.mapIndexed { i, f ->
                                        FeatureButton(f.id, f.title, featureItems[i].badge, f.kicker, f.summary) { screen = f.id }
                                    },
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
