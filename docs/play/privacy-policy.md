# Privacy policy: RiskMandate for Android (draft)

Draft for Dinis, 8 November 2026. Not legal advice and not reviewed by a lawyer. Everything below is checked against the code of ai.riskmandate.app (file paths in `data-safety.md`). To publish: host the text between the lines at a public HTTPS URL that needs no login, is not a PDF and works on a phone. Suggested: `https://android.app.promptugal.ai/privacy` (the app's own home), or a page on `https://riskmandate.ai/` (the site already has `/privacy`; it describes the website, not the app, so the app needs its own page or a new section there). Then enter the URL in Play Console (App content, Privacy policy) and in the store listing. TODO for Dinis: choose the URL.

Before publishing, replace every `[TODO]`. The publisher/company name is Dinis's decision (which company owns the Play Console account and sells the app).

---

# Privacy policy: RiskMandate for Android

Last updated: [TODO: date of publication]

**Who we are.** The RiskMandate app for Android (package `ai.riskmandate.app`) is published by [TODO: company name, registered address]. Contact: [TODO: a role email address for privacy questions].

**In short.** The app collects no personal data. It has no accounts, no analytics, no advertising and no third-party software that sends data anywhere. The app sends nothing to us. Its in-app view of riskmandate.ai loads pages from that website the way a browser does, which the website's host sees as an ordinary web request (see "The in-app view of riskmandate.ai"). A few settings stay on your device and are never sent.

## What the app does

The app shows a home screen. A button opens https://riskmandate.ai/ (or another riskmandate.ai page) in your web browser. The app can also show riskmandate.ai pages inside the app (the in-app view, with a bundled offline copy of the main pages), has an optional stand mode (it keeps the screen on, can pin the screen, and returns the app to its start screen after a period without touch), and shows a What's new screen for each version. It tells you whether an organisation manages your device, shows a label your organisation may have set, and has a Diagnostics screen (below).

## What the app collects

Nothing that identifies you. The app does not collect, share or sell personal or sensitive data, and it sends no data of yours to us or to anyone else. It does not ask for your name, email address, location, contacts, photos, files, microphone, camera or any account.

The app asks for two permissions, both ordinary ones that Android grants at install without a prompt: internet access, so that the app can hand a riskmandate.ai address to your browser and load riskmandate.ai's pages in its in-app view (the app connects to riskmandate.ai and www.riskmandate.ai, and to no other website), and network state, so that it can show a hint when your device appears to be offline. The app only checks whether a network connection exists; it does not read or keep your network name or address.

The app's data is excluded from Android's cloud backup and device-to-device transfer. What the app does keep on your device is described next.

## What stays on your device

The app keeps a few small items in its own private storage on your device. The app never sends them to us or to anyone else, other apps cannot read them, and Android removes them when you clear the app's data or uninstall the app. They are:

- **Stand mode's settings:** whether stand mode is on and how many idle minutes you chose (a number).
- **What's new:** the version of the app for which the What's new screen was last shown, so that it is shown once per version.
- **The in-app view:** whatever the web view inside the app stores while you read riskmandate.ai, that is, cookies and its web cache. The app deletes them, with the web storage and history of the view, when you close the in-app view. riskmandate.ai says that it sets no cookie (https://riskmandate.ai/privacy). The app switches off the web view's local (DOM) storage and third-party cookies.

None of this is personal data that we receive, and the app does not look at it for any other purpose.

## Managed configuration

If your device is managed by an organisation (for example by your employer), that organisation can set two values for the app: the riskmandate.ai page the app opens (`start_url`) and a label for the device (`device_label`). Android delivers these values to the app on your device. The app reads them, shows the label on its home screen and opens the page. The values stay on your device: the app does not send them anywhere, and does not keep a copy beyond what Android already holds. Your organisation, not us, chooses these values and can see them in its own management console. The app can also tell whether your device is managed (a fully managed device or a work profile); it shows this on the screen and does not send it anywhere.

## Diagnostics screen

A Diagnostics screen shows the app's version, the Android version, the make and model of the device, whether and how the device is managed, the managed configuration as received (`start_url` and `device_label`, and whether the start page was refused), and which app installed this app (for example Google Play or Firebase App Distribution). These values are shown on the screen and are put on your clipboard only when you tap Copy. The app never sends them anywhere. The screen shows no serial number, IMEI, Android ID, account or network address.

## Downloaded language model and chat (DRAFT: include only in the build where the model setup and chat are enabled)

On first use, the app can download a language model file once. The file comes from Hugging Face (huggingface.co, where Google AI Edge's litert-community publishes the Gemma models) [TODO dev.llm and security: name every host the download touches, including any content-delivery host Hugging Face redirects to]. The app checks the file against a fixed SHA-256 checksum before it uses it, and deletes it if it does not match. Downloading the file is an ordinary web request: Hugging Face sees your IP address and what it needs to serve the file, and its own privacy policy applies to that request. You start the download yourself, after the app tells you which host it will contact. The app sends no account, no message and nothing you typed with it.

After the download the model runs on your device and works offline. **Nothing you type into the chat leaves the device.** The chat history is kept on your device only, in the app's private storage, excluded from Android's cloud backup and device-to-device transfer. The app never exports or publishes it. A Clear history button deletes it; so does clearing the app's data or uninstalling the app. A Delete model button removes the model file.

## The in-app view of riskmandate.ai

Where the in-app view is used, the app shows riskmandate.ai pages in a web view inside the app. Loading a page sends an ordinary web request to riskmandate.ai. That site is hosted on GitHub Pages: its privacy page says GitHub's servers see "the ordinary request information any web server sees, including your IP address", and that "We have no analytics dashboard, no log access and no way to see who read what" (https://riskmandate.ai/privacy, page dated 24 September 2026). The app loads riskmandate.ai's pages in the view and nothing else: it adds no identifier, runs no analytics and blocks requests from the view to any other website. Only https pages on riskmandate.ai and www.riskmandate.ai load in the view. If you tap a link in the view that leads to another website, the app hands that https address to your device's own browser, which opens it outside the app; the app never loads it inside the app and does not open it while the screen is pinned (stand mode). The app sends nothing: the browser, under its own privacy policy, loads that other website, and the app does not see what it loads. Only https addresses are handed over; others (such as http, intent or file addresses) are refused. The site's Contact page, whose form posts to another host, is not part of the in-app view. When you are offline, the app shows a copy of the main pages bundled in the app, and sends nothing.

## The website the app opens

When you tap the button, your browser opens riskmandate.ai. From that moment the website, not the app, handles what happens. riskmandate.ai has its own privacy page at https://riskmandate.ai/privacy, which says that the site has no analytics, no cookies and no advertising pixel, and describes what its forms and its hosting do. The button and the in-app view allow only https addresses on riskmandate.ai and www.riskmandate.ai; the one exception is the link handover to your browser described above.

## Advertising, analytics and children

The app shows no advertising and contains no analytics, crash-reporting or advertising software. It is not designed for children and is not directed at them.

## Your rights

Because the app collects nothing, we hold no personal data about you from the app. For data held about you by riskmandate.ai, see its privacy page. [TODO Dinis: add the controller's rights wording if a lawyer asks for it; the app collects no data, so the minimal statement is accurate today.]

## Changes

If the app ever collects data, this policy will change first and the Play Store Data safety section will be updated before that release. The history of this page is public: [TODO: the repository URL, once Dinis decides the repository is public].

## Open source

The app's source code is published under the Apache License 2.0. [TODO: the repository URL.]

---

## Notes for Dinis (not part of the policy)

0. **R-stand-2 (9 November 2026):** the sections "What stays on your device" and "The in-app view" describe stand mode (`work/dev.stand`: `StandSettings`, SharedPreferences file `stand_mode`), the in-app view (`work/dev.site`: WebView, DOM storage off, third-party cookies off, other hosts blocked with 403, Contact page not a tab) and the What's new screen. **Neither branch was on `dev` when I wrote this**, and I found no What's new SharedPreferences code on any branch (the conductor's note is the source for that bullet) and no code that clears the web view's cookies or cache at the time; `work/dev.site` ba979fc now has `disposeSite()` (cache, history, cookies, web storage) called when the screen closes. Re-check every bullet against the merged code before this goes on a public URL.
0. **On-device LLM draft (9 November 2026):** the section "Downloaded language model and chat" is a DRAFT from `briefs/2026-11-09__on-device-llm/README.md` (decisions by Dinis, 9 Nov) and describes features G and H, which are placeholders with `enabled = false` on `dev`. Delete the section from any public copy unless the submitted build has both on, and re-check it against the code (hosts, checksum, backup exclusion, Clear history). Model licences: `model-licences.md`.
1. Every sentence is true of the code at the date above. Re-check this policy on each release that adds a dependency or a permission; CI could grep the manifest for new permissions (a follow-up).
2. "Excluded from Android's cloud backup" is `app/src/main/res/xml/data_extraction_rules.xml` and `android:allowBackup="false"` in `AndroidManifest.xml`.
3. If a lawyer adds GDPR wording (controller, legal basis, retention, the right to complain to a supervisory authority such as Portugal's CNPD), the facts above still hold; the lawyer adds, nothing here needs withdrawing.
4. The policy must be reachable without login, not geo-fenced, and not a PDF (Play's requirements, from my knowledge; the Console also checks the link).
