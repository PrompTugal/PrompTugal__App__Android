# Store assets plan: icon, feature graphic, screenshots

For Dinis. Written 8 November 2026 by play.android@promptugal.ai. Supersedes the "Graphics needed" table in `listing.md` where they differ. No brand changes are proposed: the designer works from what riskmandate.ai and the iOS app already use, and Dinis approves every file. Nothing is uploaded.

## Specifications (Play Console Help, "Add preview assets", https://support.google.com/googleplay/android-developer/answer/9866151, read 8 November 2026)

| Asset | Spec as stated on the page | Required |
|---|---|---|
| App icon | 32-bit PNG (with alpha), 512 x 512 px, maximum 1024 KB | Yes |
| Feature graphic | JPEG or 24-bit PNG (no alpha), 1024 x 500 px | Yes, to publish |
| Screenshots, all types | JPEG or 24-bit PNG (no alpha); each side 320 px to 3840 px; the longer side no more than twice the shorter; up to 8 per device type; **a minimum of two screenshots across device types** to publish | Yes |
| Tablet (7-inch and 10-inch) and Chromebook screenshots | "a minimum of 4" to show the in-app experience; each side between 1,080 and 7,680 px; 16:9 landscape or 9:16 portrait | Needed for large-screen presence; not required to publish |

Corrections to `listing.md`: the 3840 px maximum and the 2:1 limit apply to every screenshot type, so a 7,680 px tablet side is allowed only where the tablet rule applies (the page lists both; check the Console's own validator). Not on the page: the icon "design specifications" (shape and shadow); read them in the Console's icon help when the designer starts. The page's rule against text or badges implying ranking, price or promotion in icon, feature graphic and screenshots is already in `listing.md`.

## Icon

- **Today (updated 9 November 2026):** design.android's RM redraw is the launcher icon (`app/src/main/res/mipmap/ic_launcher_rm.xml`: navy background, white RM with two green corner brackets, a monochrome layer for themed icons), approved by Dinis on 9 November via the conductor. The Play icon is `docs/design/brand/play-icon-512.png`: 512 x 512, RGBA, 7 KB (limit 1024 KB), full bleed, no text, no badge. It meets the specs on the page above as far as I can check by eye and by file size; I did not run Google's icon checker, and the Console's own validator is the final test.
- **What it is not:** the original artwork. It is a vector redraw from a 634 x 418 JPEG: letters drawn by eye, the R's small top-left notch omitted, flat colour instead of the bracket gradient, wordmark typeset (`docs/design/brand/README-redraw.md`). If the original SVG arrives, replace the four drawables and re-render the PNG (`UiScreenshotTest.playIcon`). Whether it matches the iOS RiskMandate icon is Dinis's call.
- The old placeholder (`ic_launcher_foreground.xml`, a white "R" block) is no longer the icon.
- **Deliverables, status:** (1) the 512 x 512 PNG: done (`play-icon-512.png`); (2) the adaptive icon on a 108 dp canvas inside the 66 dp safe zone: done (design's notes say the brackets' far corners sit 32.2 dp from the centre); (3) the monochrome layer: done. What is still open for the **feature graphic only** (below) and for the original vector.
- Not Apache 2.0: keep the final icon files in their own folder with a note that the mark is a trademark (`open-source.md`).

### Designer brief (placeholder, no brand changes; the icon part is done, keep it for the feature graphic)

> Product: RiskMandate for Android, an app that opens riskmandate.ai and shows device-management status. Audience: staff who run organisations' devices and AI agents.
> Use the existing RiskMandate mark and colours from riskmandate.ai. Do not invent a new logo, tagline or colour. If the mark does not work at 48 px, say so and propose a simplification for Dinis to approve; do not apply it.
> Deliver: icon 512 x 512 PNG (alpha); adaptive foreground, background and monochrome SVG or vector on a 108 dp grid; feature graphic 1024 x 500 PNG (no alpha).
> No text claiming ranking, price, "free", "new", "sale", "best"; no call-to-action text; no badges; no device frames with text; no claims about what RiskMandate does beyond the words on riskmandate.ai.
> Legible in light and dark surroundings and as a monochrome silhouette.
> Return source files (vector) and exports. Everything is a draft until Dinis approves it.

## Feature graphic

1024 x 500, no alpha. The page says to avoid prominent branding that duplicates the icon (per `listing.md`); the safe layout is the wordmark "RiskMandate" and, only if Dinis approves it, the site's line "Give agents access. Not free rein." Keep key content central: Play crops it on some surfaces. No prices (the site shows GBP 10 to 1,500; none goes on any graphic). TODO designer and Dinis.

## Screenshots: which come from where

**Update (8 November 2026, later):** app.android's `work/app` (commit 44131ad, not yet on `dev`) adds `ScreenshotTest`: home and licences, phone (1080x2400), 7" (1200x1920), 10" (1600x2560), portrait and landscape, light and dark, 24 PNGs, written by `./gradlew testDebugUnitTest` to `app/build/screenshots/<screen>-<device>-<orientation>-<theme>.png`. I have not built or looked at them. The harness request in "For app" is therefore met; the scenes below still wait on the feature decision. Before that update, no screenshot code existed on `dev` or this branch.

Plan for the renders, once they exist, per device class (phone 1080 x 1920 or 1080 x 2400 portrait; 7-inch tablet and 10-inch tablet in landscape and portrait; each in light and dark), all from real composables with fixed fake data:

| # | Scene | Source | Why |
|---|---|---|---|
| 1 | Home, unmanaged ("Not detected"), no label | Robolectric render | Shows the plain app |
| 2 | Home, fully managed, label "Lisbon kiosk 3" | Robolectric render (the `HomePreview` data) | Shows the Device section |
| 3 | Home, work profile, long label (wrapping) | Robolectric render | Checks the layout and shows tablets how it copes |
| 4 | Home, message "no browser" state | Robolectric render | Honest edge state; optional for the store |
| 5 | Open source licences screen | Robolectric render | Optional; shows open source |
| 6 | riskmandate.ai open in the Custom Tab | **Real device** | A Custom Tab is the browser's UI and cannot be rendered by Robolectric |
| 7 | Any scene that shows the real status bar and navigation bar | **Real device** | The Play listing needs screenshots that match the real experience |
| 8 | Fully managed state as delivered by a real policy (M2) | **Real device (managed tablet)** | Robolectric fakes `DevicePolicyManager`; `REALITY.md` says the managed behaviour is "to be checked on a real managed device" |

Honest caveat: a render is the app as drawn from the test setup, not a photo of a device. Google's rule (listing.md) says screenshots must show the real app and must not carry text that is not part of the app experience. Rendered images of the real composables are the real UI; I would still capture 1, 2 and 6 on a real device before the public listing and keep the renders for the tablet sets and for the Firebase and internal testing pages, which do not need screenshots.

Minimum set for the first public listing: 2 phone screenshots (1 and 2, plus 6 from a device) and 4 each for 7-inch and 10-inch tablets (1, 2, 3 and one more after the feature decision in `minimal-functionality.md`). Light and dark: submit light only, or one of each; Play does not require both. Do not submit more than the features that exist.

**Screenshots wait on the feature decision.** If Dinis picks items 1 to 3 in `minimal-functionality.md`, the screens change and these scenes must be redone.

## Order of work

1. Dinis: confirm the mark and the feature set. 2. app.android: render harness. 3. Designer: feature graphic (the icon is done, see above). 4. Real-device captures after M1 (a tester's phone) and M2 (a managed tablet). 5. Dinis uploads in the Console.
