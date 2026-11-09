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
             └─ <feature.id>  FeatureFrame(title, onBack) { feature.Content(FeatureContext) }
```

- **Host** (`MainActivity`, `HomeScreen`, `Browser`, `Links`, `ManagedConfig`, `DeviceStatus`, `Diagnostics*`): owned by app.android. Holds the only Activity-level state: `config` (managed configuration, re-read on resume and on `ACTION_APPLICATION_RESTRICTIONS_CHANGED`), `device`, the home message, and the current route.
- **Features contract** (`features/Feature.kt`, architect): `interface Feature { id; @StringRes title; enabled; @Composable Content(FeatureContext) }`. `FeatureContext` is everything a feature gets from the host: `onBack` (go home), `config` (the host's `ManagedConfig`), `device` (the host's `DeviceStatus`). It deliberately does not carry the Activity (decided 9 Nov, after security's summit checklist E).
- **Feature list** (`features/Features.kt`, conductor): `FEATURES` in home-screen order. A feature never edits it.
- **Features** (`features/<id>/`, one session each): a Kotlin `object` implementing `Feature`, plus whatever private files the package needs.

## Routing
- A route is a string. Built-in routes are `Routes.HOME`, `LICENCES`, `DIAGNOSTICS`; a feature's route is its `id` (lowercase letters only, unique, never one of the built-in names).
- The host owns the top level: it resolves the route before drawing (a saved route to a missing or disabled feature shows Home), installs one `BackHandler { goHome }` for every non-home route, and wraps each feature in `FeatureFrame` (Back button, title as heading, safe-drawing insets). **A feature draws content only: no title, no Back or Home button, no `safeDrawingPadding` of its own.** `FeatureFrame` is the only frame; design's `ui/` offers content-only layout (`FeatureBody`: padding, reading width, optional scroll, `LocalWideLayout`), never a second title bar. `FeatureContext.onBack` goes home. Leaving a feature removes its whole composition, so everything it `remember`ed or `rememberSaveable`d is dropped. That is also how stand mode's idle reset clears a feature.
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
| Use `LocalContext` for resources and its own `PackageManager` entry, `LocalLifecycleOwner`, `LocalView` (`keepScreenOn`, which sets `FLAG_KEEP_SCREEN_ON` only while attached) | Cast `LocalContext` to an Activity or `MainActivity`; change the window, theme, orientation or system bars; call `finish()`, `recreate()`; start any Activity except through the host: `Browser.open` (riskmandate.ai only) or, for site's main-frame taps on off-site links, `Browser.openExternal` (https only, refused while pinned). `startLockTask`/`stopLockTask` are stand's alone, through `StandHost`'s Activity parameter |
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

## Stand mode and the other features
- StandHost wraps every screen. Its staff corner (bottom-end, 48dp) shows on the home screen only, so it never covers a feature's controls.
- The idle reset goes home after N minutes without a touch on the main window. Touches inside a feature's dialog (another window) do not count, so keep dialogs short. A feature that runs unattended on purpose (abpdemo's auto-play) holds the reset off with `context.HoldIdleWhile(autoPlay)`, and only that way: it releases when the condition ends or the feature leaves the screen.

## Strings, assets, tests
- Strings: `res/values/feature_<id>.xml` and `res/values-pt-rPT/feature_<id>.xml`, every name prefixed `<id>_` (for example `stand_title`, `abpdemo_next`), both files with the same names; names are unique across all files (LocalisationTest). No literal text in `Text(...)` or `contentDescription`. The title string (`feature_<id>_title`) stays in `features.xml` (conductor).
- Test tags: `<id>_<thing>` (for example `site_webview`, `abpdemo_layer_3`), declared in an `object <Id>Tags` in your package.
- Tests: JVM (Robolectric and Compose UI test are available) under `app/src/test/java/ai/riskmandate/app/features/<id>/`. Keep the logic that matters pure and test it without Android: site's navigation decision, abpdemo's step sequence, mandate's reach-and-gap model, stand's idle timer. Then one Compose test that `Content` renders with a `FeatureContext` built from a Robolectric `ComponentActivity` and that `onBack` is reachable.
- Turn `enabled = true` only when the screen works end to end on the gate and on a tablet.

## WebView rules (site; security reviews these too)
- Security's checklist (`docs/security/summit-checklist.md`, section A) is the full list; this is the structural part.
- Main-frame navigation decided by a pure function in the site package: allowed if `Links.isAllowed(url)` or it is the offline copy (`https://appassets.androidplatform.net/assets/feature_site/...`, `WebViewAssetLoader` with an `AssetsPathHandler` on `/assets/` only, files in `app/src/main/assets/feature_site/`). Never `file://`, never `loadDataWithBaseURL` with a riskmandate.ai base. Everything else is refused in `shouldOverrideUrlLoading(view, WebResourceRequest)`; whether a refused link opens in the browser is a decision for security and stand (it escapes screen pinning), not a default.
- `setSupportMultipleWindows(false)` so `target=_blank` comes through the same check; `allowFileAccess = false`, `allowContentAccess = false`; no `addJavascriptInterface`; `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`; JavaScript only if the pages need it; `WebChromeClient.onPermissionRequest` denies, no geolocation, no file chooser; downloads ignored; `onReceivedSslError` cancels; `onRenderProcessGone` returns true and drops the WebView (otherwise the app dies); `WebView.setWebContentsDebuggingEnabled` only in debug builds.
- Mandate (C) states what this app collects. If the site's pages load third-party resources, that statement must say so or site must block them: site and mandate agree the sentence, security checks it.

## `FeatureContext` history
- **9 Nov, v2 (architect):** `activity` removed; `config` and `device` added (P1); `onBack` kept and means "go home" (P2). The transitional `FeatureContext(activity, onBack)` constructor was deleted once app passed its own values (`remember(config, device)`, P3; app f317cf8).
- **P4 closed:** stand's switch is the long-press corner inside StandHost, shown on Home only.
- **9 Nov, v3 (architect, approved by the conductor):** `holdIdle: () -> () -> Unit` (default no-op), `FeatureContext.HoldIdleWhile(active)` and the host-side `IdleHolds`. Wiring:
  - app (MainActivity): `val idle = remember { IdleHolds() }`; `FeatureContext(..., holdIdle = idle::hold)`; `StandHost(..., idleHeld = idle.held)`. **No** `LaunchedEffect(route) { idle.clear() }`: it runs after the feature's hold is taken and wipes it (app review A-app-7). `HoldIdleWhile` already releases when the feature leaves.
  - stand (StandHost): new parameter `idleHeld: Boolean = false`; while true, do not reset; when it turns false, `timer.touch()` so the countdown starts again from that moment.
  - abpdemo: `context.HoldIdleWhile(auto)` in `Content`.
- **9 Nov, P6 (architect, approved by the conductor):** `@Composable @StringRes fun homeBadge(): Int? = null` on `Feature`. The home screen asks every enabled feature for a badge and shows it on the button; the host never imports a feature package (releases' "New" after an update uses it). Disk reads inside it go off the main thread. The host must not call into a feature package for anything else either (`ReleasesNotice`, `StandSettings`, ...); `StandHost` is the one sanctioned host entry point into `features/stand/`.
- **9 Nov, P7 (architect, approved by the conductor):** `icon: ImageVector?`, `@StringRes kicker: Int?`, `@StringRes summary: Int?` on `Feature`, all null by default, for the shell's sidebar and Home cards. Each feature fills its own (strings in its `feature_<id>.xml`, en and pt-PT); icons from `material-icons-core` or drawn in the feature, never `material-icons-extended`.
- **9 Nov, `openFeature(id: String)` (architect, asked by the conductor):** a host-owned request to open another feature by its id (chat's no-model state opens `models`). Default no-op. The host ignores unknown or disabled ids and may refuse, for example in stand mode. Features name each other only by id string, never by package; ids are stable and tested (lowercase, unique).
- Anything else a feature needs from the host: ask (above). A new field needs a reason and a test.

## On-device LLM (side team: dev.llm, dev.chat, dev.lab, eval; brief `briefs/2026-11-09__on-device-llm/`)

### Where things live
- `app/src/main/java/ai/riskmandate/app/llm/` (dev.llm): the `LlmService` interface, the LiteRT-LM implementation, `FakeLlmService` (in main, so previews, qa and other features' tests can use it), the model catalogue (pinned URLs and SHA-256) and the model manager. Feature G's screen is `features/models/`.
- `features/chat/` (H), `features/lab/` (I, including the ABP checks module), `eval/` (the harness; not app code, never on the app's classpath).

### The service: one per process, owned by the host
- Loading a model costs seconds and gigabytes, so the service must not be per screen and must survive a rotation (the Activity is recreated). This is the **one sanctioned process-wide object**: an `LlmHost` in `llm/`, created lazily, held by app (an `Application` subclass or the host's own holder; app decides), and handed to features through `FeatureContext`. No feature, and no other package, keeps its own reference beyond its composition.
- It unloads the model on `onTrimMemory` (background or critical) and after a few idle minutes, and reloads on the next request. Features see a "loading model" state, never a crash.
- One request at a time: the service serialises (a `Mutex`). The Lab's "two paths at once" interleaves the two paths on one loaded model; it never loads a second copy.

### The contract (as built by dev.llm, step 2; `llm/LlmService.kt`)
- `state: StateFlow<LlmState>`: NoModel, Idle(modelId), Loading(modelId), Ready(modelId, backend), Failed(modelId?, reason).
- `stream(LlmRequest): Flow<LlmEvent>`, cold, ending with exactly one Done(stats) or Error(LlmError, message); `generate(request)` collects it. Events: Token(text), ToolCallProposed(name, argsJson), Done, Error.
- `LlmRequest(messages, tools: List<ToolSpec>, settings: Sampling, maxTokens)`; system prompts are `LlmRole.SYSTEM` messages. Each request is a fresh runtime conversation: send the whole history each time.
- Tool proposals are checked with `checkToolCall(tools, call)` / `ToolSpec.check(argsJson)` (strict flat JSON through `StrictJson`): use these, do not write another validator.
- No `installed` list and no `modelId` or `system` on `LlmRequest` (conductor ruling on A-llm-12, 9 Nov): feature G owns the model choice; chat and other features show the active model read-only from `Ready(modelId, backend)` and send system prompts as `LlmRole.SYSTEM` messages.
- **One strict JSON parser in the app: `llm/StrictJson` (dev.llm owns it).** Ruling of 9 Nov: dev.llm's stays, because every feature will use it; `features/lab/StrictJson.kt` goes. Lab's parser is the more general one (nested values, depth cap, `render`), so dev.llm takes over its general `parse(text, maxChars, maxDepth)` and `render` into `llm/StrictJson` (tests included), and `parseFlatObject` (tool arguments) is built on it. Lab keeps only Lab-specific helpers (`hash16`, `parseAt` for its text tool protocol) as functions over `llm/StrictJson`, and checks every proposal with `checkToolCall`. Fix forward, after dev.llm's contract is on dev.
- Cancellation is coroutine cancellation (the screen's scope ends, or Stop cancels the collecting job). No separate `cancel()` needed.
- Inference runs on the service's own dispatcher; every call is main-safe.
- `FeatureContext.llm: LlmService? = null` (on dev with architect's change after dev.llm 25d9cb7; no feature-owned CompositionLocal for the model; the host must not import a feature package to provide one): null when the host has no service (state NoModel covers "no model installed"). A feature without a model shows "set up a model" and calls `context.openFeature("models")` (never imports `features/models`). For app: `FeatureContext(..., llm = llmHost)` with the one `LlmHost.create(applicationContext)`.

### Tool calls: the model proposes, code decides
- The service **never executes** anything. It returns `ToolCallProposed(name, argsJson)`.
- Each feature declares its tools in Kotlin (`ToolSpec`: name, flat typed arguments, description), three to five per task. Its own registry parses every proposal against that allow-list: unknown name, missing, extra or wrongly typed argument, oversize string → rejected, shown, never run.
- Executors in the Lab are fakes that record calls. No tool in any feature may reach a network, file outside its own folder, intent, clipboard or account.
- Model output is shown as plain text. Never HTML or Markdown links; any URL goes through `Links.isAllowed` before it can be opened (`Browser.open`).

### Network, storage, privacy
- Model downloads are the only new network destination: exact URLs and SHA-256 pinned in `llm/`, a list separate from `Links`. Verified before first load and after any copy; a mismatch deletes the file.
- Download with `HttpURLConnection` and HTTP `Range` (resumable) in a coroutine while G's screen is open. **No** foreground service, WorkManager or `DownloadManager` without a new review: each brings a permission or a public directory.
- Files: models in `noBackupFilesDir/models/`, chat history in `noBackupFilesDir/feature_chat/` (never backed up by construction). Both excluded from backup and device transfer (app owns `data_extraction_rules.xml` and `backup_rules.xml`). Mandate lists both with their own reasons, in the same merge.

### Dependencies for the side team
- **Approved:** `com.google.ai.edge.litertlm:litertlm-android:0.18.1` (Google Maven only, Apache 2.0), for `llm/` only. `-Xskip-metadata-version-check` is accepted for the summit only (dev.llm review A-llm-2: bump Kotlin, the Compose plugin and AGP afterwards and drop it); LiteRT-LM calls stay in `llm/`. dev.llm names the exact coordinates and version in its status page. security runs the licence and OSV check, app adds the line and rebuilds the in-app licence list. llama.cpp through JNI only if LiteRT-LM blocks: say so first; it needs its own review (NDK build, native code).
- **No new dependency for JSON:** scenario files, tool arguments and chat history use `org.json` (part of Android) or a small hand-written parser. kotlinx-serialization (a compiler plugin) and Moshi/Gson are not approved without a reason.
- **Chat storage:** one JSON file per conversation (or one file) under `filesDir/feature_chat/`, written off the main thread. Room is not approved; ask with a reason if a file is not enough.
- **ViewModel:** not needed (the service is host-held). Ask before adding `lifecycle-viewmodel-compose`.

## Design pass: the PrompTugal iOS look (Dinis, 9 Nov)
- **Ownership:** design owns `Theme.kt` and `ui/` (tokens, components); app owns the shell (`MainActivity`, `HomeScreen`, `FeatureFrame`, navigation). Features adopt components; they do not restyle the shell.
- **One frame still:** on tablets the shell may become a sidebar (feature list) plus a detail pane, but each feature is still drawn inside exactly one `FeatureFrame` (or its successor in the shell). A feature never draws a title bar, a Back button, a sidebar or insets. In the two-pane shell `onBack` still means "back to home" (the list), and on a tablet that may simply clear the selection.
- **Navigation state** (the selected route) stays a `rememberSaveable` string in the host and survives rotation and the switch between phone (one pane) and tablet (two panes) layouts. Features keep their own sub-screen state as before.
- **One title per screen:** the host's frame draws the feature's header (`ui/ScreenHeader` from `Feature.kicker/title/summary`, P7). Features never draw their own main title; `ScreenHeader` inside a feature is only for sub-screen headings.
- **Home as cards:** cards come from `FEATURES` (title, `homeBadge()`, and an optional icon or summary only if added to `Feature` through the architect, with defaults). No per-feature code in the host.
- **No new dependency without the architect's OK.** Material 3 and Compose foundation cover the shell (`ListDetailPaneScaffold` from material3-adaptive is a new dependency: ask first). A bundled font is allowed only as a `res/font` file with its licence recorded in `docs/security/licences.md` and the in-app licences, and an OFL or Apache licence. No downloadable fonts (a network call).
- **When features switch:** once design's components and app's shell are on dev together, the conductor tells the feature devs. Until then features keep using `MaterialTheme` and the current `ui/` components, which keep working under the new theme.

## Preview of disabled features (staff only; conductor's request, 9 Nov; as built by app 2b0749f)
A feature with `enabled = false` is never on Home, in the sidebar, or reachable through `openFeature` from an enabled feature. To let Dinis try one on a release build before it is enabled, the host has **one separate path**:
- **A distinct route:** `preview/<id>`, produced **only** by Diagnostics > Preview. `resolveRoute` accepts it for any known feature id while stand mode is off; otherwise Home. A plain `<id>` for a disabled feature still goes Home, so a restored or guessed route cannot open it.
- **Saved like any route** (the same `rememberSaveable` string), so rotation keeps it. Stand mode turning on sends it Home, as with Diagnostics.
- **`openFeature`:** refused in stand mode, and refused for disabled ids from normal screens. While a Preview screen is showing, a disabled target opens as `preview/<target>` (chat in preview, then "set up a model", then models).
- **Marked:** `FeatureFrame(preview = true)` shows a "Preview" pill next to the title.
- **Same contract:** the same `FeatureContext` (`llm` included). Nothing in `features/` knows it is in preview, and no feature reads the route.

## Workbench (feature `workbench`; brief `briefs/2026-11-09__on-device-llm/workbench.md`; ruling of 9 Nov)
- **Feature:** id `workbench`, `enabled = false`, reached only through Diagnostics > Preview (`preview/workbench`), hidden in stand mode by the host as today. **No new `FeatureContext` capability:** `llm`, `holdIdle` (sweeps hold the idle reset), `openFeature("models")` (model choice) and `LocalView.keepScreenOn` cover it.
- **Ownership (one owner per package):**
  - `features/workbench/` (screens, tabs 1 Run and 2 Experiments, run log storage): **dev.llm**.
  - `features/workbench/tooltests/` (tab 3's composable only): **dev.lab**, called from the workbench screen. A sub-package with its own owner, so the two never edit the same file.
  - `experiments/` (new shared, non-feature package `app/src/main/java/ai/riskmandate/app/experiments/`): **dev.lab**. It holds the code both the Lab and the Workbench use:
    - the tool-test catalogue format and parser, and the code checks (right tool, args pass `checkToolCall`, value rules, no call when none fits, no made-up tool, two-step, no secret in args, text after a result);
    - the `ToolTestRunner` over `LlmService`, pure and JVM-tested with `FakeLlmService`;
    - the generic loop with its caps: 100 runs, 30 minutes, 15% battery, severe thermal, 3 errors in a row. It moves out of the Lab's `LoopRunner`.
    - `DeviceGuard`/device readings: battery, charging, thermal. It moves out of the Lab.
    - the text tool-call protocol extractor (the generic part of the Lab's `ToolProtocol`).

    The Lab keeps only what is ABP-specific: `PathRun`, `AbpChecks`, `Oracle`, `Claims`, scenarios and lanes. A feature package never imports another feature package; both import `experiments/` and `llm/`.
- **Stats and run options live in `llm/` (dev.llm), additive only, defaults keep every caller compiling:**
  - `LlmStats` gains `stopReason` (DONE, MAX_TOKENS, CANCELLED, ERROR), `backendUsed` and `fellBack`, and `coldLoad`, alongside the existing prompt/output tokens, TTFT, prefill and decode tokens/s, load ms and total ms. Each value says whether it is runtime-reported or measured in Kotlin.
  - `MemoryProbe` stays in `llm/`.
  - **Shared prompts:** the RiskMandate base system block (and any prompt more than one feature uses) lives as a versioned asset `assets/prompts/<name>.v<N>.txt` with a small loader in `llm/prompts/` (dev.llm). The Workbench and chat both read it from there; nobody imports `features/chat`. Its claims come only from riskmandate.ai, and security checks the text like any claim.
  - `LlmRequest.backend: BackendPreference = AUTO` (AUTO, GPU, CPU). The host reloads when it must, still serialised.
  - `suspend fun unload()` on `LlmService` (default no-op in the interface) for an honest cold-load measurement. Not `LlmHost` casts.
  - **No model parameter:** the model is chosen in G (`openFeature("models")`). The Workbench shows the active model from `Ready(modelId, backend)` and logs it per run. A model sweep is not offered for now (compare by switching in G; the log keeps both).
- **Storage:** `noBackupFilesDir/feature_workbench/`, JSON through `llm/StrictJson`, written off the main thread, with a clear button. mandate lists it with its own reason in the same merge. No export path yet beyond "Copy run" (plain text, no identifiers). Results use the Lab's export format, so eval can read them later.
- **Output:** plain text only; tools are fakes that record calls; nothing executes.

## Cloud model via OpenRouter (Dinis, 9 Nov, about 10:35 UTC; architect ruling)
### The service
- `cloud/CloudLlmService : LlmService` in its **own top-level package `ai.riskmandate.app.cloud`** (owned by **dev.chat**; revised from `llm/cloud/` at dev.chat's request), with the same contract as the on-device service: a cold `stream`, exactly one Done or Error, coroutine cancellation, main-safe, tool calls only **proposed**, never executed. `cloud/` may import `llm/` (the contract, `StrictJson`, `checkToolCall`); **`llm/` never imports `cloud/`**. `cloud/` is the one reviewed exception to the network ban outside `llm/`'s model download: qa's `LlmSandboxTest` gets an explicit allowance for `cloud/` only, with its own guard (only the pinned host, redirects off), and adds `experiments/` to the scanned roots.
- Network: **one pinned host, `openrouter.ai`**, HTTPS only, in a list in `cloud/` separate from `Links.isAllowed` and from the model-download list, checked on every request URL. `HttpURLConnection` with `instanceFollowRedirects = false` (a redirect is an error), the server-sent-events stream parsed by hand. **No new dependency** (no OkHttp, no SDK). Request JSON built with `org.json`; tool arguments come back through `llm/StrictJson` and `checkToolCall` like local ones.
- **Models:** an allow-list of OpenRouter model ids pinned in code. Cost per run from OpenRouter's `usage` field (`cost`), persisted. A spending cap enforced in code per install (default **US$1** until Dinis sets one), shown as a counter, and a credit limit on the key itself in OpenRouter (Dinis). On the cap, stop with `LlmError` and a clear message.
- **Location is part of the contract:** `LlmService.location: LlmLocation` (LOCAL or CLOUD, default LOCAL in the interface; dev.llm adds it, `CloudLlmService` returns CLOUD), and `LlmStats.location` the same. Badges come from it, never from which field a feature used.
- **History is sent only by explicit choice:** the service is stateless, so a cloud request contains exactly the messages the caller passes. A feature sends earlier turns to the cloud only on an explicit per-request choice made visible on screen ("include this conversation"); the default is the current prompt alone.
- No user data unless a person typed it on that screen for a cloud run. Synthetic scenario and tool-test data only for judging. Nothing from other features, the device or the managed configuration goes into a prompt.

### How a feature gets it
- **A separate field, never mixed with `llm`:** `FeatureContext.cloudLlm: LlmService? = null`. `llm` stays **on-device only**, so a feature can never send text to the cloud by accident through the field it already uses.
- **Opt-in per feature:** `Feature.usesCloud: Boolean = false`. The host passes `cloudLlm` only when (a) the feature declares `usesCloud = true`, (b) a key is stored, and (c) stand mode is off. At first only the Workbench declares it. Chat stays on-device unless Dinis asks.
- **Contract changes (architect, after the conductor's OK):** `usesCloud` on `Feature`, `cloudLlm` on `FeatureContext`, both with defaults, plus contract tests. The host wiring is app's.

### The "Cloud" badge rule
- Every answer, judgement or stat that came from the cloud carries a visible **"Cloud"** badge with the model id and "via openrouter.ai". On-device ones carry **"On device"**. Never an unlabelled mix on one screen.
- While a screen can use the cloud, it shows a persistent "Cloud on" indicator. Screenshots must keep the badges.

### The key
- Entered by staff in **Diagnostics** (host UI, app owns it): pasted once, never shown again (only the last 4 characters), with a delete button, and hidden in stand mode.
- Stored by `cloud/CloudKey` (dev.chat): AES-GCM with a key in the **Android Keystore** (`javax.crypto` and `AndroidKeyStore`, no `security-crypto` dependency), ciphertext in `noBackupFilesDir/cloud/`, so never backed up. It is never logged, never in a crash or diagnostics text, never in results or screenshots, and never sent anywhere but `openrouter.ai` as `Authorization: Bearer`.
- Security reviews it before merge.

### Judges (experiments/, dev.lab)
- `interface Judge { val layer: JudgeLayer; suspend fun judge(case, output): JudgeVerdict }` with `CodeJudge` (pure), `LocalModelJudge(llm)` and `CloudJudge(cloudLlm)`. A verdict is pass, fail or unsure, with a reason, the layer, the model id and the prompt version.
- A `JudgePanel` runs the layers in order: code, then local, then cloud. Where `CodeJudge` can decide, it is authoritative, and the model judges are recorded but never override it. Where code cannot decide, the model verdicts are reported side by side.
- **Every disagreement between layers is recorded** (case, outputs, each verdict) and counted per case and per kind.
- Judge prompts are versioned files. Judge output is parsed as strict JSON; unparseable output is "unsure", never "pass". The cloud judge sees only the synthetic case and the output under test.

### Platform-neutral formats (for the shared PrompTugal__App__Mobile repo)
- Scenario, tool-test, judge-prompt and result files are JSON with a `format` and `version` field, lowercase string enums, explicit units (`_ms`, `_tokens`, `_usd`), no Kotlin or Android names, UTF-8, and documented once in a spec file (eval's FORMATS.md today, the shared repo later). App code parses them through `llm/StrictJson`. iOS must be able to read the same files unchanged.

### Claims change in the same merge
The app has said "nothing you type leaves the device" and "connects only to riskmandate.ai". With OpenRouter in the app, security, play and mandate update the privacy page, Data safety, the in-app privacy and about screens, and mandate's network and data lines **in the same merge** as the first cloud call. openrouter.ai is named as a new destination for staff-entered cloud runs.

## Post-summit items (approved, not before the summit build)
- **P8, feature icons as drawables** (conductor's go for after the build; design chose drawables):
  1. Add `@DrawableRes iconRes: Int? = null` to `Feature`, mark `icon: ImageVector?` `@Deprecated`; the shell uses `iconRes`, then its default icon (it ignores `icon`). Contract test.
  2. design draws `res/drawable/ic_feature_<id>.xml` (24 dp, 2 dp stroke, line style) on request; each owner replaces its `icon` override with `iconRes`. No borrowed Material icons.
  3. When no feature overrides `icon`, delete it.
  - Same round: app derives the sidebar order from `FEATURES` (`NAV_ORDER` goes); sections move onto `Feature` through the architect only if they stay (A-app-14).
- **Kotlin, Compose plugin and AGP bump** so LiteRT-LM's 2.4 metadata reads without `-Xskip-metadata-version-check`, then drop the flag (A-llm-2; release and app).
- **APK size:** an AAB-only or ABI-split path for the arm64 and x86_64 native libraries (A-llm-8; release).
