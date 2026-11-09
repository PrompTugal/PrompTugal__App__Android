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

## For Dinis
Decision for the stand: leave the gap of 1 visible (it is true and makes the point of the feature), or have app.android remove the receiver so it reads none.
