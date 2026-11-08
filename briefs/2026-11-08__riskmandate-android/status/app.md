# app.android status

> Second task list done (diagnostics screen, androidTest smoke test, offline behaviour, size and startup). Gate green. Stopped.

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
