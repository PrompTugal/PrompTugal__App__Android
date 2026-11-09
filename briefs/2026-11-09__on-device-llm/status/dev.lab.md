# dev.lab status

> Second push: the Lab now reads eval's formats (scenario, graph, recording) from assets with a strict bounded parser, and the ABP checks follow eval's rule semantics. Replay only; live runs, loop mode and settings are next.

## Doing
- Fix-forwards from architect and security (A-lab-1, A-lab-3, R-lab-1, I.1 to I.3), done below. A-lab-2 waits on eval's golden file.

## Done
- b67a7c5 (on dev): first checks module, five scenarios, side-by-side screen on hand-written samples.
- This push:
  - `StrictJson.kt`: strict RFC 8259 parser, depth cap 12, size cap, no duplicate keys, no trailing commas; also the canonical form and the 16-hex scenario hash (a test checks it against a value computed by eval's Python).
  - `ToolProtocol.kt` (A-lab-1, R-lab-1): reads tool calls from model text (JSON objects and Gemma's own syntax), scanned text capped at 16,000 characters, at most 16 calls parsed and 64 brace positions tried, 4 calls per turn (later ones refused), 6 arguments, 2,000 characters per argument, tool names 64 characters of `[A-Za-z_]\w*`. Refused with a recorded reason: unknown tool, missing, extra or wrongly typed argument (string, integer, number, boolean; no nested values), over any cap. Valid arguments become flat strings.
  - `AbpChecks.kt`: a port of eval's `abp.py` (rules in order: `tool_allowlist`, `read_only`, `egress_recipients`, `secret_egress`; `block` and `hold` stop a call, `flag` marks the answer with `citations_required` and `state_consistency`). `Claims.kt` ports `claims.py`. `Oracle.kt` ports `oracle.py` and the outcome part of `abp.py`; it is separate from the checks on purpose.
  - `FakeTools.kt` (port of `fake_tools.py`), `PathRun.kt` (one path of a run, fed one model turn at a time: the live runs will only add the model call).
  - `LabFormats.kt` + `LabAssets.kt` (A-lab-3): read `abp-eval-scenario/1`, `abp-eval-graph/1` and `abp-lab-recording/1` from `assets/feature_lab/{scenarios,graph,recordings}`; a bad file is skipped and counted on screen.
  - `LabScreen.kt`, `LaneModel.kt`: design's lane layout (`StepRow`, 36sp lane headers, twin rows with "Not reached", a REPLAY strip on every lane, "Replay of a recorded run" and "Hand-written sample" chips, a computed one-sentence result). Scenario 5 shows the real network state.
  - Bundled assets are SAMPLES in eval's formats (`sample-*` ids, `source.kind: "sample"`, synthetic). They stand in until eval's vault files are copied in at build time; the real files go in the same folders and replace them. The planted secret is now `PLANTED-SECRET-7f3a-do-not-use` (I.3); `scripts/secret-scan.sh` is clean.
  - Tests: strict JSON, protocol caps, checks, oracle rescoring of every recording, the verdicts in every recording equal Kotlin's, path runs, the screen for all five scenarios, the asset loader, and `LabImportsTest` (security I.1: no java.net, files, intents, clipboard, reflection or processes; `android.net` only for the connectivity hint). Gate passed.

## Next
1. Live runs: `LabModelSource` over `FeatureContext.llm` (not on dev yet; `PathRun` is ready), interleaving the two paths on one loaded model, interactive mode (typed instruction), error states.
2. Loop mode (N, default 20, cap on N and time, battery guard), counters (`StatTile`), settings panel, export in `abp-lab-loop-export/1`.
3. Real recordings from eval's vault replacing the samples.

## Blocked
- A-lab-2 (golden vectors): eval has not published a golden file. I will run it as soon as it exists.
- Live runs: waiting for `LlmService` / `FeatureContext.llm` on dev.

## For other roles
- For eval (finding): `abp.py` `_domain` takes the text after the last "@" even for a URL, so `https://evil.test/x@example.test` passes as `example.test`. The Kotlin port reads a URL by its authority (host, without user info or port) and so blocks it. Please adopt the same rule so the golden vectors agree, and include this case. Also: please publish the golden file as one JSON with cases for `check_call` (scenario id, call, expected action and rule) and `check_final` (scenario id, fetched triplet ids, final text, expected flags); I will read whatever shape you choose. Rule ids are read from each scenario's policy, nothing is hard-coded.
- For eval: scenario titles and descriptions are English only; the Lab shows them as data. If the Lab should show pt-PT, add optional `title_pt` and `description_pt` fields (excluded from the hash).
- For architect: `PathRun` and `ToolProtocol` are the "propose, validate, decide" path for tools; live runs will use `ToolCallProposed` from `LlmService` when it exists by passing its text or arguments through `ToolProtocol.validate`.

## For Dinis
- Nothing yet. The Lab is still off the home screen (`enabled = false`) until it works on the tablet.
