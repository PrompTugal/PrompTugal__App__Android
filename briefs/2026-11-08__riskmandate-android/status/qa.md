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

- Round 5, 9 Nov (after the conductor's interrupt): **Q-llm-1 LIFT** (dev.llm dc63ad8, 467 tests, `reviews/2026-11-09-dev.llm-dc63ad8.md`); **G's download path PASS** with one low finding, Q-g-1: the privacy line names huggingface.co while the bytes come from its CDN hf.co (`...-g-download.md`, plus five installer probes, all pass); **app's navigation-test edit PASS** and **work/app 0833ca0 PASS** (451 tests; the duplicate-title finding on dev is fixed there). Flake work: `FeatureNavigationTest` is deterministic and shell-agnostic (clicks through the node's click action, idles the Robolectric looper, waits for the recreated activity and an enabled back callback); navigation class 30 of 30, full suite 19 of 19 valid runs (one run killed by my own pkill is excluded); stand's fix 22 of 22 full runs. `SmokeTest` leaves Licences and Diagnostics with the system Back. Product note: the "New" badge pops in after Home appears and resizes What's new's button.

- Round 6, 9 Nov: deep reviews on dev b821788. **Chat HOLD for enabling** (`reviews/2026-11-09-dev.chat-b821788.md`): Q-chat-1 confirmed by two probes, a rotation empties the chat on screen and loses a question asked while the answer streams (0 messages shown, 0 saved); Q-chat-2 for Dinis (history on a shared stand tablet). **Lab live head a01fcf7 PASS** (524 tests; live mode keeps "model proposes, code decides"; the scheme-relative egress bypass is fixed and pinned by eval's golden vectors); Q-lab-1 and Q-lab-2 still open (state the exact-match and line-format limits on screen). **dev.llm part HOLD for the device run** (`...-dev.llm-b821788.md`): Q-bench-1, a model downloaded in the app does not unhide the bench on a release build (probe); Q-g-4, `ModelSetupScreenTest.deletingThroughTheFeatureRefreshesTheHostsService` is flaky (2 of 5 isolated runs on plain dev, aborted two full gates). `docs/qa/device-run-idea-tab-plus.md` rewritten for the real flow (Firebase build, no computer, download on the tablet, then bench, chat, lab), adb as appendices only; it cannot be run until Preview exists and Q-bench-1 is fixed.

- Round 7, 9 Nov: **dev.chat acd3e5f PASS, Q-chat-1 LIFT** (531 tests; my two probes pass 8 of 8; the open chat survives a rotation, a question asked mid-answer is saved). **dev.lab loop head 1799c03 HOLD for enabling** (544 tests green): Q-loop-1, a rotation wipes a finished loop's counters and cancels a loop in progress (two probes); the arithmetic is right (my `QaLoopCountersTest`, 9 tests, mutation-checked, parked as `reviews/...loopcounters.test.kt.txt` until the loop code is on dev). Device guide updated to work/app 093bdc4's Preview (Diagnostics > Preview > Model setup, Chat, ABP Lab, Model bench; hidden in stand mode) and to loop mode; [confirm] marks removed, to be re-checked against a built APK.

- Round 8, 9 Nov, on dev fda3225: device guide final against the real strings (every label matched, one fixed: the bench item is "Model bench (on-device LLM test)"; the way in is covered by app's HostPreviewTest); Q-chat-1 LIFT confirmed on dev (my probes pass); Q-bench-1 fixed (my `BenchGateTest` adopted into qa/); `LoopCountersTest` adopted into qa/ (the loop code is on dev); Q-loop-1 still present on dev (both probes fail), so the guide still says not to rotate during a loop; Q-g-4 (`ModelSetupScreenTest.deletingThrough...`) still flaky on dev: it stopped 1 of 2 full gates here, and the second run with `--continue` was green, 569 tests.

- Round 9, 9 Nov: **dev.lab 0097158 PASS, Q-loop-1 LIFT** (575 tests; my rotation probes pass 3 of 3: finished-loop counters survive, a running loop ends with "Stopped: the screen was turned ... The counts so far are kept"). Q-lab-1 and Q-lab-2 are on screen ("What this check sees", checked). **Q-g-4 fixed on dev**: `ModelSetupScreenTest` 10 of 10 isolated runs (it failed 2 of 5 before). **Chat grounding texts vs the live pages: PASS**, two low findings (Q-chat-4 "Only a boundary is a control." is the app's wording, not a sentence on the page; Q-chat-5 the inventory passage is compressed). Guide updated for the loop rotation behaviour and the "What this check sees" lines. Still open: Q-chat-2 (Dinis: history on a shared stand tablet).

- Guide corrections (conductor, security R-qa-1): export marked off in this build; results go to the conductor in a message and any committed copy omits serial numbers and accounts; the loop-rotation step already checks the 0097158 behaviour.

- Workbench (9 Nov, on request): `docs/qa/workbench-test-plan.md` (stats correctness with sources, sweep caps, run log, tool-test checks with a mutation scheme, rotation, sandbox) and `docs/qa/device-run-workbench.md` (draft device section; labels marked [confirm] until the feature lands). The plan needs `FakeLlmService` to script stats, stop reasons and a virtual clock (asked of dev.llm); today it scripts only a token delay.

- Cloud key (9 Nov, dev.chat's request): `docs/qa/device-run-cloud-key.md` drafted (key never shown, survives restart, Remove, FLAG_SECURE and recents, not in stand mode, and section E: every on-device claim must stay true or change when the cloud switch is on). `KeystoreCipherTest` is not on the pushed `work/dev.chat` (14f671b has only SmokeTest in androidTest); I cannot run instrumented tests here (no emulator): the CI smoke job runs `connectedDebugAndroidTest` on dev.

## Next
Review each app branch as it lands into `docs/qa/reviews/<date>-<role>-<sha>.md` and message the conductor with each verdict. Tighten the plan with what each feature really does.

## Blocked
Nothing.

## For other roles
- dev.llm: Workbench test double, `FakeLlmService` must script `LlmStats` (all fields, incl. null), stop reason and cancellation, and the Workbench takes `now: () -> Long` and injected memory/battery/thermal readers (plan section 0).
- dev.lab: Q-loop-1, keep a finished loop's counters across a rotation (a Saver for the RunScore list) and say so if a rotation stops a running loop; probes and my `QaLoopCountersTest` in `docs/qa/reviews/`.
- dev.llm: Q-bench-1 (`benchAvailable` must also look at the internal folder; the probe is the test) and Q-g-4 (make `ModelSetupScreenTest.deletingThrough...` deterministic).
- dev.chat: Q-chat-1 (rememberSaveable for the open conversation; save the question when sent and the partial answer from a NonCancellable block; probes in `reviews/2026-11-09-dev.chat-b821788.probe.kt.txt`).
- dev.lab: Q-lab-1, Q-lab-2 (one line each on the screen).
- app: Preview, as the way into Models, Chat and the Lab on a release build.
- dev.llm: Q-g-1, name the CDN in the privacy line (en, pt-PT, privacy-policy draft); adopt `reviews/2026-11-09-dev.llm-dc63ad8-installer.probe.kt.txt` as a test. After dev.llm is on dev I move the probe into qa/ if you prefer.
- app: the "New" badge pops in asynchronously and resizes the button: reserve its space or read the flag synchronously. Take work/qa's `qa/FeatureNavigationTest.kt` and `androidTest/SmokeTest.kt` on conflict.
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
