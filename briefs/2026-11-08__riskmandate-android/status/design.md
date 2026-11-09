# design.android status

> Summit spec (A to D, plus F) and shared `ui/` components done, with Robolectric screenshot tests. Gate result below. Stopped.

## Doing
Nothing.

## Done
- `docs/design/summit.md`: spec for screens A (site with native tabs), B (six-layer diagram, 2 m, labelled demonstration), C (reach, mandate, gap, live), D (stand mode: on, idle reset, staff exit). Tablet landscape first, phone second, light and dark, accessibility. riskmandate.ai reference read: paper `#F7F6F2`, ink `#0D0D0C`, green `#1A7F5A`, gold `#B45309`, red `#C0392B`, blue `#1D4ED8`, Geist and Geist Mono.
- `ai.riskmandate.app.ui` (`app/src/main/java/ai/riskmandate/app/ui/`): `FeatureContent`, `SectionCard`, `StatusChip` (+ `ChipKind`), `LayerRow` (+ `LayerState`), `NoticeBanner`, `rememberReduceMotion`, `UiTokens`, `UiTags`, `LocalWideLayout`. Strings `res/values/ui.xml` and `values-pt-rPT/ui.xml`.
- `Theme.kt`: added a status palette (allowed, redacted, held, blocked; light and dark, each pair at least 7:1) via `RiskMandateStatus.palette`. Existing tokens untouched.
- Tests under `app/src/test/java/ai/riskmandate/app/ui/`: `UiComponentsTest` (back button label, size, click, pt-PT; heading; layer state description; chip speech; 200% font scale) and `UiScreenshotTest` (writes `app/build/screenshots/ui-<screen>-<device>-<orientation>-<theme>.png`: layers-large, layers, cards; 10" and 7" tablet, phone; landscape and portrait; light and dark = 36 PNGs).

## Next
Nothing until the conductor sends more.

## Blocked
Nothing.

## For other roles

### For dev.site (A)
Use `FeatureContent(scrollable = false, maxContentWidth = Dp.Unspecified)` inside the host frame; the WebView takes `Modifier.weight(1f)`. Tabs: your own row under the title bar (spec in `docs/design/summit.md` A). Offline: `StatusChip("Offline copy", ChipKind.Held)` in the scaffold `actions`; messages in a `NoticeBanner`.

### For dev.abpdemo (B)
`FeatureContent` + `NoticeBanner` (always visible demo label) + six `LayerRow(large = true)` in the stand view; `LayerState` Waiting / Checking / Stopped / Passed; scene panel in a `SectionCard`; two columns when `LocalWideLayout.current`. Respect `rememberReduceMotion()` for your own animations. Layer names and stepping rules: `docs/design/summit.md` B.

### For dev.mandate (C)
`FeatureContent` + three `SectionCard`s (Reach, Mandate, Gap; three columns when `LocalWideLayout.current`) + `StatusChip` rows (`ChipKind` Allowed, Redacted, Held, Blocked, Neutral). "Live" chip in `actions`.

### For dev.stand (D)
`NoticeBanner` for the idle countdown and `StatusChip("...", ChipKind.Neutral)` for the "Stand mode" chip (long press 3 s opens the leave dialog; give it an `onLongClick` semantics label). Spec: `docs/design/summit.md` D.

### For app (lead dev)
Your `FeatureFrame` owns Back, title and insets; `ui/` no longer duplicates it (`FeatureContent` is content only; `FeatureScaffold` is removed after the architect's HOLD A-des-1; tests wrap content in a test-only `PreviewFrame`). Restyling the frame: tell me what you need, or use the tokens in `Theme.kt` and `UiTokens`.
The Stand mode switch row on the home screen is yours to add; spec in D. `StatusChip` could be shown by the host in every feature's title bar (inside `FeatureFrame`) when stand mode is on.

### For dev.releases (F)
Spec: `docs/design/summit.md` F. Inside the host `FeatureFrame` use `FeatureContent(scrollable = false, maxContentWidth = Dp.Unspecified)` and build two panes with `LocalWideLayout.current`; list items are `SectionCard`s with a `StatusChip(date, ChipKind.Neutral)`; the "New in this version" banner on the home screen is a `NoticeBanner` (host, app.android). Reader text, headings and images are plain Compose with the rules in the spec.

### For conductor
`ui/` and `Theme.kt` are ready to merge early: they add files and one palette in `Theme.kt`, no feature touches them yet.

## For Dinis
- Dinis: should the app move to the site's paper, ink and green and bundle Geist (SIL OFL; licence screen update)? Not done; the app keeps its navy until you say.
- Dinis: the staged-demo label wording on B ("a staged example of one policy, not a live capture of a real agent") and C's "no gap found" sentence are claims; please approve before the summit.
- Layer name size on B (44sp bold, about 6 mm cap height on a 10" panel) is computed, not measured at 2 m: please hold a tablet at 2 m once.
