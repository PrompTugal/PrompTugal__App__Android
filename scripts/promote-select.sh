#!/usr/bin/env bash
# Which dev commit may be promoted to main: the newest commit on dev's first-parent line (up to MAX, default 60)
# for which the signed-approval check passes. Prints the SHA; exit 1 if none (the newest candidate's reason
# goes to stderr). Fails closed: only a verifier exit status of 0 counts; anything else, including a crash, is no.
# Usage: scripts/promote-select.sh <main-ref> <dev-ref>   (run from a checkout of MAIN, with dev fetched)
# VERIFIER overrides the command (tests): it is called  $VERIFIER --repo . --main <sha> --tip <sha>.
set -uo pipefail
mainref=${1:?main ref}; devref=${2:?dev ref}
verifier=${VERIFIER:-node scripts/verify-approval.mjs}
max=${MAX:-60}
main=$(git rev-parse "$mainref^{commit}") || exit 2
first=""
for tip in $(git rev-list --first-parent --max-count="$max" "$main..$devref"); do
  out=$($verifier --repo . --main "$main" --tip "$tip" 2>&1); rc=$?
  [ -z "$first" ] && first="${tip:0:7}: ${out:-no output} (exit $rc)"
  if [ $rc -eq 0 ]; then echo "$tip"; exit 0; fi
done
echo "no approved commit among the newest $max on dev; the newest: $first" >&2
exit 1
