# Architecture review: commits merged on dev without a prior architect review (up to 8410fb8)

Verdict: PASS

Reviewer: architect.android@promptugal.ai, 9 Nov 2026. Scope: the app-touching commits merged on dev between 53f0deb and 8410fb8, except dev.lab b67a7c5 (own review file) and those already reviewed on their branches (site 747b521, app 3ca3f19).

- **app 024f17d (A-app-10 closed):** the What's new card is gone. `FeatureButton(id, title, badge)` takes `f.homeBadge()` for every enabled feature, so the host imports no feature package but `StandHost` (checked on dev: `MainActivity` imports only `FEATURES`, `FeatureContext`, `IdleHolds`, `StandHost`). The badge is a small label inside the button, and TalkBack reads "title, badge". The badge is recomputed each time Home enters the composition, so it disappears after the feature is opened.
- **app 03b393b (A-app-9 closed):** `Browser.open` returns `PINNED` while the task is pinned, as `openExternal` does. No full browser inside a pinned stand.
- **app 17033a8:** device-protected backup domains excluded too, with a test. Defence in depth.
- **releases c554cd4 (P6 implemented):** `homeBadge()` reads the seen state in `produceState` on `Dispatchers.IO` with the application context. Off the main thread, cheap, keyed to the composition. **8cb3f56** removes the sample article (A-rel-3) and makes tests independent of the bundled articles. **5abcff5** opens links with the Activity context (A-rel-2).
- **design 372ddc5, 43dbe72:** `ui/StatTile`, `ui/StepRow`, `SectionCard(large)`: stateless, text from callers (StepRow's number from a string resource), screenshot-tested. LLM screen specs in docs/design/llm-screens.md. No new dependency.
- **mandate 0b29d55, 7f5706d; stand 8def8ac, ecd28c2; site aaaf525; qa b3181d9, d9e9075; editor a5b8570, 3a695d3, 7433259:** wording, components adopted, deterministic tests, screenshots, articles. No structural change. `app/build.gradle.kts` on dev: no dependency beyond those already reviewed (webkit 1.12.1 the latest).
