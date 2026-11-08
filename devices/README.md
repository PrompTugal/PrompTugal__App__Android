# devices/: milestone M2 (fully managed tablets)

M2: a factory-fresh Android tablet scans a QR code at the welcome screen, enrols as **fully managed** through the Android Management API (AMAPI), and gets the RiskMandate app force-installed and kept up to date as a **private app on managed Google Play**.

What is here, built before the Google Cloud project, the Play Console account and any secret exist:

| File | What it is |
|---|---|
| `amapi.mjs` | A zero-dependency Node 18+ CLI for the AMAPI calls M2 needs. Every command has `--dry-run`. |
| `policies/riskmandate-fully-managed.json` | The device policy. `__PACKAGE__`, `__START_URL__` and `__DEVICE_LABEL__` are filled at apply time; keys starting with `_` are notes, stripped before sending. |
| `test/amapi.test.mjs` | Tests (`node --test devices/test/*.test.mjs`). No network, no real credentials. |
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
- `enrol-<time>.qr.txt`: AMAPI's own `qrCode` string, exactly as returned. Turn it into a QR image with any generator, e.g. `qrencode -o qr.png < enrol-<time>.qr.txt`, or paste it into ZXing's generator. The CLI does not draw the image itself (that would need a QR encoder; the repo has no dependencies).

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

## AMAPI or Workspace device management

| | AMAPI (this folder) | Google Workspace / Cloud Identity device management |
|---|---|---|
| Fully managed + QR enrolment | Yes | Yes (company-owned Android, advanced management) |
| Force-install a private app | Yes, by policy, scriptable | Yes, from the admin console (Apps, Android) |
| Automation | Full API; this CLI; can run from CI | Admin console clicks; a narrower API for devices |
| Cost | No charge on the docs read; quota by request | Workspace or Cloud Identity licences (Cloud Identity Free has basic Android management; fully managed company-owned devices need a paid tier, by my reading, not verified today) |
| Rules | Permissible-usage policy restricts first-party in-house use (above) | Built for exactly this: an organisation managing its own devices |
| Identity | Gmail-bound enterprise for a test, or a domain | The organisation's own domain and admins |

**Recommendation, to be confirmed after a day's test:** use AMAPI with a Gmail-bound test enterprise to prove M2 end to end (a tablet, a QR code, the app installed and updated by CI), because it is scriptable and costs nothing to try. Before more than a handful of tablets, or anything beyond a test, move the enterprise to PrompTugal's Workspace or Cloud Identity domain, and decide with Dinis whether management stays on AMAPI (if PrompTugal's use fits the permissible-usage policy) or moves to Workspace's own device management. The policy file maps almost one to one onto Workspace's Android settings, so little is lost either way.

## What could not be verified here

- No call has been made to Google: there is no project, no key and no enterprise yet. Requests match the AMAPI discovery document; responses in the tests are fakes.
- The initial device quota of a new project before the quota request is granted.
- Nothing further on private apps: per Google (Managed Google Play Help, answer 6145139) an app restricted to organisations stays private for ever, on both routes, and going public needs a new package name. Not re-read here; taken from the coordinator's citation.
- Workspace and Cloud Identity licence tiers for fully managed Android (the comparison above is from memory, not from a page read today).
- How a given tablet model's welcome screen behaves with six taps (varies by OEM and Android version; the `afw#setup` fallback covers most).
