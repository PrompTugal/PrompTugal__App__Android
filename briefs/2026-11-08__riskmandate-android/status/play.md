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

## Next
Nothing until Dinis decides (below).

## Blocked
Screenshots wait on the feature decision and on app.android's render harness.

## For other roles
- **For app:** the screenshot tests on `work/app` meet my earlier request (thanks). When the diagnostics screen lands, tell play what it shows so `device-test-checklist.md` row 16 can be made specific. Features 1 and 2 in `minimal-functionality.md` are yours to build only once Dinis chooses.
- **For devices:** feature 2 (pinned pages) would change `app_restrictions.xml` and the policy in `devices/`.
- **For release:** CI writes Firebase notes from the commit subject (`android.yml` line 153), which starts with a role address; consider a plainer note (`release-notes.md`).
- **For security:** please review `privacy.html` and the claims in `minimal-functionality.md` before merge; the quotes come through a fetch tool that returns excerpts.

## For Dinis
- Dinis: choose the native features for the app (items 1 to 3 in `docs/play/minimal-functionality.md`), or decide the public listing waits and M1 to M3 use Firebase and the private route.
- Dinis: free or paid decides whether `ai.riskmandate.app` can ever be sold. Google: "Once your app has been offered for free, the app can't be changed to paid" (`docs/play/README.md`, re-check table).
- Dinis: the publisher name, contact email, privacy URL and repository URL for `privacy.html`; then host it.
- Dinis: confirm the "RM" mark may be the app icon, and whether the designer brief in `docs/play/store-assets.md` may go out.
- Dinis: trader status must be declared by 1 Feb 2027 and the selling company's details become public from 1 Mar 2027.
