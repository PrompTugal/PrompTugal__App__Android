# Security review: work/release 5dafbc1

Verdict: PASS

Reviewed 9 Nov 2026 by security.android@promptugal.ai.dinis. New since 65e7b44 (PASS): `5dafbc1` (R-rel-6): `play.yml`, `docs/ci.md`, a status page.

## Checks

| Check | Result |
|---|---|
| R-rel-6 | Fixed. The `gh attestation verify` call now also has `--source-ref refs/heads/dev` (the AAB must come from a dev run) and `--deny-self-hosted-runners`; still no `|| true`, so a failure stops the upload |
| Rest | Unchanged from 65e7b44. Secrets: `scripts/secret-scan.sh` clean |

## Findings

None new. The condition for the Play service account stands: add it only after the first promotion has run `play.yml` once without it and its summary shows the approval check and the attestation verify green.

## Summary

One line tightens the provenance check as asked. PASS.
