# architect.android status

> 9 Nov 2026, about 01:30 UTC: review round 2. app 5afbb97 PASS, qa a2a49c6 PASS; dev.stand 94da82a, dev.abpdemo 38dfbf8 and dev.mandate 78ae8d6 HOLD (each fix is small). A trial merge of all of them has no conflicts but 3 tests fail. FeatureContext v2 (e3eb80c) waits to be merged with app's MainActivity line.

## Doing
Waiting for the first feature or app commits to review.

## Done
- Review round 2 (`docs/architecture/reviews/2026-11-09-*`): app 5afbb97 PASS (FeatureFrame accepted into the rules); qa a2a49c6 PASS (A-qa-1: exemption marker for mandate's probe URLs); dev.stand 94da82a HOLD (corner swallows taps on every screen); dev.abpdemo 38dfbf8 HOLD (own title and Home inside FeatureFrame; string template in a contentDescription fails two tests); dev.mandate 78ae8d6 HOLD (own Back, title and insets inside FeatureFrame; probe URLs fail qa's link test). Trial merge of dev + architect + app + qa + stand + abpdemo + mandate: no conflicts, compiles, lint passes, 157 tests with 3 failing (abpdemo x2, mandate x1).
- README: FeatureFrame rule (features draw content only), string prefix `<id>_`, stand corner and idle-reset notes.
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
- conductor: FeatureContext narrowing. I kept it to onBack, config and device: Context, LifecycleOwner and View already come from Compose (`LocalContext`, `LocalLifecycleOwner`, `LocalView`), and copying them into the context adds nothing. LockTask and window control stay in StandHost (stand only), which already gets the Activity.
- dev.stand + dev.abpdemo: agree whether auto-play may hold off the idle reset (A-stand-3, A-abp-4). If yes, I propose a FeatureContext signal.
- qa / app: `LocalisationTest.everyStringIsTranslated` checks only `strings.xml`, and `noScreenHardcodesText` lists only the top-level package (not `features/**`, not `ui/`). Extend both to `features.xml`, `feature_*.xml` and all sources recursively, or feature strings can ship untranslated.
- qa: a contract test under `app/src/test/java/ai/riskmandate/app/features/`: ids unique, lowercase letters, not `home`/`licences`/`diagnostics`; every feature title resolves in en and pt-PT.
- dev.site + dev.mandate + security: agree the "what the app collects" sentence against what riskmandate.ai's pages load in the WebView.
- dev.stand: idle detection by a non-consuming `pointerInput` at `PointerEventPass.Initial` in StandHost (check on a tablet that WebView touches reach it); keep-screen-on via `LocalView.keepScreenOn`.

## For Dinis
Nothing.
