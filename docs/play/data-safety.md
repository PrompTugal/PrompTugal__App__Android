# Play Console: Data safety, content rating, audience, ads, app access

Draft answers for Dinis to enter, 8 November 2026. Nothing is submitted. I could read Google's Data safety help (answer 10787469) but not the Console forms (they need sign-in), so the questions below follow the form's structure and the help's definitions, not its exact wording. Re-check each against the live form.

## How the answers were reached

Google's definition (verified): "Collect" means transmitting data from your app off a user's device, including data sent by libraries and SDKs. Data from a webview the developer controls counts too; a page the user navigates on the open web does not. The app does none of these:

| Check | Evidence |
|---|---|
| Permissions | `app/src/main/AndroidManifest.xml` declares only `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE` (both normal permissions; the second is used by `Browser.isOffline` only to read whether a network with internet capability exists, for an offline hint; nothing is sent). The `<queries>` entry (Custom Tabs service intent) is package visibility, not a permission. |
| No network code | No `HttpURLConnection`, OkHttp, Retrofit, WebView or socket use in `app/src/main`. The only outward act is handing a URL to the browser: `Browser.kt` (`CustomTabsIntent.launchUrl`, or `Intent.ACTION_VIEW`). The app opens riskmandate.ai in the user's browser, which is the browser's traffic, not the app's. No WebView exists, so Google's webview clause does not apply. |
| No SDKs | `app/build.gradle.kts` dependencies: AndroidX (core, activity, lifecycle, browser), Compose and Material 3, plus test libraries. No Firebase, analytics, ads, crash reporting or Google Play services library. (Firebase App Distribution is a CI delivery step, not in the app.) |
| No storage | Search of `app/src/main` found no SharedPreferences, DataStore, database, file writing or logging. The app reads managed configuration and device state at runtime and holds them in memory (`MainActivity.kt`). |
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

The result on the store page: "No data collected" and "No data shared with third parties". This is only true while the code stays as above. Any release that adds a dependency or permission must re-open this file first.

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
