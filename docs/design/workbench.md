# Workbench: control-centre layout (design spec)

Owner: design.android@promptugal.ai. Written 9 Nov 2026 from `briefs/2026-11-09__on-device-llm/workbench.md` (Dinis). Readers: dev.llm (tabs 1 and 2), dev.lab (tab 3), architect, QA, security. Theme A (`android-theme.md`): paper, cards, serif titles, green accent. **Red appears only for blocked and errors** (a failed case, a blocked call, an error stop reason): `ColourRulesTest` enforces it. Mock screens, rendered by `UiScreenshotTest.workbench` from the shared components with fake numbers:

- `docs/design/workbench/workbench-run-light.png`, `-dark.png`
- `docs/design/workbench/workbench-experiments-light.png`, `-dark.png`
- `docs/design/workbench/workbench-tooltests-light.png`, `-dark.png`

**Where it lives.** The Workbench is a disabled feature until Dinis has used it on the tablet, so it is **not in the sidebar**: it is reached through Diagnostics > Preview, like the other LLM screens (architect's ruling, security R-app-2). The mocks draw the shell with only the enabled destinations and Diagnostics selected, which is where a person is when they open it. When it is enabled it joins the sidebar under the section the conductor chooses; nothing in the layout changes.

## Principles

- **Staff only, dense, tablet landscape first.** Designed at 1280 x 800 dp with the shell's permanent sidebar (so the content area is about 956 x 760 dp), three panes side by side. It is a screen to work at with the tablet in hand or on a stand, not a screen to read from 2 m: 14sp is the smallest text, 16sp for answers and prompts, 48 dp for anything you touch, 28 dp for static rows (stats), 40 dp for static table rows, 48 dp for clickable ones. Staff screens are hidden in stand mode by the host, so none of the 2 m sizes of B and the Lab apply.
- **Numbers are tabular.** Every figure uses `Pt.type.tabular` (14sp semibold, tabular digits), right-aligned in tables and stat rows, so a value changing from 5.4 to 5.5 tok/s does not move anything, and columns line up. Units are in the label or the column header, not repeated in every cell.
- **Every figure says where it came from.** The brief wants each stat labelled: `StatRow(source = ...)` shows a 64 dp tag, "runtime" (reported by the runtime) or "app" (measured in Kotlin). A legend line sits at the foot of the panel. If neither could be obtained the value is "not available" (muted), never a blank or a zero.
- **Plain text, no links.** The streamed answer, prompts, raw tool arguments and raw model output are plain text in `bodyLarge` or `Pt.type.data`, selectable, never HTML, markdown or links (security). Raw tool arguments are monospace on a paper2 block.
- **Honest labelling.** The header always says what is running: `StatusChip("On this device")` and `StatusChip("<model>, <backend>")`, where backend is the one actually used (GPU, or CPU if it fell back, shown as Held with "CPU, fell back"). Tablet results are never mixed with hosted ones on this screen (there are none).
- **Within caps, visibly.** A sweep shows its caps and whether it is inside them (`StatusChip("Within caps")` Allowed); when a cap stops it, a `NoticeBanner` at the top of the experiments pane says which ("Stopped at run 14: battery 15%"), and the chip becomes Held.

## Frame (all three tabs)

One header row, 64 dp: kicker "Staff" and the serif title "Workbench" on the left; provenance chips; then the actions: **Copy run** and **Re-run** (outlined) and **Run** (primary), which become **Stop** (outlined) and a disabled "Running" while a run or sweep is going. Under it a `SegmentedControl` (Run, Experiments, Tool tests, 420 dp wide, 48 dp). Then the body, three panes of cards that scroll independently (each its own vertical scroll) so the settings stay put while the answer grows. 12 dp gutters, 16 dp outer padding.

Below 840 dp wide (phone, portrait, 7" portrait) the panes become one column in the order settings, answer, stats; the header actions wrap to a second row; the tab control is full width. Staff use this on a phone rarely; it must work, it need not be pretty.

## Tab 1: Run

Three panes, left to right (widths at 956 dp: 256, flexible about 370, 304):

1. **Settings** (`TitledCard`, kicker "Experiment"): Model (installed models; a row that opens a menu), Backend as a two-way `SegmentedControl` (GPU, CPU), System prompt (a row showing the preset name: none, RiskMandate base block, the chat presets; below it an editable multi-line field in `Pt.type.data` on paper2, max 6 lines then scrolls), then Temperature and Top-p as labelled sliders with the value as text at the right and minus and plus 48 dp buttons for exact values (TalkBack adjusts by the step: 0.05), Top-k, Seed ("random" or a number), Max output tokens, Tool set (none, or a set from the tool-test catalogue). User prompt is the last field and the largest (4 lines), because it is what changes most. A **Reset to defaults** text button at the foot.
2. **Answer** (flexible): `TitledCard("Answer", kicker = "Streamed, plain text")` with the stop reason as a `StatusChip` at the end (Done Allowed, Max tokens Held, Stopped Held, Error Blocked with its reason code in the card body). The text streams in `bodyLarge`, 16sp, 1.5 line height; the card grows and the pane scrolls; a thin static bar at the end while streaming (no blinking). Under it `TitledCard("Tool calls proposed", accessory = CountPill)`: one `StepRow(dense = true)` per call: name, raw arguments in monospace 14sp, verdict chip (Passed checks Allowed, Blocked Blocked, Not offered Blocked, Invalid arguments Blocked). When the protocol under test is the text one, a second block "Parsed from text" shows the same rows and the raw JSON the model wrote.
3. **Stats** (`TitledCard("Stats", kicker = "Last run")`), updating in place while it runs (live values; "..." until known; no layout shift because digits are tabular):
   - **Timing:** model load (cold or warm in the label), time to first token (ms), prefill (tok/s), decode (tok/s), total time. Prefill and decode tok/s update every second while streaming.
   - **Tokens:** input, output.
   - **Engine:** backend used (green "GPU", or Held "CPU, fell back" with the reason in a one-line note), stop reason.
   - **Device:** memory before to after (GB, "1.9 to 2.3 GB"), battery before to after (%), thermal before to after (normal, light, moderate, severe: severe is a Blocked tone, moderate Held).
   Group titles are muted `Kicker`s (not headings for TalkBack in the group sense: each group is a labelled region).

**Accessibility (Run):** the streaming answer is not a live region (it would read every token); on completion one polite announcement: "Done. 68 tokens, 5.5 tokens per second." Stats rows are merged items ("Decode: 5.5 tok/s, runtime"). Focus order: header actions, tabs, settings, answer, stats. Stop is reachable before the answer in focus order while running.

## Tab 2: Experiments

Two panes:

1. **Left, 304 dp: the builder and progress.** `TitledCard("Sweep")`: Variable (temperature, top-p, max tokens, backend, system prompt on or off, model), the values as `StatusChip(Neutral)` chips that are added with a small field and removed by touching one (each 48 dp tall hit area), Repeats (N), the prompt row, the tool-test row. Under it `TitledCard("Progress")` with `StatRow`s: run k of n, elapsed of the 30:00 cap, battery (tone Warn below 30%, Bad at the 15% cap), thermal, failed in a row of 3; and the caps in one muted sentence. A/B: the same card with two columns of settings (A and B) and "Alternate N times".
2. **Right, flexible: results**, with its own `SegmentedControl` (Sweep, A/B, Run log):
   - **Sweep table** (`DataTable`): variable value, runs (n of n if some failed), time to first token (mean ± spread, ms), decode tok/s (mean ± spread), output tokens (mean ± spread), tool-test pass rate as `PassRateBar` plus "k of n". The spread is the sample standard deviation, labelled in the card kicker ("Mean and spread over 5 runs each"). Rows are sorted by the variable's order, not by result; the best value in a numeric column is semibold (not coloured: colour means pass or fail here).
   - **A/B table**: two rows (A, B) with the same columns and a third row "A minus B" in muted tabular numbers, and a one-line sentence under it that is computed, never hand-written ("A is 0.3 tok/s faster over 8 runs each; the difference is within the spread").
   - **Run log** (`DataTable`, clickable rows 48 dp, newest first, `LazyColumn` of your own beyond about 50 rows): time, model, backend, the settings that differ from the defaults (one muted line), time to first token, decode tok/s, output tokens, stop reason chip. Touching a row opens it in the Run tab (answer, tool calls, stats as recorded) and selects the Run tab. A **Clear log** text button (confirm dialog) in the card header.
   - All tables truncate text at the end and never wrap numbers; give chip columns a weight of at least 1.5 so a chip never wraps.

**Accessibility (Experiments):** table rows are single TalkBack items reading "<header>: <value>" for each cell; the sweep's progress is announced politely at each finished run, not each second; stop on a cap announces the reason as an assertive message once.

## Tab 3: Tool tests (dev.lab)

Two panes, list left (weight 1.15) and detail right (weight 1):

- **Left:** two `StatTile`s at the top, "Native calls" and "Text protocol": "31 of 40" with the percentage and the runs per case in the detail line, tone Good at or above 85%, Warn from 60% to 84%, Bad below 60% (and the numbers say it too). Under them `TitledCard("Cases by kind", kicker = "<n> cases")` with **Run all** (and, in the header of each kind, **Run kind**; in the case row's detail, **Run case**) and a `DataTable`: case, kind, native pass rate and text-protocol pass rate (each a `PassRateBar` with "k of n", bar 44 dp), clickable rows, the selected row tinted. Kinds are the nine in the brief, grouped with a muted kind heading row; the group row carries that kind's two pass rates.
- **Right:** the selected case. `TitledCard(<case id>, kicker = <kind>, accessory = StatusChip("2 of 5 native", Blocked or Allowed))`: a one-line "What this tests" sentence (from the case file), a `SegmentedControl` for the protocol (Native, Text, Both) and Repeats, then `StatRow`s: tools offered (names), expected outcome. Under it `TitledCard("Runs")`: one `StepRow(dense = true)` per run with timing in the title ("Native, 5.5 tok/s"), the verdict chip (Passed, or the failed check by name: Wrong tool, Arguments invalid, Value mismatch, Called a tool that is not offered, Leaked a value) and for a failure the proposed call in monospace; below the rows, "Raw output" in a monospace block for the selected failed run (selectable, plain text, never parsed or executed).

**Honesty about the checks:** a pass is a check in code, shown by name; there is no model judge, and the screen says so once in the card kicker ("Checks run in code, not by a model"). The results format is the same JSON as the Lab's export (dev.lab), for eval to read later; this screen has no share or export button.

**Accessibility (Tool tests):** a pass rate reads "4 of 5 passed", never only the bar; the verdict words are the failed check's name; the case list is a list (`collectionInfo`), the detail a region.

## Components (all in `ai.riskmandate.app.ui`)

| Need | Component |
|---|---|
| Frame, tabs and sub-tabs | `SegmentedControl` (new) |
| Panes, cards | `TitledCard`, `Card` |
| Settings and stats rows with tabular numbers and a source tag | `StatRow` (new) |
| Sweep, A/B, run log, case list | `DataTable`, `TableColumn`, `TableCell` (new) |
| Pass rate per case, kind | `PassRateBar` (new) |
| Run's tool calls, the case's runs | `StepRow(dense = true)` |
| Provenance, stop reason, verdicts, caps | `StatusChip`, `ChipKind` |
| Counts (calls, runs, failed) | `CountPill` |
| Headline numbers (31 of 40) | `StatTile` |
| Caps stop, errors | `NoticeBanner` |
| Empty and loading | `StateView` |
| Wide layout | `LocalWideLayout`, `Page` or `FeatureContent(scrollable = false)` for pane layouts |

Plain Material in the feature: `Slider`, text fields, `Button`, `OutlinedButton`, dialogs, menus. Colours only from `Pt.colors`, `colorScheme` and `RiskMandateStatus.palette`; no `Color(0x...)` and no `.red` outside Theme.kt and StatTile.kt.

## Open for the owners

- Whether a sweep's spread is a standard deviation or a min to max range is dev.llm's call; this spec says standard deviation and the label must say which.
- Memory, battery and thermal readings can lag; if a reading is older than 5 seconds the row shows it muted with "(5 s ago)".
- dev.lab owns the case file format; the names of the failed checks above are the ones this spec expects (Wrong tool, Arguments invalid, Value mismatch, Called a tool that is not offered, Leaked a value) and should match the code checks' names.
