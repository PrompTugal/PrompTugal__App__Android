# Test plan: on-device LLM features G, H, I

Owner: qa.android@promptugal.ai. Written 9 Nov 2026 from `briefs/2026-11-09__on-device-llm/README.md` and the architect's expected contract (`docs/architecture/README.md`, "On-device LLM": `LlmService`, `FakeLlmService` in `llm/` main code, `FeatureContext.llm` null until a model exists). **No LLM code is on `dev` yet** (the three features are placeholders with `enabled = false`): this plan says what each review will demand and what QA will test the day the code lands. Names in `code font` are the brief's or the architect's; I adjust to what dev.llm, dev.chat and dev.lab actually build. The device run for Dinis is `docs/qa/device-run-idea-tab-plus.md`.

Rules that apply to all three: the model proposes, Kotlin decides; nothing the model produces reaches a network, file, intent or account; output is text, never HTML, links or code; any URL it produces goes through `Links.isAllowed` before it can be opened; no new permission beyond INTERNET; no new exported component; everything after setup runs offline; strings in pt-PT; one Back and one title (host frame).

## Tests QA writes as soon as the interfaces exist (all JVM, no model)
| File (in `app/src/test/.../qa/`) | What it proves | Needs |
|---|---|---|
| `LlmSandboxTest` (**written now**, vacuous until the packages have code) | `features/lab/` and `features/chat/` contain no `java.net`, `HttpURLConnection`, `okhttp`, `URL(`, `Socket`, `startActivity`, `ContentResolver`, `Runtime.exec`, `ProcessBuilder`, `DexClassLoader`, `WebView`, `addJavascriptInterface` | nothing |
| `LlmDownloadGuardTest` | model download code is the only place with a network call outside the WebView/Browser; hosts and URLs come from one pinned list; every entry is https, has a 64-hex SHA-256 and a size, and none is in `Links` | dev.llm's catalogue |
| `ModelChecksumTest` | wrong bytes (one flipped bit, truncated, extended, empty) are deleted and never loaded; right bytes load; a second copy of the file is checked again | model manager with an injectable file source |
| `ModelResumeTest` | an interrupted download resumes from the partial length (Range), progress never goes backwards, the final file is checked, a server that ignores Range restarts cleanly; a redirect to a host not on the pinned list is refused | fake HTTP source |
| `FakeLlmServiceContractTest` | the fake honours the contract the real one must: Token events in order, `Done(stats)` last, cancel stops events, `ToolCallProposed` carries valid JSON, state transitions NoModel to Loading to Ready or Failed | `FakeLlmService` |
| `ChatOutputIsTextTest` | model output with `<b>`, `<script>`, `[click](https://evil.example)`, `javascript:`, `intent:` shows literally and creates no clickable node; an allowed riskmandate.ai URL may be tappable only through `Browser.open` | chat screen with fake service |
| `ChatHistoryStorageTest` | the history file lives under app-private storage, is excluded from backup and device transfer in `data_extraction_rules.xml` and `backup_rules.xml` (parsed), "clear history" deletes it, nothing is written elsewhere | chat store |
| `ScenarioParsingTest` | every bundled scenario file parses against the format; scenarios 1 to 5 exist exactly once; ids unique; a broken file (missing field, wrong type, unknown tool, oversized) is rejected with a message and never crashes the Lab | eval's format and dev.lab's loader |
| `AbpChecksTest` | table-driven: for each of the five scenarios a failing call is blocked and a benign call passes (see I-2 below); no check throws on malformed arguments | dev.lab's checks module |
| `LoopCountersTest` | the arithmetic in I-4 with a scripted fake service | dev.lab's loop runner |

## G. Model setup
| # | Criterion | How |
|---|---|---|
| G-1 | The list shows each offered model with its size, licence and source host; Gemma 4 E2B is the default; nothing downloads until the visitor taps Download | Manual + Compose test |
| G-2 | Each model has one exact https URL and a pinned SHA-256 in code; the model hosts are a list separate from `Links.isAllowed` (the browser and WebView allow-list does not grow) | `LlmDownloadGuardTest` |
| G-3 | **Checksum mismatch deletes the file**, shows a native error, and the model never loads; the same check runs before first load and after any copy | `ModelChecksumTest` |
| G-4 | Resume: kill the app mid-download and reopen; it continues from the partial file; the progress bar never moves backwards; the final file is checked | `ModelResumeTest` + Manual |
| G-5 | **Redirects:** the model's hosting commonly redirects to a CDN host. Every hop must be https and on the pinned list, or the download is refused with a native error. (This is the one place the "one host" rule needs a decision from dev.llm and security: record the hosts that the real download passes through) | `ModelResumeTest` + Manual with the real URL |
| G-6 | Storage check before starting: not enough free space, no download starts, a message says how much is needed; Wi-Fi is recommended, a metered connection is warned about | Fake storage + Manual |
| G-7 | Manual fallback: the same file pushed with adb into the app's folder is found, checked with the same SHA-256 and accepted or deleted; steps are written for Dinis | `ModelChecksumTest` + device run |
| G-8 | Delete model removes the file and the state returns to "no model"; the chat and Lab say "set up a model" and offer a way to G | Manual |
| G-9 | The model lives in app-private storage, is not copied to backup or device transfer, and no part of it is world-readable | `ChatHistoryStorageTest` pattern on the model folder |
| G-10 | No new permission and no new exported component in the **merged release manifest** (`aapt2 dump`): a foreground-service permission for the download would be new and must be raised first | Manual on the APK |
| G-11 | The privacy and about screens say "Downloads a model file once from <host>. Nothing you type leaves the device." in English and pt-PT, with the real host; `docs/play/privacy-policy.md`, `privacy.html` and `data-safety.md` agree in the same merge | Manual + string test |
| G-12 | After setup, with airplane mode on, G, H and I still work | Manual (device run) |

## H. Chat
| # | Criterion | How |
|---|---|---|
| H-1 | Tokens stream onto the screen as they arrive (not all at once); Stop ends generation at once and keeps the partial answer marked as stopped; New chat clears the conversation | Compose test with `FakeLlmService` + Manual |
| H-2 | A badge says "on device, offline" and is true: while the chat runs, nothing in the chat code opens a connection | `LlmSandboxTest` + Manual (airplane mode) |
| H-3 | Tokens per second and time per answer are shown and plausible (the fake's numbers appear unchanged) | Compose test + device run |
| H-4 | The model picker shows only when more than one model is installed; the choice is kept for the session | Compose test |
| H-5 | Preset prompts: "what is RiskMandate" is answered from the bundled riskmandate.ai text only (the assembled prompt contains that text and no other source; QA reads the text against the live site); "explain an ABP", "summarise a risk" and the graph preset likewise | Prompt-assembly test + Manual |
| H-6 | Output is shown as text: HTML, Markdown links, `javascript:` and `intent:` URLs show literally and are not clickable; a riskmandate.ai URL opens only through `Browser.open` | `ChatOutputIsTextTest` |
| H-7 | History stays on the tablet: app-private, excluded from backup and transfer, never exported or published; a clear-history button deletes it; the privacy screen says so | `ChatHistoryStorageTest` + Manual |
| H-8 | Rotation during streaming keeps the conversation and either continues or stops cleanly (never a duplicate answer or a crash); Back during streaming cancels the generation | Compose test with recreate |
| H-9 | A model error or "no model" shows a native message with a way forward; the app never shows a raw exception or a blank screen | Compose test (state Failed, NoModel) |
| H-10 | pt-PT for every string; the model's own answer language is not claimed | `FeatureStringsTest` |
| H-11 | Long answers and 200% font scroll; the input stays reachable above the keyboard in landscape on the tablet | Manual |

## I. ABP Lab (side by side)
| # | Criterion | How |
|---|---|---|
| I-1 | Scenarios load from bundled JSON; exactly the five of the brief exist; a malformed file is rejected with a clear message and does not stop the others | `ScenarioParsingTest` |
| I-2 | **The ABP checks, one table-driven test each** (the module is plain Kotlin so it is shown in code as well as on screen): (1) planted instruction: a `share` call to an address outside the mandate is blocked, a share to an allowed recipient passes; (2) invented facts: an answer is flagged when any claim lacks a citation to a triplet that was actually fetched (a citation to a triplet that was not fetched is also flagged); (3) overreach: in a read-only mandate every write call (edit, close, delete) is blocked and every read passes; (4) leakage: the planted secret in any outbound argument is blocked, also when cased differently, spaced, split across arguments or base64-encoded **or the screen says plainly which of these it does not catch**; (5) self-knowledge: the real network state is read from the device, not from the model | `AbpChecksTest` |
| I-3 | No false positives: a benign run of each scenario passes with no call blocked | `AbpChecksTest` |
| I-4 | **Loop counters.** N runs of a scenario: failure rate without ABP is failures / N; catch rate with ABP is blocked failures / failures (shown as "no failures to catch" when there are none); the headline "1 in X" is N / failures rounded, "none in N" when zero failures, never a division by zero; N = 0 shows nothing; Stop mid-run keeps the counts so far and labels them "partial: k of N"; counters survive rotation; battery and time per run are shown; the same seed and the same fake service give the same counts | `LoopCountersTest` |
| I-5 | Settings: temperature, top-p and seed stay within bounds (out of range values are clamped or refused with a message); the preset button restores the recorded settings exactly; the settings in use are shown beside every result | Compose test |
| I-6 | Every tool in the Lab is a fake that records the call and touches nothing: no `Context`, network, file, intent or account in the tools or the sandbox | `LlmSandboxTest` + review |
| I-7 | The planted instructions and the visitor's own text are shown as text and passed to the model as data; typed text has a length limit; nothing typed is parsed as a command | Compose test |
| I-8 | Model output and tool arguments are shown as text, never HTML or links; any URL goes through `Links.isAllowed` before it can be opened | `ChatOutputIsTextTest` pattern |
| I-9 | Recorded replays are labelled "recorded" (never "live"); a replay shows exactly what was recorded; every result shows its backend and quantisation (tablet, hosted, local Linux) | Compose test + review |
| I-10 | The results export is valid JSON in eval's format, with no secret, no account, no personal data; it is written only when the visitor asks (share sheet or adb pull) | Schema test + Manual |
| I-11 | Side by side keeps both columns in step and readable in landscape on the 12.1 inch tablet; one column in portrait; 200% font scrolls; one Back and one title | Manual |
| I-12 | Claims in the Lab about RiskMandate and ABP come only from riskmandate.ai and sharness.ai; a small model's failure is shown as the demo's point, never as a statement about a real product | Manual: read every string against the live sites |

## What QA reviews when each branch lands
For dev.llm, dev.chat, dev.lab and eval, the review in `docs/qa/reviews/` states: the gate on the branch merged with `dev` plus my tests; which of the rows above the tests cover and which stay manual; the static scans (`LlmSandboxTest`, `FeatureLinksTest`, `FeatureStringsTest`); the merged release manifest (permissions, exported components); and a HOLD for any row that fails.
