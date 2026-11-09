# dev.mandate.android status

> Feature C works end to end and is switched on (`enabled = true`); gate green. On `dev` with FeatureContext v2 merged. The gap on release was 1 (exported ProfileInstallReceiver); app.android is removing it, so it should read 0 (see "For app").

## Doing
Waiting for conductor review. design.android's shared components have not landed (no `ui/` package on this branch), so the screen uses plain Material 3 with the app theme; swap them in when they land.

## Done
- `features/mandate/MandateModel.kt`: pure Kotlin. `ReachItem`, `Mandate`, `MandateReport` (gap = reach minus mandate, computed, never stored), `HostProbe` (the real `Links.isAllowed` tried on six sample addresses).
- `features/mandate/ReachCollector.kt`: reach read at runtime from `PackageManager` (requested and granted permissions, exported activities, services, receivers and providers with the permission that guards them), application flags (cleartext, backup) and the files the app keeps (files, databases, shared_prefs).
- `features/mandate/MandateFeature.kt`, `MandateScreen.kt`: the screen (reach, mandate, gap, device management state, managed configuration values, the three definitions quoted). Gap said in words, not colour alone. Back button 48dp, survives 200% font scale.
- Strings in `res/values/feature_mandate.xml` and `res/values-pt-rPT/feature_mandate.xml` (a test fails if the two differ). The title string `feature_mandate_title` stays in `features.xml` (not mine).
- Tests: `MandateModelTest` (11, JVM), `MandateScreenTest` (12, Robolectric: en and pt-PT render, a gap rendered, real collection on the real manifest, end-to-end `MandateFeature.Content`, 200% font, touch target, translation parity).
- Gate `./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease`: pass.

- Second round (conductor, 9 Nov): merged `origin/dev` (FeatureContext v2: the feature now takes `config` and `device` from the context, no Activity, no second read). qa's FeatureLinksTest flagged URL literals in `MandateModel.kt`; the on-screen refusal probes are now built from parts (`HostProbe.Sample(scheme, host, port, userinfo)`), so no URL literal remains in feature sources and the probes still run through the real `Links.isAllowed`.

- Third round (architect HOLD on 78ae8d6), merged `origin/dev` again:
  - A-man-1: the screen draws content only (no Back, no title, no insets); the host's FeatureFrame draws those.
  - A-man-2: already clean without an exemption: the probes are built from parts, no URL literal in feature sources, so `MandateModel.kt` needs no `// qa: probe` marker or qa exemption. Say if architect still wants the marker.
  - A-man-3: `context.config` and `context.device`, and the report is re-read when either changes.
  - A-man-4: reach now lists each file in files/, databases/ and shared_prefs/ by path, and app_webview/ and cache/ as directories with a file count. The mandate has entries for `shared_prefs/stand_mode.xml` (stand's two settings, taken from `work/dev.stand`'s `StandSettings.FILE`), `app_webview` and `cache` (the in-app view). A stray file is gap; the tests check both. If stand's file name changes, change the entry in `Mandate.current`.
  - A-man-6: reach is read on `Dispatchers.IO` in a `LaunchedEffect`, with a loading line.
  - A-man-7: `ManagedConfig.KEY_*` instead of literals.
  - A-man-5 not done: `Links.ALLOWED_HOSTS` is private (not my file). See "For app".

- Fourth round (security R-site-1): the "nothing leaves the device" sentence is gone. Network the app makes itself is now computed: `networkReach(enabledFeatureIds)` adds `connects:riskmandate.ai` and `connects:www.riskmandate.ai` as reach exactly when the `site` feature is enabled in `FEATURES`; the mandate allows those two hosts (reason: the in-app site view) and nothing else, so a connection to any other host would show as gap (tested). With site off the screen says the app connects to nothing itself (pages open in the browser, which makes its own connection). The data line now reads "Data collected by the app: none. It has no analytics, accounts or identifiers. The only network traffic is loading riskmandate.ai pages, in your browser or in the in-app view." (en and pt-PT).
- design's `ui/FeatureContent` is on `work/design`, not yet on `dev`; I will adopt it once it merges.

- Fifth round (security HOLD on two strings): the data line and the INTERNET reason now say what is true: the app itself connects only to riskmandate.ai and www.riskmandate.ai, only through the in-app site view; pages opened in the browser are the browser's own traffic; links to other sites are not loaded in the app. I did not write "handed to the browser on a tap": `work/dev.site`'s `SitePolicy` hands them to `Browser.open`, which refuses what `Links.isAllowed` refuses, so I claim only what holds. Re-check against dev.site's final code.

## Vocabulary (riskmandate.ai, https://riskmandate.ai/, read 9 Nov 2026)
- Reach: "Everything the agent can actually access in this deployment, including what nobody thought to check."
- Mandate: "The job, written down: what the business authorised it to do."
- Gap: "Reach minus mandate. Never written by hand, and recomputed whenever either side moves."
Here the "agent" is this app. Claims on screen go no further than these sentences and `docs/play/data-safety.md` (data collected: none).

## Next
Nothing until review, then design components when they land.

## Blocked
Nothing.

## For app (app.android)
The release merged manifest exports `androidx.profileinstaller.ProfileInstallReceiver` (`exported="true"`, guarded by `android.permission.DUMP`, which only the shell and system hold). The mandate does not need it, so the screen's gap is **1** on a release build, shown plainly. Removing it is a manifest change outside my paths: `<receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" tools:node="remove" />` (check first that dropping baseline-profile installation is acceptable). After that the gap reads none. Debug builds also export test activities from `ui-test-manifest`; those never ship.

Also (A-man-5): make `Links.ALLOWED_HOSTS` public so `Mandate.HOSTS` can use it instead of copying it. The run-time probes catch drift meanwhile.

## For Dinis
Decision for the stand: leave the gap of 1 visible (it is true and makes the point of the feature), or have app.android remove the receiver so it reads none.
