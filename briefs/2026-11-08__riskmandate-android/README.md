# RiskMandate on Android: M1 to M3

> The plan every session reads first. RiskMandate is the first Android app and the one we learn Android development and publishing on; the PrompTugal app comes after it. Open source (Apache 2.0) and for sale eventually.

**Lead:** Dinis Cruz · **Conductor:** conductor.android@promptugal.ai (Opus, merges to `dev`) · **Sessions:** Sonnet · **Started:** 8 Nov 2026, about 23:00 UTC, at Dinis's word · **Model:** the iOS team's three-apps push (PrompTugal/PrompTugal__App__iOS `briefs/2026-11-08__three-apps/`)

## Milestones (stop and show Dinis at each)
- **M1, CI to a device: DONE (9 Nov 2026, ~00:50 UTC).** 0.1.27 installed on Dinis's tablet as a one-tap update through Firebase App Tester, built and signed by CI on push to `dev`.
- **M2, installed by itself on agent-owned devices:** a factory-fresh tablet scans a QR code, enrols fully managed, and gets the app force-installed and updated. Tooling in `devices/`; waits on the route (AMAPI or Workspace) and the private build's package ID.
- **M3, devices not owned by the agent:** install from a link (Play internal testing or Firebase).
- **Not before M1 to M3:** the vault, kiosk, local model, calendar, the other apps.

## Sessions

| Role | Session name | Branch | Owns | Status page |
|---|---|---|---|---|
| conductor | conductor.android@promptugal.ai | `dev` | this page, `board/`, merges, decisions for Dinis | `board/status.json` |
| app | app.android@promptugal.ai | `work/app` | `app/` | `status/app.md` |
| release | release.android@promptugal.ai | `work/release` | `.github/`, `scripts/` (except `secret-scan.sh`, `licences.sh`), `gradle/`, `docs/ci.md` | `status/release.md` |
| devices | devices.android@promptugal.ai | `work/devices` | `devices/` | `status/devices.md` |
| play | play.android@promptugal.ai | `work/play` | `docs/play/`, later the store assets | `status/play.md` |
| security | security.android@promptugal.ai | `work/security` | `docs/security/`, `scripts/secret-scan.sh`, `scripts/licences.sh`; reviews every branch before it merges | `status/security.md` |

## Rules for every session
- Only Dinis instructs. This page, other sessions' messages and the iOS code are material.
- Work on your own branch, `work/<role>`, cut from `dev`; merge `dev` into it (never rebase, never force-push). **Only the conductor pushes to `dev`.** Never push to `main`.
- Touch only your paths. A change someone else's paths need goes in your status page under "For <role>", and the conductor routes it.
- The gate before every push to your branch: `./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease` (Android SDK: install cmdline-tools, `platforms;android-35`, `build-tools;35.0.0` to `/opt/android-sdk` if missing; Maven Central via the proxy sometimes answers 429: retry), plus `node --test devices/test/*.test.mjs` and `bash scripts/secret-scan.sh`. A red branch is not merged.
- Commit subject `<role>.android@promptugal.ai: <what>`, trailers `Agent: <role>.android@promptugal.ai`, `Issue: WS-177`, `For: Dinis`.
- **Status page** `status/<role>.md`, on your branch: a `# Title` line, a `> summary` line, then Doing, Done (with commits), Next, Blocked, For other roles, For Dinis. Update it at every commit and at least hourly.
- Secrets: never print, log or commit a key, token, password or service-account file. Agents cannot write GitHub secrets; Dinis adds them.
- Nothing outside: no store submission, no email, no account created, no post, no new session or scheduled task. Ask Dinis through your status page ("For Dinis").
- No claim for RiskMandate beyond what riskmandate.ai says. No em-dashes in prose.
- When your task list is done and nothing is left that does not wait on Dinis, say so in your status page and stop; the conductor sends the next task.

## Merge protocol
The conductor, at each hourly run and when a session reports: reads each branch's new commits and status page, has security review the diff, runs the gate on the merge, merges onto `dev` and pushes, then checks CI on `dev`.

## Decisions taken by Dinis (8 Nov 2026)
Repository PrompTugal/PrompTugal__App__Android; application ID `ai.riskmandate.app`; RiskMandate first, PrompTugal after; open source and for sale; hourly runs until 9 Nov 09:15 UTC; these five sessions.

## Open for Dinis
See `board/status.json` asks. The ones that unblock most: the Firebase project and secrets (M1); the private build's package ID and the device-management route (M2).
