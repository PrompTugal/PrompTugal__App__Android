# RiskMandate Android: architecture in one page

> Owner: architect.android@promptugal.ai. Read this before writing a feature. It describes `dev` at 13c47b4 and the rules for the summit feature sessions (dev.site, dev.abpdemo, dev.mandate, dev.stand). Changes to these rules go through the architect and the conductor.

## The shape

```
MainActivity (app.android)                 one Activity, one Compose tree, no Fragments, no nav library
 └─ RiskMandateTheme / Surface
     └─ StandHost(activity, atHome, goHome)  feature D wraps everything (dev.stand)
         └─ when (screen)                   screen: String, rememberSaveable (survives rotation)
             ├─ "home"        HomeScreen    lists FEATURES.filter { enabled }
             ├─ "licences"    LicencesScreen
             ├─ "diagnostics" DiagnosticsScreen
             └─ <feature.id>  feature.Content(FeatureContext)
```

- **Host** (`MainActivity`, `HomeScreen`, `Browser`, `Links`, `ManagedConfig`, `DeviceStatus`, `Diagnostics*`): owned by app.android. Holds the only Activity-level state: `config` (managed configuration, re-read on resume and on `ACTION_APPLICATION_RESTRICTIONS_CHANGED`), `device`, the home message, and the current route.
- **Features contract** (`features/Feature.kt`, architect): `interface Feature { id; @StringRes title; enabled; @Composable Content(FeatureContext) }`. `FeatureContext` is everything a feature gets from the host: `onBack` (go home), `config` (the host's `ManagedConfig`), `device` (the host's `DeviceStatus`). It deliberately does not carry the Activity (decided 9 Nov, after security's summit checklist E).
- **Feature list** (`features/Features.kt`, conductor): `FEATURES` in home-screen order. A feature never edits it.
- **Features** (`features/<id>/`, one session each): a Kotlin `object` implementing `Feature`, plus whatever private files the package needs.

## Routing
- A route is a string. Built-in routes are `Routes.HOME`, `LICENCES`, `DIAGNOSTICS`; a feature's route is its `id` (lowercase letters only, unique, never one of the built-in names).
- The host owns the top level: it installs `BackHandler { goHome }` around every feature, and `FeatureContext.onBack` goes home. Leaving a feature removes its whole composition, so everything it `remember`ed or `rememberSaveable`d is dropped. That is also how stand mode's idle reset clears a feature.
- **Navigation inside a feature is the feature's business**: hoist a small sealed class or enum of sub-screens in a `rememberSaveable`, and add your own `BackHandler(enabled = <not at your first sub-screen>)` inside `Content`. The innermost enabled `BackHandler` wins, so back steps through your sub-screens (or the WebView history) before the host's handler takes the user home.
- No deep links, no new Activities, no Fragments, no Navigation-Compose.

## State
- **No global mutable state.** A `Feature` is an `object`, which is a process-wide singleton: it must hold no `var`, no `mutableStateOf`, no cache, no listener. Only `val` constants.
- Screen state lives in the composition: `remember` for throwaway state, `rememberSaveable` for what must survive rotation (the Activity has no `configChanges`, so rotation recreates it). Save small values only (an index, a URL string), never a `WebView` state `Bundle` or a bitmap (`TransactionTooLargeException`).
- Larger state: a plain class held in `remember { }` (or a `ViewModel` only if you need it to outlive rotation and can justify it to the architect; `lifecycle-viewmodel-compose` is not a dependency today).
- Persistent state (only stand mode should need any): a `SharedPreferences` file named `feature_<id>`, read and written by your package only. No databases, no files outside `context.filesDir/feature_<id>/` or `cacheDir/feature_<id>/`. Backup is off for the whole app; keep it that way.
- Process-wide singletons you cannot avoid (WebView's `CookieManager`, `WebStorage`) are touched only by the feature that needs them (site), and the review checks how.

## Threading
- Main thread for Compose and every View/WebView call.
- Work off the main thread with coroutines from the composition: `LaunchedEffect` / `rememberCoroutineScope` plus `withContext(Dispatchers.IO)` for disk (reading raw assets, prefs). They are cancelled when the user leaves the feature.
- Never `GlobalScope`, `Thread`, `Executors`, `runBlocking`, `Handler.postDelayed` without removal, or a `Timer`. A delay is `LaunchedEffect(key) { delay(...) }`.
- Animations (abpdemo) use Compose animation APIs (`animate*AsState`, `Animatable`, `updateTransition`, `rememberInfiniteTransition`), all already on the classpath through the Compose BOM.

## Lifecycle
- Anything with a lifecycle (a `WebView`, a receiver, a callback, a window flag) is created and released in the same composable: `DisposableEffect` / `AndroidView(onRelease = ...)`. A `WebView` is `destroy()`ed on release.
- Prefer composition-scoped effects to Activity-scoped ones: `LocalView.current.keepScreenOn = true` inside a `DisposableEffect` (reset in `onDispose`) instead of `window.addFlags`.
- Observe lifecycle with `LocalLifecycleOwner` and a `LifecycleEventObserver` in a `DisposableEffect` (for example, pause a WebView or an animation on `ON_PAUSE`).

## Where a feature may and may not reach

| A feature may | A feature may not |
|---|---|
| Read and write files in its own package, its own `res/values*/feature_<id>.xml`, its own raw assets (`res/raw/feature_<id>_*` or `app/src/main/assets/feature_<id>/`), its own tests under `app/src/test/java/ai/riskmandate/app/features/<id>/` | Edit another feature's package, `Feature.kt`, `Features.kt`, `MainActivity`, `HomeScreen`, the manifest, `build.gradle.kts`, `strings.xml`, `features.xml`, `Theme.kt`, `ui/` |
| Use `FeatureContext`, `LocalContext`, `LocalLifecycleOwner`, `LocalView`, `MaterialTheme`, and the shared components in `ui/` (design) | Import another feature's package. Shared code goes to `ui/` (design) or to the host (app), via a request |
| Call pure host helpers that already exist: `Links.isAllowed`, `ManagedConfig.fromMap`, `DeviceStatus.from`, `Browser.open` (allow-listed) | Call `ManagedConfig.read(...)` or `DeviceStatus.detect(...)` itself: use `context.config` and `context.device`, which the host keeps current |
| Use `LocalContext` for resources and its own `PackageManager` entry, `LocalLifecycleOwner`, `LocalView` (`keepScreenOn`, which sets `FLAG_KEEP_SCREEN_ON` only while attached) | Cast `LocalContext` to an Activity or `MainActivity`; change the window, theme, orientation or system bars; call `finish()`, `recreate()`; start any Activity except an `ACTION_VIEW` for a URL that passed `Links.isAllowed` (through `Browser.open`). `startLockTask`/`stopLockTask` are stand's alone, through `StandHost`'s Activity parameter |
| Ask for a new permission, manifest entry or dependency through the owner (below) | Add a permission, `<queries>`, service, receiver, provider or dependency itself |

### How a feature asks the host for something
1. Write it in your status page under "For app" (host or manifest), "For design" (`ui/`), or "For architect" (`FeatureContext`), with the smallest API that would do and why.
2. Tell the conductor (`@parent`). app.android implements host changes; the architect decides `FeatureContext` changes; the conductor merges and routes.
3. Until it lands, build against a local stand-in inside your package (a parameter with a default, a fake), never by editing the host.

## Dependencies
No new dependency without the architect's OK (in your status page, then in a review). Every addition also needs security's licence and OSV check (`scripts/licences.sh`, `scripts/osv-check.sh`) and the in-app licence list rebuilt (`app/tools/make-licences.sh`, app). app.android adds the line to `app/build.gradle.kts`.

**Pre-approved** (ask app.android to add; no further architect review needed):
- `androidx.webkit:webkit` (1.12.x), for site only: `WebViewAssetLoader` for the offline copy, `WebViewFeature`/`WebSettingsCompat` checks.
- `androidx.lifecycle:lifecycle-runtime-compose`, same version as `lifecycle-runtime-ktx` (2.8.7), for any feature that wants `collectAsStateWithLifecycle` / `LifecycleResumeEffect`.
- `androidx.media3:media3-exoplayer` and `media3-ui` (1.5.x), only if feature E starts, for E only.

**Not approved without asking**: anything else, including `material-icons-extended` (draw the few vectors you need), Accompanist, Coil/Glide, a JSON library (abpdemo and mandate data is Kotlin code, not JSON), Hilt/Koin, Navigation-Compose, OkHttp/Retrofit (no feature makes its own network calls; site's WebView is the only network user).

## Strings, assets, tests
- Strings: `res/values/feature_<id>.xml` and `res/values-pt-rPT/feature_<id>.xml`, every name prefixed `feature_<id>_`, both files with the same names. No literal text in `Text(...)` or `contentDescription`. The title string (`feature_<id>_title`) stays in `features.xml` (conductor).
- Test tags: `<id>_<thing>` (for example `site_webview`, `abpdemo_layer_3`), declared in an `object <Id>Tags` in your package.
- Tests: JVM (Robolectric and Compose UI test are available) under `app/src/test/java/ai/riskmandate/app/features/<id>/`. Keep the logic that matters pure and test it without Android: site's navigation decision, abpdemo's step sequence, mandate's reach-and-gap model, stand's idle timer. Then one Compose test that `Content` renders with a `FeatureContext` built from a Robolectric `ComponentActivity` and that `onBack` is reachable.
- Turn `enabled = true` only when the screen works end to end on the gate and on a tablet.

## WebView rules (site; security reviews these too)
- Security's checklist (`docs/security/summit-checklist.md`, section A) is the full list; this is the structural part.
- Main-frame navigation decided by a pure function in the site package: allowed if `Links.isAllowed(url)` or it is the offline copy (`https://appassets.androidplatform.net/assets/feature_site/...`, `WebViewAssetLoader` with an `AssetsPathHandler` on `/assets/` only, files in `app/src/main/assets/feature_site/`). Never `file://`, never `loadDataWithBaseURL` with a riskmandate.ai base. Everything else is refused in `shouldOverrideUrlLoading(view, WebResourceRequest)`; whether a refused link opens in the browser is a decision for security and stand (it escapes screen pinning), not a default.
- `setSupportMultipleWindows(false)` so `target=_blank` comes through the same check; `allowFileAccess = false`, `allowContentAccess = false`; no `addJavascriptInterface`; `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`; JavaScript only if the pages need it; `WebChromeClient.onPermissionRequest` denies, no geolocation, no file chooser; downloads ignored; `onReceivedSslError` cancels; `onRenderProcessGone` returns true and drops the WebView (otherwise the app dies); `WebView.setWebContentsDebuggingEnabled` only in debug builds.
- Mandate (C) states what this app collects. If the site's pages load third-party resources, that statement must say so or site must block them: site and mandate agree the sentence, security checks it.

## `FeatureContext` history
- **9 Nov, v2 (architect):** `activity` removed; `config` and `device` added (P1); `onBack` kept and means "go home" (P2). A deprecated `FeatureContext(activity, onBack)` constructor stays only so `MainActivity` compiles until app.android passes its own values, built with `remember(config, device)` (P3); then it is deleted.
- **Open, P4:** stand mode's on/off switch on the home screen: a small `StandController` the host hoists and passes to `StandHost` and `HomeScreen` (app and stand agree it; not a `FeatureContext` field).
- Anything else a feature needs from the host: ask (above). A new field needs a reason and a test.
