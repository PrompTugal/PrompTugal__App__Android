# devices.android status

> All five M2 tasks are done and pushed to work/devices (68 tests pass, secret scan clean). Waiting on Dinis's answers; nothing here has touched Google.

## Doing
Nothing. Stopped, as the brief says.

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
