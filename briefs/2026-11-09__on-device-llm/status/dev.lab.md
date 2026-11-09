# dev.lab status

> ABP checks module and the side-by-side Lab UI on replayed samples are written; gate result below. Live runs, loop mode and settings are next.

## Doing
- First push: the ABP checks module, the five scenarios, a runner for both paths and the side-by-side screen, on replayed samples.

## Done
- `AbpChecks.kt`: tool allow-list, argument allow-list, mandate, read-only, egress destination, secret-in-outbound-argument (spacing and case tolerant, masked in the display), claim citations against fetched triplets, remote-claim check for scenario 5. Pure Kotlin with unit tests.
- `Scenario.kt`: scenarios 1 to 5 with a small fictional demo graph (ten triplets), the task, the document, the policy and the failure in words. All text synthetic; the planted instruction and the demo secret are fixed strings shown as text.
- `LabRunner.kt`: `FakeTools` (records only; graph tools read the demo graph and feed the citation check), `runRaw` / `runAbp` / `runBoth`, and the per-run flags the loop counters will use (raw failed, ABP caught, ABP escaped).
- `LabScreen.kt`: scenario picker, task and document shown as text, "Run both paths", steps revealed one by one (all at once with animations off), raw left and ABP right on a wide tablet (stacked otherwise), result chips as live regions, scenario 5 shows the real network state beside the answer. Content only, inside the host's FeatureFrame, built from `ui/` components.
- Strings in `feature_lab.xml` and pt-PT.
- The five presets are SAMPLES, written by hand to show the mechanism, and the banner says so. They are not recordings and must not be presented as model output.

## Next
1. Live runs: a `LabModelSource` over dev.llm's `LlmService` (a fake until it lands on `origin/work/dev.llm`; it has no LlmService yet), tool-call parsing and validation in Kotlin, interactive mode (typed instruction).
2. Loop mode: N runs (default 20), failure rate without ABP, catch rate with ABP, "1 in X", battery and time per run, temperature, top-p, seed and the preset button; JSON export in eval's format.
3. Replace samples with curated recordings from eval's vault once its format exists.

## Blocked
- No `LlmService` on `origin/work/dev.llm` and no scenario files or recording format on `origin/work/eval` yet. Built against local stand-ins.

## For other roles
- For eval: my `Scenario` fields (id, task, document, tools offered with kind, mandate, read-only flag, egress allow-list, secrets, failure in words) and `Recording` (scenario id, model, backend, raw turns, ABP turns; a turn is what was said, the proposed calls and the claims) are my guess at what the Lab needs. Tell me the real format and I will read it.
- For app: `LabFeature.enabled` stays false until it works on the tablet; the conductor turns it on after a device run.

## For Dinis
- Nothing yet. The screen is not on the home screen (feature still disabled).
