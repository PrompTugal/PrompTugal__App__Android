# M2 rehearsal: a factory-fresh tablet, a QR code, the app installed by itself

For Dinis and whoever holds the tablet. Written 8 November 2026 by devices.android@promptugal.ai. Nothing here has been run against Google: there is no project, key or enterprise yet. The command outputs below are `--dry-run` outputs, produced offline with an invented enterprise (`enterprises/LC0example`) and an example private package ID (`ai.riskmandate.app.managed`, option B in `docs/play/README.md` step 8; **not decided**). Real values replace both. What the **tablet** shows at each step is described from Google's documentation and general Android behaviour; **no one on this team has seen it yet**, so the rehearsal's first job is to confirm or correct those descriptions here.

## What success is

One tablet, factory-reset, scans one QR code on its welcome screen. Within minutes it is fully managed, RiskMandate and Chrome are installed without anyone touching the tablet, `devices-list` shows it `ACTIVE`, `DEVICE_OWNER` and compliant with RiskMandate's version, and a later CI build reaches it by itself.

## Before the day (checklist)

- [ ] The questions at the end of `devices/README.md` answered, at least: the route (AMAPI test enterprise), the account that owns the enterprise, the private build's package ID, and the hardening picks (or none).
- [ ] The private build exists as a Play-signed release for that package ID, or the managed Play iframe route is chosen for a **throwaway** test app (never public; do not use it for the real one).
- [ ] A tablet that may be factory-reset, charged, with Android 7 or later; its model noted. A Wi-Fi network it can join (WPA2 or better; if the minimum-security hardening option is wanted, test that too).
- [ ] A laptop with Node 18 or later and this repository. The service-account key outside the checkout, mode 600.
- [ ] Gate on the laptop, to start from a known state:

```
node --test devices/test/*.test.mjs
bash scripts/secret-scan.sh
```

## The sequence

Steps marked **Dinis** need his Google account. Everything else the agent or Dinis can run. Each step has: the command, what the dry run shows, what success looks like, what to do if it does not.

### 1. Google Cloud project and key (**Dinis**, `devices/README.md` steps 1 and 2)

Success: the project exists, the Android Management API shows as enabled, the quota request is submitted, a service account with only the role Android Management User exists, its JSON key is saved outside the repository, and in the shell:

```
export GOOGLE_APPLICATION_CREDENTIALS=~/.config/promptugal/amapi-service-account.json
```

If the quota request has not been granted, enrolment may fail at step 7 or 8 (see Recovery, quota).

### 2. Signup URL

```
$ node devices/amapi.mjs signup-url --project promptugal-devices --callback https://promptugal.news/ --dry-run
{
  "dryRun": true,
  "command": "signup-url",
  "method": "POST",
  "url": "https://androidmanagement.googleapis.com/v1/signupUrls?projectId=promptugal-devices&callbackUrl=https%3A%2F%2Fpromptugal.news%2F",
  "headers": {
    "authorization": "Bearer <access-token>"
  }
}
```

Real run, same command without `--dry-run`. **Success:** two lines of JSON, `name` (`signupUrls/C...`) and `url`. Open the url (**Dinis**), choose the account, finish the sign-up. Google redirects to `https://promptugal.news/?enterpriseToken=...`; the page itself does not matter, copy the token from the address bar.

If it fails: `PERMISSION_DENIED` means the service account lacks the role or the API is not enabled in that project; `invalid project` means a wrong project ID (the ID, not the display name).

### 3. Create the enterprise

The two values go in environment variables, not on the command line:

```
read -r -s -p "enterprise token: " AMAPI_ENTERPRISE_TOKEN; export AMAPI_ENTERPRISE_TOKEN; echo
export AMAPI_SIGNUP_TOKEN=signupUrls/C...
```

```
$ node devices/amapi.mjs enterprise-create --project promptugal-devices --name "PrompTugal Devices" --dry-run
{
  "dryRun": true,
  "command": "enterprise-create",
  "method": "POST",
  "url": "https://androidmanagement.googleapis.com/v1/enterprises?projectId=promptugal-devices&signupUrlName=signupUrls%2FCEXAMPLE&enterpriseToken=<redacted>",
  "headers": {
    "authorization": "Bearer <access-token>"
  },
  "body": {
    "enterpriseDisplayName": "PrompTugal Devices"
  }
}
```

**Success:** `Enterprise bound. Use --enterprise enterprises/LC0... from now on.` Write that name down; it is not a secret. If the token is rejected, the signup URL was used already or the token expired: go back to step 2 for a new URL. The URL and the token are single-use.

### 4. The app on managed Google Play (**Dinis**, `devices/README.md` step 4, `docs/play/README.md` step 8)

Not a CLI step. **Success:** in managed Google Play, searching the organisation's private apps lists RiskMandate with the package ID chosen. Until it does, step 5 will apply but the app will not install. Google puts the Play Console route at about two hours, the iframe at about ten minutes (iframe: throwaway only).

### 5. Apply the policy

```
$ node devices/amapi.mjs policy-apply --enterprise enterprises/LC0example --policy riskmandate --file devices/policies/riskmandate-fully-managed.json --package ai.riskmandate.app.managed --dry-run
{
  "dryRun": true,
  "command": "policy-apply",
  "method": "PATCH",
  "url": "https://androidmanagement.googleapis.com/v1/enterprises/LC0example/policies/riskmandate",
  "headers": {
    "authorization": "Bearer <access-token>"
  },
  "body": {
    "applications": [
      {
        "packageName": "ai.riskmandate.app.managed",
        "installType": "FORCE_INSTALLED",
        "autoUpdateMode": "AUTO_UPDATE_HIGH_PRIORITY",
        "managedConfiguration": {
          "start_url": "https://riskmandate.ai/",
          "device_label": "riskmandate-tablet"
        }
      },
      {
        "packageName": "com.android.chrome",
        "installType": "FORCE_INSTALLED",
        "autoUpdateMode": "AUTO_UPDATE_DEFAULT"
      }
    ],
    "playStoreMode": "WHITELIST",
    "statusReportingSettings": {
      "applicationReportsEnabled": true,
      "deviceSettingsEnabled": true,
      "softwareInfoEnabled": true,
      "hardwareStatusEnabled": true,
      "memoryInfoEnabled": true,
      "displayInfoEnabled": true,
      "powerManagementEventsEnabled": true,
      "networkInfoEnabled": false
    },
    "systemUpdate": {
      "type": "WINDOWED",
      "startMinutes": 120,
      "endMinutes": 300
    },
    "advancedSecurityOverrides": {
      "untrustedAppsPolicy": "DISALLOW_INSTALL",
      "developerSettings": "DEVELOPER_SETTINGS_DISABLED",
      "googlePlayProtectVerifyApps": "VERIFY_APPS_ENFORCED"
    },
    "autoDateAndTimeZone": "AUTO_DATE_AND_TIME_ZONE_ENFORCED",
    "addUserDisabled": true,
    "modifyAccountsDisabled": true
  }
}
```

(`--start-url` and `--device-label` take their defaults here: `https://riskmandate.ai/` and `riskmandate-tablet`; the CLI refuses anything else that is not riskmandate.ai over https.) **Success:** the real run prints the policy `name`, a `version` and the two applications with `FORCE_INSTALLED`. A hardened trial is the same command with `--file devices/policies/riskmandate-hardened-options.json` (see `devices/README.md`, Hardening options; leave H2, the factory-reset block, **off** while rehearsing).

If it fails with an `INVALID_ARGUMENT` that names a field, the discovery document has moved on since 4 November 2026: re-run `node --test devices/test/*.test.mjs` after refreshing `devices/schema/` (instructions in `devices/trim-discovery.mjs`).

### 6. Make the QR code

```
$ node devices/amapi.mjs enrol --enterprise enterprises/LC0example --policy riskmandate --duration 3600s --dry-run
{
  "dryRun": true,
  "command": "enrol",
  "method": "POST",
  "url": "https://androidmanagement.googleapis.com/v1/enterprises/LC0example/enrollmentTokens",
  "headers": {
    "authorization": "Bearer <access-token>"
  },
  "body": {
    "policyName": "enterprises/LC0example/policies/riskmandate",
    "duration": "3600s",
    "allowPersonalUsage": "PERSONAL_USAGE_DISALLOWED_USERLESS",
    "oneTimeOnly": true
  },
  "qrPayloadShape": {
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.google.android.apps.work.clouddpc/.receivers.CloudDeviceAdminReceiver",
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "I5YvS0O5hXY46mb01BlRjq4oJJGs2kuUcHvVkAPEXlg",
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://play.google.com/managed/downloadManagingApp?identifier=setup",
    "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {
      "com.google.android.apps.work.clouddpc.EXTRA_ENROLLMENT_TOKEN": "<enrollment-token>"
    }
  }
}
```

Real run, same command without `--dry-run`. **Success:**
- the JSON shows `oneTimeOnly: true` and an `expirationTimestamp` an hour ahead;
- three lines name `devices/out/enrol-<time>.json`, `.qr.txt`, and the two images `.qr.svg` and `.qr.png`. Open the PNG on the laptop screen at full size. It is a QR code of roughly 80 to 90 modules a side: dense, so give it room.

Keep `out/` private: whoever scans the image before it expires or is used can enrol a device into the enterprise. One-time means the first device that uses it wins. If a QR code leaks before use, revoke it:

```
$ node devices/amapi.mjs token-revoke --token enterprises/LC0example/enrollmentTokens/EXAMPLE --dry-run
{
  "dryRun": true,
  "command": "token-revoke",
  "method": "DELETE",
  "url": "https://androidmanagement.googleapis.com/v1/enterprises/LC0example/enrollmentTokens/EXAMPLE",
  "headers": {
    "authorization": "Bearer <access-token>"
  }
}
```

### 7. Enrol the tablet (the person with the tablet)

If the tablet has been set up before (including with the M1 Firebase build), **factory-reset it first**: Settings, System, Reset options, Erase all data. QR enrolment works only from the welcome screen of a new or reset device. What each stage should look like (not yet seen by us; correct this list on the day):

1. **Welcome screen** (language or "Welcome"). Tap **six times in the same spot** on a blank part of the screen. A camera opens with a viewfinder, and on older builds it may first say it is downloading a QR reader, which needs Wi-Fi: join it when asked.
2. **Scan** the PNG on the laptop. Hold the tablet 20 to 40 cm away, screen brightness up, the whole code with its white border in frame. **Success:** the screen changes to a "Connecting to Wi-Fi" step, or to "Setting up your device" with a progress bar.
3. **Wi-Fi.** Choose the network, enter its password. The tablet then downloads Android Device Policy (the management app) and may show a notice that the device is managed by the organisation: accept it.
4. **Provisioning.** A progress screen for a few minutes while the policy applies. It then lands on the home screen with no Google account prompt (the userless setup).
5. **Apps.** RiskMandate and Chrome appear on their own, within minutes (if managed Play has the app published). The Play Store may show only these apps.

If the six taps do nothing: finish a normal setup up to the Google sign-in screen and type `afw#setup` into the email field, then scan the same QR code (Android Enterprise Help documents this as an alternative). If the camera does not read the code: increase the size, reduce glare, or draw it bigger (`node devices/amapi.mjs qr --in devices/out/enrol-<time>.qr.txt` writes a fresh PNG; render it at a larger scale in an image viewer). Do not retry with a different token until you know whether the first was used (step 8 shows the device).

### 8. Check

```
$ node devices/amapi.mjs devices-list --enterprise enterprises/LC0example --package ai.riskmandate.app.managed --dry-run
{
  "dryRun": true,
  "command": "devices-list",
  "method": "GET",
  "url": "https://androidmanagement.googleapis.com/v1/enterprises/LC0example/devices?pageSize=100",
  "headers": {
    "authorization": "Bearer <access-token>"
  }
}
```

Real run, same command without `--dry-run`. **Success**, for the tablet:
- `state: ACTIVE` (`PROVISIONING` until its first policy applies: wait and re-run);
- `managementMode: DEVICE_OWNER` (fully managed);
- `ownership: COMPANY_OWNED`, `policyCompliant: true`, `nonCompliance: []`;
- `policy` ends in `/policies/riskmandate`;
- `app` shows RiskMandate with a `versionName` of the form `0.1.<run>` and a state;
- `model` and `androidVersion` are filled in, `lastStatusReportTime` is recent.

If `policyCompliant` is false, `nonCompliance` says what and for which package, for example an app that cannot be found on Play (step 4 is not finished, or the package ID in `--package` is not the published one). If the list is empty, the tablet has not enrolled: it never finished step 7, or the token was used elsewhere.

On the tablet: RiskMandate opens; its Device section should say fully managed; the Open button opens riskmandate.ai in Chrome. **That the app detects fully managed mode correctly is itself one of the things this day checks:** it uses public APIs only and was never run on a managed device (`REALITY.md`).

### 9. An update by itself

1. Push a change to `dev`; CI builds, signs with the upload key, and uploads a draft to the internal track (`docs/ci.md`); someone completes the rollout, and for the organisation's private app the release must reach the managed Play track the tablet gets (production for the Managed Google Play route).
2. Re-run `devices-list --package ...` every ten minutes or so. **Success:** `app.versionCode` rises to the new CI run number with nobody touching the tablet. Google notes high-priority updates can still take time; its docs say up to 24 hours for widely deployed apps.

### 10. Tidy up

Revoke any unused token (step 6). A device that is finished with: `device-delete` below wipes it. Keep nothing from `devices/out/`: delete the folder's files after the day.

## Recovery

**Re-enrol a tablet** (wrong policy, failed half-way, test again): factory-reset it on the device (Settings, System, Reset options, Erase all data, or from recovery mode if it will not boot into Settings), delete its record so the old one does not linger, then enrol with a **new** token (one-time tokens do not work twice):

```
node devices/amapi.mjs devices-list --enterprise enterprises/LC0example      # note the device name
$ node devices/amapi.mjs device-delete --enterprise-device enterprises/LC0example/devices/3abc --dry-run
{
  "dryRun": true,
  "command": "device-delete",
  "method": "DELETE",
  "url": "https://androidmanagement.googleapis.com/v1/enterprises/LC0example/devices/3abc",
  "headers": {
    "authorization": "Bearer <access-token>"
  }
}
warning: this wipes the tablet; it must be enrolled again from a factory reset
```

Real run needs `--yes`. Deleting the record also tells an online tablet to wipe itself; an offline one wipes when it next connects (Google: "attempts to wipe the device but this is not guaranteed to succeed if the device is offline for an extended period"). Then step 6 again.

**A tablet stuck in provisioning or showing the wrong policy:** apply the policy again (step 5; it is the single source of truth, a full PATCH), wait a few minutes, check `lastPolicySyncTime` is recent (it is in the full device record, not in `devices-list`'s summary). If it stays stuck, factory-reset and re-enrol.

**Wipe a lost or finished tablet from here:** `device-delete` as above. If the hardened option H2 (`factoryResetDisabled`) was on, the tablet cannot be reset from Settings; the API delete is then the way, which is why H2 stays off for the rehearsal.

**The app does not install:** in `devices-list` the `nonCompliance` entry names the package. Common causes: the package is not published to this organisation yet (step 4); `--package` is wrong; the managed Play listing is still processing (hours for the Play Console route). Fix at the source, then wait for the next sync.

**The app installed from a Firebase build earlier:** signed with the upload key, so Play cannot update over it. A factory reset removes it; there is no other fix.

**Quota errors.** Two kinds, and I have **not** seen either (no call to Google has been made), so this lists what Google's documentation says and what to look for, not message text:
- *Device quota* (Google: "To request an initial quota of up to 500 devices, you will need to provide a full business justification."). Likely sign: `enrol` or `devices-list` works but a tablet does not become `ACTIVE`, or the API returns a permission or quota status for enrolment. Remedy: the quota request in step 1, which only Dinis can submit.
- *Rate quota* (1000 queries per 100 seconds per project; 60 per minute per resource). Likely sign: HTTP 429 or `RESOURCE_EXHAUSTED`. Remedy: wait a minute and run again; the CLI does not loop, so it will not make it worse.
If an error text appears that is not described here, add it to this file with the date.

**A leaked QR code or token:** `token-revoke` (step 6). If a device enrolled that should not have, `device-delete` it, and rotate nothing else: the QR code is not the service-account key.

**A leaked service-account key** (never paste it anywhere; if it happened): delete the key in the Cloud Console (IAM, Service accounts, Keys) and make a new one.

## What this rehearsal will tell us that we cannot know today

- Whether the QR code from `qr.mjs` is read by the tablet's provisioning scanner first time at that density.
- Whether the six taps or `afw#setup` is the way in on that tablet model.
- How long the private app takes to appear, by route.
- What `devices-list` really shows for a fully managed tablet.
- Whether the app's Device section reads fully managed correctly on a real managed device.
- Which quota, if any, the project hits.
