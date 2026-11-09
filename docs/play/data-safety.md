# Play Console: Data safety, content rating, audience, ads, app access

Draft answers for Dinis to enter, 8 November 2026. Nothing is submitted. I could read Google's Data safety help (answer 10787469) but not the Console forms (they need sign-in), so the questions below follow the form's structure and the help's definitions, not its exact wording. Re-check each against the live form.

## Update 9 November 2026 (security R-stand-2): the in-app view and local storage change this file

The table below was written for the app as it was on 8 November. Two features on branches not yet on `dev` (`work/dev.stand`, `work/dev.site`) change it. **Rows "No network code" and "No storage" below are superseded by this section.** Re-run the whole file against the merged code before any submission.

What changes (from the branches' code, not from a build):
- **Local storage (stand mode's SharedPreferences file `stand_mode`: on/off and idle minutes; What's new's last-seen version, per the conductor, not found in code I can see; the web view's cookies and cache).** Google's definition (https://support.google.com/googleplay/android-developer/answer/10787469, read 9 November 2026): "Collect" is transmitting data off the device; data "processed locally on the user's device and not sent off device" does not need disclosing. **So stored-but-never-sent items are not collection and need no Data safety entry.** They do need the privacy policy sentences (done: `privacy-policy.md`, "What stays on your device").
- **The in-app view (a WebView) makes the app itself send web requests to riskmandate.ai.** Google, same page: "Data collected from a WebView opened from your app counts as collection if your app controls the WebView's code or behavior"; users browsing the open web are exempt. Our app controls this view (host allow-list, requests to other hosts blocked, settings fixed), so the exemption does not apply. Google also says to "disclose your collection, use, and sharing of IP addresses based on how you use them."
- **What leaves the device:** an ordinary request to riskmandate.ai (hosted on GitHub Pages, per https://riskmandate.ai/privacy, 24 September 2026): "That host sees ... your IP address" in its words about web servers, and "We have no analytics dashboard, no log access and no way to see who read what." Other hosts are blocked (the app connects to `riskmandate.ai` and `www.riskmandate.ai` only); the view's cache, cookies, history and web storage are deleted when the screen closes (`disposeSite()`, `work/dev.site` ba979fc); the Contact page (whose form posts to another host) is not in the view. The app adds no identifier and no analytics.

- **Links to other websites (architect A-app-5, `Browser.openExternal` on `work/app`, not yet on `dev`):** a link tapped in the in-app view that leaves riskmandate.ai is handed, as an https address, to the device's own browser (`ACTION_VIEW`), never loaded in the app and not while the app is pinned (stand mode). The app sends nothing and does not see what the browser loads; the browser's own privacy policy applies. This is the user's own tap handing a link to another app on the device, not data the app collects; it does not change the A, B, C question below.

**What I cannot settle from Google's pages: whether "No data collected" is still the right answer.** The request metadata (IP address, user agent) is transmitted off the device, to a host we chose, by a WebView we control. Google's text does not say whether an ordinary page load's IP address counts as collected data when we do not store, use or see it (GitHub is arguably a service provider under Google's definition, which excludes sharing but not collection), and I could not read the live form.

| Option | Answer | Good | Bad |
|---|---|---|---|
| A. Keep "No data collected" | The app collects nothing; the host's request metadata is not used by us | Matches the intent and the page's claim of nothing measured | If Google's reviewers read the WebView rule strictly, a Data safety mismatch is a policy problem (Misrepresentation) |
| B. Declare the IP address as the live form allows, with the purpose "app functionality" and "not shared", processing ephemeral if the form offers it | The store page shows data collected | Cannot be called a mismatch | Shows a collection line for an app that stores and uses nothing; a user-facing cost |
| C. Do not ship the in-app view in the store build (keep the Custom Tab) | "No data collected" stays true | No question to answer | Drops the feature; the offline copy and the review-risk benefit (`minimal-functionality.md`) go too |

I recommend **asking the live form first** (Dinis, Console), because its wording decides it, and until then not claiming "No data collected" in any text. The listing line is marked TODO. The decision is Dinis's.

## Update 9 November 2026 (security R-shell-1): everything the app now stores on the device

All of it is local and never transmitted, so none of it is "collected" (Google: data "not sent off device" need not be disclosed) and none of it changes the form. It must all appear in the privacy policy (done, `privacy-policy.md`, "What stays on your device") and the Data safety row "No storage" above stays superseded:

| Item | Where | Content | On `dev`? |
|---|---|---|---|
| Shell | SharedPreferences `shell`, key `sidebar_collapsed` | yes or no (UI preference) | `work/app` only, for now |
| What's new | SharedPreferences `feature_releases`, key `last_seen_version_code` | an integer | yes |
| Stand mode | SharedPreferences `stand_mode` | on/off, idle minutes | `work/dev.stand` |
| In-app view | WebView cookies and cache | deleted when the view closes | `work/dev.site` |
| Chat history (G/H, DRAFT) | `noBackupFilesDir/feature_chat/`, one JSON file | conversations | yes (feature flag state not checked) |
| Model files (G, DRAFT) | `noBackupFilesDir/models/` (finished files, a `.part` file while downloading, a `.verified` marker); adb copy in `getExternalFilesDir("models")` on API 30+, deleted after import | model weights | `work/app`, `work/dev.llm` |
| Workbench run log (staff only, DRAFT) | `noBackupFilesDir/feature_workbench/runs.jsonl` | per run: settings and figures, the first 200 characters of the prompt, the first 300 of the answer; at most 500 runs, 4 MB; Clear button; no identifiers; never sent or exported | `dev` (feature off; Diagnostics, Preview) |
| Chosen model (G, DRAFT) | SharedPreferences `feature_models` | which downloaded model to use | `work/dev.llm` |

`data_extraction_rules.xml` excludes every cloud-backup and device-transfer domain by name, `noBackupFilesDir` is never backed up by the platform, and `allowBackup` is false.

## Update 9 November 2026: on-device model download and chat (DRAFT; not for Play yet)

From `briefs/2026-11-09__on-device-llm/README.md`; features G and H are `enabled = false` placeholders on `dev` and the brief says "Not for Google Play yet", so **nothing here changes a form today**. For the day they ship in a store build:
- **Model download (details from dev.llm's status, `work/dev.llm`, not yet on `dev`).** The app's own network code (`HttpURLConnection`, not a WebView) requests a file from `huggingface.co`, started only when the user taps Download; it follows at most five redirects by hand, each only to https on `huggingface.co` or a host under `hf.co` (seen: `us.aws.cdn.hf.co`, a signed URL), with no Authorization header, no cookies and no caches. What leaves the device is an ordinary request: IP address and request headers, no user content. It is the same IP-address question as options A to C above, with a **third-party host** (and any content-delivery host it redirects to) instead of our own site. Google's page (read 9 November) excludes from "sharing" a transfer "based on a specific user-initiated action, where the user reasonably expects the data to be shared", so a download the user starts after an on-screen disclosure of the host fits that exception for sharing; it does not by itself settle "collect". Dinis decides together with A to C, from the live form.
- **Chat history stored on the device only** (app-private, excluded from backup and transfer, Clear history). Not collection (data "not sent off device" need not be disclosed). Needs the policy text (drafted), and `data_extraction_rules.xml` must really exclude the chat file (app.android; check the merged file).
- **What the user types never leaves the device** is a claim the code must keep true: no analytics on prompts, no network tools in the chat, and the Lab's tools are fakes (per the brief). Security's checklist covers it. If any later feature sends a prompt anywhere, this file and the policy change first.
- **OpenRouter** is used by the eval harness (`eval/`, synthetic scenario data only), not by the app.

## How the answers were reached

Google's definition (verified): "Collect" means transmitting data from your app off a user's device, including data sent by libraries and SDKs. Data from a webview the developer controls counts too; a page the user navigates on the open web does not. The app does none of these:

| Check | Evidence |
|---|---|
| Permissions | `app/src/main/AndroidManifest.xml` declares only `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE` (both normal permissions; the second is used by `Browser.isOffline` only to read whether a network with internet capability exists, for an offline hint; nothing is sent). The `<queries>` entry (Custom Tabs service intent) is package visibility, not a permission. |
| No network code (SUPERSEDED, see the update above) | No `HttpURLConnection`, OkHttp, Retrofit, WebView or socket use in `app/src/main`. The only outward act is handing a URL to the browser: `Browser.kt` (`CustomTabsIntent.launchUrl`, or `Intent.ACTION_VIEW`). The app opens riskmandate.ai in the user's browser, which is the browser's traffic, not the app's. No WebView exists, so Google's webview clause does not apply. |
| No SDKs | `app/build.gradle.kts` dependencies: AndroidX (core, activity, lifecycle, browser), Compose and Material 3, plus test libraries. No Firebase, analytics, ads, crash reporting or Google Play services library. (Firebase App Distribution is a CI delivery step, not in the app.) |
| No storage (SUPERSEDED, see the update above) | Search of `app/src/main` found no SharedPreferences, DataStore, database, file writing or logging. The app reads managed configuration and device state at runtime and holds them in memory (`MainActivity.kt`). |
| No backup | `android:allowBackup="false"` and `res/xml/data_extraction_rules.xml` exclude the app's data from cloud backup and device transfer. |
| Encryption in transit | `network_security_config.xml` and `usesCleartextTraffic="false"`: cleartext is off; `Links.kt` accepts only `https://` on riskmandate.ai and www.riskmandate.ai. The app transmits nothing itself. |
| Accounts | None: no sign-in code exists. |
| Managed values | `ManagedConfig.kt` reads `start_url` and `device_label` from `RestrictionsManager`, trims and validates them, keeps them in memory; they are never sent anywhere. Device status (`DeviceStatus.kt`) uses `DevicePolicyManager` and `UserManager` locally, shown on screen only. |
| Diagnostics screen | `Diagnostics.kt`, `DiagnosticsScreen.kt` (merged 8 November 2026): shows app version and code, application ID, build type, Android version, make and model, management state, managed configuration, installer package. No IMEI, serial, Android ID, account or network address. Written to the clipboard only on the user's tap on Copy; not transmitted. Model and installer package are not advertising or device identifiers (every unit of a model shares the model string), so Data safety stays "no data collected". |

## Data safety form, question by question

1. **Does your app collect or share any of the required user data types?** No.
   - Reasoning: nothing leaves the device from the app (table above). Managed configuration and device-management status are read locally and shown on screen; they are not transmitted, so they are neither collected nor shared.
2. **Data types (location, personal info, financial, health, messages, photos, audio, files, calendar, contacts, app activity, web browsing, app info and performance, device or other IDs):** none selected. Specifically:
   - Web browsing history: the app does not see which pages the user visits in the browser.
   - App info and performance (crash logs, diagnostics): none; no crash reporter. Play Console's own Android vitals are collected by Google from devices that opted in, not by the app.
   - Device or other IDs: the app does not read an advertising ID, Android ID, IMEI or MAC address.
3. **Is all of the user data collected by your app encrypted in transit?** The form hides this question when nothing is collected. If it is shown: not applicable; the app transmits nothing.
4. **Do you provide a way for users to request that their data is deleted?** Hidden when nothing is collected. If shown: not applicable; the app holds no data. The Play account-deletion requirement (answer 13327111) applies to apps that let users create an account in the app; this app has none.
5. **Security practices badges.** Optional; not claiming "independent security review" (no review exists).
6. **Privacy policy URL:** required for every app (Play Console Requirements policy, section 3.2). Link `privacy-policy.md` once hosted. TODO for Dinis.

What the store page will show depends on the Data safety answers, which are **open** (see the updates at the top of this file: the in-app view, local storage, the model download). Any release that adds a dependency, a permission or a network destination must re-open this file first.

## Content rating (IARC questionnaire)

Console: App content, Content rating, then the IARC questionnaire. I could not read it without sign-in; the answers below are by topic. The questionnaire asks for an email address and an app category first.

- Category: choose a utility, productivity, reference or similar category, not a game, social or news app. TODO for Dinis: the actual choice (the questionnaire's category options differ from store categories).
- Violence, blood, fear: none.
- Sexual content and nudity: none.
- Language: none.
- Controlled substances (drugs, alcohol, tobacco): none, no references.
- Gambling or simulated gambling: none.
- User-generated content, user-to-user communication, sharing of user content or location: none in the app. (riskmandate.ai has a contact form; the app does not embed or offer it.)
- Purchases: the app has no in-app purchases and no digital goods. (The website sells policies at its own store; the app does not.) If the app is created as Paid, the purchase is the app price, not in-app.
- Does the app share the user's location or let users interact: no.
- Web browsing: the app opens a website in the user's browser. The questionnaire has a question about unrestricted internet access or web content that can be freely browsed. The app only opens riskmandate.ai, which the team controls, and cannot navigate elsewhere; the user's browser can once the tab is open. Answer honestly according to the live wording; if it asks whether the app provides access to the open web, the accurate answer is that the app itself only links to one site. Dinis checks the effect on the rating.
- Expected rating: the lowest tier (for example Everyone / PEGI 3 / USK 0) given all answers above. Not verified until the questionnaire is run.

Ratings are issued by IARC at the end of the questionnaire; do not publish before it is done (the Console blocks it).

## Target audience and content

- Target age groups: **18 and over** only (or 16+ at most; Dinis decides). The app is for the staff of organisations managing agents, per riskmandate.ai ("Agent Behaviour Policies for the AI agents in Gmail, HR, payroll, CRM and meeting apps"). Not children: do not tick any age group under 13 (a child audience brings the Families policy and extra rules).
- Does the app appeal to children? No: it has no cartoon characters, games or child-oriented content.
- Store listing must not attract children unintentionally; `listing.md` does not.

## Ads

"Does your app contain ads?" **No.** No ad SDK in `app/build.gradle.kts`; no ads in any screen (`HomeScreen.kt`).

## App access

"All functionality is available without special access" (no login, no restricted area, no QR code to scan, no demo account). The Play Console Requirements policy (3.3) asks for a demo account only if the app needs sign-in; this one does not. The review team will open the button and reach riskmandate.ai, which needs no sign-in for the public pages the app opens by default (`https://riskmandate.ai/`).

## Other App content declarations (all "No" or "not applicable")

- Government app: No.
- Financial features (loans, banking, crypto): No. (An organisation account is required for those; not for us.)
- Health: No.
- News app: No.
- COVID-19: not applicable.
- Advertising ID: the app does not use it (declare "No"). With target SDK 33 and above Play asks if the app uses the advertising ID; the manifest has no `com.google.android.gms.permission.AD_ID` entry of its own and no ad library adds it. Verify on the first uploaded AAB: the Console reports the merged manifest.
- Data deletion URL: not applicable (no accounts).
- Foreground services, exact alarms, accessibility, SMS, call log, all files access, VPN, photo/video permissions: none; the manifest asks for none of these.
- Target API level: 35 (`app/build.gradle.kts`). Play's rule for new apps and updates depends on the date; the app is on the current level as of the repository's date. Not verified against Google's table today.

## Review risk, in plain words

The Data safety form and policy are easy; the likely friction is functionality: the app's only action is opening a website. See the review-risk paragraph in `README.md` step 7.
