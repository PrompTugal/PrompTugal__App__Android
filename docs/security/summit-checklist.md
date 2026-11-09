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

## F. What's new (`dev.releases`)
Reviewed (PASS, `2026-11-09-dev.releases-61d2b70.md`): own assets only; links clickable only through `Links.isAllowed` and opened with `Browser.open`; image paths limited to `screenshots/<name>` of the article's own folder; one stored integer.

---

# On-device LLM side team (added 9 November 2026, from `briefs/2026-11-09__on-device-llm/README.md`)

The app is about to download a multi-gigabyte file, load it into native code, run a model that can be talked into anything, store what visitors type and, on the evaluation side, hold a paid API key. Each item below is something I will HOLD on. The brief's own list is repeated where it applies and extended where the code needs more.

## G. Model setup and download (`dev.llm`, `app/.../llm/`, `features/models/`)
1. **Pinned sources.** Each model is one exact URL on `huggingface.co/litert-community/...` (the brief's list, no token, ungated), in code, with the file's **exact byte size** and **SHA-256**. The list is separate from `Links.isAllowed`; the WebView and browser allow-list does not grow. No URL comes from a server answer, a config, a link or the model.
2. **Redirects.** Hugging Face answers `resolve/main/...` with a redirect to a storage host (a different name under `hf.co` or `huggingface.co`). Handle redirects yourself, not with the platform default: https only, at most five hops, and the final host must match a short pinned suffix list (`huggingface.co`, `hf.co`); anything else aborts. Never send an `Authorization` header or cookies. Timeouts on connect and read. Cleartext stays off (`usesCleartextTraffic=false`; the network security config must not gain a domain exception).
3. **Verify before use.** Download to a temporary file in `noBackupFilesDir`; check size against the pinned size while streaming (stop at the limit, so a hostile or broken server cannot fill the disk); compute SHA-256 over the finished file; only on a match rename atomically to the final name. A mismatch deletes the file and shows a native error. Verify again **after any copy** and before the **first load** in each process (or keep a verified marker bound to the file's size and modification time, never to its name alone). A file that is not verified is never passed to the native runtime.
4. **No "load any file".** No file picker, no intent that hands a path, no setting that accepts another model. The `.litertlm` file is parsed by native code: a malformed file is memory-corruption surface, so only the pinned bytes may reach it. The adb-pushed fallback loads the **same** pinned file from the app's own folder through the same copy and verify steps.
5. **Storage and permissions.** Model and partial files in app-private storage, not world-readable, excluded from backup (they are under `noBackupFilesDir`; the explicit backup rules already name every domain). Check free space (file size plus margin) before starting. A "delete model" button removes the file and any partial. No new permission: INTERNET is held; a GPU delegate may need `<uses-native-library android:required="false">` entries, which are not permissions (list them in the status page).
6. **Native runtime dependency (LiteRT-LM).** Pin the exact version; run `scripts/licences.sh` and `scripts/osv-check.sh` (I do this when `build.gradle.kts` changes: Apache-2.0, MIT or BSD only, no known vulnerability). Check the dependency's **merged manifest** before and after (`app/build/intermediates/merged_manifests/release/`): any exported component, receiver, provider or permission the AAR adds (the `ProfileInstallReceiver` precedent) is removed with `tools:node="remove"` or I HOLD. Note the added native libraries (`.so` per ABI) and the APK size in the status page. If the fallback (llama.cpp through JNI) is used instead, the same applies, plus the JNI boundary: no model-controlled strings in `RegisterNatives` or `dlopen` names.
7. **Model licences.** Weights have their own licences, separate from the runtime's. Record each offered model in `docs/security/models.md` (below) before it is offered: source URL, licence and where read, size, SHA-256, date checked. The app's licence screen lists the models offered.
8. **Honest text.** The screen and the privacy page say what happens: "Downloads a model file once from huggingface.co. Nothing you type leaves the device." The download tells Hugging Face the device's IP address and user agent: the privacy text and the Data safety answer must say so (play updates them in the same merge; Dinis reads the live form).

## H. Chat (`dev.chat`)
1. **Output is text.** Model output is shown as plain text. If any Markdown is rendered, it is the `dev.releases` subset (no HTML, no images from the model), and a link is clickable only when `Links.isAllowed` and then goes to `Browser.open`; otherwise it is plain text. No `WebView` for chat. No `linkify`.
2. **No bridge from the model to the app.** The chat has no tools beyond what H lists; the model cannot open a URL, start an intent, read a file or touch the clipboard. Presets are bundled text only ("what is RiskMandate" from the bundled riskmandate.ai snapshot, never a live fetch).
3. **History.** Dinis decided: kept on the tablet, app-private, excluded from backup and device transfer, never exported, with a clear-history button. Added by security because a stand tablet is shared: **the stand-mode idle reset (and "new chat") also clears the on-screen conversation**, and persisted history is stored where `noBackupFilesDir` or the explicit backup exclusions apply, not in `getExternalFilesDir`. Say in the privacy text what is stored and for how long. Consider `FLAG_SECURE` on the chat screen so the recents thumbnail and screen capture do not keep a visitor's text (decision for Dinis: it blocks demo screenshots).
4. **No logging of content.** No prompt, answer or history in `Log`, crash text or diagnostics; the Diagnostics screen shows counts and speeds only.
5. **Resource limits.** A cap on prompt length and on generated tokens; a stop button that really cancels the native call; no background generation after the screen closes; release the model when the app is paused for long (memory).
6. **Honest badges.** "On device, offline" is shown only when it is true (no network call is made during generation; the badge reads the real model-loaded state, not a constant).

## I. ABP Lab (`dev.lab`)
1. **Fake tools only.** Every Lab tool is a fake that records the call and touches nothing: no network, file, intent, clipboard, account or content provider. A test enforces it: the Lab package may not import `android.content.Intent`, `java.net`, `okhttp`, `android.net` (beyond the connectivity hint) or the file APIs, and a test fails on a real call.
2. **The model proposes, code decides.** Every tool call is parsed with a strict JSON parser (bounded size and depth), matched by name to a fixed allow-list (`when`, never reflection or a name-to-class lookup), arguments type-checked and length-capped, and refused otherwise. An unparseable or unknown call is a recorded refusal. The ABP checks live in one pure-Kotlin module with unit tests, shown in code and on screen.
3. **Planted content.** Planted instructions and the planted secret are fixed strings shown as text, never executed or sent. **The planted secret must be an obvious fake that matches no secret pattern** (for example `PLANTED-SECRET-7f3a-do-not-use`, not an `sk-`, `ghp_`, `AKIA` or `AIza` shape): a real-looking key trips GitHub push protection and `scripts/secret-scan.sh`, and a scanner must not learn to ignore the Lab.
4. **Labelling.** The Lab states plainly that it is a demonstration, which model and backend produced each line, and whether a result is live or a recorded preset replay. A replay is never presented as live. Numbers (failure rate, catch rate, "1 in X") come from counters in code, not from text.
5. **Loop mode.** A cap on N and on total time; stop on low battery or high temperature; the results export carries scenario ids, settings, counts, verdicts, speeds and timings, never device identifiers, and is shared only by an explicit tap through the share sheet with `ACTION_SEND` text, or written to the app's external files folder for `adb pull`. **A `FileProvider` is a new component**: if used it must be `exported="false"`, `grantUriPermissions="true"`, with the narrowest `paths` entry; I review it before it merges.
6. **Presets.** Curated recordings are copied into the app at build time from the vault; they are reviewed text. No recorded output may contain a real secret, a personal address or a real person's name; check before they are bundled.

## J. Evaluation harness and the OpenRouter key (`eval`, `eval/`)
1. **The key.** `OPEN_ROUTER_KEY` or `OPENROUTER_API_KEY` is read **only from the environment**, never from a file in the repository, never as a command-line argument (it would show in `ps`), never echoed. It must not appear in logs, results, error messages (redact any `Authorization` value and any `sk-or-` string before printing or saving), request dumps, screenshots or the status page. A test feeds a fake key through a failing request and asserts it is absent from every output.
2. **The $5 cap.** Enforce it in code (log the cost of every run, stop at the cap) **and** ask Dinis to set a credit limit on the key itself in OpenRouter, so a bug cannot overspend. The harness refuses to start without a cap setting.
3. **Only synthetic data leaves.** Only the scenario files' synthetic text is sent. The planted secret is the obvious fake from I.3. No device data, no repository content, no real mail or names.
4. **Results stay out of the repository.** `eval/results/` is in `.gitignore`; results and recordings go to the vault. `scripts/secret-scan.sh` scans everything tracked, and now has explicit `OpenRouter key` and `Hugging Face token` classes; run it before every push. The vault is public, so give it the same scan and the same rule: no secrets, no contact details.
5. **Dependencies.** Python or Node dependencies are pinned with hashes (`pip install --require-hashes`, `npm ci --ignore-scripts`); no install-time scripts; the harness never runs model output as code or as a shell command.
6. **Local backend.** The desktop LiteRT-LM or llama.cpp run downloads the same pinned files with the same SHA-256 check as G; it never executes anything from the model.
7. **CI.** If the harness ever runs in CI, the key is an **environment secret of the `release`-style environment with a required reviewer**, never a repository secret, never available to pull requests from forks, never printed. Dinis sets the budget (the brief says so).
8. **Honest labelling.** Hosted results are labelled as the closest sizes (`google/gemma-3-4b-it`, `google/gemma-4-26b-a4b-it`), not as "Gemma 4 E2B"; every figure states backend, quantisation and settings; the "this device" figure comes from the tablet's loop mode only.

## K. Cross-cutting for the LLM team
- No new exported component and no new permission beyond what the download needs (none: INTERNET is held). Any `FileProvider`, service or receiver is named in the status page first.
- No JavaScript bridge, no `WebView` in G, H or I.
- The mandate screen (`features/mandate`) lists the model file and the chat history as stored data and the Hugging Face download as a network connection the app makes itself, computed from reach (not typed): it must stay truthful.
- `Links.isAllowed` stays the only allow-list for pages; the download host list is separate and exact.
- Claims about RiskMandate and ABP come only from riskmandate.ai and sharness.ai.
- Privacy, Data safety and the in-app licence list change in the same merge as the feature that needs it, or I HOLD (the app is not for Play yet, but the in-app privacy and licence screens are).

---

## L. Workbench (staff-only, Preview, added 9 November 2026; checked against `briefs/2026-11-09__on-device-llm/workbench.md` at 850a2dc, items 11 to 14 added after reading it)

Each item is something I will check; the first three are conditions for any head.

1. **Staff-only.** Disabled (`enabled = false`), reachable only from Diagnostics > Preview, hidden in stand mode; its `openFeature` requests are refused in stand mode. Preview is in every build (R-app-2), so treat the Workbench as present in every build.
2. **Fake tools only.** Every proposal goes through the per-turn cap, `checkToolCall` (exact name, strict flat arguments, required, typed, `maxLength`) and, where an ABP lane exists, `AbpChecks`, before a fake tool runs. The "Tool tests" tab defines tools as data checked at load (the Lab's contract: name, types, at most six parameters). No script, expression, file, URL or code typed by staff is ever executed or fetched.
3. **No network, file, intent or process.** The package joins the import-ban tests (`LabImportsTest` style) and qa's `LlmSandboxTest` roots.
4. **Run log** in `noBackupFilesDir/feature_workbench`: bounded (a cap on runs and bytes, oldest dropped), written only by the feature, a Clear button that removes it and anything cached in memory, never sent anywhere. Keep to counts, scores, settings, timings and the model's answers. If the typed prompt is logged, say so on the screen beside Clear. It must appear before the feature is turned on in any shipped build in: the mandate view (a reach entry and reason for the folder), the privacy policy and Data safety text ("what stays on your device"), the tester guide and the stored-data lists (the R-shell-1 lesson).
5. **Typed prompts.** Bounded length (for example 2,000 characters), shown and kept as plain text; output is plain text; the only thing taken from it is a link that passes `Links.isAllowed`, opened on a tap through `Browser.open`.
6. **Experiments and loops.** Settings clamped to ranges (temperature, top-p, top-k, seed, maximum output tokens within the service's 2,048), runs at most 100, 30 minutes, stop on heat, low battery (not charging) and three failed runs in a row; one request at a time through the service; idle reset held while it runs; saved state only validated counters.
7. **No export path.** If one is ever added it is new outbound code and needs its own review (R-lab-11): an explicit tap, share sheet or file only, the exported text as the only payload, nothing that identifies the device or an account.
8. **Models.** Only files from `ModelCatalog` (pinned revision, size, SHA-256) and only through feature G; no import of an arbitrary file and no URL field.
9. **Stats.** Memory and speed from the service and `/proc/self` only; no serial number, Android ID, account or other identifier; the device label is read before anything is pasted (R-qa-2).
10. **Claims.** Nothing on screen says more than the check can see (the Lab's "what this check sees" lines), and nothing claims more for RiskMandate than riskmandate.ai says.
11. **Copy run.** The only way out of the screen is a "Copy run" button: on a tap only, the run's own plain text (settings, numbers, answer, tool calls), no identifier, and the clipboard is never read. Nothing else is shared or written outside `noBackupFilesDir/feature_workbench`; the results JSON stays in memory and the log until an export path is reviewed.
12. **Keep the screen on and the idle hold.** Through `view.keepScreenOn` and `HoldIdleWhile` only, as stand mode does; no `WAKE_LOCK` or other permission, no service, no foreground notification. Battery and thermal readings from `BatteryManager` and `PowerManager` as in the Lab, with no permission.
13. **The tool-test catalogue (assets).** `synthetic: true`, parsed with the strict parser at load, limits on file size, case count, tools per case (2 to 5) and parameters (at most six), no external URL or contact detail, and the value a case must not share is an obvious fake that matches no scanner class (like `PLANTED-SECRET-7f3a-do-not-use`). A bad file is skipped and counted, never partly run.
14. **Import-ban roots.** `features/workbench`, the shared `experiments/` package (architect 02477e0) and `llm/` stats code are added to the import-ban test and to qa's `LlmSandboxTest`; the loop and caps that move out of the Lab keep their behaviour and their tests (100 runs, 30 minutes, 15% battery, severe thermal, three failures).

---

## M. The cloud model: OpenRouter from the app (written 9 November 2026 on the conductor's message of about 10:35 UTC; Dinis's decision; owner of the build: dev.chat)

This adds a **new network destination** and a **secret on the tablet**, and it is the first time text typed on the device can leave it. Every item is something I will HOLD on. The key route (a key pasted per tablet) is Dinis's ask; the limits of that route are in 1.

### M.1 The key
1. **Per tablet, pasted by staff, never built in.** No key in the repository, a resource, `BuildConfig`, a CI secret baked into the APK, the Firebase build or any file the build copies; an APK can be unpacked, so a build-time key is a published key. Each tablet gets **its own key**, labelled at OpenRouter with that tablet's name, with a **credit limit on the key itself** set by Dinis and revocable from OpenRouter. A lost or stolen tablet means revoke that one key.
2. **Stored encrypted with the Android Keystore.** A non-exportable AES-GCM key in the Keystore (generated on the device, a fresh random IV for every write); the ciphertext goes in `noBackupFilesDir/feature_cloud` (never `shared_prefs`, never `files/`, never external storage). Use the platform's JCA classes; no new library for this (a dependency needs a licence and OSV check first). It is protection against a copied file or backup, not against someone who can use the unlocked app or has root; say that on the key screen in one line.
3. **Never shown, never logged, never moved.** After saving the screen says "key set" or "no key", and may show the last four characters on the staff-only key screen only (accepted 9 Nov, R-cloud-3). Never in a log, exception message, `toString`, crash text, Diagnostics report, "Copy run", run log, saved instance state or `rememberSaveable`, semantics label, screenshot, notification, intent, clipboard (the app never writes it there), history file or a request body. Every string that leaves a function that touched the key passes a redaction (the key and any `sk-or-` string) as eval's `scrub` does.
4. **Entering it.** The field is a password field (no suggestions, no autofill, no spell check), filled by the system's own paste (the app never reads the clipboard itself), checked for shape (ASCII, `sk-or-` prefix, no whitespace, a length bound) before it is kept, cleared from memory and from the field the moment it is saved or the screen is left. The window is `FLAG_SECURE` while the key screen is shown (no screenshot or recents thumbnail). After saving the app clears the clipboard where the platform lets it (API 28 and later) and says to the staff member to do the same.
5. **Removing it.** A "Remove key" button deletes the ciphertext and the Keystore entry; the same happens on any "clear everything" action. Removing it works with no network.
6. **Staff only.** The key screen is in Diagnostics (hidden in stand mode) and cannot be opened from stand mode, from `openFeature`, or by a restored route.

### M.2 The network path
1. **One pinned destination.** `https://openrouter.ai/api/v1/chat/completions`, in code, checked by a policy function like `DownloadPolicy` (https, port 443 or none, no user info, host exactly `openrouter.ai`), tested with lookalikes (`openrouter.ai.evil.test`, `evilopenrouter.ai`, a trailing dot, user-info tricks). `Links.isAllowed` does not grow. No setting, field or build type can change the host; tests use an injected URL through an internal constructor, never a runtime option.
2. **No redirects, nothing extra on the wire.** Redirects refused (the `Authorization` header must only ever reach the URL we chose; eval's `_NoRedirect` is the model), no cookies, no cache, no proxy setting, default TLS trust, `network_security_config` unchanged (no user certificates), cleartext stays off. `Authorization: Bearer <key>` only on requests to that host. A **fixed `User-Agent`** (the default `HttpURLConnection` one names the device model and Android version) and a fixed `X-Title`; `Accept-Encoding: identity`.
3. **Bounded.** Timeouts on connect and read; the request body bounded (dev.chat uses 200,000 characters; I want a number Dinis chooses); the response read at most 1 MB and then refused (the client allows 8 MB: R-cloud-6); `max_tokens` at most the service's 2,048; one request at a time; no background, scheduled or retried-forever request (at most two tries, with the spend counted for both). No SDK: `HttpURLConnection` as in feature G, so no new dependency.
4. **Provider data preference.** Check OpenRouter's current documentation for a request option that refuses providers that store or train on prompts, and send it if it exists; record the date and the wording I relied on in `docs/security/models.md`. Done 9 Nov 2026: OpenRouter's provider-routing page lists `data_collection` ("allow" or "deny", default "allow") and a `zdr` option; the client sends `data_collection: deny`.
5. **Mandate and policy.** The mandate view lists `connects:openrouter.ai`, derived from the policy function (as the Hugging Face hosts are), plus `no_backup/feature_cloud` (the key ciphertext and the ledger) with their reasons. The privacy policy, Data safety text, tester guide, the site and the What's new article say what is sent, to whom (OpenRouter and the model provider behind it; their own policies apply), when (only with the cloud model switched on and a tap), and that the key stays on the tablet. **Data safety:** user-entered text sent to a third party is data shared and R-play-9 must be answered with that in mind; it is Dinis's decision and play prepares it before any store build.

### M.3 What is sent
1. **Only what staff chose.** The current prompt, the system block and the settings. **Never** chat history unless staff pick it for that request (an explicit choice, off by default, and a "show what will be sent" view before the first send of a session), never a device identifier, device label, battery, memory, app version or account, never a page the user is reading, never a file.
2. **The third judge** (the cloud model as a judge on Lab or eval output) sees synthetic scenario text only, never chat history; its verdict is advisory data parsed strictly and never gates anything.
3. **Planted secrets are obvious fakes** (like `PLANTED-SECRET-7f3a-do-not-use`); scenarios stay `synthetic: true`.
4. **A model allow-list in code** (OpenRouter ids from eval's `models.json`), not a free text field, so a typo cannot call an unknown, expensive model.

### M.4 Consent and the visitor
1. **Off by default and off whenever stand mode is on.** The cloud route is refused while stand mode is on, whatever any screen or saved state says. A visitor's typed text never goes to a third party.
2. **A switch the staff member turns on**, which falls back to off when the app restarts and when stand mode turns on.
3. **A visible badge** ("Cloud: openrouter.ai", the model) on every screen and every answer that used the cloud, in the log row and in "Copy run". The chat badge "Runs on this device" and the lines "Everything stays on this device", "Nothing you type leaves the device" (chat kicker, chat summary, `models_privacy`, the chat part of the policy) are true only while the cloud is off: they become conditional or are replaced, and a test fails if one is shown while a cloud answer is on screen. The Workbench brief's "Everything runs on the device; nothing leaves it" changes the same way.

### M.5 Spend
1. **Caps in code that the screen cannot raise**: a per-session cap and a per-tablet total (numbers from Dinis; propose $0.50 a session and $2 in all), `max_tokens` per call, a minimum gap between calls. The cost comes from `usage.cost`; a missing cost is charged at least a reserve (eval's R-eval-4).
2. **A ledger** in `noBackupFilesDir/feature_cloud` (a number, not secret) that survives restarts; a missing or corrupt ledger refuses to spend (eval's rule), never assumes zero.
3. **Loops and sweeps** that include the cloud stop at the spend cap as well as at the loop's caps (100 runs, 30 minutes, battery, heat, three failures); a sweep shows its worst-case cost before it starts.
4. The credit limit on the key itself (M.1.1) stays Dinis's, as the backstop for a bug.

### M.6 The response
Untrusted text: shown as plain text, size-capped, parsed with `StrictJson` where it is JSON; native `tool_calls` are proposals that go through the per-turn cap and `checkToolCall` and run only fake tools; the only thing taken from the text is a link that passes `Links.isAllowed`, opened on a tap; error bodies are shown cut short and scrubbed; no response field is ever fetched, executed or stored as a URL.

### M.7 Tests I will look for
Host policy (allowed and lookalikes); a redirect is refused and the header does not follow; the fake key through a failing request, an echoing server, rotation, "Copy run", the log, the Diagnostics report and saved state is absent everywhere; the fixed `User-Agent`; the request and response size caps; the spend cap stops a run, a corrupt ledger refuses, a missing cost counts the reserve; stand mode refuses the cloud route; chat history is not in the body unless chosen; the Keystore blob is in `noBackupFilesDir` and not in a backup rule's included domains; the badge appears with a cloud answer and the "on this device" lines do not; no new permission and no new dependency (`scripts/osv-check.sh`, `scripts/licences.sh` if one is added).
