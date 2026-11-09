# architect.android status

> 9 Nov 2026, about 06:00 UTC: contract on dev or pending merge: homeBadge, icon/kicker/summary, openFeature, llm. Preview path reviewed (app 093bdc4 PASS). Post-summit, approved: P8 (iconRes), the Kotlin bump to drop the metadata flag, APK split. Waiting for new heads.

## Doing
Waiting for the first feature or app commits to review.

## Done
- Review app 4e26cca (45752f8 Preview) PASS: matches the ruling (`preview_<id>`); A-app-14: host tables iconFor/sectionOf/NAV_ORDER belong on Feature/FEATURES (offer: P7 icon as @DrawableRes). README preview section aligned with the code.
- Ruling: preview of disabled features through a separate host route `preview/<id>` from Diagnostics only. openFeature stays refused except from a feature itself opened in preview. README section written.
- FeatureContext.llm: LlmService? = null, with FeatureContextLlmTest (null by default; a feature reaches a FakeLlmService). Gate green, secret scan clean.
- FeatureContext.openFeature(id) (a26f037) with FeatureContextOpenFeatureTest.
- Ruling A-lab-7: one strict JSON parser, llm/StrictJson (dev.llm takes lab's general parse/render; lab keeps hash16/parseAt and uses checkToolCall). README and the lab review updated.
- Review dev.llm 31a87ac PASS (contract accepted with Idle and LlmError; ToolSpec, StrictJson and checkToolCall are the one validator; LlmHost per the README; A-llm-12 installed list for chat; A-llm-13 TOO_LONG mapping; A-llm-3 still open). README contract section updated to what was built. FeatureContext.llm patch verified on a trial and waiting for LlmService on dev.
- Reviews: design fb824aa PASS (A-des-4 ScreenHeader belongs to the host frame; A-des-5 breakpoints written down); dev.llm 9c3fbd5 PASS (A-llm-4/5 closed; A-llm-3: conflicts with app's shell, app re-does the host lines). README: one title per screen.
- P7: `icon`, `kicker`, `summary` on `Feature` (null defaults) plus `FeaturePresentationTest`. Gate green.
- Review: app 894a93b shell step 1 PASS (A-app-11 double header on phones and Back next to the sidebar; A-app-12 use P7; A-app-13 unused import).
- Reviews: dev.llm c02632a PASS (A-llm-1 licences, A-llm-2 metadata flag only for the summit, A-llm-3/4/5: bench route through app, out of features/, debug or model-present only); dev.chat 4c6d11e PASS (A-chat-1: use context.llm, not a chat-owned CompositionLocal); eval 2aebd3d PASS (A-eval-1 budget ledger path); dev.lab b67a7c5 PASS fix-forward (A-lab-1 argument validation, A-lab-2 golden vectors shared with eval, A-lab-3 parse eval's formats). README: contract adds `installed` and `modelId`; noBackupFilesDir; LiteRT-LM approved.
- Reviews: site 747b521 PASS; app 62a5ee6 HOLD (A-app-10: the What's new card imports ReleasesNotice and StandSettings and reads disk in composition; rework onto homeBadge). A-app-8 closed.
- P6 pushed: `Feature.homeBadge()` (default null) plus `FeatureBadgeTest` (default, override, every listed feature answers). Gate green. README: the host must not import feature packages except `StandHost`.
- On-device LLM rules (README): one host-held `LlmHost` per process (the one sanctioned process-wide object), a serialised service, a Flow-based contract shape, tool calls proposed by the model and validated in Kotlin per feature, plain-text output, pinned downloads over HttpURLConnection with Range, and storage paths with backup exclusions. Dependencies: LiteRT-LM approved in principle; org.json for JSON; Room, WorkManager, kotlinx-serialization and ViewModel not without a reason.
- Review round 6: app 95e7978 PASS (A-app-7 closed; A-app-8 still open; A-app-9 new: Browser.open ignores pinning); qa bdb73a5 PASS (guards precise); site ee25896 PASS (openExternal only on real main-frame taps); mandate 42f7a7b PASS (network wording true); releases 61d2b70 PASS (A-rel-1: the host must not call into the feature; proposal P6). Trial merge of all of them: green, 253 tests.
- Deleted the transitional `FeatureContext(activity, onBack)` constructor (ffa3c6b), since app f317cf8 is on dev. Gate green.
- Review round 5: app e914cc6 PASS (A-app-7: drop `LaunchedEffect(route) { idle.clear() }`, my own suggestion, which wipes a live hold after a rotation; A-app-8: pass idleHeld); stand 9f207e8 PASS; site 13114fd PASS (content only); abpdemo b94232d PASS (content only, adaptive layout, HoldIdleWhile); mandate 5a43197 PASS (content only, honest storage, off the main thread). README and IdleHolds.clear() doc corrected.
- Review round 4: app b2ad8aa PASS (Browser.openExternal is the right shape; A-app-5: the privacy wording must change, for security and play); design b61b5d1 PASS (FeatureContent, content only); dev.stand b9c9af3 PASS (corner gesture consumes nothing and is on Home only; A-stand-7: wire idleHeld); dev.site ba979fc HOLD (only A-site-2, its own frame; renderer, asset origin and v2 all fixed); dev.abpdemo 9bbfe59 HOLD (only A-abp-1; wire HoldIdleWhile); dev.mandate 5a35d4c HOLD (only A-man-1; A-man-4 medium); qa note A-qa-6 to A-qa-9 for site, A-qa-1 withdrawn.
- FeatureContext v3 (`features/Feature.kt`): `holdIdle` (default no-op), `HoldIdleWhile(active)` (DisposableEffect, releases on leave), `IdleHolds` (host-side token list, Compose state, `clear()`). `app/src/test/.../features/FeatureContextTest.kt` (4 tests; contract tests, architect-owned). Gate green. Secret scan: the working tree is clean; 2 history hits on origin/work/editor d722a6b (`website/validate.py`, `website/test_site.py`) are the validator's own detection patterns, not keys (for security to confirm).
- Review round 3: app f317cf8 PASS; qa c0c10e8 PASS with A-qa-6 to A-qa-8 (static tests flag site's safe settings); dev.site e6c92f0 HOLD (does not compile against v2; own Back, title and insets; no onRenderProcessGone; offline copy under the real origin is for security to decide); design 3397228 HOLD (FeatureScaffold is a second frame: make it a content-only FeatureBody); dev.stand 23d16f3 HOLD (keep-screen-on fixed; corner still over every screen). Trial merge of all nine branches: site fails to compile; patched locally, 191 tests with 5 failing: abpdemo x2 (one cause), the URL-literal test x1 (mandate probes plus a false positive on site), site x2 (qa false positives).
- Review round 2 (`docs/architecture/reviews/2026-11-09-*`): app 5afbb97 PASS (FeatureFrame accepted into the rules); qa a2a49c6 PASS (A-qa-1: exemption marker for mandate's probe URLs); dev.stand 94da82a HOLD (corner swallows taps on every screen); dev.abpdemo 38dfbf8 HOLD (own title and Home inside FeatureFrame; string template in a contentDescription fails two tests); dev.mandate 78ae8d6 HOLD (own Back, title and insets inside FeatureFrame; probe URLs fail qa's link test). Trial merge of dev + architect + app + qa + stand + abpdemo + mandate: no conflicts, compiles, lint passes, 157 tests with 3 failing (abpdemo x2, mandate x1).
- README: FeatureFrame rule (features draw content only), string prefix `<id>_`, stand corner and idle-reset notes.
- FeatureContext v2 (`features/Feature.kt`): `activity` removed; `onBack` (go home), `config: ManagedConfig` and `device: DeviceStatus` added. A deprecated `FeatureContext(activity, onBack)` constructor keeps MainActivity compiling (it reads config and device itself) until app switches. Gate: 71 tests, lint, release APK and AAB green; secret scan clean. README updated (reach table, WebView offline path aligned with security's checklist A2: `/assets/feature_site/` on appassets.androidplatform.net).
- `docs/architecture/README.md`: host, features contract, routing, state, threading, lifecycle, reach table, how a feature asks the host, dependencies (pre-approved: androidx.webkit 1.12.x for site, lifecycle-runtime-compose 2.8.7, Media3 1.5.x only if E starts), strings/assets/tests, WebView rules.
- Review round 1 (01:35 UTC): `git log origin/dev..origin/work/<role>` empty for app, design, qa, dev.site, dev.abpdemo, dev.mandate, dev.stand. play has fb617fd (docs only, not app-touching, not reviewed).

## Next
- After the summit build: P8 step 1 (iconRes, icon deprecated), then steps 2 and 3 with design and the owners; review app's NAV_ORDER from FEATURES. See README "Post-summit items".
At each wake: fetch, review every app-touching branch head into `docs/architecture/reviews/<date>-<role>-<sha>.md`, message the conductor with the verdict.

## Blocked
Nothing.

## FeatureContext
- v2 applied: P1 (config, device), P2 (onBack means go home). Activity removed (security E).
- **For app (P3):** in MainActivity replace `FeatureContext(activity = this@MainActivity, onBack = goHome)` with `remember(config, device) { FeatureContext(onBack = goHome, config = config, device = device) }`. Then I delete the deprecated constructor.
- Open, P4: stand's home-screen on/off switch (`StandController` hoisted by MainActivity, passed to StandHost and HomeScreen), app and stand to agree.

## For other roles
- conductor: **P6 proposal**, `@Composable fun homeBadge(): Int? = null` on `Feature`, so the home screen can show "New" for releases (or any feature) without importing a feature package. Say yes and I push it with a contract test; app then shows it on the feature button.
- conductor: FeatureContext narrowing. I kept it to onBack, config and device: Context, LifecycleOwner and View already come from Compose (`LocalContext`, `LocalLifecycleOwner`, `LocalView`), and copying them into the context adds nothing. LockTask and window control stay in StandHost (stand only), which already gets the Activity.
- dev.stand + dev.abpdemo: agree whether auto-play may hold off the idle reset (A-stand-3, A-abp-4). If yes, I propose a FeatureContext signal.
- qa / app: `LocalisationTest.everyStringIsTranslated` checks only `strings.xml`, and `noScreenHardcodesText` lists only the top-level package (not `features/**`, not `ui/`). Extend both to `features.xml`, `feature_*.xml` and all sources recursively, or feature strings can ship untranslated.
- qa: a contract test under `app/src/test/java/ai/riskmandate/app/features/`: ids unique, lowercase letters, not `home`/`licences`/`diagnostics`; every feature title resolves in en and pt-PT.
- dev.site + dev.mandate + security: agree the "what the app collects" sentence against what riskmandate.ai's pages load in the WebView.
- dev.stand: idle detection by a non-consuming `pointerInput` at `PointerEventPass.Initial` in StandHost (check on a tablet that WebView touches reach it); keep-screen-on via `LocalView.keepScreenOn`.

## For Dinis
Nothing.
