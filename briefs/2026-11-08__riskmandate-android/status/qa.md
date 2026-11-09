# qa.android status

> 9 Nov 2026, about 02:00 UTC: tasks 1 and 2 written and green locally; no app branch has landed yet, so task 3 has nothing to review. The new navigation tests are vacuous until a feature is enabled (by design: they pick up each feature on its own).

## Doing
Waiting for the first app branch (`git log origin/dev..origin/work/<role>` is empty for all roles at 01:55 UTC).

## Done
- `docs/qa/summit-test-plan.md`: criteria for A to D, regression, a 10-minute run for Dinis, pt-PT summary.
- `app/src/test/java/ai/riskmandate/app/qa/`: `FeatureNavigationTest` (open from home, Back, rotation, unique ids), `FeatureStringsTest` (pt-PT completeness, placeholders, format args, no hardcoded text, in `features/` recursively), `FeatureLinksTest` (URL literals, navigating code names `Links`, risky WebView settings, feature strings, manifest exports, allow-list refusals). The static guards were checked by planting a bad URL, an un-gated `loadUrl` and a hardcoded `Text("...")`: all three failed the tests as intended.
- `androidTest/SmokeTest`: three new tests (every enabled feature opens and Back returns; rotation; disabled features have no button). Compiled, not run (no emulator here).
- Environment: Android SDK installed to `/opt/android-sdk`; Robolectric artifact fetch hit Maven 429 once and passed on retry.

## Next
Review each app branch as it lands into `docs/qa/reviews/<date>-<role>-<sha>.md` and message the conductor with each verdict. Tighten the plan with what each feature really does.

## Blocked
Nothing.

## For other roles
- Feature owners: the cross-feature tests need no change when you set `enabled = true`; they fail if a feature navigates without naming `Links`, hardcodes text, has an untranslated string, or holds a URL outside `Links.isAllowed`. Give the Site feature a `testTag` on its WebView and tabs and tell QA the tags.
- dev.stand: StandHost needs an injectable clock for the idle test (D-4, D-5).

## For Dinis
- The 10-minute run is in the plan, section "10-minute manual run". It cannot be done until A to D exist.
