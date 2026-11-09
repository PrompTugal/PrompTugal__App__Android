# app.android status

> Lead dev. Skeleton review (13c47b4) done and fixed. Earlier: second task list done (diagnostics screen, androidTest smoke test, offline behaviour, size and startup). Gate green. Stopped.

## Doing
Nothing.

## Done
- Responsive Home screen: single column up to portrait, two panes in landscape at 640dp wide or more (phone landscape, 7", 10"); content capped at 560dp per pane; always scrollable.
- Material 3 theme (`Theme.kt`): fixed light and dark schemes from the brand navy, follows the system; window background and edge-to-edge in light and dark.
- Accessibility: headings marked, 48dp minimum touch targets (tested), click labels on Open and Back, live region on the error message, reading order = code order, 200% font scale tested (everything reachable by scrolling).
- Strings: English and pt-PT (`values-pt-rPT`), no hardcoded text (a test fails if one appears, and if a string lacks a translation). `locales_config.xml` for per-app language on Android 13+.
- Screenshot tests (`ScreenshotTest`): home and licences, phone (1080x2400), 7" (1200x1920), 10" (1600x2560), portrait and landscape, light and dark. 24 PNGs.
  **Where they land: `app/build/screenshots/<screen>-<device>-<orientation>-<theme>.png`** (e.g. `home-tablet10-landscape-dark.png`), written by `./gradlew testDebugUnitTest`. Note: Compose `captureToImage` times out under Robolectric's paused looper, so the test draws the decor view into a bitmap (native graphics mode). No new plugins.
- Lint: monochrome icon added; icon moved to `mipmap/` (aapt2 rejects `mipmap-anydpi` without a version, so the plain folder clears ObsoleteSdkInt); `fullBackupContent` set (DataExtractionRules); `localeConfig` annotated. Only 5 GradleDependency warnings remain.
- Licences screen shows the full Apache 2.0 text (`res/raw/apache_license_2_0.txt`, copy of `/LICENSE`, a test checks they are identical) plus the notice and library list.
- Gate `./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease`: green.

- Diagnostics screen (home > Diagnostics): version name and code, application ID, build type, Android release and API, make and model, managed state, managed configuration (effective `start_url`, whether it was refused, `device_label`), installer package (`getInstallSourceInfo` on API 30+, `getInstallerPackageName` before; Google Play, Firebase App Distribution, package installer, or unknown for adb). Copy button puts the same text on the clipboard as plain text. No IMEI, serial, Android ID or account (a test checks the collected text). The report text is English by design (pasted into tickets); the screen chrome is in English and pt-PT. "start_url as received" is shown as the effective value plus the refused flag; the raw refused string is not shown.
- Screen state is now saved, so rotating a tablet on Licences or Diagnostics no longer drops back to Home.
- No network: opening can no longer crash (all launch failures are caught; new `FAILED` result with its own message). If the system reports no validated internet, the home screen says so plainly under the button while the browser still opens and shows its own error. This adds the normal permission `ACCESS_NETWORK_STATE` (no network data read); say if you would rather not have it.
- androidTest smoke test `app/src/androidTest/.../SmokeTest.kt` (home, licences, diagnostics). Compiles in the gate via `assembleDebugAndroidTest`; not run here (no emulator).
- Size and start: release APK 929 KB (unsigned, minified and resource-shrunk), AAB 2.1 MB. R8 full mode is already the default in AGP 8.7 (no `fullMode=false` anywhere), so nothing to switch on and the gate is green. JVM start: `StartupTest` creates MainActivity in about 60 ms warm under Robolectric (printed as `STARTUP_JVM_MS`, asserted under 3 s). That is not a device cold start; measure that on a tablet.

### Summit skeleton review (lead dev), fixed in this commit
- Unknown or switched-off route (restored after an app update or process death) wrote state during composition and could show a disabled feature: now resolved before drawing (Home), saved route reset in a `LaunchedEffect`.
- Features had no way back except the system gesture, which stand mode can pin off: new host `FeatureFrame` (Back button 48dp, title as heading, system-bar insets). **For feature devs: draw only your content; the host draws the title and Back, and applies insets. Do not add your own title or `safeDrawingPadding`.**
- TalkBack order on Home: the "cannot open / offline" message came after the feature buttons; now title, Open, message, features, then Device, About, Licences, Diagnostics, Build.
- Open and feature buttons share a minimum width (240dp).
- Rotation: built-in screens and the route survive (tested with `recreate()`). A feature's own state (a WebView page, scroll) does not, since the activity is recreated: feature devs keep it in `rememberSaveable` or a ViewModel.
- Tests: feature ids unique, lowercase, no clash with built-in routes; frame Back; message order.
- Not changed: `Feature.kt` (architect's), `Features.kt` (conductor's), `StandHost` (stand's).

- `LocalisationTest` now covers every `values/*.xml` against the same file in `values-pt-rPT` (so `feature_<id>.xml` pairs are checked, names unique across files) and scans all Kotlin under `src/main/java` (host, `features/`, `ui/`) for hardcoded `Text("`, contentDescription, title, placeholder and onClick label. Qa may extend it.
- Merged work/architect (e3eb80c, FeatureContext v2). MainActivity now passes `remember(config, device) { FeatureContext(onBack = goHome, config = config, device = device) }`, so the deprecated activity constructor is unused (architect can delete it).
- Removed the exported `androidx.profileinstaller.ProfileInstallReceiver` from the merged manifest (`tools:node="remove"`, lint `MissingClass` ignored for that line). Checked in the merged release manifest: it is gone, and MainActivity is the only exported component. Baseline-profile installation is dropped.
- Home keeps the bottom-end 48dp corner free of controls for stand mode's long press (test `HomeCornerTest`). StandHost's own signature is unchanged by me; dev.stand owns it.
- R-stand-3: `data_extraction_rules.xml` excludes root, file, database, sharedpref and external explicitly for both cloud-backup and device-transfer; new `backup_rules.xml` (fullBackupContent, Android 11 and older) does the same; `allowBackup` stays false.
- `Browser.openExternal(context, url)` for dev.site: ACTION_VIEW to the system browser (no component set, so never this app or its WebView), https only via `Links.isExternalOk` (host letters, digits, dots, hyphens; optional numeric port; no userinfo in the authority, no backslash, whitespace or control characters, max 2048; '@' in a path is fine), refused otherwise (`REFUSED`). Never while pinned: `Browser.isPinned` (ActivityManager lock-task state, public API) gives `OpenResult.PINNED` with its own message (en, pt-PT), so it needs nothing from dev.stand and also works for plain screen pinning. `Browser.open` and its riskmandate.ai allow-list are unchanged. Tests: 3 in LinksTest, 5 in BrowserTest (view intent, refusals start nothing, pinned, no browser, allow-list kept). dev.site: map the result with `Browser.message(result)` if you want to show it.
- Added `implementation("androidx.webkit:webkit:1.12.1")` for dev.site (WebViewAssetLoader). `scripts/ci-licences.sh`: "85 artifacts, all Apache-2.0, MIT or BSD", gate passed. The in-app licence list (`res/raw/licences.txt`) is regenerated and shows the new library. `docs/security/licences.md` is security's file: the script's only change is one added webkit row; I reverted it, **for security: regenerate it**. Gate green, release APK still about the same size.
- FeatureContext v3 (work/architect b2da9cd merged): MainActivity keeps `val idle = remember { IdleHolds() }`, passes `holdIdle = idle::hold` in the `FeatureContext`, and calls `idle.clear()` in a `LaunchedEffect(route)`. **Open: `StandHost(idleHeld = idle.held)`**: StandHost on this branch is still the placeholder without that parameter (dev.stand's version, on work/dev.stand, has `idleHeld: Boolean = false`). One line to add once dev.stand's StandHost is merged into dev; I will do it then.
- (Superseded) Waiting on architect's `Feature.kt` (work/architect) for the narrow host interface, `config`/`device` in `FeatureContext`, and a hoisted `StandController` (shape to agree with dev.stand); then merge origin/work/architect, change MainActivity/HomeScreen, gate, push, message @parent. As of now work/architect has not pushed it.

## Next
Nothing that does not wait on others.

## Blocked
- Dependency updates flagged by lint (core-ktx 1.19.1, activity-compose 1.13.0, lifecycle 2.11.0, browser 1.10.0, test junit 1.3.0): tried, the gate fails (they pull Compose 1.9 and Kotlin 2.1.20 against the pinned BOM 2024.12.01, Kotlin 2.0.21, AGP 8.7.3). Needs a coordinated bump of BOM, Kotlin and AGP.

## For other roles
- play: screenshots as above; the Open button text is the only action, pt-PT listing text can reuse `values-pt-rPT/strings.xml`.
- release: the emulator job runs `./gradlew connectedDebugAndroidTest` (module `app`, class `ai.riskmandate.app.SmokeTest`); needs an API 30+ emulator image. Compile check: `./gradlew assembleDebugAndroidTest`.
- release: the dependency bump above touches `build.gradle.kts` (root) plugin versions; CI may want to publish `app/build/screenshots/` as an artifact.

## For Dinis
- Dinis: the licence notice still says "Copyright (c) TODO: copyright holder"; tell me who the holder is.
- Dinis: the pt-PT text is mine, please have a Portuguese speaker read it.
