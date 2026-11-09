# devices/: milestone M2 (fully managed tablets)

M2: a factory-fresh Android tablet scans a QR code at the welcome screen, enrols as **fully managed** through the Android Management API (AMAPI), and gets the RiskMandate app force-installed and kept up to date as a **private app on managed Google Play**.

What is here, built before the Google Cloud project, the Play Console account and any secret exist:

| File | What it is |
|---|---|
| `amapi.mjs` | A zero-dependency Node 18+ CLI for the AMAPI calls M2 needs. Every command has `--dry-run`. |
| `qr.mjs` | A dependency-free QR encoder (byte mode, versions 1 to 40, levels L to H) writing SVG and PNG. `enrol` uses it; `amapi.mjs qr --in <file>` draws any text file. |
| `policies/riskmandate-fully-managed.json` | The device policy. `__PACKAGE__`, `__START_URL__` and `__DEVICE_LABEL__` are filled at apply time; keys starting with `_` are notes, stripped before sending. |
| `policies/riskmandate-hardened-options.json` | The same policy plus every hardening option from the security review. **Not applied.** See Hardening options. |
| `schema/androidmanagement-v1.trimmed.json`, `trim-discovery.mjs` | A trimmed copy of the AMAPI discovery document (revision 20261104), and the script that makes it from the full one. |
| `test/` | Tests (`node --test devices/test/*.test.mjs`): the CLI, the QR encoder, and every policy field against the discovery document. No network, no real credentials. |
| `REHEARSAL.md` | The M2 rehearsal: the exact sequence on the day, what each step looks like on the tablet, and how to recover. |
| `out/` | Created by the CLI; holds enrolment and web-token files. Gitignored. Never commit, share or paste it. |

The application ID is provisional (`ai.riskmandate.app` today) and the private organisation build should get its own package ID (see step 4), so it is a parameter everywhere (`--package <private-build-application-id>`), never hard-coded in the policy.

Docs read for this, on 8 November 2026: the AMAPI discovery document (`androidmanagement.googleapis.com/$discovery/rest?version=v1`) for every field and enum, and developers.google.com/android/management pages `create-enterprise`, `service-account`, `provision-device`, `apps`, `permissible-usage`, `quickstart`, `policies/fully-managed-devices` and `manage-custom-apps`.

## Before anything: two things Dinis must know

1. **AMAPI's permissible-usage policy.** Google restricts AMAPI to commercial EMM developers, Device Trust providers and OEMs, and lists as **not allowed**: "Solutions developed and used exclusively for first party in-house applications". PrompTugal managing its own tablets for its own app looks close to that line. The quickstart (a Gmail account and a Cloud project) works for testing, but production use under this policy is a question for Dinis, not something the agent can settle. The alternative that sits clearly inside the rules is Google Workspace (or Cloud Identity) device management, or a commercial EMM; see the comparison at the end.
2. **Device quota needs a request.** Per `permissible-usage`: "To request an initial quota of up to 500 devices, you will need to provide a full business justification", via Google's form; over 500 needs an application to Android Enterprise. Quota is granted for at most 2 projects per developer. Default rate limits: 1000 queries per 100 seconds per project, and 60 per minute per resource (same action, enterprise and policy or device). There is no price on these pages: the API is not billed, but it is not open-ended either. I could not verify how many devices a fresh project can enrol before the quota request is granted (the quickstart says "Request initial device quota" as a step, which suggests zero or very few).

## The runbook (Dinis does the steps marked **Dinis**)

Every command below can be run first with `--dry-run` to see the exact request; that needs no credentials.

### 1. Google Cloud project (**Dinis**)

1. Create or choose a project at console.cloud.google.com (a dedicated one, e.g. `promptugal-devices`, keeps the key's reach small). Note the **project ID**.
2. APIs and services, Library: enable **Android Management API**.
3. Request the initial device quota (link on the `quickstart` and `permissible-usage` pages), with the business justification.

### 2. Service account and key (**Dinis**)

1. IAM and admin, Service accounts: create one, e.g. `amapi-cli`.
2. IAM: grant it the role **Android Management User** (`roles/androidmanagement.user`), which carries `androidmanagement.enterprises.manage`. Nothing broader.
3. Keys: add a JSON key. The download is the only copy. Keep it **off the repository** (for example `~/.config/promptugal/amapi-service-account.json`, mode 600). `*service-account*.json` is gitignored, but do not rely on that: keep it outside the checkout.
4. In the shell where the CLI runs:
   ```
   export GOOGLE_APPLICATION_CREDENTIALS=~/.config/promptugal/amapi-service-account.json
   ```
   The CLI signs a short JWT with the key, exchanges it at oauth2.googleapis.com for a one-hour token with scope `androidmanagement`, and never prints or writes either.

### 3. Create the enterprise (**Dinis**, about 10 minutes)

```
node devices/amapi.mjs signup-url --project <project-id> --callback https://promptugal.news/
```

It prints a `name` (`signupUrls/C...`) and a `url`. Open the url in a browser and sign up. **The account choice:**

- **A plain Google account (Gmail)** creates a *managed Google Play Accounts enterprise*. Fastest; fine for a test. One Gmail account can be bound to only one enterprise at a time (to reuse it, delete the organisation at play.google.com/work, Admin settings).
- **A Google Workspace or Cloud Identity admin account** binds the enterprise to that domain (a *managed Google domain*). Better for the long term (several admins, audit, the same identity as the rest of the team's Google estate) and the route if Workspace device management is chosen later. Which account owns PrompTugal's device enterprise is a **Dinis decision**.

At the end Google redirects to the callback URL with `?enterpriseToken=...` appended. The callback page does not need to do anything; copy the token from the address bar. Then:

```
read -r -s -p "enterprise token: " AMAPI_ENTERPRISE_TOKEN; export AMAPI_ENTERPRISE_TOKEN; echo
export AMAPI_SIGNUP_TOKEN=signupUrls/C...
node devices/amapi.mjs enterprise-create --project <project-id> --name "PrompTugal Devices"
```

The two values go in environment variables, not on the command line, so they stay out of shell history and `ps`. `--enterprise-token`, `--signup-url-name` and `--signup-token` (an alias) still work but print a warning; the environment variable wins when both are set. It prints `enterprises/LC0...`: the enterprise name used from now on. (The API calls the first value `signupUrlName`.)

### 4. Publish RiskMandate as a private app (**Dinis**)

The app must be on managed Google Play before a policy can install it. Two routes:

**a. Play Console (needed anyway for Play App Signing):**
1. In Play Console, create the app with the application ID chosen for the private organisation build. Per Google's Managed Google Play Help (answer 6145139), an app restricted to organisations stays private for ever; a public listing later needs a new package name. So this build should use its own package ID, separate from any future public app (for example an `applicationIdSuffix` flavour). Dinis decides; see `docs/play/README.md`, step 8.
2. Enrol it in **Play App Signing** and upload the AAB that CI builds, signed with the upload key (`scripts/make-upload-key.sh`). Private apps are distributed through Play, so the AAB must be signed this way.
3. Advanced settings, Managed Google Play: turn it on and add the enterprise's **Organization ID** (managed Google Play, Admin settings, Organization information). Publish to production; Google's docs put this route at about 2 hours, against about 10 minutes for the iframe.

**b. The managed Google Play iframe (quicker, about 10 minutes per Google's docs):**
```
node devices/amapi.mjs web-token --enterprise enterprises/LC0... --parent-frame-url https://<a page you host>
```
The web token and the iframe URL go to `devices/out/web-token-*.json`. The iframe only loads inside a page served at exactly `parentFrameUrl` over HTTPS, using Google's snippet (`apis.google.com/js/api.js`, then `gapi.iframes.getContext().openChild({url: 'https://play.google.com/work/embedded/search?token=<web token>&mode=SELECT', ...})`). In its **Private apps** tab, upload the AAB and a title. Per Google: this silently creates a Play Console account for the enterprise, waives the 25 USD fee, and **apps published this way can never be made public**.

Either way, the package name is taken on Play for good, and a private (organisation-only) app can never become public under that name. **Dinis decision:** the final application ID of the private build before the first upload (`ai.riskmandate.app` is provisional); the recommendation is a separate package ID for it, so the plain ID stays free for a possible public app.

### 5. Apply the policy

```
node devices/amapi.mjs policy-apply --enterprise enterprises/LC0... --policy riskmandate \
  --file devices/policies/riskmandate-fully-managed.json --package <private-build-application-id> \
  [--start-url https://riskmandate.ai/] [--device-label <label>]
```

The CLI checks `--start-url` with the app's own rule before sending: `https`, host exactly `riskmandate.ai` or `www.riskmandate.ai`, no `@`, backslash or whitespace, port 443 only. `--device-label` is trimmed, cut to 64 characters and refused if it holds control characters.

```
```

This PATCHes the whole policy (no `updateMask`), so the file is the single source of truth. Re-run it after any change.

### 6. Create an enrolment token

```
node devices/amapi.mjs enrol --enterprise enterprises/LC0... --policy riskmandate --duration 3600s [--reusable]
```

It writes two files under `devices/out/` (the folder is set to mode 700 each time, the files are mode 600 and never overwrite an existing file) and prints only their paths:
- `enrol-<time>.json`: the token's name, expiry and the QR payload as JSON.
- `enrol-<time>.qr.txt`: AMAPI's own `qrCode` string, exactly as returned.
- `enrol-<time>.qr.png` and `enrol-<time>.qr.svg`: the QR code drawn by `qr.mjs` (a 537-byte payload with a 24-character stand-in token came out as QR version 16, level L, 81 modules a side; the real token is longer, so expect version 16 to 18; four-module quiet zone, 8 pixels a module in the PNG). Show the PNG full screen on a laptop at normal brightness, or print the SVG. If the tablet's camera will not focus on it, make it bigger, not brighter. To draw another text file the same way: `node devices/amapi.mjs qr --in <file> [--ecc L|M|Q|H]`.

These files are a credential to join the enterprise until they expire: do not commit, share or paste them. The token is **one-time by default** (`oneTimeOnly`): one device can enrol with it, so a photographed QR code is of little use afterwards. Add `--reusable` only for a deliberate batch of tablets (the CLI warns); keep `--duration` short either way (`--one-time` is still accepted and changes nothing).

The default `--personal-usage PERSONAL_USAGE_DISALLOWED_USERLESS` makes a fully managed device with no user account (right for tablets kept by the agent; per the API it is mandatory for dedicated devices). `PERSONAL_USAGE_DISALLOWED` instead requires someone to sign in with a work account.

### 7. Enrol the tablet (**a person with the tablet**)

1. If the tablet was ever set up (including with the M1 Firebase build), **factory-reset it**: Settings, System, Reset options, Erase all data. QR provisioning only works from the welcome screen of a new or reset device. A Firebase-distributed build is signed with the upload key, not the Play App Signing key, so Play cannot update over it (signature mismatch): it must not be on the device, and a reset removes it.
2. On the welcome screen, **tap six times in the same spot**. A QR reader opens (Android 7+; on some older builds it first downloads the reader, so join Wi-Fi when asked).
3. Scan the QR code. Join Wi-Fi when asked. The tablet installs Android Device Policy, enrols as fully managed, applies `riskmandate`, and installs RiskMandate and Chrome from managed Play.
4. Fallback if the six taps do nothing: go through setup to the Google sign-in and type `afw#setup`, then scan the same QR code.

### 8. Check

```
node devices/amapi.mjs devices-list --enterprise enterprises/LC0... --package <private-build-application-id>
```

It shows each device's state, management mode (`DEVICE_OWNER` is fully managed), applied policy, model, Android version, last report and the app's installed version. A new CI build published to Play should reach the tablet without anyone touching it (`AUTO_UPDATE_HIGH_PRIORITY`); Google notes that can take up to 24 hours for widely deployed apps.

## The policy

`policies/riskmandate-fully-managed.json`, field by field:

- **RiskMandate**: `FORCE_INSTALLED` (installed without asking, cannot be removed), `AUTO_UPDATE_HIGH_PRIORITY` (updated as soon as possible, no constraints). `managedConfiguration` sets the app restrictions `start_url` and `device_label`, which the app worker is adding; the keys must match the app's restrictions exactly, or Play rejects the configuration.
- **Chrome** (`com.android.chrome`), force-installed: the app opens riskmandate.ai in a Custom Tab, which needs a browser, and `WHITELIST` mode would otherwise not keep Chrome updated from Play.
- **`playStoreMode: WHITELIST`** (the API's name for an allowlist, and its default): only apps in the policy come from Play, and any other Play app is removed. Chosen over `BLACKLIST` (everything allowed unless blocked) because these tablets exist to run one app, and whoever holds one should not be able to install others.
- **`statusReportingSettings`**: app, device-settings, software, hardware, memory, display and power reports on, so `devices-list` can show the app version and health. **Network info off**: it reports identifiers (IMEI, MAC) that M2 does not need.
- **`systemUpdate: WINDOWED` 02:00 to 05:00** device local time. Per the API this window also governs Play updates on the default mode (Chrome here); RiskMandate's high-priority mode ignores it.
- **`advancedSecurityOverrides`**: no installs from unknown sources, developer options off (flip to `DEVELOPER_SETTINGS_ALLOWED` for a debugging session), Play Protect verification enforced.
- **`autoDateAndTimeZone` enforced**, **`addUserDisabled`** and **`modifyAccountsDisabled`**: one managed, userless device.

Left out on purpose, as **Dinis decisions**:
- **Factory reset protection** (`frpAdminEmails`). Without it a reset tablet is free to enrol anywhere; with it, a reset tablet needs one of the listed Google accounts to sign in before it can be used, which protects a stolen tablet but can lock out a tablet whose admin account is lost. Which account(s), if any, is Dinis's call.
- **`factoryResetDisabled`**: left false, so a tablet can be reset and re-enrolled during testing.
- **A screen lock** (`passwordRequirements`): a userless tablet with an enforced PIN needs someone to hold the PIN.
- **Kiosk mode** (`installType: KIOSK`): locks the tablet to RiskMandate. Possible later; not asked for M2.

## Hardening options (not applied)

`policies/riskmandate-hardened-options.json` is the base policy plus every option from the security review (A-11), as one complete file. **It is not applied and `policy-apply` in the runbook still points at the base file.** It is there so Dinis can pick: copy the options he wants into the base file, or apply the whole file with `--file devices/policies/riskmandate-hardened-options.json`. Every field and enum in it was checked against the live schema (`test/policy-schema.test.mjs`); the notes beside each setting in the file say the same as the table below.

One finding from that check: three settings the review named are marked **deprecated** in the current API schema, so the file does not use them as the review wrote them. `usbFileTransferDisabled` is replaced by `deviceConnectivityManagement.usbDataAccess`; `tetheringConfigDisabled` by `deviceConnectivityManagement.tetheringSettings`; `passwordRequirements` by `passwordPolicies`. `safeBootDisabled` is also deprecated and **has no replacement in the schema**: it is kept (H3) and is the one deprecated field the test allows, with that reason written in the test.

| Id | Setting in the file | What it protects against | What it costs | Suggestion |
|---|---|---|---|---|
| H1 | `systemUpdate: AUTOMATIC` (base: windowed 02:00 to 05:00) | A security patch waiting up to a day for the window | An update may reboot the tablet while it is in use | Take it. A tablet that holds a session is better patched. |
| H2 | `factoryResetDisabled: true` | A thief wiping the enrolment from Settings | You cannot reset the tablet from Settings either; recovery then needs the API (delete the device) or recovery mode | **Leave off while rehearsing M2**, decide before tablets leave the building. Pair with `frpAdminEmails` (H10). |
| H3 | `safeBootDisabled: true` (deprecated) | Safe mode starting the tablet without managed apps | Google may stop honouring the field | Take it; it costs nothing today. |
| H4 | `usbDataAccess: DISALLOW_USB_DATA_TRANSFER`, `mountPhysicalMediaDisabled: true` | Copying data off or files onto the tablet by cable, SD card or stick | No file transfer by cable (charging works). `DISALLOW_USB_FILE_TRANSFER` is the milder choice. | Take it. |
| H5 | `passwordPolicies` (`COMPLEXITY_MEDIUM`, wipe after 10 wrong tries, lock every day), `keyguardDisabledFeatures` (camera, notification text) | Whoever picks the tablet up seeing the app and Chrome | A userless tablet needs a person who holds the PIN; the wipe after 10 tries means re-enrolment | Dinis decides: it is the control that matters most for a stolen tablet, and the one that adds a human step. |
| H6 | `bluetoothConfigDisabled`, `bluetoothSharing`, `tetheringSettings`, `deviceRadioState` (NFC, UWB, 2G off, minimum Wi-Fi security `PERSONAL_NETWORK_SECURITY`) | Short-range and radio side doors | No pairing of keyboards or headsets; no open or captive-portal Wi-Fi (check the rehearsal network is WPA2 or better first) | Take all but the Wi-Fi minimum until the network is known. |
| H7 | `screenCaptureDisabled`, `cameraAccess`, `microphoneAccess`, `locationMode: LOCATION_DISABLED` | Data leaving through screenshots, or sensors no one needs | No screenshots for bug reports; a later feature that scans or locates needs these lifted | Take camera, microphone and location off (the app uses none). Screenshots: your call. |
| H8 | `removeUserDisabled`, `installAppsDisabled` | A second user or an installer path | None on a one-app tablet | Take it. |
| H9 | `advancedSecurityOverrides.mtePolicy: MTE_ENFORCED` | Memory-corruption bugs in any app being exploitable | Small slowdown; only devices whose hardware supports memory tagging act on it | Take it; harmless elsewhere. |

Left out of the file, each for a reason:
- **H10, `frpAdminEmails`**: needs Dinis's choice of Google accounts. Without it, a reset tablet is free to enrol anywhere; with it a reset tablet needs one of the listed accounts, and losing those accounts locks the tablet.
- **H11, a Chrome URL allow-list.** The review suggests a managed configuration on `com.android.chrome` with `URLAllowlist` and `URLBlocklist`, so Chrome opens riskmandate.ai only. The managed configuration is a free-form object in the API, so the offline test cannot check the key names or the value shape (a JSON string or an array), and I could not read Chrome's own restriction schema from here. It should be set up through managed Google Play's configuration screen on a test tablet first, then copied into the policy. Until then Chrome can browse anywhere on a managed tablet; the app's allow-list protects only the app's own button.
- **H12, kiosk mode** (`installType: KIOSK`): locks the tablet to RiskMandate. Not for M2: the app opens riskmandate.ai in a Custom Tab (Chrome), which a kiosk lock would have to allow, and that interaction is untested.
- **Wi-Fi lockdown** (`deviceConnectivityManagement.configureWifi`): a tablet that cannot add a network cannot be moved to a new one without an admin. Not worth it for M2.
- **Support messages** (`shortSupportMessage`, `deviceOwnerLockScreenInfo`): shown on the device; this repository holds no contact details, so Dinis supplies the text.
- Reporting: `statusReportingSettings` collects application, hardware, memory, display and power data (network info is off). Say so in the privacy notice before the app is sold.

## AMAPI or Workspace (Cloud Identity): side by side

Read on 8 November 2026. "Verified" means I fetched the page and quote or paraphrase it; "not verified" means I could not find it on a page or the pages disagree. The Workspace route here means Google's own device management in the Admin console, **Google endpoint management (GEM)**, which comes with Google Workspace and Cloud Identity.

### What Dinis clicks

**AMAPI** (steps 1 to 8 above, about two hours of clicking plus Google's wait for private apps):
1. Cloud Console: a project, enable the Android Management API, request quota with a business justification, a service account with the role Android Management User, one JSON key kept off the repository.
2. Sign up the enterprise with `signup-url` (a Gmail account is fine for a test, a Workspace or Cloud Identity account for the long term).
3. A Play Console developer account (US$25, an organisation needs a D-U-N-S) to publish the private app, or the managed Play iframe (private for ever, never sellable).

**Workspace or Cloud Identity** (my reading of Google's pages; the exact menu names may differ in the live console):
1. Create a Cloud Identity account on a domain PrompTugal controls, which means proving the domain in DNS. A Google Workspace edition that supports advanced mobile management also works.
2. Admin console, **Devices, Mobile and endpoints, Settings, Universal, General, Mobile management**, choose **Advanced**, Save. Each user who signs in on a managed device needs a licence that supports it, otherwise only basic management applies (verified, "Set up advanced mobile management").
3. Admin console, **Apps, Web and mobile apps, Add app, Add private Android app**, enter a title, upload the AAB or APK, choose the organisation, groups or organisational units, Finish. No Play Console account is needed for this (verified, "Manage private Android apps in Google Play").
4. Enrol the tablets. Here the pages stop being clear; see the table.

### Comparison

| | AMAPI (`devices/`) | Workspace or Cloud Identity (GEM) |
|---|---|---|
| Fully managed device, QR enrolment | **Yes.** Verified: AMAPI enrolment tokens and the six-tap QR flow set fully managed mode (`provision-device`). | **Not shown on any page read.** The pages I read route company-owned Android through zero-touch enrolment (which needs devices bought from a reseller) or the company-owned device inventory. Not verified: a QR code or token for a factory-reset tablet. |
| Does GEM manage device-owner (fully managed) devices at all? | n/a | **The pages disagree.** Google's "About Android device management with Google endpoint management" says: "GEM doesn't support devices with device owner privilege, though this setup is now an option in Android Enterprise." The same page says zero-touch enrolment "lets organizations automatically set up fully-managed devices for users" and that GEM manages them when the users are in organisational units with advanced mobile management on. Unresolved: ask Google, or test with one device. |
| Userless tablet (no person signs in) | **Yes**: `PERSONAL_USAGE_DISALLOWED_USERLESS`. | Not verified. Everything read is per user: "Any user who wants to sign in to a managed device must have a supported license for a feature to apply." |
| Force-install a private app | By policy (`FORCE_INSTALLED`), scriptable. The app comes from the Play Console (organisation ID) or the iframe. | In the Admin console, after upload. Verified: "typically ready for distribution within 10 minutes", though "It can take a few hours for the app to be available to users". Some options, such as force install, depend on the edition. Applies "only to devices with Basic or Advanced Google endpoint management". |
| Who signs the private app | Our upload key; Play App Signing. | Verified: for an AAB uploaded in the Admin console "Google generates and manages the signing key for the app"; your own key means publishing through the Play Console. So the CI upload-key signing in `docs/ci.md` would not apply to this route. |
| Charging for the app | Not for the private build either way. | Verified: "Publishers can't charge for private apps." Also: "Apps can't be published as a private app and in the public Google Play store at the same time." The package-name question (`docs/play/README.md`, step 8) stays open on this route too. |
| Limits | Quota: up to 500 devices on a justified request, more by application; 1000 queries per 100 s per project, 60 per minute per resource. | Up to 15 private apps uploaded a day (verified). Device limits not read. |
| Managed configuration (`start_url`, `device_label`) | In the policy. | A row in the Cloud Identity comparison: "Managed Android app settings configurations" is Premium only. |
| Automation | Full API; this CLI; can run from CI. | Admin console clicks. A narrower API exists for devices; not read. |
| Hardening | The whole policy surface, about 100 fields (see Hardening options). | The settings the console exposes. Not compared field by field. |
| Cost | The API has no price on the pages read; quota is by request. Plus the Play Console fee if the app is published there. | Cloud Identity **Free** has "basic mobile device management" and "Public and private app management (Android)" but not "Advanced mobile management", "Company-owned device inventory", "Zero-touch enrollment for Android devices" or "Managed Android app settings configurations". **Cloud Identity Premium: US$7.20 per user per month on the Flexible Plan, US$6 per licence per month on the annual plan** (Google's "Compare Cloud Identity billing plans"). Workspace editions (Business Plus and up) include advanced mobile management at their own prices (not read). Licences are per user, not per device. |
| Rules | Permissible-usage policy (below). | None found on the pages read. Workspace's own terms were not read. |

### Google's AMAPI permissible-usage terms

Source: https://developers.google.com/android/management/permissible-usage (fetched 8 November 2026). Quoted:

> The use of Google's Android Management API and associated SDK ("Service") are restricted solely to commercial Enterprise Mobility Management (EMM) developers, Device Trust from Android Enterprise (Device Trust) solution providers, and Original Equipment Manufacturers of Android devices (OEMs).

The page then lists what is **not** permitted, among it:

> Solutions developed and used exclusively for first party in-house applications.

> Push, preload or auto install applications and/or services on Android Devices without the express and informed prior consent of relevant customers and their end users.

> Alteration of a commercially available Android Device to subsequently sell, resell or lease such altered device with the exception of devices in a userless kiosk mode (e.g., a tablet locked to a single application or web app).

On quota:

> To request an initial quota of up to 500 devices, you will need to provide a full business justification.

> Quota will be granted for a maximum of 2 projects per developer, to support separation of development and production environments.

**My reading, not legal advice:** the first sentence names who may use the API. PrompTugal managing its own tablets for its own app is, on its face, neither a commercial EMM, a Device Trust provider nor an OEM, and the "first party in-house applications" line names that case. A test enterprise on the quickstart is how Google's own quickstart onboards people; running a fleet this way is a different matter, and Google decides what a quota request is granted. If PrompTugal later sold device management as a product, the first sentence would fit better; today it does not say so.

### What follows

- **M2 as a proof of concept can use AMAPI**, in a Gmail-bound test enterprise, because it is the one route verified to give a userless fully managed tablet with a QR code and a policy we have already written and tested. That proves the tablet side. It does not settle whether PrompTugal may rely on it.
- **Workspace or Cloud Identity Premium is the route clearly built for "an organisation manages its own devices"**, but I could not verify that it can enrol a factory-reset, userless tablet fully managed with a QR code, and the pages contradict each other on device-owner support. It is only worth paying for after that is answered, with one device.
- **A commercial EMM** (Google keeps a list at https://support.google.com/work/android/answer/16859243) sits inside the rules by construction. It costs per device; I read no prices.

### Questions only Dinis can answer

1. **Route for M2:** AMAPI test enterprise now (and accept the permissible-usage risk for a proof), Cloud Identity Premium, or a commercial EMM? My suggestion is AMAPI for the one-tablet proof and Google's answer in parallel.
2. **Is PrompTugal, or the agent software, a device-management product** that it sells or will sell? That changes whether AMAPI fits.
3. **Who asks Google?** Two questions need a person with an account: does GEM support a userless, device-owner tablet enrolled with a QR code; and is a company managing its own tablets for its own app within AMAPI's permissible use. Nothing has been sent in his name.
4. **Which account owns the enterprise:** a Gmail account (test) or a domain account, which domain, and who is the super admin. A domain account needs DNS access for the verification record.
5. **Budget and tablets:** may Cloud Identity Premium be bought for a trial (per user, so at least one)? How many tablets, which model, and may the first one be factory-reset?
6. **The private build's package ID** (`docs/play/README.md`, step 8: A, B or C). This is needed by both routes.
7. **Play Console account or Admin-console private app?** The Admin-console route needs no US$25 fee and no D-U-N-S but gives up our upload-key signing and, per Google, a private app cannot also be public.
8. **Hardening picks**, H1 to H12 above, in particular H2 (factory reset), H5 (screen lock and who holds the PIN) and H10 (which Google accounts for factory reset protection).
9. **The device quota request**: may it be submitted, and in whose name, with what justification? It is a business statement to Google that the agent will not make.
10. **The rehearsal network** (Wi-Fi name and security; the minimum-security option H6 depends on it) and **who holds the tablet** on the day.

## What could not be verified here

- No call has been made to Google (apart from fetching its public discovery document and help pages): there is no project, no key and no enterprise yet. Request bodies, URLs and every policy field are checked offline against the discovery document (revision 20261104); responses in the tests are fakes.
- The QR encoder was decoded by its own tests, and once, outside the repository, by the independent zxing-cpp decoder: every version 1 to 40 at every level at full capacity, 204 symbols, all read back exactly. It has **not** been scanned by an Android device's provisioning screen: that is the first thing the rehearsal shows.
- The initial device quota of a new project before the quota request is granted.
- Nothing further on private apps: per Google (Managed Google Play Help, answer 6145139) an app restricted to organisations stays private for ever, on both routes, and going public needs a new package name. Not re-read here; taken from the coordinator's citation.
- Whether Google endpoint management can enrol a userless, factory-reset tablet fully managed with a QR code (Google's pages disagree on device-owner support; see the comparison). The per-user licence rule for a tablet nobody signs in on.
- Prices for Workspace editions and for commercial EMMs; the Cloud Identity Premium price is from Google's billing-plan page.
- Chrome's managed-configuration key names and value types (H11), and the exact meaning of the password complexity levels in H5.
- AMAPI's error text for an exhausted quota or a used one-time token: REHEARSAL.md lists what to look for, not strings.
- How a given tablet model's welcome screen behaves with six taps (varies by OEM and Android version; the `afw#setup` fallback covers most).
