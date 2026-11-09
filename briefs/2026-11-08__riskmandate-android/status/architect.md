# architect.android status

> 9 Nov 2026, about 01:15 UTC (by the session clock): FeatureContext v2 pushed, at the conductor's request after security's checklist E. It no longer carries the Activity, and it gains config and device. Gate green. No feature branch has commits yet.

## Doing
Waiting for the first feature or app commits to review.

## Done
- FeatureContext v2 (`features/Feature.kt`): `activity` removed; `onBack` (go home), `config: ManagedConfig` and `device: DeviceStatus` added. A deprecated `FeatureContext(activity, onBack)` constructor keeps MainActivity compiling (it reads config and device itself) until app switches. Gate: 71 tests, lint, release APK and AAB green; secret scan clean. README updated (reach table, WebView offline path aligned with security's checklist A2: `/assets/feature_site/` on appassets.androidplatform.net).
- `docs/architecture/README.md`: host, features contract, routing, state, threading, lifecycle, reach table, how a feature asks the host, dependencies (pre-approved: androidx.webkit 1.12.x for site, lifecycle-runtime-compose 2.8.7, Media3 1.5.x only if E starts), strings/assets/tests, WebView rules.
- Review round 1 (01:35 UTC): `git log origin/dev..origin/work/<role>` empty for app, design, qa, dev.site, dev.abpdemo, dev.mandate, dev.stand. play has fb617fd (docs only, not app-touching, not reviewed).

## Next
At each wake: fetch, review every app-touching branch head into `docs/architecture/reviews/<date>-<role>-<sha>.md`, message the conductor with the verdict.

## Blocked
Nothing.

## FeatureContext
- v2 applied: P1 (config, device), P2 (onBack means go home). Activity removed (security E).
- **For app (P3):** in MainActivity replace `FeatureContext(activity = this@MainActivity, onBack = goHome)` with `remember(config, device) { FeatureContext(onBack = goHome, config = config, device = device) }`. Then I delete the deprecated constructor.
- Open, P4: stand's home-screen on/off switch (`StandController` hoisted by MainActivity, passed to StandHost and HomeScreen), app and stand to agree.

## For other roles
- qa / app: `LocalisationTest.everyStringIsTranslated` checks only `strings.xml`, and `noScreenHardcodesText` lists only the top-level package (not `features/**`, not `ui/`). Extend both to `features.xml`, `feature_*.xml` and all sources recursively, or feature strings can ship untranslated.
- qa: a contract test under `app/src/test/java/ai/riskmandate/app/features/`: ids unique, lowercase letters, not `home`/`licences`/`diagnostics`; every feature title resolves in en and pt-PT.
- dev.site + dev.mandate + security: agree the "what the app collects" sentence against what riskmandate.ai's pages load in the WebView.
- dev.stand: idle detection by a non-consuming `pointerInput` at `PointerEventPass.Initial` in StandHost (check on a tablet that WebView touches reach it); keep-screen-on via `LocalView.keepScreenOn`.

## For Dinis
Nothing.
