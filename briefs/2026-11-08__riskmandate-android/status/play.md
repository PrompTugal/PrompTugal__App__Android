# play.android status

> First task list and the conductor's second list done (8 Nov 2026). Waiting on Dinis's decisions. Stopped.

## Doing
Nothing. Stopped as instructed.

## Done
All in `docs/play/`, on `work/play` (commit hash in the conductor's merge; one commit):
1. `privacy.html`: self-contained, accessible page (no scripts, light and dark, skip link, mobile width), same wording as `privacy-policy.md`; publisher, date, contact, URL and repository left as marked TODOs.
2. `minimal-functionality.md`: Google's Limited Functionality policy and the Webviews and Affiliate Spam policy quoted with URLs, plus Google's 2020 blog on webview spam. Honest assessment: the two Spam clauses do not apply (our own site, no affiliate links, Custom Tab not WebView); **limited functionality is a real risk for a public listing**, low for Firebase and internal testing. Six native feature proposals, ranked; recommended 1 and 2 (device report, pinned pages from managed config), and 3 (mandate worksheet) if the public app must stand alone.
3. `store-assets.md`: specs re-read, icon and feature-graphic plan, designer brief (no brand change), screenshot table (Robolectric renders vs real device).
4. `release-notes.md`: guidance for Firebase, Play internal and production, en and pt-PT, template and examples (500 characters per language on Play).
5. `README.md`: "could not verify" items re-checked with URLs and date read.

Settled: service-account permission names; free cannot become paid; 100 internal testers; trader status (deadline 1 Feb 2027, public details from 1 Mar 2027). Still open: IARC question wording; whether organisations have the 12-tester gate; whether an internal-track build can reach managed devices; payments profile, tax, refunds.

6. Second list (from the conductor): `tester-guide-firebase.md` (en and pt-PT; Firebase pages cited with the date read), `tester-guide-m3.md` (Play internal testing vs Firebase, when to use which, en and pt-PT), `device-test-checklist.md` (per-device checks with result boxes; Android versions from public specs). Honest gaps: Firebase's pages do not give the App Tester minimum Android version, the Android 8 to 15 on-device screens or the signature-conflict wording (marked "from my knowledge", to confirm on the first device); the Tab One version conflicts between sources (Android 14 vs 15); the Galaxy A17 variant and current version are unknown; the diagnostics screen is on no branch yet, so its checklist row is generic. `store-assets.md` updated: app.android's screenshot tests (`work/app` 44131ad) now cover the render request.

7. Security R-play-1: `privacy.html`, `privacy-policy.md`, `listing.md`, `data-safety.md` now say the app opens only riskmandate.ai and www.riskmandate.ai (checked against `Links.kt` on `dev`: hosts exactly those two, https, port 443 only). `origin/dev` merged into `work/play`.

8. Checklist row 16 now describes the diagnostics screen as the conductor listed it (version and versionCode, Android version and model, managed state, managed config as received, installer package, Copy). R-play-1 was already done in 3bf16dc.

9. R-app-4/R-play-6: `privacy-policy.md`, `privacy.html`, `data-safety.md`, `listing.md` now list two permissions (INTERNET, ACCESS_NETWORK_STATE, the latter only for the offline hint) and describe the Diagnostics screen (values shown on screen, copied only on Copy, never sent; no serial, IMEI, Android ID, account or network address). Checked against `Diagnostics.kt`, `Browser.isOffline` and the manifest on `dev` f699d2d. Data safety stays "no data collected". `origin/dev` merged first.

10. Conductor's fix: `docs/play/README.md` (step 3, step 4 of the first upload, checklist rows 3 and 13) now says environment secrets in the GitHub environment `release`; Firebase is keyless (variables, no JSON). Step 9 now carries the blocker: the org policy refuses the Play service-account key, so the Play upload stays skipped until Dinis picks keyless, a policy exception or manual upload (none tested). `tester-guide-m3.md` updated to match. `origin/dev` merged first.

11. R-stand-2: `privacy-policy.md`, `privacy.html`, `listing.md`, `data-safety.md` no longer say the app keeps no files or preferences. New policy sections "What stays on your device" (stand mode settings, What's new last-seen version, web view cookies and cache; never sent by the app) and "The in-app view of riskmandate.ai" (ordinary requests to the host on GitHub Pages; other hosts blocked). **Data safety is now an open decision, not "no data collected"**: local storage is not collection (Google's definition, read 9 Nov), but the in-app WebView is one the app controls, so its requests count; whether the IP address of an ordinary page load must be declared I cannot settle from Google's pages (`data-safety.md`, options A to C). Written from `work/dev.stand` and `work/dev.site` code, neither on `dev` yet; I found no What's new preferences code and no code that clears the web view's cookies or cache. `origin/dev` merged first.

12. R-site-1: the policy no longer says the app downloads nothing; it says the app connects to riskmandate.ai and www.riskmandate.ai only, loads those pages in the in-app view and nothing else, and deletes the view's cache, cookies, web storage and history when the in-app view closes (checked in `work/dev.site` ba979fc, `disposeSite()`, not yet on `dev`). Data safety options A to C unchanged (Dinis).

13. A-app-5: policy and `data-safety.md` now say plainly that a tapped link to another website in the in-app view is handed (https only) to the device's own browser, never loaded in the app and not while pinned; the browser's own policy applies and the app sends nothing. Checked in `Browser.openExternal` and `Links.isExternalOk` on `work/app` (not yet on `dev`).

14. On-device LLM drafts (conductor, from Dinis's brief): `privacy-policy.md` and `privacy.html` have a section "Downloaded language model and chat" marked DRAFT (include only in the build where G and H are on; dashed box in the HTML); `data-safety.md` has a DRAFT update (model download is a third-party request, same IP question as A to C; chat history on device is not collection); `model-licences.md` records the weight licences (Gemma 4 E2B and E4B: Apache 2.0 on the Hugging Face cards and on Google's licence page, no gate seen, read 9 Nov) and drafts the in-app licences note in en and pt-PT. I did not edit `app/` (app.android's path).

## Next
Nothing until Dinis decides (below).

## Blocked
Screenshots wait on the feature decision and on app.android's render harness.

## For other roles
- **For app (LLM):** adopt the licences-screen note in `docs/play/model-licences.md` (en and pt-PT) in `res/raw/licences.txt` or its generator, and add the runtime library once it is a dependency; make sure `data_extraction_rules.xml` excludes the chat history file; the in-app privacy screen needs the same model-download and chat-history sentences (drafted in `privacy-policy.md`).
- **For dev.llm / security:** record exact file names, sizes, SHA-256 and every host the download touches (Hugging Face redirects to CDN hosts); re-read the licence line on the build day; the smallest-model choice needs its licence read first (Gemma 3 variants are gated per the conductor, not read by me).
- **For app / security:** the diagnostics screen shows the device model and installer package and offers Copy. Nothing is transmitted, so Data safety stays "no data collected", but `privacy-policy.md` says only that the app shows managed state and label; it should gain one sentence once the screen ships. Tell play when it lands and I will write it.
- **For app:** the screenshot tests on `work/app` meet my earlier request (thanks). When the diagnostics screen lands, tell play what it shows so `device-test-checklist.md` row 16 can be made specific. Features 1 and 2 in `minimal-functionality.md` are yours to build only once Dinis chooses.
- **For devices:** feature 2 (pinned pages) would change `app_restrictions.xml` and the policy in `devices/`.
- **For release:** CI writes Firebase notes from the commit subject (`android.yml` line 153), which starts with a role address; consider a plainer note (`release-notes.md`).
- **For security:** please review `privacy.html` and the claims in `minimal-functionality.md` before merge; the quotes come through a fetch tool that returns excerpts.

## For Dinis
- Dinis: Data safety with the in-app view: keep "No data collected", declare the IP address, or leave the in-app view out of the store build (`docs/play/data-safety.md`, update of 9 November). The live Console form's wording decides it; please read it before choosing.
- Dinis: the Play service-account key cannot be created under the organisation policy; choose how Play uploads will authenticate (keyless like Firebase, a policy exception, or manual upload) (`docs/play/README.md` step 9).
- Dinis: choose the native features for the app (items 1 to 3 in `docs/play/minimal-functionality.md`), or decide the public listing waits and M1 to M3 use Firebase and the private route.
- Dinis: free or paid decides whether `ai.riskmandate.app` can ever be sold. Google: "Once your app has been offered for free, the app can't be changed to paid" (`docs/play/README.md`, re-check table).
- Dinis: the publisher name, contact email, privacy URL and repository URL for `privacy.html`; then host it.
- Dinis: confirm the "RM" mark may be the app icon, and whether the designer brief in `docs/play/store-assets.md` may go out.
- Dinis: trader status must be declared by 1 Feb 2027 and the selling company's details become public from 1 Mar 2027.
