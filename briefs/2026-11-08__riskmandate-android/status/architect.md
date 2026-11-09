# architect.android status

> 9 Nov 2026, about 01:40 UTC: architecture page written (docs/architecture/README.md). No app branch has commits beyond dev yet, so no review is due. FeatureContext proposals P1 to P4 sent to the conductor; Feature.kt unchanged until agreed.

## Doing
Waiting for the first feature or app commits to review.

## Done
- `docs/architecture/README.md`: host, features contract, routing, state, threading, lifecycle, reach table, how a feature asks the host, dependencies (pre-approved: androidx.webkit 1.12.x for site, lifecycle-runtime-compose 2.8.7, Media3 1.5.x only if E starts), strings/assets/tests, WebView rules.
- Review round 1 (01:35 UTC): `git log origin/dev..origin/work/<role>` empty for app, design, qa, dev.site, dev.abpdemo, dev.mandate, dev.stand. play has fb617fd (docs only, not app-touching, not reviewed).

## Next
At each wake: fetch, review every app-touching branch head into `docs/architecture/reviews/<date>-<role>-<sha>.md`, message the conductor with the verdict.

## Blocked
Nothing.

## Proposals for FeatureContext (For conductor and app; Feature.kt changes only once agreed)
- **P1** add `config: ManagedConfig` and `device: DeviceStatus` (the host's current values). Needed by mandate (allow-list, start URL, management state) and site (start at `config.startUrl`). Avoids features calling `ManagedConfig.read`/`DeviceStatus.detect` themselves and drifting from the host. Needs one line in MainActivity (app).
- **P2** keep `onBack`, document it as "go to the home screen".
- **P3** MainActivity builds the context in `remember(config, device) { FeatureContext(...) }` (today a new instance every recomposition).
- **P4** stand's home-screen on/off switch: a `StandController` hoisted in MainActivity, passed to `StandHost` and `HomeScreen`; agreed between app and stand, not a FeatureContext field.

## For other roles
- qa / app: `LocalisationTest.everyStringIsTranslated` checks only `strings.xml`, and `noScreenHardcodesText` lists only the top-level package (not `features/**`, not `ui/`). Extend both to `features.xml`, `feature_*.xml` and all sources recursively, or feature strings can ship untranslated.
- qa: a contract test under `app/src/test/java/ai/riskmandate/app/features/`: ids unique, lowercase letters, not `home`/`licences`/`diagnostics`; every feature title resolves in en and pt-PT.
- dev.site + dev.mandate + security: agree the "what the app collects" sentence against what riskmandate.ai's pages load in the WebView.
- dev.stand: idle detection by a non-consuming `pointerInput` at `PointerEventPass.Initial` in StandHost (check on a tablet that WebView touches reach it); keep-screen-on via `LocalView.keepScreenOn`.

## For Dinis
Nothing.
