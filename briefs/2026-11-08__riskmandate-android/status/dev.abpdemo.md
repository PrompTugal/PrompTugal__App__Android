# dev.abpdemo.android status

> Feature B ("ABP in every layer") built: pure-Kotlin step model, Compose screen, English and pt-PT, tests. Gate green.

## Doing
Nothing; pushed, waiting for the conductor.

## Done
- `features/abpdemo/AbpDemoModel.kt`: pure Kotlin. `Layer` (browser, model, hook, proxy, harness, device), `Strength` (guidance, soft, firm, strong, floor), `STEPS` (scene + six layers), `DemoPlayer` (next, back, hand-press stops auto-play, `tick()` for auto-play, loops at the end).
- `features/abpdemo/AbpDemoFeature.kt`: landscape-first screen for a tablet read from 2 m. Permanent red banner (never dismissable) saying it is a demonstration, not a live attack. Left: the six-layer diagram, the current layer lit (animated), done and waiting layers written out in words, not only colour. Right: scenario (step 1) or, per layer, what it does, the rule that fires, a five-notch strength meter with the word. Bottom: Home, step counter, Back, Next, Auto-play (7 s a step, loops; Next or Back stops it). State survives rotation.
- Strings: `res/values/feature_abpdemo.xml` and `res/values-pt-rPT/feature_abpdemo.xml`; no hardcoded text.
- Tests: `AbpDemoModelTest` (JVM: step order, strengths, ends, auto-play and loop, hand-press), `AbpDemoScreenTest` (Robolectric: every step renders with the banner, Next/Back, auto-play on the test clock, pt-PT, strings translated, no em-dashes).
- Gate `./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease`: green (16 abpdemo tests). `AbpDemoScreenshotTest` writes `app/build/screenshots/abpdemo-step{1,2,5,7}-tablet-{light,dark}.png` (1280x800 dp landscape); a dark-theme text-colour bug found that way is fixed.
- Environment note: Robolectric fetches its Android jar from repo1.maven.org (429 here); I pre-seeded `~/.m2` from repo.maven.apache.org. Not a repo change.
- After conductor's note: merged origin/dev (fa5dfc4, FeatureContext v2); removed the last hardcoded text (a row content description and an animation label) from `AbpDemoFeature.kt`, now `abpdemo_layer_cd` in both languages. Full gate green with qa's `FeatureStringsTest` and `LocalisationTest`. The screen uses only `context.onBack`, no Activity.
- Architect HOLD A-abp-1 and the medium finding: merged dev b535782 (FeatureFrame); removed my Home button and heading (content only). Layout adapts: two panes only on a landscape window at least 720dp wide and font scale up to 1.3; otherwise one scrolling column with stacked controls. Tests added for portrait phone and 200% font (everything reachable by scrolling). Gate green.
- Merged work/architect b2da9cd (FeatureContext v3, not yet on dev: drops out of my branch's diff once dev has it). Auto-play now calls `context.HoldIdleWhile(auto)`; test checks the hold is taken when auto-play starts and released when it stops. The host must pass `holdIdle` (app.android) for it to have an effect; the default is a no-op.
- Sources read 9 Nov 2026: https://riskmandate.ai (v1.40.3) and https://sharness.ai (v0.6.35).
- `enabled = true` is set in `AbpDemoFeature`. Reasoning: the screen works end to end under test; the conductor can revert if the gate or reviews say otherwise.

## What each line of wording rests on
- Model, "told": riskmandate.ai, "Told: A line in the prompt. The model keeps it because it chooses to." (9 Nov 2026).
- Hook, "checked": riskmandate.ai, "Checked: A hook. Deterministic, and on the agent's own machine, where the same account can switch it off. A setting." (9 Nov 2026).
- Proxy, "held": riskmandate.ai, "your gateway, proxy or IAM ... configured from the rules in the ABP" (9 Nov 2026).
- Harness: sharness.ai, "a policy the kernel enforces", "A tricked agent cannot widen it" (9 Nov 2026); the on-screen note says what runs today (early version, child process, Linux) and that the launcher for the agent itself is still the design.
- The rule shape ("Block") follows riskmandate.ai's example "Files labelled Confidential sent to any outside domain: Block".

## TODO for Dinis (not sourceable from riskmandate.ai or sharness.ai)
1. **Browser layer** (layer 1): neither site describes an ABP check in the web page. The wording and "guidance: the page can be bypassed" come from the conductor.b brief only.
2. **Device policy** (layer 6) and the word "floor": not on either site. Wording from the brief.
3. **The strength words** guidance, soft, firm, strong, floor: from the task brief. riskmandate.ai uses told, checked, held. Which does Dinis want on the stand?
4. **The scenario** (planted line, "agent map" going to an outside host) and the rule text "Agent map to an outside host: Block" are an illustration, modelled on the site's example; not a case from either site.
5. **Inline proxy on the tablet and the SHarness port to Android**: the brief calls these designs (on-device VPN, isolated processes). The screen therefore says nothing about Android; it only says what the sites say. If Dinis wants the Android design shown, it needs his sign-off and a clear "design, not built" label.
6. **pt-PT**: my translation; a Portuguese reader should check "anfitrião", "hook", "proxy em linha", "harness" and the strength words.
7. The banner says "DEMONSTRATION ... nothing is sent and nothing is at risk". That is true of this screen (it sends nothing, it has no network code), but it is my wording.

## Next
- Swap in design's `LayerRow`/`FeatureContent` once `work/design` is on `dev` (not there yet, so the row stays private).
- design.android's layer-row component (`app/.../ui/`) has not landed on this branch; the layer row here is private to the feature. When it lands, swap it in (small change in `LayerRow`).

## Blocked
Nothing.

## For other roles
- conductor: `Features.kt` already lists `AbpDemoFeature`; nothing to change.
- design: if the layer-row component lands, tell me its name and signature.
- qa: screenshots are not produced by this feature; if you want PNGs for the stand, ask and I will add a screenshot test under `features/abpdemo/`.

## For Dinis
See "TODO for Dinis" above. Nothing was published, sent or created.
