# RiskMandate monogram: vector redraw (proposal)

Prepared 9 Nov 2026 by design.android@promptugal.ai from `riskmandate-logo-reference.jpg` (634 x 418 JPEG, Dinis). **Approved by Dinis on 9 Nov 2026 (via the conductor), ready to ship as the launcher icon.** The files are in the resources; the manifest and `themes.xml` switch is app.android's (below). No claim, tagline or wordmark artwork is being invented: the wordmark is typeset, not redrawn, and **the tagline is not shown on any screen**.

## Sampled colours (from the JPEG, 8-bit sRGB)

| What | Value | How sampled |
|---|---|---|
| Navy, around the mark | `#051223` | top-left corner average; the image has a vignette toward the edges |
| Navy, median of all dark pixels | `#030D1D` | median of 15,576 pixels below 60 in every channel; reads as near-black on a screen |
| Navy, centre of the frame | `#020B1B` | 40 x 40 block at the centre |
| Green, wordmark ("Mandate") | `#66B78F` | average of 3,889 green pixels in the text |
| Green, bright bracket (top-right) | `#6CC69B` | average of the bracket's green pixels |
| Green, bottom bracket / left edge | `#66BD89` / `#49936C` | the left edge is shaded in the image (a gradient) |
| White (letters) | `#F8F8F8` | most common near-white; the redraw uses `#FFFFFF` |

The redraw uses navy `#051223` (the lighter, more truthful navy where the mark sits; `#030D1D` is a vignette effect), green `#6CC69B` for the brackets and `#66B78F` for the wordmark. On light backgrounds a darker green is needed for text and fine shapes: `#177251` (riskmandate.ai's `#1A7F5A` darkened 10%, because `#1A7F5A` is 4.4:1 on paper and 4.1:1 on its own tint). They are in `app/src/main/res/values/brand_rm.xml` (`rm_navy`, `rm_green`, `rm_green_bright`, `rm_green_on_paper`).

## What was redrawn

`app/src/main/res/drawable/`:
- `ic_rm_monogram.xml`: white "RM" and two mitred corner brackets (top-right and bottom-left, each an L with 45-degree cut ends) on transparent. Used by `BrandMark` on a navy rounded square.
- `ic_rm_monogram_dark.xml`: the same in navy letters and `#177251` brackets, for light backgrounds.
- `ic_rm_launcher_foreground.xml` and `ic_rm_launcher_monochrome.xml`: the mark scaled to 52 dp wide, centred on the 108 dp adaptive canvas. A launcher shows the central 72 dp through its mask and guarantees a 66 dp safe zone; the brackets' far corners are 32.2 dp from the centre, inside both.
- `app/src/main/res/mipmap/ic_launcher_rm.xml`: the adaptive icon (navy background, foreground, monochrome layer for themed icons on Android 13+).

`ui/Brand.kt`: `BrandMark` (the mark on navy, for the sidebar's brand block and the splash) and `BrandWordmark` ("Risk" in the text colour, "Mandate" in green: `#66B78F` on dark, `#177251` on light).

Also `ui/Brand.kt`: `BrandBlock(subtitle)`, the sidebar's brand block (mark, wordmark, one muted line, no tagline).

Preview: `proposal-brand-sheet.png` (the adaptive icon through circle, squircle and rounded-square masks, the monochrome layer, the light-background mark, the brand block and the wordmark on paper and navy), rendered by `UiScreenshotTest.brand`. The Play Store icon, 512 x 512 px with alpha, full bleed (Play applies its own mask): `play-icon-512.png`, rendered by `UiScreenshotTest.playIcon`; regenerate it with `./gradlew testDebugUnitTest` (`app/build/screenshots/ui-play-icon-512-square-portrait-light.png`).

## Where the redraw departs from the JPEG (so the approver can judge)

- The letters are a bold geometric sans drawn by eye from a 634 px source, not traced from the logo's font. Proportions match (R and M each about 55 units stem on a 240-unit cap height); letter shapes will differ in detail. **The R's small notch at its top-left is omitted.**
- Flat colour instead of the bracket gradient (the JPEG shades the left edge darker).
- The wordmark is the system sans in semibold, not the logo's typeface. It reads as the logo's colour scheme, not its lettering. A proper wordmark needs the original SVG.
- The vertical rule, the tagline "AI AGENT GOVERNANCE & SECURITY" and the vignette are not part of the icon, and the tagline is not on any screen (Dinis).

**The best long-term source is the original vector (SVG) from whoever drew the logo.** Dinis approved this redraw for now; if the SVG arrives, replace the paths in the four drawables and re-render `play-icon-512.png`.

## Splash (proposal, not applied)

Android 12 and later draw the splash from the theme: `android:windowSplashScreenBackground` = `@color/rm_navy` and `android:windowSplashScreenAnimatedIcon` = `@drawable/ic_rm_launcher_foreground`, with no animation. This needs a `values-v31` theme edit in `themes.xml` (app.android's file) and is not made here. On older Android the launch window uses `windowBackground` (the paper colour) and the icon as today.

## To ship (app.android; the conductor has asked)

1. `AndroidManifest.xml`: `android:icon` and `android:roundIcon` from `@mipmap/ic_launcher` to `@mipmap/ic_launcher_rm` (app.android's file); delete the old `mipmap/ic_launcher.xml` and `drawable/ic_launcher_foreground.xml` in the same change.
2. `docs/play/store-assets.md` (play.android): the icon section can use `play-icon-512.png`; the mark is RiskMandate's, the trademark note stays.
3. If the splash is wanted, the `values-v31` theme above.
