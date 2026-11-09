# Workbench: an experiment control centre for the on-device model

From Dinis, 9 Nov 2026 about 10:00 UTC, after the first chat ran on the Idea Tab Plus (build 0.1.78, Gemma 4 E2B, about 5.5 tokens/s): "go to town ... lots and lots of experiments ... a chat workbench ... a control center ... experiment after experiment ... understand better how tool calling works ... a series of tests I could even run, almost like evals, but more on the functionality."

## What it is

A new feature, id `workbench`, title "Workbench", reached through Diagnostics > Preview like the other LLM screens (enabled = false until Dinis has used it on the tablet). Staff only, hidden in stand mode by the host as today. Everything runs on the device; nothing leaves it.

Three tabs:

### 1. Run (one experiment at a time)
- Inputs: model (installed ones), backend (GPU or CPU), system prompt (editable, with presets: none, RiskMandate base block, the chat presets), user prompt, temperature, top-k, top-p, seed, max output tokens, and an optional tool set (from the tool-test catalogue below).
- Live while it runs: the streamed answer (plain text), and a stats panel updating in place.
- Stats for every run, all from the runtime where it reports them, measured in Kotlin where it does not, each labelled which: model load time (cold or warm), input tokens, output tokens, time to first token, prefill tokens/s, decode tokens/s, total time, the stop reason (done, max tokens, stopped, error with its reason code), the tool calls proposed (name, raw arguments, checkToolCall verdict), the backend actually used (GPU or fell back to CPU), app memory before and after, battery % and thermal status before and after.
- Stop button. Re-run button (same settings). "Copy run" (plain text, no identifiers).

### 2. Experiments (many runs, compared)
- A run log kept in memory and in app-private storage (noBackupFilesDir/feature_workbench, excluded from backup; clear button), newest first: one row per run with the settings that differ and the key numbers.
- Sweep: pick one variable (temperature, top-p, max tokens, backend, system prompt on or off, model) and a list of values, plus N repeats; it runs the grid and shows a table: mean and spread of time to first token, decode tokens/s, output length, and pass rate when a tool test is attached.
- A/B: two settings side by side on the same prompt, run alternately N times.
- Same caps as the Lab's loop mode: at most 100 runs and 30 minutes per sweep, stop at 15% battery when not charging, stop on severe thermal status, stop after 3 failed runs in a row, keep the screen on and hold the stand idle reset while running.

### 3. Tool tests (functional evals of tool calling)
A bundled catalogue of small, deterministic cases (JSON in assets, format owned by dev.lab, fake tools only, nothing executes for real). Each case: the messages, the tools offered (2 to 5, flat arguments, as ToolSpec), and the expected outcome. Checks are code, not a model:
- calls the right tool (name)
- arguments parse (StrictJson) and pass checkToolCall (types, required, limits)
- argument values match the expected ones (exact, or a stated rule: case-insensitive, number within a range, one of a set)
- calls no tool when none fits (asks a question or answers in text)
- does not call a tool that is not offered (a made-up name)
- picks between two similar tools correctly
- two-step: uses the first tool's (fake) result in the second call
- refuses to put a value it was told not to share into an argument
- answers after a tool result in text, without calling again
Start with about 20 cases across these kinds, each with a one-line "what this tests". Run one case, a kind, or all, N times each; show pass rate per case and per kind, the failures with the raw output, and the timing. The same runner must work in a JVM unit test against FakeLlmService (scripted replies), so the catalogue's checks are tested in CI, and the results format is the same JSON as the Lab's export so eval can read tablet results later.

Two ways to ask for a tool call, both measured: the runtime's native tool calling (ToolCallProposed) and a text protocol (the model writes a JSON call in its answer; parsed with StrictJson). The workbench reports both, so we learn which works on this model.

## Who does what

| Role | Part |
| --- | --- |
| dev.llm | richer LlmStats from the runtime (input tokens, TTFT, prefill and decode tokens/s, load time cold or warm, backend actually used, stop reason), memory and thermal readings, a run API the Workbench calls; feature `workbench` tabs 1 and 2 |
| dev.lab | the tool-test catalogue format and the first ~20 cases, the code checks, the runner (shared with the Lab's PathRun and checkToolCall), tab 3, the JVM test over FakeLlmService |
| design | the control-centre layout (dense, tablet first, tabular numbers, a stats panel), within theme A |
| architect | the shape: feature id, where the runner and stats live, no new FeatureContext capability unless needed |
| qa | a test plan and a device section in the guide |
| security | reviews: storage, caps, fake tools only, plain text, no export path yet |
| editor | a What's new article once it runs on the tablet |

Same rules as always: own branch and paths, the gate, security PASS before merge, no claims for RiskMandate beyond riskmandate.ai, no em-dashes in prose.
