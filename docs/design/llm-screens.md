# On-device LLM screens: design spec (G, H, I)

Owner: design.android@promptugal.ai. Written 9 Nov 2026 from `briefs/2026-11-09__on-device-llm/README.md`. Reader: dev.llm (G), dev.chat (H), dev.lab (I), QA, security. Same ground rules as `summit.md` (tablet landscape first, phone second, light and dark, 48 dp touch targets, status never colour alone, English and pt-PT, no em-dashes, every screen scrolls so 200% font scale works, `FeatureContent` inside the host `FeatureFrame`, no second title bar). No brand change.

**Honest labelling is a design rule here.** Every number, answer and verdict on these screens says what it is: which model, which backend (on this device, hosted, or recorded replay), which quantisation where known. A recorded replay is never shown as a live run: a `StatusChip("Replay of a recorded run", Held)` sits on every lane that is a replay, and `StatusChip("Live on this device", Allowed)` on every live one. Model output is shown as plain, selectable text: never HTML, never a link, never code that runs. The planted instructions and the Lab's tools are fixed, fictional and fake, and the screen says so.

## Shared pieces (all in `ai.riskmandate.app.ui`)

| Need | Use | Screens |
|---|---|---|
| Content area in the host frame | `FeatureContent` | G, H, I |
| Titled blocks, lane headers (`large = true`, 36sp) | `SectionCard` | G, H (settings), I |
| Status words (Ready, Downloading, Blocked, Live, Replay, On device) | `StatusChip`, `ChipKind` | G, H, I |
| One step of a run, the same row in both Lab lanes | `StepRow` | I |
| Big counters ("1 in 3", "20 of 20", tokens per second) | `StatTile` (`large` for the Lab) | H (small), I (large) |
| Always-visible note (privacy line, Wi-Fi, the demo label) | `NoticeBanner` | G, I |
| Two columns on a wide tablet | `LocalWideLayout` | G, H, I |
| Animation off | `rememberReduceMotion` | I |
| Sizes, tags | `UiTokens`, `UiTags` | all |

Not in `ui/` (plain Material 3 inside the feature): `LinearProgressIndicator`, `Slider`, `OutlinedTextField`, `Switch`, `AlertDialog`, the chat bubble. Style them from `MaterialTheme`; do not add colours.

## G. Model setup (`features/models/`, dev.llm)

Purpose: choose a model, download it once, verify it, finish. One screen, no wizard.

- **Layout.** A short list of `SectionCard`s, one per model (two or three), single column up to 720 dp, two columns when `LocalWideLayout`. Order: default first (Gemma 4 E2B), then E4B, then the small fallback. A `NoticeBanner` at the top, verbatim from the brief: "Downloads a model file once from <host>. Nothing you type leaves the device." (host filled in from the pinned list; pt-PT in the string file). Second, shorter banner when not on Wi-Fi: "Wi-Fi recommended. This file is <size>."
- **Card content.** Model name (titleLarge), one line what it is for ("Default. Fast enough to chat." text by dev.llm), then three facts as `label: value` rows: Size (GB, one decimal), Licence (as recorded), Runs on (GPU or CPU, measured on the tablet, never promised). Status `StatusChip` top right: Not downloaded (Neutral), Downloading (Held), Verifying (Held), Ready (Allowed), Failed (Blocked). Primary action under the facts, 56 dp, full width on a phone: **Download** / **Pause** and **Cancel** / **Verify again** / **Delete model** (text button, confirm dialog: "Delete <model>? You can download it again.").
- **Progress.** Determinate `LinearProgressIndicator` (8 dp high), under it "1.2 of 3.1 GB, 38%" and, when known, "about 4 minutes left". TalkBack is told at 25%, 50%, 75% and on finish (polite live region), never every percent. While verifying: indeterminate bar and "Checking the file".
- **Storage check before starting.** "Needs 3.1 GB, 48 GB free" under Download; if short, Download is disabled with "Not enough space: free 1.2 GB first" (Blocked chip), never a silent failure.
- **Checksum mismatch.** The file is already deleted; the card shows a native error block (`StatusChip("Checksum did not match, file deleted", Blocked)` and one sentence of what to do: Download again, or load the file from adb). Never offers to keep the file or to skip the check.
- **Manual fallback (bad venue Wi-Fi).** A collapsed `SectionCard` "Load from adb" at the bottom (staff, not visitors): the exact folder and the command to push the file (text from dev.llm, monospace and selectable), a **Check file** button that runs the same checksum, and the same result states as a download.
- **Offline.** If there is no network, Download shows "No network. Connect to Wi-Fi, or load the file with adb." and the adb card opens itself.
- **When done.** Ready cards stay; the screen's back goes to home; H and I read the same list of installed models.
- **Accessibility.** Each card is a heading plus rows; chips read the word only (glyph hidden); buttons carry the model name in their label ("Download Gemma 4 E2B") so TalkBack lists are unambiguous; 200% font scale scrolls.

## H. Chat (`features/chat/`, dev.chat)

Purpose: prove that it is a real LLM, on the device.

- **Top row** (not scrolling): model picker (an outlined dropdown, 56 dp, shown only when two or more models are installed, otherwise the model name as text), then `StatusChip("On device, offline", Allowed)` and, beside it, the real network state as `StatusChip("Network: off")` Neutral or `("Network: on")` Held. The "on device" chip is shown while the model runs locally; it is not claimed offline when the device has a connection: the network chip tells the truth. Right end: **New chat** (text button, 56 dp) and a menu with **Clear history** (confirm dialog: "Delete every chat on this tablet?").
- **Conversation.** One column centred, max 840 dp on a tablet, bubbles at most 80% of that. User bubble right in `primaryContainer`; model bubble left in `surfaceVariant`; 18sp text, 1.5 line height, selectable. Model text appends as it streams; the list auto-scrolls to the end unless the user has scrolled up (then a "Jump to latest" chip appears). A blinking cursor is not used (no flashing): a thin static bar at the end of the text while streaming.
- **Under each model answer.** `18.4 tokens/s · 6.2 s` (bodySmall, 14sp minimum, `onSurfaceVariant`) and, if stopped, "Stopped". Small `StatTile`s are for a dashboard, not every bubble: use plain text here; `StatTile` appears only in an optional "Last answer" strip if dev.chat wants one.
- **Composer.** Bottom, `OutlinedTextField` min 56 dp, max 4 lines, 2,000 characters, with **Send** to its right (56 dp). While generating, Send becomes **Stop** (same place, same size, Blocked-style outline), never both. Keyboard: `imePadding`, the conversation scrolls above it; on a landscape tablet the keyboard leaves at least the last bubble visible.
- **Empty state.** The four preset prompts as large cards (two by two on a tablet, one column on a phone): "What is RiskMandate?", "Explain an ABP", "Summarise a risk", "Read a few triplets and explain them". Each is a button with the full prompt visible; touching it fills and sends. Answers from bundled riskmandate.ai text only; the screen does not paraphrase product claims itself.
- **States.** No model installed: a `SectionCard` "No model yet" with a button to G. Model loading: "Loading <model>" with an indeterminate bar, composer disabled. Out of memory or error: a plain error `SectionCard` with Retry; never a stack trace.
- **Privacy line.** Under the composer, bodySmall: "Chats stay on this tablet." and, in the menu, the same as an About entry. Clear history empties storage as well as the screen.
- **Accessibility.** The streaming text is not a live region (it would read every token); on finish a polite announcement "Answer ready, 18 tokens per second". Stop is reachable by the first Tab from the composer. Reading order: top row, conversation, composer.

## I. ABP Lab: side by side (`features/lab/`, dev.lab)

Purpose: the main demo. One model, one input, two paths, run at once: left **without ABP** (the tool calls run in a sandbox of fakes), right **through the ABP** (each call checked before it runs). **Must read from 2 m at the stand.** Everything above that size also reads fine near the tablet.

### Reading distance targets (10" tablet, 1280 x 800 dp, computed from dp, to be verified once at 2 m)

| Element | Size | Why |
|---|---|---|
| Headline counter ("1 in 3") | 72sp bold (about 12 mm cap) | the one number a passer-by takes away |
| Lane headers ("Without ABP", "Through the ABP") | 36sp bold | which side is which, from the aisle |
| Step title | 28sp bold | what the model tried |
| Verdict chip | 22sp semibold, glyph + word | Failure or Blocked is read before the step is |
| Counter labels, scenario title | 24sp | |
| Call arguments, model text, settings | 18sp, 16sp minimum | for people at the tablet |

Only 5 to 6 steps fit per lane at these sizes: a run longer than that scrolls inside its lane, newest step in view, and the verdict summary stays pinned above the counters.

### Layout (wide tablet, landscape)

Top to bottom, no horizontal scroll:
1. **Scenario bar** (one row, 64 dp): the scenario name (24sp) with prev/next buttons (56 dp) or a picker listing the five scenarios; a mode toggle **Interactive / Loop** (segmented, 56 dp); **Run** (primary, 56 dp), **Stop**; a **Settings** button. At 840 dp or less the picker and the mode toggle wrap to a second row.
2. **Provenance row**: model name and backend as one `StatusChip` per lane ("Live on this device" Allowed, or "Replay of a recorded run" Held) and, if the run is hosted, "Hosted: <model>" Neutral. Never omitted.
3. **Two lanes**, equal width, a 16 dp gutter and a 2 dp vertical divider (so the eye separates them): each a `SectionCard(large = true)` titled **Without ABP** / **Through the ABP**, containing `StepRow`s with the same index on the same row of both lanes (a step the ABP never reaches because it stopped an earlier call shows an empty, dimmed twin: "Not reached"). Left chips: Ran (Neutral) or "Failure: <what>" (Blocked). Right chips: Allowed, Blocked, Held, Redacted, or "Uncited claim flagged" (Redacted). A stopped step on the right also shows one line, in words, why ("Out of mandate: sharing", from the policy rule), 18sp.
4. **Counters strip** (Loop mode, and a single run's result in Interactive): `StatTile(large = true)` x3: **Failed without ABP** ("7 of 20"), **Caught with ABP** ("20 of 20"), headline **"1 in 3"** (spanning double width, 72sp), and, smaller and below, battery used and time per run (`StatTile`, not large). A run in progress shows "Run 14 of 20" under the Run button, a determinate bar, and the counters update when each run finishes, never mid-run.
5. **Demonstration label.** A `NoticeBanner` above the lanes: "All tools here are fakes that record calls and touch nothing real. The planted instructions are fixed text." Never collapsible.

Phone and portrait: one lane at a time with a two-tab switch **Without ABP | Through the ABP** (48 dp tabs), the counters under the tabs; both lanes are never squeezed side by side below 840 dp.

### Interactive mode

- Pick one of the five scenarios (list or prev/next) or type a sneaky instruction: an `OutlinedTextField` labelled "Add your own instruction to the document" (max 280 characters, 56 dp, placed under the scenario bar, expanding to 3 lines), with the note "Stays on this device." The typed text is shown inside a monospace quoted block in the document view so it is clear what the model was given.
- **Scenario 5 (self-knowledge opener)** shows the model's answer beside a `StatusChip` with the real network state read from the device ("Network: off, Wi-Fi: off"), not a claim.
- Result: when both lanes finish, a one-sentence summary (28sp): "Without the ABP the model <did the thing>. Through the ABP it was <blocked/flagged> at step 2." Text computed from the verdicts, never typed by hand; in pt-PT too.

### Loop mode

- N runs of the selected scenario (default 20; a stepper, 56 dp, 5 to 100). **Run** starts, **Stop** ends after the current run. Counters as above; the "1 in X" headline is "X" rounded to one decimal, and "Never failed in N runs" if zero (not "1 in infinity"). If the sample is small (under 10 runs), a one-line caution "Small sample" Held chip sits next to the headline.
- Results export is for staff, in the Settings panel (share sheet), with the same JSON as the eval harness; never on the main screen.
- A loop run keeps the screen on (host's keep-screen-on) and, in stand mode, the idle reset is paused while a loop runs.

### Settings panel

- A modal side sheet from the right (420 dp wide on a tablet; full screen on a phone), opened by **Settings**. Not part of the stand view: closing returns to the lanes unchanged.
- Rows: **Temperature** (0.0 to 1.5, step 0.1), **Top-p** (0.1 to 1.0, step 0.05), **Seed** (number field, or "Random"), **Replay a recorded run instead of running live** (`Switch`), **Export results** (loop mode). Each slider shows its value as text on the right and has minus and plus buttons (48 dp) beside it, so a precise value never depends on dragging; TalkBack adjusts by the step.
- **Recorded settings** button (56 dp) at the bottom restores the values the recordings were made with, shown as "Recorded: temperature 0.3, top-p 0.95, seed 7". If current values differ from the recorded ones, a `StatusChip("Not the recorded settings", Held)` shows on the scenario bar so a changed setting is never invisible.

### States

- No model installed: `SectionCard` "Install a model first" with a button to G, and a **Replay recorded runs** button that works without a model (so the stand always has something to show).
- Model loading: "Loading <model>" in the lanes' place with a bar; Run disabled.
- A scenario that did not fail on cue: the left lane says "No failure this time" (Allowed chip) and the right "Nothing to catch". Never faked. The counters carry it honestly; the "Replay" switch offers a curated recording and labels it as such.
- Run error: the lane shows "This run did not finish" (Blocked chip) and does not count in the counters; a "did not finish" tally is shown in the counter strip's small line.

### Motion and accessibility

- Steps appear one at a time with a 0.3 s fade; with `rememberReduceMotion()` they appear at once. No flashing, no scrolling marquees.
- Verdicts are word + glyph + colour (all pairs 7:1 or better). Left and right lanes mean the same thing in light and dark.
- TalkBack: lane headers are headings; each `StepRow` reads "<n>. <title>, <verdict>. <detail>"; the headline counter and the run result sentence are polite live regions announced once per run, not per step; the final sentence is the single most important string on the screen.
- Order of focus: scenario bar, provenance, left lane, right lane, counters. The side sheet traps focus while open and returns it to **Settings** on close.
- Not shown: a claim about how likely a real agent is to fail; the numbers are this model, this scenario, these settings, and the lane chips say so.
