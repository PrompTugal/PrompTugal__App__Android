# Privacy policy: RiskMandate for Android (draft)

Draft for Dinis, 8 November 2026. Not legal advice and not reviewed by a lawyer. Everything below is checked against the code of ai.riskmandate.app (file paths in `data-safety.md`). To publish: host the text between the lines at a public HTTPS URL that needs no login, is not a PDF and works on a phone. Suggested: `https://android.app.promptugal.ai/privacy` (the app's own home), or a page on `https://riskmandate.ai/` (the site already has `/privacy`; it describes the website, not the app, so the app needs its own page or a new section there). Then enter the URL in Play Console (App content, Privacy policy) and in the store listing. TODO for Dinis: choose the URL.

Before publishing, replace every `[TODO]`. The publisher/company name is Dinis's decision (which company owns the Play Console account and sells the app).

---

# Privacy policy: RiskMandate for Android

Last updated: [TODO: date of publication]

**Who we are.** The RiskMandate app for Android (package `ai.riskmandate.app`) is published by [TODO: company name, registered address]. Contact: [TODO: a role email address for privacy questions].

**In short.** The app collects no personal data. It has no accounts, no analytics, no advertising and no third-party software that sends data anywhere. It sends nothing to us or to anyone else.

## What the app does

The app shows one screen. A button opens https://riskmandate.ai/ (or another riskmandate.ai page) in your web browser. The app also tells you whether an organisation manages your device and shows a label your organisation may have set.

## What the app collects

Nothing. The app does not collect, transmit, store, share or sell personal or sensitive data. It does not ask for your name, email address, location, contacts, photos, files, microphone, camera or any account.

The only permission it asks for is internet access. The app declares the permission so that it can hand a riskmandate.ai address to your browser; the app does not itself download or upload any content.

The app keeps no files, no database and no preferences of its own, and the app's data is excluded from Android's cloud backup and device-to-device transfer.

## Managed configuration

If your device is managed by an organisation (for example by your employer), that organisation can set two values for the app: the riskmandate.ai page the app opens (`start_url`) and a label for the device (`device_label`). Android delivers these values to the app on your device. The app reads them, shows the label on its home screen and opens the page. The values stay on your device: the app does not send them anywhere, and does not keep a copy beyond what Android already holds. Your organisation, not us, chooses these values and can see them in its own management console. The app can also tell whether your device is managed (a fully managed device or a work profile); it shows this on the screen and does not send it anywhere.

## The website the app opens

When you tap the button, your browser opens riskmandate.ai. From that moment the website, not the app, handles what happens. riskmandate.ai has its own privacy page at https://riskmandate.ai/privacy, which says that the site has no analytics, no cookies and no advertising pixel, and describes what its forms and its hosting do. The app only allows https addresses on riskmandate.ai and www.riskmandate.ai.

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

1. Every sentence is true of the code at the date above. Re-check this policy on each release that adds a dependency or a permission; CI could grep the manifest for new permissions (a follow-up).
2. "Excluded from Android's cloud backup" is `app/src/main/res/xml/data_extraction_rules.xml` and `android:allowBackup="false"` in `AndroidManifest.xml`.
3. If a lawyer adds GDPR wording (controller, legal basis, retention, the right to complain to a supervisory authority such as Portugal's CNPD), the facts above still hold; the lawyer adds, nothing here needs withdrawing.
4. The policy must be reachable without login, not geo-fenced, and not a PDF (Play's requirements, from my knowledge; the Console also checks the link).
