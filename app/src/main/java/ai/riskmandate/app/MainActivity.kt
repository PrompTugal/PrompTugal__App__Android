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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ai.riskmandate.app.features.FEATURES
import ai.riskmandate.app.features.FeatureContext
import ai.riskmandate.app.features.IdleHolds
import ai.riskmandate.app.features.stand.StandHost
import ai.riskmandate.app.llm.LlmService
import ai.riskmandate.app.llm.bench.ModelBenchScreen

/** Routes: the built-in screens, or a summit feature's id (features/Features.kt). */
object Routes {
    const val HOME = "home"
    const val LICENCES = "licences"
    const val DIAGNOSTICS = "diagnostics"

    /** The on-device LLM bench (dev.llm), reached from Diagnostics only on a debuggable build or once a model file is pushed. */
    const val MODEL_BENCH = "modelbench"

    /** The staff-only OpenRouter key screen (Diagnostics > Cloud model). Never reachable in stand mode. */
    const val CLOUD = "cloudkey"

    /** A feature that is not switched on yet, opened from Diagnostics > Preview (never on Home or in the sidebar). */
    fun preview(id: String) = "preview/$id"
    fun previewId(route: String): String? = if (route.startsWith("preview/")) route.removePrefix("preview/") else null
}

/**
 * What the host hands every feature: the managed configuration, the device state, the idle hold, the
 * `openFeature` request and the process's one on-device model service ([LlmProcess], created on first use).
 */
fun hostFeatureContext(
    context: android.content.Context,
    onBack: () -> Unit,
    config: ManagedConfig,
    device: DeviceStatus,
    holdIdle: () -> () -> Unit,
    openFeature: (String) -> Unit,
    /** The cloud service for this feature, or null (see [cloudLlmFor]). */
    cloudLlm: LlmService? = null,
    /** The fact that the cloud may be used at all (switch on, key stored, no stand mode), for every feature. */
    cloudOn: Boolean = false,
): FeatureContext = FeatureContext(
    onBack = onBack, config = config, device = device, holdIdle = holdIdle, openFeature = openFeature,
    llm = LlmProcess.get(context), cloudLlm = cloudLlm, cloudOn = cloudOn,
)

/**
 * The route to open when a feature asks for [id] through `FeatureContext.openFeature`, or null when the host
 * refuses: an id that is unknown, or any request while stand mode is on (stand mode keeps a visitor on the
 * screens staff chose). A switched-off feature opens only when [previewIds] holds it, which the host passes
 * only while a Preview screen is showing (Diagnostics > Preview).
 */
fun resolveOpenFeature(id: String, standOn: Boolean, enabledIds: Set<String>, previewIds: Set<String> = emptySet()): String? = when {
    standOn -> null
    id in enabledIds -> id
    id in previewIds -> Routes.preview(id)
    else -> null
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
 * and Home for the staff-only screens (Diagnostics, the model bench, every Preview screen) while stand mode
 * is on. A `preview/<id>` route (produced only by the Preview list in Diagnostics) is accepted for any known
 * feature id, enabled or not; an unknown id goes Home. A plain `<id>` of a switched-off feature still goes Home,
 * so a restored or guessed route cannot open it.
 */
fun resolveRoute(screen: String, standOn: Boolean, featureEnabled: Boolean, knownFeatureIds: Set<String> = emptySet()): String = when {
    standOn && (screen == Routes.DIAGNOSTICS || screen == Routes.MODEL_BENCH || screen == Routes.CLOUD || Routes.previewId(screen) != null) -> Routes.HOME
    Routes.previewId(screen) != null -> if (Routes.previewId(screen) in knownFeatureIds) screen else Routes.HOME
    screen in BUILT_IN || featureEnabled -> screen
    else -> Routes.HOME
}

/** The cloud can be used at all: the staff switch is on, a key is stored and stand mode is off. */
fun cloudIsOn(keySet: Boolean, switchOn: Boolean, standOn: Boolean): Boolean = keySet && switchOn && !standOn

/**
 * The features a visitor on a stand-mode tablet may see: the vetted ones that run no model (the pages, the scripted
 * ABP demo, the mandate view, the release notes). Every other feature, so every feature that uses an LLM (Chat, Lab,
 * Workbench, Models), is hidden while stand mode is on, **even when its owner has switched it on**; a new feature is
 * hidden in stand mode until it is added here on purpose (fail closed). Outside stand mode `enabled` decides alone.
 */
private val STAND_VISITOR_FEATURES = setOf("site", "abpdemo", "mandate", "releases")

/** Whether the feature may be shown (Home, sidebar, its route) given stand mode. Not about `enabled`. */
fun featureVisible(id: String, standOn: Boolean): Boolean = !standOn || id in STAND_VISITOR_FEATURES

/** The enabled features that may be shown now: `enabled`, and in stand mode only the vetted ones. */
fun visibleFeatures(all: List<ai.riskmandate.app.features.Feature>, standOn: Boolean): List<ai.riskmandate.app.features.Feature> =
    all.filter { it.enabled && featureVisible(it.id, standOn) }

/** Which sidebar section a feature sits in, by id (design spec table); unknown ids go to Explore. */
fun sectionOf(id: String): Int = when (id) {
    "xray", "abpdemo", "lab", "chat", "workbench" -> R.string.nav_section_demos
    "site", "releases" -> R.string.nav_section_today
    "mandate", "models" -> R.string.nav_section_device
    else -> R.string.nav_section_explore
}

/** The order of features inside their sections (design spec table). */
private val NAV_ORDER = listOf("xray", "abpdemo", "lab", "chat", "workbench", "site", "releases", "mandate", "models")

/** Where a feature sits in the order (Demos first); an id this list does not know goes last. */
fun navRank(id: String): Int = NAV_ORDER.indexOf(id).let { if (it < 0) NAV_ORDER.size else it }

/** A line icon per known feature id, until each feature gives its own (Feature.icon). */
fun iconFor(id: String): Int = when (id) {
    "site" -> R.drawable.ic_nav_globe
    "releases" -> R.drawable.ic_nav_news
    "abpdemo" -> R.drawable.ic_nav_layers
    "xray" -> R.drawable.ic_nav_pulse
    "lab" -> R.drawable.ic_nav_lab
    "chat" -> R.drawable.ic_nav_chat
    "mandate" -> R.drawable.ic_nav_shield
    "models" -> R.drawable.ic_nav_chip
    else -> R.drawable.ic_nav_star
}

private val BUILT_IN = setOf(Routes.HOME, Routes.LICENCES, Routes.DIAGNOSTICS, Routes.MODEL_BENCH, Routes.CLOUD)

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
                    // Stand mode (a visitor at a stand): Diagnostics and the model bench are staff tools, so they
                    // are hidden and unreachable while it is on (R-shell-1). Set from StandHost, see standOn below.
                    var standOn by remember { mutableStateOf(false) }
                    // In stand mode only vetted visitor features are shown: every feature that uses an LLM (Chat, Lab,
                    // Workbench, Models) is hidden even when its owner has switched it on (featureVisible).
                    val feature = FEATURES.firstOrNull { it.id == screen && it.enabled && featureVisible(it.id, standOn) }
                    // Features not switched on yet: reachable only from Diagnostics > Preview (hidden in stand mode).
                    val cloud = remember { CloudProcess.get(this@MainActivity) }
                    // Cloud state the screens depend on (the truth lives in CloudHost; this makes the UI follow it).
                    var cloudTick by remember { mutableIntStateOf(0) }
                    val cloudKeySet = remember(cloudTick) { cloud.key.isSet() }
                    val cloudOn = remember(cloudTick, standOn) { cloud.switch.isOn }
                    val previewFeatures = FEATURES.filter { !it.enabled }
                    val previewIds = previewFeatures.map { it.id }.toSet()
                    val route = resolveRoute(screen, standOn, featureEnabled = feature != null, knownFeatureIds = FEATURES.map { it.id }.toSet())
                    val previewFeature = Routes.previewId(route)?.let { id -> FEATURES.firstOrNull { it.id == id } }
                    LaunchedEffect(route, screen) { if (route != screen) screen = route }
                    // Features that run unattended on purpose (abpdemo's auto-play) hold stand mode's idle
                    // reset off through this. HoldIdleWhile releases when the feature leaves the screen; the
                    // host does not clear holds itself (that wiped a feature's hold after rotation, A-app-7).
                    val idle = remember { IdleHolds() }
                    // Destinations, grouped as in docs/design/android-theme.md. Features are grouped by id (a
                    // `section` on Feature is the architect's to add) and use their own icon when they give one.
                    val enabledFeatureIds = FEATURES.filter { it.enabled }.map { it.id }.toSet()
                    // Home cards and the sidebar share one order, the demos first (docs/parity-ios.md, H1).
                    val homeFeatures = visibleFeatures(FEATURES, standOn).sortedBy { navRank(it.id) }
                    val featureItems = homeFeatures.map { f ->
                        NavItem(f.id, f.title, iconFor(f.id), f.homeBadge(), vector = f.icon)
                    }
                    fun inSection(section: Int) = featureItems.filter { sectionOf(it.route) == section }
                    val sections = listOf(
                        // "Demos" is the first entry (H2): the demonstrations a visitor or a colleague is shown first.
                        NavSection(R.string.nav_section_demos, inSection(R.string.nav_section_demos)),
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
                        Routes.CLOUD -> R.string.cloud_title
                        else -> (feature ?: previewFeature)?.title ?: R.string.home_now_title
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
                            CloudProcess.standMode = on
                            if (on) {
                                sidebarCollapsed = true
                                cloud.switch.turnOff() // a visitor's text never goes to a third party
                                cloudTick++
                            }
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
                            cloudOn = cloudIsOn(cloudKeySet, cloudOn, standOn),
                            cloudModel = cloud.model.id,
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
                                    FeatureFrame(title = R.string.diagnostics_title, kicker = R.string.nav_section_device) {
                                        DiagnosticsScreen(
                                            text = text, onCopy = rememberCopyAction(text),
                                            onCloud = { screen = Routes.CLOUD },
                                            // Preview: features not switched on yet, and the model bench, in every build.
                                            // Diagnostics is hidden in stand mode, and so is everything behind it.
                                            preview = previewFeatures.map { f -> PreviewItem(f.id, f.title) { screen = Routes.preview(f.id) } } +
                                                PreviewItem("modelbench", R.string.diagnostics_model_bench) {
                                                    // The bench loads its own copy of a model: free the app's one first.
                                                    lifecycleScope.launch { LlmProcess.peek()?.unload() }
                                                    screen = Routes.MODEL_BENCH
                                                },
                                        )
                                    }
                                }
                                route == Routes.CLOUD -> FeatureFrame(title = R.string.cloud_title, kicker = R.string.nav_section_device) {
                                    CloudKeyScreen(host = cloud, standOn = standOn, onChanged = { cloudTick++ })
                                }
                                route == Routes.MODEL_BENCH -> FeatureFrame(title = R.string.feature_models_title, kicker = R.string.nav_section_device) {
                                    ModelBenchScreen()
                                }
                                (feature ?: previewFeature) != null -> {
                                    val shown = (feature ?: previewFeature)!!
                                    FeatureFrame(
                                        title = shown.title,
                                        kicker = shown.kicker ?: sectionOf(shown.id),
                                        preview = feature == null,
                                    ) {
                                        shown.Content(
                                            remember(config, device, standOn, shown.id, cloudKeySet, cloudOn, cloud.model.id) {
                                                hostFeatureContext(
                                                    context = this@MainActivity, onBack = goHome, config = config, device = device,
                                                    holdIdle = idle::hold,
                                                    // The cloud model only reaches a feature that declares usesCloud, with a key stored,
                                                    // the staff switch on and stand mode off; everyone else gets null.
                                                    cloudLlm = cloudLlmFor(shown.usesCloud, cloudKeySet, cloudOn, standOn) { cloud.service() },
                                                    // Only the fact, for every feature (cloud or not), so on-device screens say honestly
                                                    // whether anything may leave the device. No service, no key.
                                                    cloudOn = cloudIsOn(cloudKeySet, cloudOn, standOn),
                                                    // A feature asks for another by id (chat's "set up a model" opens "models"); the
                                                    // host decides: unknown ids and stand mode are refused, and a switched-off feature
                                                    // opens only from a Preview screen.
                                                    openFeature = { id ->
                                                        val ids = if (feature == null) previewIds else emptySet()
                                                        resolveOpenFeature(id, standOn, enabledFeatureIds, ids)?.let { screen = it }
                                                    },
                                                )
                                            },
                                        )
                                    }
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
                                    features = homeFeatures.mapIndexed { i, f ->
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Only tells an existing host (it unloads the model when the app is in the background or memory is
        // critical); never creates one.
        LlmProcess.peek()?.onTrimMemory(level)
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
