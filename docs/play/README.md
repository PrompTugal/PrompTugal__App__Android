# Google Play: the path for RiskMandate, step by step

For Dinis. Written 8 November 2026 by dev.android.play@promptugal.ai. The app is `ai.riskmandate.app` (decided), open source (Apache 2.0), to be sold eventually. It is the app the team learns Android publishing on; the PrompTugal app follows and starts from this file.

Nothing here has been done. No account exists, nothing was submitted, nothing was sent. Every step marked **Dinis** needs a person with a Google account and a payment card.

Companion files: `listing.md` (store text and graphics), `privacy-policy.md` and `privacy.html` (the policy, as a page to host), `data-safety.md` (Data safety, IARC, audience), `open-source.md`, `minimal-functionality.md` (review risk and feature proposals), `store-assets.md`, `release-notes.md`, `tester-guide-firebase.md` (M1), `tester-guide-m3.md`, `device-test-checklist.md`.

## What I verified and what I could not

Verified on 8 November 2026 by fetching the pages (they answered 200):

- Play Console Help, "Get started with Play Console" (answer 6112435): US$25 one-time fee; personal or organisation account; identity verification may ask for a government ID and a credit card under the legal name, and the fee is not refunded if the information is invalid; prepaid cards not accepted; 18 or older.
- "Choose a developer account type" (13634885): an organisation account needs a D-U-N-S number; getting one from Dun and Bradstreet is free (beware of third parties that charge).
- "Play Console Requirements" (10788890): organisation details must match the Dun and Bradstreet profile; a privacy policy and the Data safety form are required before submitting; app package names must be registered in Play Console to comply with Android developer verification.
- "App testing requirements for new personal developer accounts" (14151465): personal accounts created after 13 November 2023 must run a closed test with at least 12 testers opted in continuously for at least 14 days before applying for production access. Internal testing has no requirement; closed testing needs the app setup completed.
- "Device verification requirements for new developer accounts" (14316361): new personal accounts must verify access to a real Android device with the Play Console mobile app before publishing.
- "Create and set up your app" (9859152): the Create app fields (name, package name, default language, app or game, free or paid, contact email, declarations, Play App Signing terms).
- "Use Play App Signing" (7384423): Google holds the app-signing key; you hold an upload key.
- "Publish private apps from the Play Console", Managed Google Play Help (answer 6145139): the Managed Google Play tab under Release, Setup, Advanced settings; up to 1000 organisation IDs per app; only an AAB and a title are required; the Organization ID comes from the Managed Google Play store, Admin settings. **It states: once an app is restricted to organisations it is private and available to those organisations only; to make it public you must publish a new app with a different package name.** See step 8: this changes the advice in `docs/lessons.md` and `devices/README.md`.
- Google Play Developer API "getting started" (developers.google.com/android-publisher/getting_started): create a Cloud project, enable the Google Play Developer API, create a service account in Cloud, then invite its email in Play Console, Users and permissions, and grant rights. It also says you no longer need to link the developer account to a Cloud project.
- Store listing asset rules (answer 9866151): see `listing.md`.
- Data safety (10787469): definitions of "collect" and "ephemeral"; see `data-safety.md`.

Could not verify (pages I did not find, or that need a signed-in Console):

- The exact Play Console permission names for a release-only service account (steps 9 and 10 give my best reading; confirm in the Users and permissions screen).
- The current IARC questionnaire wording (it is inside Play Console, behind sign-in). `data-safety.md` gives answers by topic, not by exact question text.
- Payments profile, pricing and country availability pages (step 12 is from my own knowledge of the Console, not read today).
- Whether an app in an internal or closed testing track can be pushed to managed devices through managed Google Play without the Managed Google Play organisation tab. I believe closed tracks can be offered to managed users in some set-ups, but I found no page that says so. Treat it as unknown.
- Whether the "12 testers, 14 days" rule is the whole story for an organisation account: the pages read say the rule is for personal accounts only (accounts created before 13 November 2023 are exempt, organisation accounts are not mentioned). Confirm in Console after registering.
- Any trader-status requirement details (there is a "Trader status declaration" page in the help index; I did not read it). It matters for selling in the EU.

## Re-check of "could not verify", 8 November 2026 (play.android@promptugal.ai)

Each item below was read on support.google.com on 8 November 2026 (via a fetch tool that returns excerpts) and is settled, partly settled or still open.

| Item | Result | Source |
|---|---|---|
| Service-account permission names | **Settled.** Use app-level **"Release apps to testing tracks"** (upload drafts, create, edit and roll out testing-track releases; "does not allow production publishing") and, only if CI must change tracks or testers, **"Manage testing tracks and edit tester lists"**. Production is the separate **"Release to production, exclude devices, and use Play App Signing"**: keep it off. A release-notes edit is covered by the first one for apps not active in production. Steps 9 and 10 stand. | https://support.google.com/googleplay/android-developer/answer/9844686 |
| Free cannot become paid | **Settled, and it matters.** "Once your app has been offered for free, the app can't be changed to paid." "You can change your app from paid to free." "If you want to charge for the app, you need to create a new app with a new package name and set a price." The Create-app page itself says only "Specify whether your application is free or paid". So if `ai.riskmandate.app` is ever to be sold as a paid app, it must be created as **Paid** now, or it can never charge. Choosing Free spends the ID for selling. | https://support.google.com/googleplay/android-developer/answer/6334373 |
| Internal testers limit | **Settled.** "An internal test can have up to 100 testers per app." "Internal tests might not be subject to standard Play policy or security reviews." A new bundle "becomes available to testers within minutes." First-time uploads show temporary app details "for up to 48 hours". | https://support.google.com/googleplay/android-developer/answer/9845334 |
| Internal or closed track to managed devices | **Partly settled.** Closed tests: "With a closed test, you can choose which organization can access your track"; turn on the Managed Google Play option in the app's Advanced settings, and "Enabling this feature makes the app private, which means it is no longer searchable on the public Play Store." For a private app you must also add the organisation to the targeted list. The page does **not** mention organisations or managed Google Play for **internal** tests, which use email lists; "Enterprise targeting used to publish private apps" is not supported for additional closed tracks. So option C in step 8 is not confirmed for internal tests, and for closed tests it turns on the private flag, which per the Managed Google Play Help is permanent (step 8). Still do not pick the iframe route. | https://support.google.com/googleplay/android-developer/answer/9845334 |
| 12 testers for 14 days: whose rule | **Partly settled.** The page says it applies to "personal developer accounts created after November 13, 2023" and does not mention organisation accounts. Whether an organisation account has the same gate is **not stated**; confirm in the Console. | https://support.google.com/googleplay/android-developer/answer/14151465 |
| Trader status | **Settled, and newer than my notes.** "All active developer accounts must declare whether they are a trader or non-trader", regardless of where apps are distributed (non-EEA/UK developers may declare non-trader). "This declaration must be completed by February 1, 2027"; new accounts declare at creation. Trader means "any natural or legal person acting for purposes relating to their trade, business, craft or profession" (profit motive, volume, business set-up and legal status are the criteria; a paid, ad-sponsored or in-app-purchase app points to trader). From **1 March 2027** Google shows trader contact details publicly (a D-U-N-S number in "App support", Terms of Service link if given; for personal accounts a phone number and address) and in the EEA and UK the trader name replaces Google in "Offered by". Where: Manage account, Account details, About you; and confirm per app under App content. A Google policy announcement dated 29 October 2026 says that from November 2026 the declaration is required in Console (read in a search snippet, not the page). Consequence for Dinis: the selling company's trader details will be public. Misdeclaring can lead to suspension. | https://support.google.com/googleplay/android-developer/answer/17594752 |
| Content rating (IARC) wording | **Still open.** The help page gives only the steps (Policy, App content, Start; enter an email; select a category; complete the questionnaire; review and submit; one draft at a time) and "Misrepresentation of your app's content may result in its removal or suspension." The questions are behind sign-in. `data-safety.md` stays by topic. | https://support.google.com/googleplay/android-developer/answer/9859655 |
| Payments profile, pricing, countries (step 12) | **Still open** except the free/paid rule above (the pricing page says "Play uses the price you enter as the base for calculating market-specific prices"). Payments profile, tax and refund pages: not read. | https://support.google.com/googleplay/android-developer/answer/6334373 |
| Policy on minimal functionality (step 7 review risk) | **Now read.** See `minimal-functionality.md`. | https://support.google.com/googleplay/android-developer/answer/9898783 |

Also confirmed again: Play Console Requirements section 3.4, "Register your app package names via Play Console to comply with Android developer verification requirements" (https://support.google.com/googleplay/android-developer/answer/10788890). Screenshot, icon and feature-graphic specs re-read: see `store-assets.md` (it corrects the minimum-two-screenshots rule and the tablet rule in `listing.md`).

Still open after this re-check: the IARC wording; whether an organisation account has the 12-tester gate; whether an internal-track build can reach managed devices; payments profile, tax and refunds.

## 0. Decisions before step 1 (Dinis)

1. **Which company or person owns the developer account** (organisation or personal), see step 1. It is shown on the store listing, owns the payments profile and cannot easily be moved later. TODO for Dinis.
2. **The package name question** (step 8): keep `ai.riskmandate.app` for the public, paid app, and give the private organisation build a different package name; or the reverse. TODO for Dinis.

## 1. Create the developer account (Dinis)

Go to the Play Console sign-up page with the Google account that will own it. Accept the Developer Distribution Agreement, pay US$25, choose the account type, verify identity.

| | Organisation | Personal |
|---|---|---|
| Needs | A D-U-N-S number (free from Dun and Bradstreet, allow time; details must match the D&B profile) | Legal name, address, government ID, card in the same name |
| Testing gate before production | None found in the pages read | 12 testers opted in for 14 continuous days in a closed test, then apply for production access; plus Android device verification with the Play Console mobile app |
| Shown on the listing | The organisation's verified name and address | Your legal name and address |
| Selling | Payments profile owned by the organisation | Allowed, per the account-type page (personal and organisation both monetise through a payments profile) |
| Fit for us | Yes, if the selling company has a D-U-N-S | Only for a quick start; a public listing would carry Dinis's personal name and address |

**Recommendation (for Dinis to decide):** an organisation account in the name of the company that will sell the apps, because the app is to be sold, because the listing shows the verified name and address, and because it avoids the 12-tester gate. The cost is getting the D-U-N-S first. If the company has none, apply today (free) and meanwhile build and test with Firebase App Distribution (M1), which needs no Play account.

Allow up to 48 hours for registration to process (stated on the Managed Google Play page).

Two things the team should not do: no prepaid card, and no third-party service that charges for a D-U-N-S.

## 2. Create the app (Dinis)

Play Console, Create app. Fields (verified): app name as it should appear (`RiskMandate`, max 30 characters, see `listing.md`), package name `ai.riskmandate.app` (fixed for ever once created), default language (English, United Kingdom or United States, TODO for Dinis), App, Free or Paid, a contact email for users (TODO for Dinis: a role address, not a personal one), and the declarations (Developer Program Policies, US export laws, Play App Signing terms).

**Free or paid is the decision of this step.** Per Google's help (verified 8 November 2026, see the re-check above), "Once your app has been offered for free, the app can't be changed to paid"; a paid app can be made free. If RiskMandate the app is to be sold by itself, create it as Paid; if the app is a free companion to a service sold on riskmandate.ai (the website sells policies in pounds at the store, not through the app), create it as Free. TODO for Dinis.

Then work down the Dashboard's "Set up your app" tasks: privacy policy URL (`privacy-policy.md`), app access, ads, content rating, target audience, Data safety (`data-safety.md`), government apps, financial features, health, and the store listing (`listing.md`). Several of these must be complete before the first release in a closed or production track; internal testing does not wait for the store listing, per the testing page ("Internal testing... before or after completing app setup").

## 3. Play App Signing (Dinis, once)

Accept the Play App Signing terms (step 2). Use Google-generated app-signing key (the default and the recommendation: Google protects it in its key management service, and losing an upload key can be reset, losing an app-signing key cannot).

Make the upload key on Dinis's own machine with `scripts/make-upload-key.sh`. It stays off the repository. Dinis then adds four repository secrets (`UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`, see `docs/ci.md`). Agents never see them.

Consequence already recorded in `docs/lessons.md`: a build from Firebase App Distribution is signed with the upload key; a build from Play is signed by Google. A device must uninstall one before installing the other.

## 4. Upload the first AAB by hand (Dinis)

Why by hand: the Play Developer API can only upload to an app that already exists, and for a new app the first release is usually made in the Console. The CI step in `docs/ci.md` says the same ("the Play app must already exist with the first upload done by hand if the Console requires it"). I did not find a Google page that says this is mandatory; do it by hand and the question does not matter.

1. In CI, take the signed AAB from the `riskmandate-<version>` workflow artifact of a run on `dev` (it exists only once the four upload-key secrets are set; before that the AAB is `-unsigned` and Play will not accept it).
2. Console, Test and release, Testing, Internal testing, Create new release. Accept Play App Signing if asked, upload the AAB, release name (default is fine), release notes, Save, Review release, Start rollout to internal testing.
3. The first upload registers the package name's signing: Play records the upload key's certificate. From now on every upload must be signed by that same upload key.
4. `versionCode` is the CI run number, so every later upload is higher than the one before. A manual upload of a build that CI also uploads would waste a number only if you reuse one; do not re-upload the same code.

## 5. Internal testing track and testers (Dinis, then CI)

Internal testing has no requirement (verified). Console, Internal testing, Testers tab: create an email list (for example `team`), add the Google account emails of the people who will test (up to 100 internal testers, from my memory; TODO verify), copy the opt-in link and send it to them. The tester opens the link on a device signed in with that account and installs from Play.

Internal builds are available to testers within minutes, from my experience, with no review. CI then uploads each `dev` push here as a draft release (`docs/ci.md`: track `internal`, `status: draft`; a person completes the rollout in the Console, so nothing reaches testers unattended).

## 6. Closed testing (only if the account is personal)

If step 1 is a personal account: closed testing track, at least 12 testers opted in, hold for 14 continuous days, then Dashboard, Apply for production access (answer the questions about the app, the test and readiness). Plan three weeks, not three days. Organisation accounts: not mentioned in the pages read; confirm in Console.

## 7. Before any public release (Dinis, Console tasks)

- Privacy policy hosted at a public URL (`privacy-policy.md`).
- Data safety form (`data-safety.md`), content rating, target audience, ads, app access.
- Store listing text and graphics (`listing.md`). The real icon waits on the brand.
- Register the package name for Android developer verification (stated as a requirement in the Play Console Requirements policy; the Console shows how).
- The Play review for a first app on a new account can take up to seven days or longer (stated on the Publish your app page). Plan for it.
- **A review risk to know about:** the app's native screen is one button that opens a website. Google's Spam, Functionality and User Experience policy (the Policy Center index lists it) is aimed at apps with minimal function. I did not read that policy today. The app also shows device-management status and reads managed configuration, which is function beyond a bare link, but a reviewer may still ask. The honest listing text in `listing.md` says what the app does and nothing more. If review rejects it, the first answer is to say so truthfully, not to dress it up.

## 8. The private app for our managed Google Play organisation (Dinis)

Goal (M2/M3): force-install the app on managed tablets through the Android Management API (`devices/README.md`).

Two routes exist:

**a. Play Console private app** (Release, Setup, Advanced settings, Managed Google Play tab, Add organisation with its Organization ID, up to 1000). Steps verified from Google's page. Get the **Organization ID** from the managed Google Play store, Admin settings, Organization information (created in `devices/README.md` step 3). Publish via a production release; Google's docs put the route at about 2 hours (per `devices/README.md`).

**b. The managed Google Play iframe, private apps tab.** Quicker (about 10 minutes), but per Google and per `devices/README.md`: it silently creates a Play Console account for the enterprise, and **apps published this way can never be made public**. It also blocks selling. **Do not use this route** for an app that will be sold.

**An important correction to our own notes.** `docs/lessons.md` and `devices/README.md` say the Play Console route is safe for a later public listing, with "I believe the same holds but did not confirm". Google's Managed Google Play Help says the opposite: once an app is restricted to organisations it is private to them, and to make it public you publish a **new app with a different package name**. So:

| Option | What it means | Good | Bad |
|---|---|---|---|
| A. Private build uses `ai.riskmandate.app`, public app later gets a new ID | The ID we chose is spent on the private build | Tablets work earliest; the managed route is simple | The public paid app needs another ID (for example `ai.riskmandate.store`); two apps, two histories, no reviews or installs carry over |
| B. Private build uses a different ID (for example `ai.riskmandate.app.managed`); `ai.riskmandate.app` is kept for the public app | The decided ID stays for the paid app | The public app keeps the clean ID | The code needs a build flavour or `applicationId` suffix (a small change in `app/build.gradle.kts`: `applicationIdSuffix` in a flavour); two Play apps to maintain from the start; managed configuration keys and policy `--package` must name the private ID |
| C. Do not restrict the app to organisations: release it publicly (or on an internal/closed track) and distribute to tablets as an ordinary Play app approved in managed Google Play | One app, one ID, can be sold | No second app | Unverified: I found no page confirming that a closed or internal track app can be approved for an organisation's devices, and a public app is visible to everyone while it is free; not "private" |

**Recommendation (for Dinis to decide):** B. It keeps the decided ID for the thing that will be sold, and it costs one build flavour. Revisit if Google's answer on option C turns out to be yes. If the team prefers speed over purity for the first tablet test, A is acceptable provided Dinis accepts that `ai.riskmandate.app` then stays private for good.

Do not pick the iframe route in any case.

The policy's `__PACKAGE__` and the CI `PLAY` track upload read the package from the build, so either option needs no workflow change except the flavour.

## 9. The service account for CI (Dinis, in two consoles)

Purpose: let the workflow upload the signed AAB to the app's internal track as a draft. Nothing else.

1. Google Cloud Console: use or create a project; enable **Google Play Android Developer API**. No link between the Cloud project and the developer account is needed any more (verified, Google's getting-started page).
2. Cloud, IAM and admin, Service accounts: create `play-ci` with **no Cloud IAM roles** (it does not need any). Create one JSON key. The download is the only copy.
3. Play Console, Users and permissions, Invite new users: the service account's email. Give **app-level permissions on RiskMandate only, not account-level**: the release permissions only (releasing to testing tracks; do not grant production release, financial data, orders, app access management or user management until needed). I did not find the exact permission labels today; the Console shows them as checkboxes under "App permissions" when the app exists, so add the user after step 2 of this file. Google says only "grant the necessary rights" for release actions; the Billing permissions (view financial data, manage orders and subscriptions) are only for the Billing APIs and are **not** needed here.
4. Dinis adds the JSON file's whole content as the repository secret `PLAY_SERVICE_ACCOUNT_JSON`, then deletes the local copy or keeps it off the repository. `*service-account*.json` is gitignored but do not rely on it.
5. Permissions can take a short while to take effect (an older Google note says up to 24 hours; not verified today).

Least privilege check: the CI draft release does not publish, so even a leaked key can only create draft releases on the internal track of this one app, not publish to production. Keep the Production release permission off the account until the app is public, and then think again.

## 10. First automated upload (CI)

After steps 3 to 9: a push to `dev` runs `check`, then `release`, which signs, uploads to the internal track as a draft (`docs/ci.md`). Dinis completes each rollout in the Console until he decides to automate completion. A mismatched upload key, a duplicate `versionCode`, a missing first manual upload or a service account without the app permission each show up as a FAILED line in the job summary.

## 11. Managed devices (M2/M3)

After step 8: `devices/README.md` steps 5 to 8. The app must be published to the organisation (or otherwise approvable) before `policy-apply`.

## 12. Later: selling the app (Dinis)

All from the Console; I read none of these pages today, so check each against the current help.

1. **Payments profile** (a merchant account): created in the Console under Setup, Payments profile (or at first paid app/in-app product). It is owned by the selling company, carries its legal name, address and bank account for payouts, and a tax profile. Needs the legal entity decided. TODO for Dinis: which company sells.
2. **Free or paid, and the price.** Paid apps need the payments profile set up and the app created as Paid (see step 2). Pricing is set per country; Play suggests local prices from a base price. The website sells policies at GBP 10, 50, 500 and 1,500 at its own store; whether the app sells anything, or is free and sells nothing, is a decision for Dinis (and what the app may promise about RiskMandate stays limited to what riskmandate.ai says).
3. **Countries.** Production, Countries/regions: choose where to offer the app. Selling to EU users brings the EU trader declaration (a "Trader status declaration" page exists in the Help index; read it) and tax and consumer rules for the selling company; Portugal-based sellers will meet VAT on sales. TODO for Dinis with an accountant.
4. **Refunds and support.** A paid app needs a contact route and a refund policy; Google has its own refund rules. Not read.
5. **Open-source**: see `open-source.md` before charging for an Apache 2.0 app.

## Checklist: who does what

| # | Step | Who |
|---|---|---|
| 0 | Decide the owning company, the package-name plan (A or B), free or paid | Dinis |
| 1 | Get a D-U-N-S (organisation) and create the Play Console developer account, pay US$25, verify identity | Dinis |
| 2 | Run `scripts/make-upload-key.sh` on his own machine | Dinis |
| 3 | Add the four `UPLOAD_*` repository secrets | Dinis |
| 4 | Create the app in Play Console (ID, name, language, free or paid, contact email) | Dinis |
| 5 | Host `privacy-policy.md` at a public URL; fill in the publisher name | Dinis |
| 6 | Fill Data safety, content rating, audience, ads, app access from `data-safety.md` | Dinis (an agent can prepare, not submit) |
| 7 | Store listing text and graphics from `listing.md`; the real icon from the brand | Dinis, designer for graphics |
| 8 | Build with the upload-key secrets set and download the signed AAB | CI |
| 9 | Upload the first AAB to internal testing by hand | Dinis |
| 10 | Create the tester list and send the opt-in link | Dinis |
| 11 | Decide the private-app route and, for option B, add the build flavour | Dinis decides, dev.android implements, CI builds |
| 12 | Add the organisation ID (Managed Google Play tab) and publish privately | Dinis |
| 13 | Create the Cloud service account and the Play Console permission; add `PLAY_SERVICE_ACCOUNT_JSON` | Dinis |
| 14 | Every later push to `dev` uploads a draft to the internal track | CI |
| 15 | Complete each rollout in the Console | Dinis |
| 16 | Personal account only: 12 testers for 14 days, apply for production | Dinis and testers |
| 17 | Production release, countries, pricing, payments profile, trader declaration | Dinis |
