# Security checklist for the summit features

By security.android@promptugal.ai, 9 November 2026. What I check in each `work/dev.*` head, so owners can meet it first.
The app today asks for INTERNET and ACCESS_NETWORK_STATE, exports one activity, collects nothing and opens only
`riskmandate.ai` and `www.riskmandate.ai` over HTTPS (`Links.isAllowed`). The privacy page, the Play Data safety answers and the
listing say exactly that: any feature that changes one of these facts needs the docs changed in the same merge, or I HOLD.

## Everyone
- No new permission, exported component, intent filter, deep link, `ContentProvider`, `WebView` bridge, dynamic code loading, reflection on strings from outside or `Runtime.exec`. If one is needed, say why in your status page first.
- No analytics, crash reporter or network call. The only network traffic is the browser and WebView loading `riskmandate.ai`.
- No new dependency in the release runtime classpath without saying so; I run `scripts/osv-check.sh` and the licence gate (Apache-2.0, MIT, BSD only).
- Strings from outside (managed configuration, a page, the clipboard) are shown as text, never parsed as HTML, links or code.
- No secret, key or personal data in the repository, including tests and screenshots (`scripts/secret-scan.sh`).
- Claims about RiskMandate, ABP and the six layers come only from riskmandate.ai and sharness.ai.

## A. In-app WebView (`dev.site`)
1. Every load goes through `Links.isAllowed` (host `riskmandate.ai` or `www.riskmandate.ai`, https, port 443, no userinfo, no backslash): the initial URL, `shouldOverrideUrlLoading` for every navigation including redirects and `window.open`/target=_blank (return true and open nothing, or open the browser only for an allowed URL), `shouldInterceptRequest` for subresources if offline assets are served.
2. `WebSettings`: `allowFileAccess=false`, `allowContentAccess=false`, `allowFileAccessFromFileURLs=false`, `allowUniversalAccessFromFileURLs=false`, `javaScriptCanOpenWindowsAutomatically=false`, `setSupportMultipleWindows(false)`, `mixedContentMode=MIXED_CONTENT_NEVER_ALLOW`, `safeBrowsingEnabled=true`, `setGeolocationEnabled(false)`, no `setAllowFileAccess` for offline pages: serve the offline copy through `WebViewAssetLoader` on the app-controlled `https://appassets.androidplatform.net/` origin (https only, a path handler for `/assets/` only, not `/res/`), never `file://` or `loadDataWithBaseURL` with a riskmandate.ai base.
3. JavaScript: enabled only if the pages need it (riskmandate.ai says it has no analytics and no cookies; check what the pages run). **No `addJavascriptInterface`** and no `WebMessageListener`/`postWebMessage` to the page: no JS bridge at all.
4. Navigation to anything not allowed shows a native message, never loads. Error pages are native, not the WebView's. `onReceivedSslError` calls `handler.cancel()`, never `proceed()`. Deny all permission requests (`onPermissionRequest`, `onGeolocationPermissionsShowPrompt`), downloads (no `DownloadListener`, or refuse), and file choosers.
5. Cookies and storage: say what is kept. Clear cache, storage and cookies on exit if the site sets any; `setAcceptThirdPartyCookies(false)`.
6. Offline copy: bundled assets are reviewed like code (no script from elsewhere, no remote URL, no form posting off-site); say how they are refreshed and who signs off; no fetch of new content at run time without a security review.
7. Debugging off in release: `WebView.setWebContentsDebuggingEnabled(false)` (default for release builds; do not enable it by build flag).

## B. ABP demo (`dev.abpdemo`)
Native only, no WebView, no network. It must be labelled as a demonstration; the planted instruction is a fixed string shown as text and never executed or sent anywhere.

## C. Mandate view (`dev.mandate`)
Reads only this app's own data: `PackageManager.getPackageInfo(packageName, GET_PERMISSIONS)` and its own manifest; no `QUERY_ALL_PACKAGES`, no listing of other apps, no `<queries>` growth beyond the existing browser entry. Display the permissions the app really holds (read at run time, not hard-coded), the allow-list from `Links`, and "collects: nothing" only while that is true. No clipboard, no export, no identifiers.

## D. Stand mode (`dev.stand`)
1. Screen pinning with `startLockTask()` from an activity (user is asked by Android once); `stopLockTask()` on the off switch. Do **not** use `DevicePolicyManager` calls or `setLockTaskPackages`: that needs device-owner rights and is the MDM's decision, not the app's.
2. **No PIN** in the app unless Dinis asks. If a PIN is ever added: never stored in plaintext (a salted slow hash such as PBKDF2 in Keystore-backed storage), never logged, constant-time compare, lockout after repeated failures, and the privacy page changed (the app would then keep data). A hard-coded PIN or one in resources or `strings.xml` is a HOLD, since the source is public.
3. Keep-screen-on with `FLAG_KEEP_SCREEN_ON` (no `WAKE_LOCK` permission); idle reset by a timer in the activity, with no accessibility service, overlay (`SYSTEM_ALERT_WINDOW`), or foreground service.
4. Pinning must be stoppable by the person holding the tablet (the standard Android gesture) unless the MDM pins it; say so honestly in the UI.

## E. Host and shared code (`app`, `architect`, `design`, `qa`)
- `FeatureContext` hands every feature the whole `ComponentActivity`. Features should use only what they declare; no feature starts another app's activity or an implicit intent with a URL that did not pass `Links.isAllowed`.
- Any intent sent out carries only an allowed URL; any intent received (new filter) is a HOLD until reviewed.
- Test code does not weaken production code (no `@VisibleForTesting` backdoors that disable the allow-list).
