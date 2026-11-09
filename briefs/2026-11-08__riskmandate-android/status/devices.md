# devices.android status

> All five M2 tasks done and pushed (68 tests pass, secret scan clean). M2 to M4 parked by Dinis until after Web Summit (conductor, 9 Nov). Hand-over note below; nothing here has touched Google.

## Hand-over (9 Nov, M2 parked until after Web Summit)
**Where everything is** (all under `devices/`, merged to `dev` by the conductor):
- `README.md`: runbook, hardening options H1 to H12, AMAPI and Workspace side by side with Google's terms quoted, and the ten questions for Dinis.
- `REHEARSAL.md`: the M2 day, step by step, with dry-run outputs and recovery.
- `amapi.mjs` (CLI, now with `qr`, `device-delete --yes`, `token-revoke`), `qr.mjs` (QR encoder), `policies/` (base policy, and the hardened options file which is not applied), `schema/` plus `trim-discovery.mjs` (trimmed discovery document, revision 20261104), `test/` (68 tests).

**Unverified** (nothing has run against Google, no tablet has scanned a QR code):
- That an Android provisioning screen reads the `qr.mjs` code at roughly 80 to 90 modules a side. Only zxing-cpp has decoded it.
- What the tablet shows at each enrolment step (REHEARSAL.md describes it from documentation).
- Whether Google endpoint management (Workspace) supports a userless, QR-enrolled, device-owner tablet: Google's pages contradict each other.
- Chrome's managed-configuration keys for a URL allow-list (H11), AMAPI quota and error texts, Workspace edition and EMM prices, and whether the app's Device section detects fully managed mode on a real managed device.
- The discovery document may have moved on: refresh `devices/schema/` (instructions in `trim-discovery.mjs`) and re-run the tests before applying anything.

**First thing after the summit:** Dinis answers the questions at the end of the side-by-side section in `devices/README.md`, starting with the route, the private build's package ID, and the permissible-usage question. Then, in order: the Cloud project and key, the signup URL and enterprise, the private app on managed Play, and the rehearsal on one tablet that may be factory-reset. Apply only the hardening options he picks, and leave H2 (factory-reset block) off for the rehearsal.

## Doing
Nothing. Stopped, as the brief and the conductor's hand-over request say.

## Done
- QR encoder, `devices/qr.mjs` (commit f12a5e2): byte mode, versions 1 to 40, levels L to H, SVG and PNG, no dependencies. `enrol` now writes `.qr.svg` and `.qr.png` itself; `amapi.mjs qr --in <file>` draws any text file. Tested against standard vectors (Reed-Solomon, format and version bits, capacity table, alignment positions), by decoding its own matrix, SVG and PNG, and once outside the repo with the independent zxing-cpp decoder: every version 1 to 40 at every level at full capacity, 204 symbols, all read back exactly. Not yet scanned by a tablet.
- Offline policy validation (same commit): trimmed discovery document (revision 20261104, fetched once) in `devices/schema/`, `trim-discovery.mjs`, and `test/policy-schema.test.mjs`: every field, type and enum of every file in `policies/` exists, nothing read-only or deprecated unless allowed with a reason; the bodies and URLs the CLI builds are checked against the same document. The validator's own negative tests catch a typo, a wrong type, a bad enum and a deprecated field.
- Workspace and Cloud Identity route, side by side with AMAPI, in `devices/README.md`: what Dinis clicks, cost (Cloud Identity Premium US$7.20 per user per month flexible, US$6 annual; Free lacks advanced mobile management), what each can do for a userless fully managed tablet, Google's permissible-usage terms quoted with the URL, ten questions only Dinis can answer.
- `devices/REHEARSAL.md`: the sequence on the day, seven dry-run outputs pasted, what success looks like at each step, recovery (re-enrol, wipe, quota, leaked token). New CLI commands for it: `device-delete --yes` and `token-revoke`; `devices-list` now shows `appliedState` and `nonCompliance`.
- Hardening options: `devices/policies/riskmandate-hardened-options.json` (not applied), H1 to H12 explained in `devices/README.md`.

## Next
Nothing that does not wait on Dinis. After his answers: apply the chosen hardening options to the base policy, and run the rehearsal.

## Blocked
Everything real: no Google project, key, enterprise, Play app or tablet.

## For other roles
- **app:** the Device section's fully-managed detection has never run on a managed device; the rehearsal (step 8) is where it is first checked. For the private build, the package ID decision (A or B, `docs/play/README.md` step 8) needs the build flavour.
- **release:** nothing new; no CI change needed. `node --test devices/test/*.test.mjs` now has 68 tests (it was 27 in the CLI file alone), about 1 second.
- **security:** A-11 is answered with a correction. Three settings the review named are deprecated in the live API schema: `usbFileTransferDisabled` (use `deviceConnectivityManagement.usbDataAccess`), `tetheringConfigDisabled` (use `tetheringSettings`), `passwordRequirements` (use `passwordPolicies`). `safeBootDisabled` is deprecated with no replacement and is kept, allowed by name in the test. The Chrome URL allow-list (H11) is not in the file because its key names and value shape cannot be verified offline.

## For Dinis
Dinis: the ten questions are at the end of the side-by-side section in `devices/README.md` ("Questions only Dinis can answer"). The three that unblock most: which route for M2 (AMAPI test enterprise, Cloud Identity Premium, or a commercial EMM); the private build's package ID (A, B or C); and whether PrompTugal manages its own tablets within AMAPI's permissible-usage terms, which name "Solutions developed and used exclusively for first party in-house applications" as not permitted.
Dinis: two findings you should know. (1) Google's own pages disagree on whether its Workspace device management supports fully managed (device owner) devices; I could not confirm a userless QR-enrolled tablet there. (2) The Workspace route signs private apps with a Google-held key, so our upload-key CI flow would not apply to it.
