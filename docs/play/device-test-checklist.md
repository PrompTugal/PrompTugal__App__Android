# Hands-on test checklist for Dinis's devices (M1)

For Dinis. Written 8 November 2026 by play.android@promptugal.ai. Four devices: Lenovo Tab One, Lenovo Idea Tab 11, Lenovo Idea Tab Plus, Samsung Galaxy A17. Print it or copy it; record results in the boxes. Nothing here has been run: no signed build reaches a device until the Firebase project and the signing secrets exist (`REALITY.md`). Install route: `tester-guide-firebase.md`.

## The devices (public specs, read 8 November 2026)

I could not read the makers' pages for every model, so each row says how sure I am. **The Android version on your unit is what counts: Settings, About, Android version; write it in the box.**

| Device | Android version per public specs | Screen (public specs) | How sure | Source |
|---|---|---|---|---|
| Lenovo Tab One | **Android 14** (GSMArena and Smartprix); one comparison site says Android 15 | not recorded (not read) | **Conflict between sources.** The Lenovo datasheet I opened was for a different, older tablet (TAB4 8) and is not used | https://www.gsmarena.com/lenovo_tab_one-14073.php (search excerpt); https://comparisontabl.es/tablets/lenovo-tab-one/ |
| Lenovo Idea Tab 11 (2025; model TB336FU or TB336ZU; sold in some markets as Tab K11 Gen 2) | **Android 15** at launch | 11 inch, 2560 x 1600 IPS, up to 90 Hz | Several sources agree | https://m.gsmarena.com/lenovo_idea_tab_5g-14215.php (search excerpt), https://www.yugatech.com/spec/lenovo-idea-tab-2025/ |
| Lenovo Idea Tab Plus (model codes TB361ZU, TB361FU, 5G and Wi-Fi) | **Android 15** at launch | 12.1 inch, 2560 x 1600 IPS, 90 Hz | Sources agree | https://m.gsmarena.com/lenovo_idea_tab_plus-ampp-14214.php (search excerpt); Lenovo PSREF sheet https://psref.lenovo.com/syspool/Sys/PDF/Lenovo_Tablets/Idea_Tab_Plus/Idea_Tab_Plus_Spec.PDF |
| Samsung Galaxy A17 (4G or 5G: say which) | **Android 15 with One UI 7** at launch (September 2025); some sources list the 5G as Android 16, likely after an update | 6.7 inch, 1080 x 2340 | Launch version agrees; current version on a given unit unknown; 4G or 5G not known to me | https://www.gsmarena.com/samsung_galaxy_a17_4g-14157.php (search excerpt) |

All four are above the app's minimum (Android 8.0, API 26) and all but possibly the Tab One run Android 15 (the app targets API 35 = Android 15). So **Android 8 to 13 is not covered by any of these devices**; the app's JVM tests (Robolectric) are the only check there. A GSMArena page redirected to another host when fetched; I did not follow it.

## What the app should do (from `work/app`, commit 44131ad, 8 November 2026)

Home screen: title RiskMandate, button **Open riskmandate.ai**, a Device section (managed or not, optional label), About with "Open source licences", and "Build 0.1.<number>". Layout: one column in portrait, two panes in landscape at 640 dp wide or more. Light and dark follow the system. English and pt-PT (per-app language on Android 13+; otherwise the system language). Licences screen shows the full Apache 2.0 text.
**Diagnostics screen:** app.android is adding it (conductor's note); it is **not on any branch I could see** (no match in `app/src/main` on `origin/work/app` or `dev`). The "Diagnostics" rows below are written from the name only: fill in what it shows and tell play.android what to expect, then this file gets updated.

## Before each device

- [ ] Google account added to the tester group; invitation accepted (`tester-guide-firebase.md`)
- [ ] Device factory state noted: personal, managed, work profile? ______
- [ ] Android version (Settings, About): ______ Security patch: ______
- [ ] Any older RiskMandate installed (Play or earlier build)? Uninstall it first.

## Checklist per device (copy the block four times)

Device: ______________  Android: ______  Build number: ______  Date: ______  Tester: ______

| # | Check | Pass | Fail | Notes (what you saw) |
|---|---|---|---|---|
| 1 | Install from App Tester in one tap after the first-time setup; no error | [ ] | [ ] | |
| 2 | "Install unknown apps" prompt appeared as the guide says (note the exact wording) | [ ] | [ ] | |
| 3 | Play Protect prompt, if any (note wording and what you tapped) | [ ] | [ ] | |
| 4 | App opens; no crash; icon looks right (placeholder "R" on navy; monochrome icon if themed icons are on, Android 13+) | [ ] | [ ] | |
| 5 | Home screen shows title, button, Device section, About, "Build 0.1.<n>" matching the one in App Tester | [ ] | [ ] | |
| 6 | Tap **Open riskmandate.ai**: the site opens in a Custom Tab; Back returns to the app | [ ] | [ ] | |
| 7 | With no default browser or the browser disabled: message shown, no crash (optional; only if easy) | [ ] | [ ] | |
| 8 | Rotate to landscape: layout adapts (two panes on a tablet; one scrolling column on a phone below 640 dp wide), nothing cut off; rotate back | [ ] | [ ] | |
| 9 | Split-screen or resize if the device allows it (tablets): no overlap, still scrollable | [ ] | [ ] | |
| 10 | Dark mode on (system setting): readable, no white flash at start, contrast fine; then light mode | [ ] | [ ] | |
| 11 | Larger font size (Settings, Display, Font size at maximum): everything reachable by scrolling | [ ] | [ ] | |
| 12 | Language pt-PT (Android 13+: Settings, Apps, RiskMandate, Language; older: system language to Português (Portugal)): all text Portuguese, no cut-off words; ask a Portuguese speaker to read it | [ ] | [ ] | |
| 13 | Language back to English: all text English | [ ] | [ ] | |
| 14 | **Open source licences:** opens, full Apache 2.0 text scrolls, Back returns; the line "Copyright (c) TODO: copyright holder" is still there (app.android's open ask to Dinis) | [ ] | [ ] | |
| 15 | **Device section:** says not managed on a personal device (or fully managed / work profile if it is) | [ ] | [ ] | |
| 16 | **Diagnostics screen** (when it exists): opens; values match the device (Android version, build number); nothing looks like personal data (no serial, IMEI, account email) | [ ] | [ ] | |
| 17 | Screenshot of home (portrait, light) and, if you like, landscape and dark: keep for `store-assets.md` (real-device captures). No account pages or notifications in the shot | [ ] | [ ] | |
| 18 | Install the **next** build over this one: updates without uninstalling, same Firebase signature | [ ] | [ ] | |
| 19 | Uninstall: removes cleanly; nothing left (the app stores nothing) | [ ] | [ ] | |
| 20 | Anything else odd (battery, heat, slow start, wrong orientation lock) | [ ] | [ ] | |

### Per-device extras

**Lenovo Tab One** (Android 14 per most sources; confirm). Android 14: note whether the unknown-sources and Play Protect prompts differ from the Android 15 tablets. Landscape and split-screen on a tablet: check 8 and 9 carefully.

**Lenovo Idea Tab 11** (Android 15). 11 inch tablet: the home screen in landscape should be two panes (check 8). Tablet screenshots: this is the "10-inch" class in `store-assets.md`; capture portrait and landscape, light and dark. A stylus is not relevant.

**Lenovo Idea Tab Plus** (Android 15). 12.1 inch, 2560 x 1600: confirm content stays within its 560 dp column and does not stretch; looks sparse? (note it: `listing.md` already warned). Also capture for the 10-inch class.

**Samsung Galaxy A17** (Android 15, One UI 7, or 16 after updates; say which; 4G or 5G ______). Phone: portrait is the main case; landscape on a phone (below the two-pane width) should still be one scrolling column. Samsung adds its own browser: check 6 works with Samsung Internet or Chrome, whichever is default (Custom Tab). Samsung's Auto Blocker (One UI 6.1 and later, on or off ______) can block installing apps from outside the Play Store: if install is blocked, note it (from my knowledge, not verified today).

## Summary sheet

| Device | Android | Build | Install | Open site | Rotate | Dark | pt-PT | Licences | Diagnostics | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|
| Lenovo Tab One | | | | | | | | | | |
| Lenovo Idea Tab 11 | | | | | | | | | | |
| Lenovo Idea Tab Plus | | | | | | | | | | |
| Samsung Galaxy A17 | | | | | | | | | | |

Report back (to the conductor, through Dinis or his status page): build number, the failed rows, the wording of the prompts on each Android version (so `tester-guide-firebase.md` can replace its "from my knowledge" lines), and the real-device screenshots.

## Português (pt-PT): resumo para quem testa

1. Instale pelo e-mail do Firebase (guia: `tester-guide-firebase.md`). Anote o número da versão em baixo no ecrã inicial.
2. Verifique: abre; o botão abre riskmandate.ai; rodar o ecrã; modo escuro; letra grande; português; licenças; ecrã de diagnóstico (quando existir).
3. Se algo falhar, anote o número da linha, o modelo e a versão do Android, e o que viu.
4. Português escrito por uma IA: leia com atenção e corrija o que soar estranho.
