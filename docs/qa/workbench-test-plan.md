# Test plan: the Workbench (experiment control centre and tool-calling tests)

Owner: qa.android@promptugal.ai. Written 9 Nov 2026 from `briefs/2026-11-09__on-device-llm/workbench.md` and the code on `dev` 850a2dc (`LlmStats`, `LlmEvent`, `FakeLlmService`, `checkToolCall`, `StrictJson`, the Lab's `LoopRunner` caps). **The Workbench is not on any branch yet**: this plan says what each review will demand and what QA writes the day the code lands. Names in `code font` are the brief's or existing code; I adjust to what dev.llm and dev.lab build. The device section for Dinis is `docs/qa/device-run-workbench.md` (to be merged into the guide when the feature is in a build).

The feature: id `workbench`, title "Workbench", reached through Diagnostics > Preview, `enabled = false` until Dinis has used it, staff only, hidden in stand mode, three tabs (Run, Experiments, Tool tests), everything on the device, no export path.

## 0. What the test double must offer (ask dev.llm now, or the stats cannot be tested)
Today `FakeLlmService(initial, tokenDelayMs, reply)` scripts a token delay and a reply, but **not** the numbers the Workbench shows. The plan needs, in `llm/FakeLlmService` (main code, so other tests use it):
- a scripted `LlmStats` per reply (prompt tokens, output tokens, prefill and decode tokens/s, time to first token, load time, `loadedModel`, backend) **including every field being `null`** (the runtime not reporting it);
- a scripted stop reason (done, max tokens, error with its reason code) and cancellation;
- a **virtual clock** the Workbench takes as `now: () -> Long` (the Lab's `LoopRunner` already does), so measured figures (time to first token, total, decode speed from timings) are exact and no test depends on real time;
- a per-request reply function (it has one: `reply: (LlmRequest) -> FakeReply`), so a test can script "good answer, then a wrong tool, then an error".
The Workbench's own readers (memory, battery, thermal, charging) must be injected as interfaces (like the Lab's `DeviceGuard`), so tests feed `null`, low battery, severe thermal.

## Tests QA writes when the code lands (all JVM, no model unless noted)
| File (`qa/` unless the feature's package is needed) | What it proves |
|---|---|
| `WorkbenchStatsTest` | every stat, with its source label, against scripted stats (WB-S) |
| `WorkbenchSweepCapsTest` | grid size, caps and stop reasons with a virtual clock (WB-C) |
| `WorkbenchCapsMatchLabTest` | the Workbench's caps are the Lab loop's constants (one source of truth) |
| `WorkbenchLogTest` | run-log persistence, atomic write, limits, clear, backup exclusion (WB-L) |
| `ToolTestCatalogueTest` | the catalogue parses; ids unique; 2 to 5 flat tools per case; all nine kinds present; each case has its "what this tests" line, in en and pt-PT |
| `ToolTestChecksTest` | each check, with a perfect reply and a failing reply (WB-T) |
| `ToolTestMutationTest` | a generated family of wrong replies for **every** case, each must flip the verdict (WB-T, below) |
| `WorkbenchRotationProbeTest` | `StateRestorationTester` probes on each tab (WB-R) |
| `LlmSandboxTest` (exists) | extended to `features/workbench/`: no network, files outside the log folder, intents, processes, WebView or JS bridge |

## WB-S. Stats correctness
Every stat is shown with its source: **runtime** (reported by the engine) or **measured** (by Kotlin) or **n/a**; never a guess presented as a measurement.
| # | Criterion | How |
|---|---|---|
| S-1 | Runtime-reported prompt tokens, output tokens, prefill and decode tokens/s, time to first token, load time are shown exactly as reported and labelled "runtime" | scripted stats, all present |
| S-2 | With the runtime reporting nothing (all `null`), time to first token, total time and decode tokens/s are **measured** from the virtual clock: first token time minus start; end minus start; (output pieces minus 1) per second between the first and last piece (the chat's formula); each labelled "measured"; prefill speed and token counts show "n/a", not 0 | scripted nulls, virtual clock |
| S-3 | Partial reports: the reported fields are "runtime", the missing ones "measured" or "n/a" | scripted mix |
| S-4 | Division by zero never happens: 0 output tokens, 1 output token, a first piece and last piece at the same instant, 0 ms total: the stat shows "n/a", never `Infinity`, `NaN` or a negative | scripted edge cases |
| S-5 | Cold or warm load: `loadedModel = true` shows "cold" with the load time and it is **not** added into total time (the contract says so); warm shows "warm" and no load time | scripted |
| S-6 | Stop reason: done, max tokens (output tokens reached the limit set), stopped (the Stop button), error with its reason code (`NO_MODEL`, `LOAD_FAILED`, `TOO_LONG`, `INFERENCE_FAILED`) shown as the reason name, never as free model text | scripted each |
| S-7 | Backend actually used: requested GPU, runtime says CPU: the screen says it fell back; requested CPU says CPU | scripted |
| S-8 | Memory, battery and thermal before and after: values from the injected readers; a reader that returns `null` shows "n/a"; battery "used" never negative when charging (the Lab's rule) | injected readers |
| S-9 | Tool calls proposed: name, raw arguments (shown as text, cut at a stated length), the `checkToolCall` verdict; an unknown tool name shows "not an offered tool" | scripted proposals |
| S-10 | Numbers use tabular figures, a stated number of decimals, and the device locale's separator; the same run copied as text uses a fixed format ("Copy run": plain text, no identifiers) | Compose test + string check |
| S-11 | A run that was stopped or errored never enters a sweep's mean | with WB-C |

## WB-C. Sweeps, A/B and caps
| # | Criterion | How |
|---|---|---|
| C-1 | A sweep over one variable with V values and N repeats runs exactly V x N runs, in a stated order (grouped by value, repeats inside), and the table has V rows | scripted fake |
| C-2 | A/B alternates A, B, A, B ... N times each on the same prompt (not all A then all B) | scripted fake, request order |
| C-3 | Caps (same as the Lab loop): at most **100 runs** and **30 minutes** per sweep (virtual clock), stop at **15% battery when not charging** (not when charging), stop on **severe thermal**, stop after **3 failed runs in a row**, a stop request ends after the current run; each stop shows its reason in words | scripted guard and clock; `WorkbenchCapsMatchLabTest` ties the constants to `LoopRunner` |
| C-4 | A request for more than 100 runs is clamped to 100 and says so | direct |
| C-5 | Seeds step per run (base + 1000 x i, the Lab's convention), so repeats differ and a rerun repeats; a fixed seed in the settings is honoured for run 0 | scripted, request seeds |
| C-6 | Table maths: mean and spread of time to first token, decode tokens/s and output length over **finished** runs only; spread is the sample standard deviation (state it); with 1 finished run the spread shows "n/a"; pass rate (when a tool test is attached) is passes over finished runs; "Small sample" under 10 finished runs | known inputs, e.g. [1,2,3,4] gives mean 2.5, sample SD 1.29 |
| C-7 | Failed runs are counted apart ("did not finish") and never in a mean or rate | scripted failures |
| C-8 | While a sweep runs the screen stays on and stand mode's idle reset is held; both are released when it ends, is stopped, errors or the screen is left (also after a rotation) | busy callback and `HoldIdleWhile` test |
| C-9 | System prompt on/off and model/backend as the swept variable change exactly that one setting between rows (the request carries it) | scripted, request fields |

## WB-L. The run log
| # | Criterion | How |
|---|---|---|
| L-1 | The log lives in `noBackupFilesDir/feature_workbench`; nothing else is written anywhere (static scan plus a directory listing after a run) | `WorkbenchLogTest`, `LlmSandboxTest` |
| L-2 | `backup_rules.xml` and `data_extraction_rules.xml` exclude it (parsed, like the chat's history) | parsed XML |
| L-3 | Atomic write (temp file then rename); a damaged or old-version file reads as an empty log, never a crash | corrupt-file tests |
| L-4 | Limits: a stated maximum number of runs kept and characters per answer; the oldest go first; newest on top | direct |
| L-5 | Survives the app being killed and reopened (device: step in the device section) and a rotation | store test, probe |
| L-6 | **Clear** deletes the file and empties the screen; confirmed with a dialog; a second tap does nothing harmful | direct |
| L-7 | The log holds what was typed and answered, so it is private: no device serial, account or label in any record; "Copy run" carries none either; **no export or share path** exists (security) | field list test, static scan for `Intent.ACTION_SEND` and `FileProvider` |

## WB-T. Tool tests (functional evals)
Checks are code, never a model. The catalogue is bundled JSON (format owned by dev.lab), fake tools only.
| # | Criterion | How |
|---|---|---|
| T-1 | The catalogue: about 20 cases, ids unique, each with 2 to 5 tools with flat arguments, a one-line "what this tests" in en and pt-PT, and every one of the nine kinds present (right tool; arguments valid; argument values; no tool when none fits; not an offered tool; picks between two similar tools; two-step; will not share a forbidden value; answers in text after a tool result) | `ToolTestCatalogueTest` |
| T-2 | A broken case file is skipped with a message, never a crash; one bad case does not stop the others | corrupt catalogue |
| T-3 | Each check passes a perfect scripted reply and fails a wrong one (name; arguments; value rule exact, case-insensitive, number in range, one of a set; no call when none fits; made-up name; the wrong one of two similar tools; second call ignoring the first result; forbidden value in an argument; a second call after the tool result) | `ToolTestChecksTest` |
| T-4 | **Mutation tests**, see below | `ToolTestMutationTest` |
| T-5 | Native and text protocols are scored by the same checks and reported separately; the same intent expressed either way gives the same verdict; the text parser is `StrictJson`: duplicate keys, trailing text, fenced code blocks, several calls, a call inside prose, partial JSON, oversized arguments (limit stated), deeply nested JSON and non-flat arguments are each handled as specified, never a crash | hostile-input table |
| T-6 | The forbidden-value check has the Lab's limit (exact text) and says so on screen; encoded, re-cased or split values pass, and the screen must not present that pass rate as robustness | probe table as in the Lab review |
| T-7 | Results use the Lab's export JSON shape (`abp-eval-run/1` records) so eval can read them later; written nowhere in this build (no export) | schema test on the in-memory record |
| T-8 | Run one case, one kind or all, N times: pass rate per case and per kind, the failures with the raw output (as text), timing; a run that errored is not a failure of the case | scripted |
| T-9 | The same runner works in a JVM test against `FakeLlmService` (the brief's requirement) | the tests above |

### Mutation tests (T-4): how QA makes sure the checks can fail
A pass rate means nothing if a check cannot fail. For **every** case QA generates, from the case's own perfect reply:
1. **Reply mutants** (each must flip the verdict to fail): wrong tool name; made-up tool name; right tool, one argument value changed (off by one, wrong case when the rule is exact, outside the range, not in the set); a required argument dropped; an extra unknown argument; an argument of the wrong type; arguments that are not JSON; two calls where one is expected; no call where one is expected; a call where none is expected; the second step ignoring the first result; the forbidden value placed in an argument; a further call after the tool result.
2. **Non-mutants** (each must still pass): harmless changes the rule allows (extra whitespace, key order, upper-case where the rule is case-insensitive, a number inside the range, a different member of the allowed set, polite text around the call in the text protocol).
3. **Checker mutants** (by hand, recorded in the review, as I did for the loop counters): flip a comparison, widen a range, drop the "required" check, treat a made-up name as offered, ignore case where the rule says exact. Each must make at least one test fail; any survivor means a missing test.
The test fails if a reply mutant survives (scores pass) or a non-mutant dies.

## WB-R. Rotation, Back and leaving
The Chat and the Lab each lost state on rotation (Q-chat-1, Q-loop-1). The Workbench is checked for it from the start.
| # | Criterion | How |
|---|---|---|
| R-1 | Run tab: the typed system and user prompts, the settings and the selected tool set survive a rotation (`rememberSaveable`) | `StateRestorationTester` |
| R-2 | A run in progress when the tablet is turned **stops with a line saying so** ("the screen was turned") and keeps what was received and its stats; nothing is lost silently; the Stop state and the busy hold are released | probe, like the Lab's |
| R-3 | A sweep in progress: it stops with its finished-run results kept and a stop reason; the table still shows what was measured; the screen-on and idle hold are released | probe |
| R-4 | A finished sweep and a finished tool-test batch keep their tables across a rotation | probe |
| R-5 | The selected tab survives a rotation; the log is re-read from disk | probe |
| R-6 | Back during a run cancels it (the engine is told to stop); leaving the screen cancels a sweep; the model is not left loaded by a sweep that was cancelled mid-load | fake service records the cancel |
| R-7 | One header, no Back button of its own, a "Preview" label (host frame) | `qa/FeatureNavigationTest` pattern |

## WB-X. Safety, sandbox, labelling
| # | Criterion | How |
|---|---|---|
| X-1 | Fake tools only: nothing a model proposes reaches a network, file, intent or account; model output is shown as text, never HTML, links or code; a URL in output is not opened (the Chat's `ChatLinks` rule applies if links are shown) | `LlmSandboxTest`, `ChatOutputIsTextTest` pattern |
| X-2 | Staff only: the Workbench is in Diagnostics > Preview, hidden in stand mode, `enabled = false` (no card on Home) | `HostPreviewTest` pattern |
| X-3 | No new permission, exported component, foreground service or network destination in the merged release manifest | `aapt2 dump` on the APK |
| X-4 | Claims: nothing about RiskMandate or ABP beyond riskmandate.ai and sharness.ai; the "RiskMandate base block" system-prompt preset is the same text as the chat's grounding (compare with the live page again) | manual |
| X-5 | pt-PT for every string; no hardcoded text; no em-dashes in strings | `FeatureStringsTest` |
| X-6 | Honest labels: sources of every stat (S), native versus text protocol shown separately (T-5), "Small sample" under 10 runs, "by construction" where a check mirrors its oracle | manual + Compose |
| X-7 | Idle and thermal: a long sweep on the tablet does not heat it past the stop; the stop line appears | device section |

## Gate and review
Each branch: `testDebugUnitTest lintDebug assembleRelease bundleRelease assembleDebugAndroidTest` merged with `dev` plus my tests, `--continue` if a known flake stops the run, the release manifest read with `aapt2`, then probes and mutation checks as above. HOLD for any failed row above; enabling is held separately from merging (the feature stays `enabled = false` until Dinis has used it on the tablet).
