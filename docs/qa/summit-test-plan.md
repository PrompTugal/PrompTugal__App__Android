# Summit test plan: RiskMandate on Android (9 to 12 Nov 2026)

Owner: qa.android@promptugal.ai. Written 9 Nov 2026 from `briefs/2026-11-08__riskmandate-android/README.md` ("Summit phase"), `REALITY.md` and the code; **updated the same day against the real screens on `dev`** (A site, B ABP demo, C mandate, D stand, F What's new are built and enabled; the on-device LLM features G, H, I are placeholders, see `docs/qa/llm-test-plan.md`). Each review in `docs/qa/reviews/` records what was actually run. A criterion is a testable sentence; "Auto" says where a test enforces it, "Manual" means a person on a tablet.

Context: the app runs on the team's own tablets at a stand, mostly in landscape, on venue Wi-Fi that will be bad, in front of strangers who tap anything. So the worst failures are: a blank or crashing screen, being stuck (no way back), a leak to a non-RiskMandate page, wrong claims, and text that is English where the visitor chose Portuguese.

## 0. Rules that hold for every feature
| # | Criterion | Auto / Manual |
|---|---|---|
| X-1 | An enabled feature has a button on home (tag `feature_<id>`); a disabled one has none | `qa/FeatureNavigationTest`, `androidTest/SmokeTest` |
| X-2 | The button opens the feature; system Back returns to home; the feature's own back control does the same | same |
| X-3 | Rotation (landscape and portrait) keeps the user on the same feature screen, with no crash and no reset to home | same (activity recreation) + Manual on a tablet |
| X-4 | Every feature string exists in pt-PT, is not blank or a placeholder, uses the same format arguments as English | `qa/FeatureStringsTest` |
| X-5 | No feature screen hardcodes text | `qa/FeatureStringsTest` |
| X-6 | No URL literal in feature code, no feature string and no navigation outside `Links.isAllowed`; code that navigates names `Links`; no risky WebView settings (file access, JS bridge, mixed content, SSL-error override) | `qa/FeatureLinksTest` |
| X-7 | Only `MainActivity` is exported; cleartext stays off. The source manifest is checked by `qa/FeatureLinksTest`; the **merged release manifest** (libraries add components, e.g. `ProfileInstallReceiver`) is checked by hand: `aapt2 dump xmltree --file AndroidManifest.xml app-release.apk` shows one `exported=true` | Auto (source) + Manual (APK) |
| X-12 | One Back button and one title per feature screen (the host `FeatureFrame` draws them; a feature draws content only) | `qa/FeatureNavigationTest` checks the frame Back and title exist; duplicates are found by looking (probe for site in `docs/qa/reviews/`) |
| X-11 | A control in any screen corner, bottom-end included, still gets its taps (stand mode's staff corner must not cover it) | `qa/StandCornerTapsTest` |
| X-8 | Touch targets at least 48 dp, readable at font scale 200%, light and dark | Manual (and the feature's own tests) |
| X-9 | Claims about RiskMandate, ABP or Sharness appear only if riskmandate.ai or sharness.ai says them. No benchmark, customer, price or certification claim | Manual (read every string; ask the owner for the source line) |
| X-10 | The app asks for no new permission and collects nothing (the manifest has INTERNET and ACCESS_NETWORK_STATE only; Mandate view must say the same) | Manual: `aapt dump permissions` on the CI APK |

## A. Site in a WebView, offline copy (`features/site/`)
| # | Criterion | How |
|---|---|---|
| A-1 | Opens riskmandate.ai's home page inside the app (not a browser); the page is readable and scrolls | Manual |
| A-2 | Native tabs switch between the site's sections; the selected tab is visible; each tab loads | Manual |
| A-3 | In-page Back goes to the previous page; at the first page Back leaves the feature for home (never closes the app, never a blank page) | Manual + feature test |
| A-4 | Loading shows a progress indication; a failed load shows a message and a Retry (no raw Chromium error page, no white screen) | Manual with Wi-Fi off |
| A-5 | Offline copy: with Wi-Fi off and airplane mode on, the feature still shows the bundled pages; it says plainly that this is an offline copy and when it was made | Manual |
| A-6 | A link to any host outside `Links.isAllowed` never loads in the WebView. Only a **real tap on an https link** opens the system browser (`Browser.openExternal`, `Links.isExternalOk`; default pending Dinis, R-ext-2); a script, redirect or frame never opens anything; `http:`, `javascript:`, `intent:`, `mailto:`, `tel:`, `file:`, `content:` and `data:` links do nothing; in stand mode (pinned) nothing opens and a message says so | `qa/FeatureLinksTest` (gates and no raw intents) + Manual with a crafted page |
| A-7 | A redirect from an allowed page to a disallowed host is blocked (the check runs on every navigation, not only taps) | Feature test |
| A-8 | `window.open` / target=_blank to a disallowed URL is blocked; to an allowed one it loads in the same view | Manual |
| A-9 | The offline copy's external links (social, e-mail, other sites) are blocked the same way | Manual |
| A-10 | Rotation keeps the page and scroll position; no reload flash loop | Manual |
| A-11 | No cookie or form data survives after "clear" / app restart if the owner says it clears them (record what the owner claims); no JavaScript interface exposed | Review of code |
| A-12 | Memory: ten tab switches and three rotations do not crash a tablet | Manual |

## B. ABP in every layer (`features/abpdemo/`)
| # | Criterion | How |
|---|---|---|
| B-1 | The screen says in plain words, at the top and always visible, that this is a demonstration (not a live attack and not a product claim) | Manual |
| B-2 | Six layers shown in order: browser, model, hook, inline proxy, secure harness, device policy. Each has a name, one line saying what ABP does there, and a visible state (waiting, checking, stopped) | Manual |
| B-3 | One planted instruction is shown, clearly marked as planted; the animation shows each layer stopping it; the end state says it was stopped | Manual |
| B-4 | Play, pause or replay works; Back leaves at any moment, mid-animation, to home; replay after returning starts clean | Manual + feature test |
| B-5 | Animation is smooth on the tablets (no stutter you can see); with "remove animations" in system settings the screen still tells the whole story (static) | Manual |
| B-6 | Every claim is traceable to riskmandate.ai or sharness.ai (owner supplies the sources; QA checks each line against the live pages) | Manual |
| B-7 | No network call is needed to run the demo (works in airplane mode) | Manual |
| B-8 | Landscape and portrait both lay out without clipping; 200% font scale scrolls rather than clips | Manual |

## C. This app's own mandate (`features/mandate/`)
| # | Criterion | How |
|---|---|---|
| C-1 | Shows reach (what the app can touch: permissions as granted on this device), mandate (the allow-list: `Links` hosts) and gap (what is not covered) | Manual |
| C-2 | Values are read live from this device (PackageManager, `Links`), not hardcoded: change a permission or the managed `start_url` in a test, reopen, the screen changes | Feature test + Manual |
| C-3 | Matches the manifest: only INTERNET and ACCESS_NETWORK_STATE; matches `Links` (riskmandate.ai, www.riskmandate.ai, https only, port 443) | Feature test (cross-checks the manifest) |
| C-4 | States that the app collects nothing, and that is true: no analytics, no personal data (check the code for storage and network calls other than the WebView). The one stored thing is the stand-mode setting (on/off, idle minutes) in private SharedPreferences: the screen must say so, not claim "no data stored" | Review of code |
| C-5 | No claim beyond riskmandate.ai; no "secure", "compliant" or "certified" wording | Manual |
| C-6 | Works on a managed and an unmanaged device (Device section on home agrees with it) | Manual |
| C-7 | Rotation, Back, pt-PT, 200% font scale as X-3, X-4, X-8 | as X |

## D. Stand mode (`features/stand/StandHost.kt`)
| # | Criterion | How |
|---|---|---|
| D-1 | On and off by staff: hold a finger still on the **bottom-right corner of the Home screen** (long press; nothing is drawn while off, a faint dot shows while on; it works on Home only, so a visitor deep in a feature cannot trigger it); the state survives rotation and restart (a tablet back from the drawer is still in stand mode) | Manual + feature test |
| D-2 | On: Android asks once to pin the screen (startLockTask); after accepting, Home and Recents do not leave the app. Off: the pin is released (`stopLockTask`) | Manual (the system dialog cannot be driven on the JVM) |
| D-3 | On: the screen stays on (no sleep, no dim to black) for at least 20 minutes untouched; off: normal timeout returns | Manual |
| D-4 | On: after N idle minutes the app returns to the home screen from any screen (site, ABP demo, Mandate, Licences, Diagnostics) and the animation/page state is reset; any touch resets the idle timer; N is stated | Feature test with a fake clock + Manual |
| D-5 | The idle reset never fires while stand mode is off | Feature test |
| D-6 | A visitor cannot get out by accident; the staff way out is documented on the screen or in `docs/qa/` (how to unpin: hold Back and Overview, or the device's way) and tried by QA | Manual |
| D-7 | App killed or crashed while pinned: it comes back and the state is sensible (documented either way) | Manual |
| D-8 | Rotation does not reset the idle timer wrongly and does not drop the pin | Manual |
| D-9 | Works on Android 14 and 15 tablets (Lenovo Tab One, Idea Tab 11, Idea Tab Plus per `docs/play/device-test-checklist.md`) | Manual |

## F. What's new (`features/releases/`)
| # | Criterion | How |
|---|---|---|
| F-1 | Opens a list of bundled articles, newest first; each shows title, date, summary; an empty store shows a plain "nothing yet" (no crash) | `features/releases` tests + Manual |
| F-2 | An article opens in a reader; Markdown renders (headings, lists, bold, italic, code); anything unsupported shows as plain text; Back returns to the list, then to home | Feature tests + Manual |
| F-3 | Works offline (articles are in the APK); on a Portuguese device the pt-PT article shows when there is one, else English | Manual (airplane mode, pt-PT) |
| F-4 | A link in an article opens only if it is on riskmandate.ai over https (`Links.isAllowed`, through `Browser.open`); any other link is plain text, not clickable | `qa/FeatureLinksTest` (no raw intents) + Manual |
| F-5 | Images come only from the article's own `screenshots/` folder; a missing image leaves the caption, not a hole or a crash | Feature tests |
| F-6 | After an update the What's new button on Home carries a "New" badge (a generic `homeBadge`), hidden in stand mode; once the screen is opened (marked seen) the badge is gone | Manual: install the next build over this one |
| F-7 | A malformed article folder is skipped, never fatal; the "Sample" mark shows on the sample article only | Feature tests |
| F-8 | Rotation keeps the open article and its scroll; one Back and one title (host frame) | Manual |

## E. Regression: home, licences, diagnostics (already covered by 31+ JVM tests)
| # | Criterion | How |
|---|---|---|
| R-1 | Home: title, **Open riskmandate.ai** (Custom Tab, Back returns), Device section, About, build `0.1.<n>` equals the number in App Tester | `SmokeTest`, `HomeScreenTest` + Manual |
| R-2 | Home with features: the feature buttons sit under Open, never push Licences/Diagnostics out of reach (scroll works, 200% font) | `LocalisationTest` + Manual |
| R-3 | Licences: opens, Apache 2.0 text scrolls, Back returns | `SmokeTest`, `LicencesScreenTest` |
| R-4 | Diagnostics: shows version, versionCode, Android, model, managed state, config, installer; Copy works; no serial/IMEI/e-mail | `SmokeTest`, `DiagnosticsTest` + Manual |
| R-5 | pt-PT: every core string translated | `LocalisationTest` |
| R-6 | Two-pane layout at 640 dp wide landscape; one column below | `HomeScreenTest` + Manual |
| R-7 | Version and code come from CI (`0.1.<run>`), never `0.0.0-local` on a tablet | Manual (home, bottom) |

## Gate (before QA says PASS)
`./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease assembleDebugAndroidTest` green; on the branch's merge with `dev`, not only the branch alone. The CI `smoke` job runs `connectedDebugAndroidTest` on an API 34 emulator after a push to `dev`; on a branch QA can only compile it (`assembleDebugAndroidTest`) here, there is no emulator in this container, so the emulator run is first seen on `dev`.

## 10-minute manual run for Dinis (tablet, before the doors open)
Do it on the tablet that will be on the stand, on the venue Wi-Fi, in the orientation it will stand in. Tick as you go; stop at the first red and tell the conductor the row number. Home is the **Now** page (shell step 2): cards for **RiskMandate**, **ABP in every layer**, **This app's mandate**, **What's new**, and a "This app" list with **Open riskmandate.ai**, Open source licences and Diagnostics. The chat, ABP Lab and Model setup cards are not there yet: that is right until those features are turned on. There is no Back button on a feature screen: use the system Back, or the menu (phone) or sidebar (tablet) then **Now**.

| Min | # | Do | Expect | OK |
|---|---|---|---|---|
| 0:00 | 1 | Update from App Tester; open the app; find the build line (This app list, or the bottom of Home) | Opens, no crash; `Build 0.1.<n>` equals the number App Tester shows (never `0.0.0-local`) | [ ] |
| 0:30 | 2 | Look at Home (the **Now** page) | A strip of three numbers (Features on, Permissions this app asks for, Needs a look); a card for each feature that is on (RiskMandate, ABP in every layer, This app's mandate, What's new), with a short line each; a "This app" list with Open riskmandate.ai, Open source licences and Diagnostics; the Device section. On a tablet a sidebar (Today, Explore, This device) leads to the same screens and can fold to icons; on a phone a menu button opens it. Chat, ABP Lab and Model setup have no card yet: right until they are switched on | [ ] |
| 1:00 | 3 | Set the tablet to Português (Portugal); reopen the app | Home and every screen below in Portuguese, nothing cut off; set the language back for the stand when done | [ ] |
| 2:00 | 4 | **RiskMandate**: tap it; wait for the page; tap two of the tabs; rotate the tablet; press Back | Page loads in the app; tabs switch; after rotating you are still on the same tab; Back goes back, then to Home; exactly one Back button and one title at the top | [ ] |
| 3:00 | 5 | **RiskMandate offline**: Wi-Fi off, open it again | A banner says this is an offline copy and its date; pages show; "Try online" is there; no blank page. Wi-Fi back on, tap Try online | [ ] |
| 3:30 | 6 | On an online page tap a link that leaves riskmandate.ai (for example a GitHub or sharness.ai link) | Your browser opens that page (default; Dinis has not yet confirmed this, see the status page). Press Back to return to the app | [ ] |
| 4:00 | 7 | **ABP in every layer**: open it, step through to the end (or auto-play), replay, press Back midway | Says it is a demonstration; six layers stop the planted instruction one by one; Back goes home | [ ] |
| 5:00 | 8 | **This app's mandate**: open it; read Reach, Mandate, Gap | Permissions are Internet and network state; says the app collects nothing; the Gap line says none, or lists something you can explain (write down anything odd) | [ ] |
| 6:00 | 9 | **What's new**: open it, open the newest article, scroll, tap a riskmandate.ai link if there is one, Back twice | List, then article, then Home; the link opens and Back returns | [ ] |
| 7:00 | 10 | **Stand mode on**: on Home, hold a finger still on the bottom-right corner until the staff dialog opens. Switch Stand mode on; accept Android's pin dialog | Dialog says the screen is pinned; Home and Recents do nothing; a faint dot shows in the corner | [ ] |
| 7:45 | 11 | In stand mode open RiskMandate and leave it alone for the idle time (3 minutes by default; the dialog shows it) | The app returns to Home by itself. While waiting, in stand mode: tap a link that leaves riskmandate.ai, and (back on Home, after the idle return) tap **Open riskmandate.ai**: each shows a message that web pages are not opened in a browser while pinned, and nothing opens | [ ] |
| 10:45 | 12 | **Stand mode off**: hold the corner, switch off; unpin by holding Back and Overview together (or swipe up and hold with gesture navigation); then switch Stand mode **on** again for the day and leave the app on Home | Normal tablet after unpinning; ready for the doors. Note the time and the build number | [ ] |

Row 11 takes about three minutes of waiting (time rows 1 to 10 yourself; stand mode set up is the only step that cannot be done faster). Anything red: write the row, what you saw, a screenshot, send it to the conductor. Open items for Dinis are in `briefs/2026-11-08__riskmandate-android/status/qa.md`.

## Português (pt-PT): para quem testa no stand
1. Instale a versão nova pelo App Tester e confirme o número em baixo no ecrã inicial.
2. Percorra as linhas 2 a 12 da tabela acima (o modo de bancada liga-se mantendo o dedo parado no canto inferior direito do ecrã inicial). Em cada ecrã, verifique que o texto está em português e que nada está cortado.
3. Se algo falhar, anote o número da linha, o que viu e tire uma captura de ecrã.
