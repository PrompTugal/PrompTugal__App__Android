# The PrompTugal look on Android

Owner: design.android@promptugal.ai. Written 9 Nov 2026 at Dinis's word, relayed by the conductor ("the app doesn't look very good, it has a bunch of buttons... it should look as close as the iOS apps look, especially the early versions of the PrompTugal theme"). **This replaces the earlier navy tokens and answers the brand question: the Android app uses the PrompTugal iOS theme, not a new palette.** Source read: `PrompTugal/PrompTugal__App__iOS`, `Packages/PTCore/Sources/PTCore/Design/Theme.swift` and `Components.swift`, and `https://ios.app.promptugal.news/screens/ipad-news.home.png`. Before and after: `docs/design/theme/` (`before-*.png`, `after-*.png`).

**Decision (Dinis, 9 Nov 2026): variant A ships.** The iOS structure with RiskMandate green as the accent and a navy-leaning dark mode; the red accent (B) stays in `Theme.kt` as a documented fallback, one line (`DefaultVariant`). The palette table and the contrast numbers below are the iOS Palette (variant B, the fallback and the source of the neutrals, status tones and light surfaces); variant A's differences are in the section "Two looks": accent `#177251` light and `#66B78F` dark, and the navy dark surfaces. The `after-*` images are variant A; `variants-*` show both.

## The look in one paragraph

Warm paper (`#F5F1EA`, dark `#14161B`). Cards in a slightly lighter paper with a hairline rule and a 14 dp radius. Ink text, a muted grey for the second line. The accent (red in the iOS apps; green here, variant A) marks: a small uppercase kicker above every headline, the primary button, the selected item. Headlines in a bold serif. Green, amber and blue only to mean something (good, held, web). Sectioned navigation in a sidebar on a tablet. Lots of air. No decorative gradients, no shadows.

## Palette (iOS `Palette` to `Theme.kt`)

| Token | Light | Dark | Used for |
|---|---|---|---|
| `paper` | `#F5F1EA` | `#14161B` | window and page background (`background`) |
| `paper2` | `#EBE5DA` | `#1C1F25` | grouped areas, selected-row base, filled controls (`surfaceVariant`) |
| `card` | `#FFFDF9` | `#1F2229` | cards, sidebar, dialogs (`surface`) |
| `ink` | `#111318` | `#ECE7DE` | text (`onSurface`) |
| `muted` | `#555A63` | `#A7ABB3` | second lines, labels (`onSurfaceVariant`) |
| `rule` | `#D6CFC2` | `#343842` | hairlines and card borders (`outlineVariant`) |
| `red` | `#AD222B` | `#FF868A` | accent: primary, kicker, selected, error, "bad" |
| `green` | `#0B6B1E` | `#6FCF7F` | good, allowed, native |
| `amber` | `#8A5A00` | `#F2C14E` | held, partial, notices |
| `blue` | `#1F4FA3` | `#8FB4FF` | web, redacted, links |

Compose: `Theme.kt` holds both schemes (`lightColorScheme`, `darkColorScheme`, Material 3 roles set from the table, fixed, not dynamic colour) and `Pt.colors` for the tokens Material has no slot for (`paper2`, `card`, `rule`, `green`, `amber`, `blue`). Use `MaterialTheme.colorScheme` where a role fits; use `Pt.colors` for the rest. **Never write a hex value in a screen.** The window background (`res/values*/colors.xml`) is `paper`, so there is no white flash at start.

**Contrast (measured with the WCAG formula):** ink on paper 16.5:1 light, 14.7:1 dark; muted on paper 6.2:1, 7.9:1; muted on paper2 5.5:1, 7.2:1; red on card 6.8:1 (both); green on card 6.6:1, 8.3:1; amber on card 5.8:1, 9.5:1; blue on card 7.6:1, 7.7:1; white on red button 6.9:1, paper on dark-mode red 7.8:1. Status chips are the tone on its own 13% tint over the card colour: at least 4.9:1 light, 5.5:1 dark (AA text). `rule` is a hairline and is decoration only (1.4:1): borders that must be seen as controls (text fields, outlined buttons) use `outline` = `muted`.

## Which colour for what (variant A, shipped)

| Meaning | Token | Never |
|---|---|---|
| Brand: kicker, primary button, selected row (tint at 12%), selected icon and label, links, count and word pills, progress | `Pt.colors.accent` (or `MaterialTheme.colorScheme.primary`) | `Pt.colors.red` |
| Blocked, failure, error, "bad" | `RiskMandateStatus.palette.blocked`, `MaterialTheme.colorScheme.error`, `Tone.Bad` | `accent` |
| Good, allowed, native | `RiskMandateStatus.palette.allowed`, `Tone.Good` | `accent` (green accent and "good" green are close: always add a word or glyph) |
| Held, partial, notices | `RiskMandateStatus.palette.held`, `NoticeBanner`, `Tone.Warn` | |
| Web, redacted | `RiskMandateStatus.palette.redacted`, `KindBadge(Web)` | |
| Text, second line, hairline | `ink`, `muted`, `rule` | a hex value |

`Pt.colors.red` is the iOS red and in variant A it means only "blocked" and "error": outside `Theme.kt` it should appear only through the status palette and the Material `error` role. If it is used for the brand colour the screen turns red when the app is green (a bug seen in the first shell render, fixed by switching to `accent`). `ColourRulesTest` (`app/src/test/.../ui/`) fails the gate if `.red` or `Color.Red` appears outside `Theme.kt` and `ui/StatTile.kt` (`Tone.Bad`), or if any screen writes a `Color(0x...)` literal. Variant B (fallback) makes `accent` the same colour as `red`, so code that uses `accent` for the brand is correct in both.

## Icons (ruling, 9 Nov 2026)

One style: 24 dp **line** icons, 2 dp stroke, drawn by the host (`app.android`, vector drawables, Apache 2.0, noted on the Licences screen). **Features do not set `Feature.icon`**: leave it `null`, and the host's id-based line icon applies. Filled Material icons (`material-icons-core`) next to the host's line icons mix two styles and are not allowed. A feature that needs an icon of its own asks app.android through its status page ("For app"), naming its id and what the icon shows; design supplies the drawing if asked. Until the host has one for an id, the shell's default icon is used. (`Feature.icon` stays in the contract for the architect to remove or keep; nothing should use it.)

## Type

Serif for headlines, sans for interface text, monospace for data, as the iOS `Typo`. `FontFamily.Serif` is the device's own serif (Noto Serif on Android), so no font is bundled and there is no licence to record; if the stand tablets' serif looks wrong a bundled open-licence serif can replace it later (record the licence, security checks) behind the same `Typography`. All sizes are `sp` and follow the user's font scale.

| iOS `Typo` | Android | Size | Used for |
|---|---|---|---|
| `display` (serif bold) | `headlineLarge` | 34 | `ScreenHeader` title |
| `title` (serif bold) | `titleLarge`, `headlineSmall` | 22 and 24 | big card titles, feature titles |
| `headline` (serif semibold) | `titleMedium` | 18 | `TitledCard` title |
| `kicker` (caption semibold, tracking 1.4) | `Pt.type.kicker` | 12 | the red uppercase label (`Kicker`) |
| `body` | `bodyLarge` | 16 | running text |
| `meta`, `.subheadline` | `bodyMedium` | 14 | muted second lines |
| `data` (mono) | `Pt.type.data` | 14 | tool calls, arguments, model output |
| `number` (rounded semibold) | `Pt.type.number` | 28 | stat values; tabular digits (Android has no rounded design) |

Stand sizes (Lab, B) are larger and set by the components (`large = true`): see `llm-screens.md` and `summit.md`.

## Shape and space

Radius 14 dp for cards, menus and dialogs (`Shapes.medium` and `large`); buttons, chips and pills are capsules. Spacing is a 4-point grid (`Space.Xs` 4, `S` 8, `M` 12, `L` 16, `Xl` 24, `Xxl` 32). Running text is at most 680 dp wide (`UiTokens.Readable`), dashboard pages at most 1200 dp (`UiTokens.PageMaxWidth`). Cards are flat: a hairline border (`rule` at 70%), no elevation.

## Components (`ai.riskmandate.app.ui`), iOS name to Android

| iOS (`PTCore`) | Android | Notes |
|---|---|---|
| `Card` | `Card` | `onClick` makes the whole card one 48 dp+ button |
| `TitledCard` | `TitledCard` | kicker, serif title, `accessory` slot; title is a heading; `large` for stand headers |
| `Kicker` | `Kicker` | upper-cased in the user's language |
| `ScreenHeader` | `ScreenHeader` | kicker, display title, muted summary; first item of a `Page` |
| `StatTile`, `StatGrid` | `StatTile`, `StatGrid { tile { } }` | tone Neutral, Good, Warn, Bad; `large` = 72sp for the Lab |
| `CountPill` | `CountPill` | sidebar badges, card accessories |
| `KindBadge` | `KindBadge` | Native, Web, Hybrid; show on Web destinations and in debug |
| `StateView`, `LoadPhase` | `StateView`, `LoadPhase` | loading, error with retry, empty: one wording everywhere |
| `GapNote` | `NoticeBanner` | amber note for what a screen is not or cannot show |
| `Page` | `Page` | scrolling, centred, 16 dp rhythm, `LocalWideLayout` inside |
| `Columns` | `Columns` | two columns at 840 dp or more, one below |
| (Android only) | `StatusChip`, `LayerRow`, `StepRow`, `SectionCard`, `FeatureContent` | from the summit specs; `SectionCard` is now a `TitledCard` |

`FreshnessLabel` is not ported yet (no feature has a refresh time to show); add it when C or I needs "Updated 4 min ago".

## Two looks (9 Nov 2026, from the logo): A chosen

Dinis shared the RiskMandate logo (`docs/design/brand/riskmandate-logo-reference.jpg`). The conductor asked for two variants side by side; the accent is one switch (`DefaultVariant` in `Theme.kt`), so the choice costs one line.

| | A: RiskMandate green accent | B: PrompTugal red (today's default) |
|---|---|---|
| Structure | the iOS structure: paper, cards, serif, kickers | identical |
| Accent (kickers, primary button, selected row, count pills) | light `#177251`, dark `#66B78F` (the logo's green) | red `#AD222B` / `#FF868A` |
| Red | only for blocked and errors | accent and "bad" |
| Dark mode | navy-leaning: paper `#061224`, paper2 `#0C1C33`, card `#0F2038`, rule `#22344E`, ink `#E8EEF5`, muted `#9FB0C5` | the iOS charcoal `#14161B` |
| Logo | icon, brand mark and wordmark, as redrawn | the icon only (the app is not recoloured by it) |

Measured contrast for A (WCAG): light accent on card 5.8:1, on paper 5.2:1, on its tint (selected row, count pill) 4.8:1, white text on the accent button 5.9:1; dark accent on the navy card 6.8:1, on the navy paper 7.8:1, on its tint 5.4:1, navy text on the dark accent button 7.8:1; dark ink on card 14.0:1, muted on card 7.4:1; dark red, green, amber and blue on the navy card 7.0, 8.5, 9.7 and 7.9:1. **The site's own `#1A7F5A` is not used for text on paper**: it is 4.4:1 on paper and 4.1:1 on its own tint, under AA for small text; `#177251` is the nearest passing green. B's numbers are above. Both variants pass AA for text in light and dark.

Side by side (left A, right B), tablet landscape: `docs/design/theme/variants-shell-light.png`, `variants-shell-dark.png`, `variants-lab-light.png`, `variants-lab-dark.png`. **Chosen: A** (Dinis, 9 Nov 2026). The brand pieces (approved redraw) are in `docs/design/brand/README-redraw.md`; the tagline "AI agent governance and security" is not shown on any screen.

## Shell (for app.android)

Target: the iPad shell (`Apps/PrompTugal/Shell`: sidebar, destinations, a "Now" page). Rendered target: `docs/design/theme/after-ui-shell-tablet10-landscape-light.png` (and dark). The Compose sample that draws it is `ShellSample` in `app/src/test/.../ui/UiGallery.kt`: copy its structure, not its stand-in icons.

**Tablet (landscape or 840 dp wide or more): permanent sidebar.**
- Left, 300 dp, a `card`-coloured panel inset 12 dp from the edges, 14 dp radius, scrolls on its own. The content pane takes the rest, on `paper`.
- **Brand block** at the top: the RiskMandate mark (the "RM" mark from riskmandate.ai, only once Dinis confirms it may be used as the app mark; until then a plain ink square with "RM" in the paper colour, as in the sample) 56 dp, then a `Kicker("RiskMandate")` and one muted line ("Web Summit", or the device label when managed). Not interactive.
- **Sections** in muted 14sp semibold ("Today", "Explore", "This device"), each followed by its destinations. Each destination is a 48 dp+ row: 24 dp line icon, label (16sp), at the end an optional `CountPill` (for example "3 new" on What's new) or a status glyph. Selected: row tinted red at 12%, label and icon red, label semibold, `selected` semantics; others ink.
- **Destinations**: Now (the home page), then the features by their `Feature.title`, grouped as in the table below. A feature with `enabled = false` is not listed in the sidebar and has no Home card: it is reached through Diagnostics > Preview only (architect's ruling, security R-app-2); the `ShellSample` mock lists enabled destinations only. Order and grouping are the conductor's (`Features.kt`); a `section` field on `Feature` is the architect's to add; until then the host groups by id.
- **Icons**: line (outlined) icons, 24 dp, 2 dp stroke, drawn as vector drawables in `res/drawable` (Material Symbols Outlined, Apache 2.0, licence noted on the Licences screen; no `material-icons-extended` dependency, it adds megabytes). One icon per destination, chosen by app.android; the label always accompanies it.
- A collapse button at the top of the sidebar hides it to an icon rail (72 dp); the state is remembered per device. In **stand mode the sidebar is collapsed to the rail by default** so the content is the screen; staff open it.

| Section | Destinations (when enabled) |
|---|---|
| Today | Now, RiskMandate (A), What's new (F) |
| Explore | ABP in every layer (B), ABP Lab (I), Chat (H) |
| This device | This app's mandate (C), Models (G), About and licences, Diagnostics |

**Phone and portrait (below 840 dp): modal drawer.** The same panel as a modal navigation drawer opened by a labelled menu button at the top left of a one-line top bar (menu button, current destination's title, no other chrome). A bottom bar is not used: there are up to ten destinations. The host's Back returns to Now, then leaves the app.

**Now (home): a page of cards, not a list of buttons.** `Page` with `ScreenHeader(kicker = "RiskMandate", title = "Now", summary = ...)`, then a `StatGrid` of three tiles (features on, what the app collects: 0, anything needing a look, with tone), then `Columns` of `TitledCard`s: one card per feature (kicker = its category such as "Demonstration" or "riskmandate.ai", serif title, a one-line description, a `KindBadge` accessory where it is Web or Hybrid, the whole card is the button) and, under them, the device card (managed state, label, build) and links to Licences and Diagnostics as small text rows, not buttons. The primary action "Open riskmandate.ai" is the RiskMandate card, not a standalone button; the old standalone button goes. Rendered target: `after-ui-shell-*.png` right pane, and `after-ui-now-phone-portrait-light.png` for the phone.

**Feature screens inside the shell.** The host draws the feature's `ScreenHeader` (kicker = its section, title = `Feature.title`) in place of the old title bar: **the Back button becomes the sidebar's selected item on a tablet** (no Back needed; on a phone a menu button replaces it, and the system Back goes to Now). `FeatureContent` or `Page` below it. A feature that needs the full width (the WebView in A) uses `FeatureContent(scrollable = false, maxContentWidth = Dp.Unspecified)` and shows its `ScreenHeader` compactly (kicker and title on one line). `FeatureFrame` is app.android's; ask me on the status page for anything it needs from `ui/`.

**Accessibility.** Sidebar is a navigation landmark (`Modifier.semantics { isTraversalGroup }`), rows have `Role.Tab` or `Role.Button` plus `selected`; headings in section order; the badge text is spoken ("3 new"); focus order sidebar, then page; 200% font scale: the sidebar scrolls and the content becomes one column; every colour pair above meets AA; the selected row is also bold (not colour alone).

**pt-PT.** Every label is a string resource in both languages (the localisation test enforces it). Kickers are upper-cased at draw time with the device language, so "Demonstração" and "Agentes" upper-case correctly.

## What changed in the existing screens, with no code change in them

Re-theming only: the home screen's title is now serif on paper, the Open button is red on paper with a capsule shape, links are red, cards (where used) have the hairline. Before and after: `docs/design/theme/before-home-*` and the live screenshot tests (`app/build/screenshots/home-*`). The home layout is app.android's to rebuild as Now (above).

## Not done

No icons yet (app.android, with the shell); no bundled serif; `FreshnessLabel`; the RM mark as the launcher icon (Dinis's call, `docs/play/store-assets.md`).
