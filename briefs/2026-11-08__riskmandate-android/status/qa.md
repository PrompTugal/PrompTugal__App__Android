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

- Review `docs/qa/reviews/2026-11-09-dev.stand-94da82a.md`: **HOLD**. Q-stand-1: the 48 dp staff corner is a clickable over the content, so it swallows taps in the bottom-end corner of every screen, stand mode off too (probe fails 0 of 1 taps). 114 tests green otherwise.

- Reviews, 9 Nov (all in `docs/qa/reviews/`): **work/app f317cf8 PASS** (96 tests; release manifest exports only MainActivity), **work/dev.mandate 4ff37a6 PASS** (120 tests; URL exemption for its four shown refusal samples, exact per file), **work/dev.site e6c92f0 HOLD** (does not compile on dev: `context.activity` is gone; also two Back buttons and tab reset on rotation, both probed), **work/dev.abpdemo 38dfbf8 HOLD** (red gate: hardcoded `contentDescription` template; claims traced to the live sites), **work/dev.stand 23d16f3 HOLD** (staff corner still swallows taps; probe fails).
- Guards in `qa/` made precise after three false positives of mine on the site branch, mutation-checked again; new `shownRefusalSamples` map, positive WebView lock-down check.

- Round 2, 9 Nov (all PASS, `docs/qa/reviews/`): **dev.site ee25896** (198 tests; Q-site-1 and 2 fixed, Q-site-3 rotation tab reset still open, medium), **dev.mandate 42f7a7b** (192), **app 95e7978** (168; one red test is a flake in stand's test, Q-stand-8), **architect 0b521af** (167), and, found already on dev, **dev.stand 9f207e8**, **dev.abpdemo b94232d**, **design b61b5d1** (165 on dev's head; they supersede my HOLDs). `qa/` now also holds `StandCornerTapsTest`, an SSL guard that flags only `proceed()`, the pinned site asset-origin exemption, `featuresNeverLaunchIntentsDirectly` and `externalGateRefusesWhatIsNotPlainHttps`.

- Round 3, 9 Nov (`docs/qa/reviews/`): PASS: **dev.site aaaf525** (259 tests; Q-site-3 rotation fixed), **dev.stand ecd28c2** (259; Q-stand-8 flake addressed), **dev.mandate 7f5706d** (260; the riskmandate.ai/privacy quote checked verbatim on the live page), **dev.releases ffb2ccd** (260), **work/app 5eac3ad** (269), **architect 3e3ea0e** (261), **editor 3a695d3** (259). **HOLD: design 372ddc5** (261 tests, 1 failure: `ui/StepRow.kt:44` `Text("$index.", ...)` is a hardcoded literal, use a string resource). `dev` itself plus my tests: 258 tests, full gate green.
- Plan: `summit-test-plan.md` now matches the real screens (F What's new section; stand long-press on the bottom-right corner of Home only; Open riskmandate.ai refuses while pinned). `llm-test-plan.md` (G, H, I; no LLM code on dev yet), `device-run-idea-tab-plus.md` (step by step for Dinis; lines marked [confirm] wait for the real screens), and `qa/LlmSandboxTest` (chat and lab stay in the sandbox; vacuous until code lands).

- Round 4, 9 Nov: **work/dev.llm c02632a: HOLD** (`reviews/2026-11-09-dev.llm-c02632a.md`): gate green, 329 tests, release manifest unchanged but for one native-library line, APK 50.6 MB; missing, truncated and right-size-wrong-hash files are all refused without a crash (the last driven through the real screen with a sparse 2.6 GB file); but `LiteRtBench.run` builds the `Engine` outside its `try` and catches `Exception` only, so an `UnsatisfiedLinkError` (probe) or R8 `NoClassDefFoundError` closes the app instead of showing "The run failed". Five-line fix in the review. `device-run-idea-tab-plus.md` Part 1 is the step-by-step for Dinis (curl, sha256sum, adb push, bench, copy back). `FeatureNavigationTest` rewritten to wait for the expected state after every step (flake report from dev.chat); 20-run verification and the dev.stand ea70ed4 check are in progress.

## Next
Review each app branch as it lands into `docs/qa/reviews/<date>-<role>-<sha>.md` and message the conductor with each verdict. Tighten the plan with what each feature really does.

## Blocked
Nothing.

## For other roles
- design: Q-des-1, string resource for the step number in StepRow (red gate).
- dev.llm: G-5 in `docs/qa/llm-test-plan.md`: the model host redirects to a CDN; record every host the real download passes through so security and QA can pin them; a foreground-service permission would be new (G-10). Tell QA the folder the app reads for the adb fallback and the name of the bench screen.
- dev.stand: Q-stand-8, add `waitForIdle()` before each fake-clock jump in `StandHostTest.heldLongerThan...` (flaky under load). dev.site: Q-site-3, `rememberSaveable` for the tab before the summit build.
- All three features: each draws its own Back and title inside the host FeatureFrame (site probe: two Back buttons). Conductor to decide: features draw content only, or the frame goes.
- dev.site: merge dev, replace `context.activity`; rememberSaveable for the tab; drop own Back and title. dev.abpdemo: string resource for the layer description. dev.stand: fix the corner (probe in reviews). dev.mandate: after A and D merge, add mandate entries for the stand setting and the WebView storage, and reword "in your browser" (Q-man-1). Merge work/app first (it removes the exported ProfileInstallReceiver).
- dev.stand: fix Q-stand-1 (see the review; the probe file shows the check), then ask for a re-review.
- dev.mandate and play: the app now stores the stand-mode setting locally; do not claim "stores no data" (Q-stand-3).
- Feature owners: the cross-feature tests need no change when you set `enabled = true`; they fail if a feature navigates without naming `Links`, hardcodes text, has an untranslated string, or holds a URL outside `Links.isAllowed`. Give the Site feature a `testTag` on its WebView and tabs and tell QA the tags.
- dev.stand: StandHost needs an injectable clock for the idle test (D-4, D-5).

## For Dinis
- External links (R-ext-2): a tap on any https link inside the site now opens the system browser (never while pinned). The brief said another app is never launched. OK as a default, or never?
- Stand mode has no home-screen switch: staff long-press the bottom-end corner. OK, or do you want a visible switch on home (Q-stand-2)?
- The 10-minute run is in the plan, section "10-minute manual run". It cannot be done until A to D exist.
