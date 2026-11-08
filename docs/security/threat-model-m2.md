# Threat model: M2 (fully managed tablets, app force-installed)

By security.android@promptugal.ai, 8 November 2026. Scope: the path from a Google Cloud project to a factory-fresh
tablet that enrols by QR code, receives the policy and the force-installed RiskMandate app, and is updated by
CI through managed Google Play. Based on `devices/README.md`, `devices/amapi.mjs`,
`devices/policies/riskmandate-fully-managed.json`, the app's managed configuration (`start_url`, `device_label`)
and `.github/workflows/android.yml`. Nothing here was run against Google (no project, key or enterprise exists
yet); Google behaviours are taken from the repository's own notes and from Google's public documentation as
I know it, and are marked "verify" where they decide a risk.

The repository is public and the app will be sold: assume the attacker has the source, the policy and the CLI.

## What we protect

| Asset | Why it matters |
|---|---|
| AMAPI service-account key | Can create enrolment tokens, change the policy, wipe or lock any device in the enterprise |
| Enrolment token and its QR code | A credential to join the enterprise and receive the policy until it expires or is used |
| The policy and `managedConfiguration` | Decides what runs on every tablet and where the app sends the user |
| The app's signing path (upload key, Play App Signing, CI) | Whoever can publish a build to the private track controls code on every tablet |
| The enterprise and its Google account | Owner of the fleet; loss means loss of control of the tablets |
| Data on the tablet | Little today (a Custom Tab to riskmandate.ai, no local vault until after M3), so the main risk is the session in the browser and the device identity |

## Trust boundaries

1. Operator workstation (Dinis, the CLI, `devices/out/`) to Google (AMAPI, OAuth).
2. Operator to the tablet's welcome screen (QR code, Wi-Fi), the only point where an unmanaged device becomes managed.
3. Google to the tablet (Android Device Policy, managed Play).
4. GitHub Actions (build, sign, upload to Play) to Google Play to the tablet.
5. EMM console or API (managed configuration) to the app (`start_url` is untrusted input to the app).

## Threats and mitigations

Rating is inherent risk given the design as written, then what the repository already does, then what to add.
S/T/R/I/D/E is STRIDE. "Owner" is who applies the fix.

### M2-1 Enrolment token or QR code leaks (S, E)
A QR code is a bearer credential. It is shown on a screen, printed, photographed, pasted into a chat, or left in
`devices/out/enrol-*.json`/`.qr.txt`. Anyone holding a live token can enrol their own device into the
enterprise: it then receives the policy and the force-installed app, shows up in `devices-list`, and a hostile
device inside the fleet can probe whatever the fleet can reach.
- Already done: `oneTimeOnly` is the default (`--reusable` warns), `duration` default 3600s, `devices/out/` is
  mode 700, files mode 600 and `wx`, outputs gitignored, `qrCode` and token values are redacted from printed errors.
- Residual: one hour is long for a one-time token. A one-time token consumed by an attacker's device blocks the
  real tablet (denial of service), which is visible and harmless.
- Add (devices): default `--duration` of 900s; print the expiry in local time; after each enrolment run
  `devices-list` and confirm exactly the expected serial/model appeared (check the device count against the
  number of tokens issued); delete used `devices/out/enrol-*` files; never send the QR code over chat or email,
  show it on the operator's own screen at the tablet. Never create a token with `PERSONAL_USAGE_ALLOWED` for
  these tablets. Owner: devices, Dinis for the practice.

### M2-2 The AMAPI service-account key (S, E, I)
The JSON key can act as the enterprise admin. It lives on Dinis's workstation (`GOOGLE_APPLICATION_CREDENTIALS`)
and, if enrolment is ever automated, in CI. Theft means: enrol devices, replace the policy (for example point the
force-installed app to a different package, or install an app of the attacker's choosing, since a policy can
list any app from managed Play), lock or wipe all tablets.
- Already done: role `Android Management User` only, key kept off the repo, `*service-account*.json` ignored,
  the CLI never prints the key or token, a one-hour access token.
- Add: keep the key out of CI and out of the agents entirely (agents do not hold it; Dinis runs `devices/amapi.mjs`
  himself). Dedicated Google Cloud project; create the key with an expiry via organisation policy where the
  project has one (verify); rotate after any session on a shared machine; enable Cloud Audit Logs for the
  Android Management API and read them after each enrolment day; delete the key when M2 testing ends. Prefer
  Workload Identity Federation over a downloaded key if enrolment moves to CI. Owner: Dinis.

### M2-3 Google account that owns the enterprise (S, E)
A Gmail account binds a *managed Google Play Accounts* enterprise; it is a personal login. Takeover of that
account (phishing, SIM swap, no 2SV) gives the attacker the enterprise and, through managed Play, the private
app listing. A Gmail-bound enterprise has one admin and no audit trail beyond the account's.
- Add: hardware security key or passkey and 2-step verification on the owning account, recovery options
  checked, a dedicated account used for nothing else, not Dinis's daily account; for anything beyond a test,
  move to a Workspace or Cloud Identity domain with at least two admins (already the README's
  recommendation). Owner: Dinis.

### M2-4 Policy tampering and over-broad policy (T, E)
`policy-apply` PATCHes the whole policy, so whoever can run the CLI with the key sets what every tablet runs.
The policy file is public and reviewed in git; the risk is a malicious or mistaken change merged to `dev`
(for example adding an app, `playStoreMode: BLACKLIST`, `developerSettings` allowed, a different `installType`).
- Already done: `WHITELIST` mode, `untrustedAppsPolicy: DISALLOW_INSTALL`, developer settings disabled,
  Play Protect enforced, `addUserDisabled`, `modifyAccountsDisabled`, `autoDateAndTimeZone` enforced, network
  info reporting off.
- Gaps (policy, owner devices; Dinis decides): no `safeBootDisabled` (a user can boot to safe mode, which
  disables the managed apps and, on some devices, opens a route around kiosk-like restrictions); no
  `usbFileTransferDisabled` / `mountPhysicalMediaDisabled` / `debuggingFeaturesAllowed: false` (physical
  access can copy data or enable `adb` if developer settings are ever flipped on); no `bluetoothDisabled`,
  `tetheringConfigDisabled` or `outgoingBeamDisabled` as decided by need; no `keyguardDisabled`/
  `passwordRequirements` (a userless tablet with no lock is open to whoever holds it; this is the stolen-tablet
  problem, M2-6); `factoryResetDisabled` false by choice. Chrome is force-installed as a full browser: a person
  at the tablet can open any site in it. Consider Chrome policy through managed configuration
  (`URLAllowlist`/`URLBlocklist`) or a kiosk (`installType: KIOSK`) once the app owns the whole screen.
- Add: CI check that the policy JSON keeps a list of must-have settings (`playStoreMode`,
  `untrustedAppsPolicy`, `developerSettings`) so a weakening is a red build; review every policy diff as a
  security change (`docs/security/reviews/`). Owner: devices, security reviews.

### M2-5 Managed configuration abuse: `start_url` and `device_label` (T, S)
`start_url` is set by the EMM and read by the app. Anyone who can change the policy (M2-2, M2-4) or a person
with an admin role in the Play console can point the button anywhere. The app is the last line: `Links.isAllowed`
must accept only https, host `riskmandate.ai` or `www.riskmandate.ai`. Review A-1 found a bypass in the first
version reviewed on 8 November (userinfo and backslash forms); `Links.kt` on `dev` now refuses `@`, backslash,
whitespace, control characters and `%` in the authority and allows port 443 only (read, not re-run here).
`device_label` is free text shown in the UI: must be rendered as text, never as HTML or a URL.
- Add: tests for the bypass forms (listed in the A-1 finding); the CLI already validates the same rule before
  sending (checked: README step 5). Keep the two rules identical and tested against the same vectors.
  Owner: app, devices.

### M2-6 Lost or stolen tablet (I, E, S)
A powered-off or locked-out tablet exposes nothing; the tablets here are userless and unlocked by design
(no screen lock is enforced) so a thief gets a working device with the RiskMandate session in the browser, the
managed Wi-Fi credentials if any were pushed, and an enrolled identity. A thief can also factory reset it
(`factoryResetDisabled` is false) and, without FRP, set it up as their own.
- Add: decide per tablet whether it leaves a controlled room. If it does: enforce a screen lock
  (`passwordRequirements`, with the PIN held by Dinis), set `frpAdminEmails` to the owning account (so a reset
  tablet cannot be reused: verify behaviour on the tablet model), disable USB and safe boot, and use the
  runbook's remote actions (`LOST` mode, lock, wipe, `devices-list` last-seen) the same hour. Do not push Wi-Fi
  passwords in the policy; use a guest network. Keep a register of serials, who holds each tablet, and the date.
  The riskmandate.ai session must expire server-side, and sign-out must be one tap. A wipe command must be
  tested once on a spare tablet before it is needed. Owner: devices (policy, CLI command), Dinis (decisions).

### M2-7 Factory reset and re-enrolment (T, E)
Reset by a person with physical access returns the tablet to the welcome screen, where any QR code can enrol
it into any enterprise, including an attacker's. Without FRP there is no cryptographic tie to us. During
testing `factoryResetDisabled` is deliberately false.
- Add: for tablets kept in the field, `factoryResetDisabled: true` after M2 testing, plus FRP as above, plus
  the zero-touch or QR enrolment tied to a device-bound identifier where available (zero-touch enrolment, verify
  availability for the tablet model; it re-enrols a reset device automatically so a reset does not escape
  management). Treat any device that disappears from `devices-list` as lost. Owner: devices, Dinis.

### M2-8 Supply chain into every tablet (T, E)
`AUTO_UPDATE_HIGH_PRIORITY` pushes each build published to Play to all tablets as soon as possible, with no
review window. Anyone who can get a build into the track (the Play service account in CI, the upload key,
a pushed workflow change, a poisoned dependency, `npx` of an unpinned tool) controls code on the fleet. The
old Firebase `npx` finding is closed by the lockfile (`.github/firebase`); see the review of the release branch.
- Already done: SHA-pinned actions, secrets only in `env:`, Environment `release` in the workflow (needs the
  reviewer set in the repo settings: see `github-settings.md`), the `check` job has no secrets, Play upload is
  gated on the secret existing, Play App Signing keeps the app signing key at Google.
- Add: Environment `release` with a required reviewer so each release run needs Dinis; Play upload to the
  `internal` track only until M2 is proven, with a staged rollout later; Play service account with the
  minimum role (release to the one app, no financial data); dependency verification metadata for Gradle (A-12);
  dependabot for actions and Gradle; never run the policy CLI from CI with the AMAPI key. Owner: release, Dinis.

### M2-9 Play listing, private app and the package name (T, S)
A private app is bound to the organisation. Per the README, an app published through the managed Play iframe
can never be made public, and the package name is taken for good. The risks are mistakes, not attackers:
publishing to the wrong enterprise, or someone else (a Play Console user) adding the app to a different
organisation. Also: Firebase-distributed APKs are signed with the upload key, not the Play App Signing key, so
they cannot sit on a managed tablet beside the Play build (signature mismatch; a reset resolves it).
- Add: list who has Play Console access and with which role (least privilege, no Admin for agents); record the
  chosen package ID in `board/status.json` before the first upload. Owner: Dinis, play.

### M2-10 Network position of the attacker at enrolment (S, I)
At the welcome screen the tablet joins Wi-Fi and downloads Android Device Policy from Google. An attacker on
that network cannot forge the enrolment (the QR payload carries the checksum of the DPC package; Google's
documented format), but can observe, block or delay it, and can phish the operator onto a fake hotspot.
- Add: enrol on a network you control; check that the QR payload contains
  `PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM` and `..._PACKAGE_DOWNLOAD_LOCATION` as returned by AMAPI
  (the CLI keeps AMAPI's own `qrCode` string untouched). Do not hand-assemble a QR payload. Owner: devices
  (README), Dinis.

### M2-11 Data the management plane collects (I)
`statusReportingSettings` reports app, device, software, hardware, memory, display and power data to AMAPI;
`networkInfoEnabled` is false, which avoids IMEI and MAC. Device serials and model still appear in
`devices-list` and in Cloud logs. These are PrompTugal's own devices, so low risk, but if M3 devices belong to
other people, a policy on them would be a privacy matter and consent is required: out of scope for M2.
- Add: keep `devices/out/` and any `devices-list` output out of the repo and out of public debriefs.

### M2-12 Repudiation and detection (R)
No record exists of who changed the policy or enrolled a device apart from Cloud Audit Logs.
- Add: enable and keep Cloud Audit Logs (Admin Activity are on by default; verify Data Access settings for
  the Android Management API), record each policy apply in a debrief (commit of the policy file, time, who ran
  it), and diff the live policy against the file before each enrolment day (the CLI has no read command for a
  policy yet: ask devices to add `policy-get`).
  Owner: devices, Dinis.

## Priorities before M2 goes live

1. M2-8 and M2-2 together: release Environment with Dinis as reviewer; the AMAPI key never leaves Dinis's machine.
2. M2-5: keep the `Links.isAllowed` tests and the CLI's copy of the rule in step (same vectors).
3. M2-4 / M2-6 / M2-7: decide screen lock, FRP, `factoryResetDisabled`, safe boot, USB; apply to tablets that leave the room.
4. M2-1: shorter default token duration, enrol-and-verify routine, delete used token files.
5. M2-3: a dedicated owner account with a security key; move to a domain before more than a handful of tablets.

## Not covered

AMAPI's permissible-usage question (first-party in-house use; Dinis decides, see `devices/README.md`), the
Workspace route's own model, the app's internals beyond `start_url` and the Custom Tab, Google's own platform
(Android Device Policy, Play), physical attacks on the tablet hardware beyond what the policy limits.
