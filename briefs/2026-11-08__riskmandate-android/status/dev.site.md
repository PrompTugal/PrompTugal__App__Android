# dev.site.android status

> Feature A (riskmandate.ai inside the app) built and switched on (`enabled = true`). Gate green. Not yet run on a real device or a real WebView: the first check is on the team tablets.

## Doing
Waiting for the conductor's merge and for design.android's `ui/` components.

## Done
- `features/site/`: in-app WebView, six native tabs chosen from the site's own menu (Home, What an ABP is, How it works, Pricing, SHarness, About), plain Material 3 chrome as the fallback until `ui/` lands.
- URL decision (`SitePolicy.decide`): `Links.isAllowed` -> loads in the WebView; anything else (other hosts, http, mailto, intent, file) never loads there and goes to `Browser.open`. Sub-resources to hosts off the allow-list are blocked (403), so a page cannot pull in third-party scripts or frames.
- WebView settings: JavaScript on (the site's menu is a script; there is no JavaScript interface anywhere, and a test checks the sources), no file or content access, Safe Browsing on, mixed content never allowed, third-party cookies off, DOM storage off, no pop-up windows.
- Offline copy: HTML of the six pages read from riskmandate.ai on **9 November 2026** (UTC), bundled as `res/raw/site_*.html` (about 450 KB). When the system reports no connection, or the main page fails to load, the WebView serves the copy through `shouldInterceptRequest` (nothing else is fetched; links between the six pages work; a page outside the copy says so). A banner "Offline copy from 9 November 2026" with a "Try online" button shows the mode. Strings in English and pt-PT.
- 23 tests under `app/src/test/.../features/site/`: URL decision (in-app vs external, 40 cases), sub-resource block, offline page lookup, the offline switch, a Robolectric render of the screen (online, offline banner, try online, back), the client's navigation and intercept behaviour, WebView settings, no hardcoded text.
- Gate `./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease`: green (94 tests, 0 failures; APK 1.1 MB unsigned, AAB 2.4 MB). `scripts/secret-scan.sh`: clean.

## Next
- Swap the plain chrome for design.android's components when they land.
- Re-read the site and refresh the snapshot (and `site_snapshot_date`, `OfflineCopy.SNAPSHOT_DATE_ISO`) before the stand opens if the pages change.

## Asks
- app.android: `Browser.open` calls `Links.isAllowed` first and returns `REFUSED` for any URL off the allow-list, so a tapped external link (for example the GitHub or web-summit links on the pages) shows "That address is not on riskmandate.ai, so the app will not open it." and nothing opens. That is safe, and it is what the brief's wording gives. If the stand should open such links in the system browser, `Browser` needs an explicit external-open method; I did not bypass the allow-list.
- Dinis: the Contact page is not a tab: its intake form posts to another host, which the WebView blocks. Say if you want it in.

## Blocked
Nothing. Not checked: a real WebView on a device (no emulator here), Safe Browsing and the cookie settings on a real WebView (Robolectric's shadow does not keep Safe Browsing, so that one is checked in the source), how the home page's embedded animation frame looks offline (its frame is absent from the copy).
