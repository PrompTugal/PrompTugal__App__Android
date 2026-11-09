# design.android status

> PrompTugal iOS look ported (theme, components, shell spec, before and after) at Dinis's word; summit spec (A to D, F) and on-device LLM spec (G, H, I) and shared `ui/` components done, with Robolectric screenshot tests. Gate result below. Stopped.

## Doing
Nothing.

## Done (decision pass, 9 Nov ~03:00 UTC)
- Dinis chose accent A and approved the logo redraw (via the conductor). `DefaultVariant` = A (RiskMandate green accent, navy-leaning dark); B stays in `Theme.kt` as a documented fallback and in `UiScreenshotTest.variants`. `after-*` images in `docs/design/theme/` are A; `variants-*` show both.
- Redraw ready to ship: adaptive icon `mipmap/ic_launcher_rm` (navy background, foreground, monochrome), `BrandMark`, `BrandWordmark`, `BrandBlock(subtitle)` (the sidebar brand block, no tagline), Play icon `docs/design/brand/play-icon-512.png` (512 x 512 with alpha). Manifest `icon`/`roundIcon` and the splash (`values-v31`) are app.android's; the tagline is on no screen.

## Done (logo pass, 9 Nov ~02:35 UTC)
- Vector redraw of the RM monogram with the corner brackets: adaptive icon (navy background, foreground, monochrome), `ic_rm_monogram*`, `BrandMark` and `BrandWordmark` in `ui/Brand.kt`, colours sampled from the JPEG and recorded in `docs/design/brand/README-redraw.md`. **Proposal only: the shipped icon is not replaced, nothing is referenced by the manifest.** Departures from the JPEG are listed there (letter shapes by eye, R notch omitted, flat colour, wordmark in the system sans); the real fix is the original SVG.
- Two variants behind one switch (`DefaultVariant` in `Theme.kt`): A (iOS structure, RiskMandate green accent, navy-leaning dark) and B (red, unchanged, today's default). Side by side in `docs/design/theme/variants-*`, measured contrast in `android-theme.md`.
- Found and fixed: riskmandate.ai's green `#1A7F5A` is 4.4:1 on paper and 4.1:1 on its tint (under AA for small text), so A's light accent is `#177251`, 10% darker.

## Done (top priority pass, 9 Nov ~02:20 UTC)
- `Theme.kt`: the iOS Palette (light and dark) as the Material scheme, serif headlines (`FontFamily.Serif`, nothing bundled), radius 14 shapes; `Pt.colors` and `Pt.type` for the rest; window background is paper. This answers the brand question: the iOS theme, not a new palette. Every existing screen already picks it up.
- `ui/`: `Card`, `TitledCard`, `Kicker`, `ScreenHeader`, `StatTile` (tone, detail, `large`), `StatGrid`, `CountPill`, `KindBadge`, `StateView`/`LoadPhase`, `Page`, `Columns`; `NoticeBanner` is now the amber GapNote; `SectionCard` delegates to `TitledCard`; `Space` and `UiTokens`.
- `docs/design/android-theme.md`: palette table with measured contrast, type scale, shape and space, iOS-to-Android component map, and the shell spec for app.android (permanent sidebar with brand block, sections and icons on tablets, modal drawer on phones, Home as a Now page of cards, features as destinations, CountPill badges, stand mode collapses the sidebar).
- Before and after screenshots in `docs/design/theme/`. `ShellSample` and `NowSample` (test sources) are the render targets.


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
**Build the shell** from `docs/design/android-theme.md` (section Shell): sidebar on tablets, drawer on phones, Home as a Now page. Target renders: `docs/design/theme/after-ui-shell-*` and `after-ui-now-*`. Use `Page`, `ScreenHeader`, `TitledCard`, `StatGrid`, `CountPill`, `KindBadge` from `ui/`. You draw the line icons (vector drawables, Apache 2.0, noted on Licences); a `section` on `Feature` is the architect's. Replace the standalone Open button with the RiskMandate card.
Your `FeatureFrame` owns Back, title and insets; `ui/` no longer duplicates it (`FeatureContent` is content only; `FeatureScaffold` is removed after the architect's HOLD A-des-1; tests wrap content in a test-only `PreviewFrame`). Restyling the frame: tell me what you need, or use the tokens in `Theme.kt` and `UiTokens`.
The Stand mode switch row on the home screen is yours to add; spec in D. `StatusChip` could be shown by the host in every feature's title bar (inside `FeatureFrame`) when stand mode is on.

### For dev.releases (F)
Spec: `docs/design/summit.md` F. Inside the host `FeatureFrame` use `FeatureContent(scrollable = false, maxContentWidth = Dp.Unspecified)` and build two panes with `LocalWideLayout.current`; list items are `SectionCard`s with a `StatusChip(date, ChipKind.Neutral)`; the "New in this version" banner on the home screen is a `NoticeBanner` (host, app.android). Reader text, headings and images are plain Compose with the rules in the spec.

### For dev.llm (G model setup)
Spec: `docs/design/llm-screens.md` G. `FeatureContent`; one `SectionCard` per model with a `StatusChip` (Not downloaded, Downloading, Verifying, Ready, Failed); `NoticeBanner` for the privacy line and Wi-Fi note; plain `LinearProgressIndicator`, buttons 56 dp; checksum mismatch is a Blocked chip, never skippable; adb fallback in a collapsed `SectionCard`.

### For dev.chat (H chat)
Spec: `docs/design/llm-screens.md` H. `FeatureContent(scrollable = false)` with your own list and composer; `StatusChip("On device, offline", Allowed)` plus a real "Network: on/off" chip; `SectionCard`s for the empty state and errors; tokens per second as plain text under each answer (`StatTile` only if you add a "Last answer" strip). The bubble is yours, styled from `MaterialTheme` (primaryContainer and surfaceVariant). No live region on streaming text.

### For dev.lab (I ABP Lab, read from 2 m)
Spec: `docs/design/llm-screens.md` I, with the size table. `FeatureContent`; per lane `SectionCard(large = true)` holding `StepRow(large = true)` rows (same index on the same row of both lanes); counters `StatTile(large = true)` (72sp); `StatusChip` for provenance (Live or Replay) and verdicts; `NoticeBanner` for the fakes label; `LocalWideLayout` for two lanes at 840 dp or more, tabs below; `rememberReduceMotion` for the step fade. Screenshot `ui-lab-*` in `app/build/screenshots` shows the intended shape.

### For all feature devs (dev.site, dev.abpdemo, dev.mandate, dev.stand, dev.releases, dev.llm, dev.chat, dev.lab)
Adopt the components: start screens with `Page { ScreenHeader(...) ... }` (or `FeatureContent` for self-scrolling content), group content in `TitledCard` (kicker, serif title), numbers in `StatTile`/`StatGrid`, load states in `StateView`, notes in `NoticeBanner`, verdicts in `StatusChip`. No hex values, no per-screen fonts or radii. Old calls (`SectionCard`, `StatTile(value, label)`) still compile.

### For conductor
`ui/` and `Theme.kt` are ready to merge early: they add files and one palette in `Theme.kt`, no feature touches them yet.

## For Dinis
- Dinis: if the original logo SVG exists, send it: the redraw (approved) is by eye from a 634 px JPEG and its letter shapes differ in detail from the logo's.
- Dinis: should the app move to the site's paper, ink and green and bundle Geist (SIL OFL; licence screen update)? Not done; the app keeps its navy until you say.
- Dinis: the staged-demo label wording on B ("a staged example of one policy, not a live capture of a real agent") and C's "no gap found" sentence are claims; please approve before the summit.
- Layer name size on B (44sp bold, about 6 mm cap height on a 10" panel) is computed, not measured at 2 m: please hold a tablet at 2 m once.
