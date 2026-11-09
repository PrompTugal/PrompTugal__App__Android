# qa.android status

> 9 Nov 2026: tasks 1 and 2 done; first review (work/app 5afbb97) PASS. Feature-level tests stay vacuous until a feature is enabled.

## Doing
Waiting for the next app branch (dev.* branches were empty at last fetch).

## Done
- `docs/qa/summit-test-plan.md`: criteria for A to D, regression, a 10-minute run for Dinis, pt-PT summary.
- `app/src/test/java/ai/riskmandate/app/qa/`: `FeatureNavigationTest` (open from home, Back, rotation, unique ids), `FeatureStringsTest` (pt-PT completeness, placeholders, format args, no hardcoded text, in `features/` recursively), `FeatureLinksTest` (URL literals, navigating code names `Links`, risky WebView settings, feature strings, manifest exports, allow-list refusals). The static guards were checked by planting a bad URL, an un-gated `loadUrl` and a hardcoded `Text("...")`: all three failed the tests as intended.
- `androidTest/SmokeTest`: three new tests (every enabled feature opens and Back returns; rotation; disabled features have no button). Compiled, not run (no emulator here).
- Environment: Android SDK installed to `/opt/android-sdk`; Robolectric artifact fetch hit Maven 429 once and passed on retry.

- Review `docs/qa/reviews/2026-11-09-app-5afbb97.md`: **PASS** (94 tests green on work/app plus qa tests; 5 low or info findings, Q-app-1 closed by a new qa test).
- Conductor asks (feature-id contract, translation coverage for feature_<id>.xml and for hardcoded text): covered by FeatureNavigationTest and FeatureStringsTest; app.android's LocalisationTest now overlaps and is fine.

## Next
Review each app branch as it lands into `docs/qa/reviews/<date>-<role>-<sha>.md` and message the conductor with each verdict. Tighten the plan with what each feature really does.

## Blocked
Nothing.

## For other roles
- Feature owners: the cross-feature tests need no change when you set `enabled = true`; they fail if a feature navigates without naming `Links`, hardcodes text, has an untranslated string, or holds a URL outside `Links.isAllowed`. Give the Site feature a `testTag` on its WebView and tabs and tell QA the tags.
- dev.stand: StandHost needs an injectable clock for the idle test (D-4, D-5).

## For Dinis
- The 10-minute run is in the plan, section "10-minute manual run". It cannot be done until A to D exist.
