# Device run: the Workbench (section to add to the guide when the feature is in a build)

For Dinis. Drafted 9 Nov 2026 by qa.android@promptugal.ai from `briefs/2026-11-09__on-device-llm/workbench.md`. **DRAFT: the Workbench is not in a build yet.** Labels marked **[confirm]** are my guesses from the brief; I check them against the real screens when it lands and then this becomes Part 5 of `device-run-idea-tab-plus.md`. Needs the model from the main guide (Gemma 4 E2B installed). Charger plugged in, build number ______

## A. Open it
- [ ] Diagnostics, **Preview**, **Workbench** [confirm]. Header carries a **Preview** label. Three tabs: **Run**, **Experiments**, **Tool tests** [confirm names].

## B. Run tab: one experiment
- [ ] Set: model Gemma 4 E2B, backend **GPU**, system prompt **none**, user prompt `Say hello in five words.`, temperature 0, seed 1, max output tokens 64. Run. Answer streams as text. Stats panel fills in place.
- [ ] Write the stats, and for each whether it says **runtime** or **measured** [confirm: how the source is labelled]: load time (cold) ______ ; input tokens ______ ; output tokens ______ ; time to first token ______ ; prefill tokens/s ______ ; decode tokens/s ______ ; total time ______ ; stop reason ______ ; backend used ______ ; memory before/after ______ / ______ ; battery before/after ______ / ______ ; thermal ______
- [ ] **Re-run** (same settings): load now says **warm**, time to first token shorter than the cold run. Warm load ______ ; decode tokens/s ______
- [ ] Set max output tokens to 8 and run: the stop reason says **max tokens**. ______
- [ ] Start a long answer (max tokens 512) and press **Stop**: it stops at once, keeps what was written, stop reason **stopped**. ______
- [ ] Backend **CPU**, same prompt: tokens/s ______ (compare with GPU). If the screen says it fell back to CPU when you asked for GPU, write that ______
- [ ] **Copy run**: paste into your notes. It is plain text with the settings and numbers and **no** serial, account, e-mail or device label: read it before you send it ______
- [ ] **Rotate the tablet during a run**: the run ends with a line saying the screen was turned; what was written and its stats stay. ______ (Rotate on the Run tab with nothing running: your prompts and settings stay. ______)

## C. Tool tests tab
- [ ] The list shows about 20 cases, each with a one-line "what this tests", grouped by kind [confirm]. Read three of them: clear? ______
- [ ] Run **one case** 5 times. Pass rate ______ of 5. Open a failure: the raw output is shown as text. ______
- [ ] Run **one kind** (for example "calls the right tool") 3 times each. Pass rate per case ______ , per kind ______
- [ ] Run **all** cases 3 times (a few minutes; plug in). Overall pass rate ______ ; time ______ min; battery used ______ %
- [ ] **Two ways to ask for a tool call:** the results are shown separately for **native** tool calling and the **text protocol** [confirm]. Native pass rate ______ ; text pass rate ______ . Which works better on this model, in your words ______
- [ ] The "will not share a value" cases carry a line saying what the check can see (exact text only). Read it: clear? ______

## D. Experiments tab
- [ ] Sweep **temperature** over 0, 0.7, 1.0 with **3 repeats** each (9 runs): a table with a row per value showing mean and spread of time to first token, decode tokens/s, output length. Mean decode tokens/s per row ______ / ______ / ______ . It stopped with reason ______ ; "Small sample" shown (under 10 runs)? ______
- [ ] Add a tool test to the sweep: a **pass rate** column appears. ______
- [ ] **A/B**: system prompt on versus off, 5 times each: it alternates A, B, A, B (watch the log order). ______
- [ ] **Stop** during a sweep: it ends after the current run and the table keeps the finished rows. ______
- [ ] The screen stays on during a sweep and stand mode's idle return does not fire (if stand mode is on, leave the tablet alone for the idle time: it must not leave the screen). ______
- [ ] **Rotate during a sweep**: it stops with a stated reason and keeps the finished rows. ______
- [ ] **Caps** (only if cheap to see): ask for 150 runs: it says it is limited to 100. ______ On battery below 15% (not charging) it stops by itself with a line saying so (do not drain the tablet on purpose; note it if it happens by itself). ______

## E. The run log
- [ ] The log lists the runs, newest first, each with the settings that differ and the key numbers. ______
- [ ] **Close the app completely** (swipe it away, or Settings, Apps, Force stop) and open it again: the log is still there. ______
- [ ] **Clear** the log (confirm): it is empty, and still empty after closing and reopening. ______
- [ ] There is no export or share button anywhere in the Workbench (staff-only, on the device). Write what you see ______

## F. What to copy back
One text to the conductor: the stats of the first run (section B), the tool-test pass rates (C), the temperature sweep table (D), pass or fail with what you saw for each box, and screenshots without account pages or notifications. **Read the device label line before you paste anything from Diagnostics.** Never paste a key or password.

## Stop at once if
The tablet is too hot to hold, the battery drops fast, a permission prompt appears that the app never showed before, or anything asks for a login or key. Note it, stop, tell the conductor.
