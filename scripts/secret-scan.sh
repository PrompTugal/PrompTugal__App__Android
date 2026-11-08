#!/usr/bin/env bash
# Greps the tracked files and the whole git history for secret-shaped strings. Prints file/commit and
# the pattern name only, never the matched text. Exit 1 if anything matches.
# Run before every release:  scripts/secret-scan.sh
set -uo pipefail
cd "$(dirname "$0")/.."
hits=0
patterns='PRIVATE KEY-----|AIza[0-9A-Za-z_-]{35}|ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|xox[abp]-[0-9A-Za-z-]{10,}|"private_key"[[:space:]]*:[[:space:]]*"-----|ya29\.[0-9A-Za-z_-]{20,}'
echo "tracked files:"
if git grep -lIE "$patterns" -- . ':!scripts/secret-scan.sh' ':!devices/test/*'; then hits=1; fi
echo "history (commit: file):"
for c in $(git rev-list --all); do
  if git grep -lIE "$patterns" "$c" -- . ':!scripts/secret-scan.sh' ':!devices/test/*' 2>/dev/null; then hits=1; fi
done
echo "forbidden file names ever committed:"
if git log --all --name-only --format= | grep -E '\.(jks|keystore|p12|pfx|pem)$|google-services\.json$|service[-_]account.*\.json$|\.b64$'; then hits=1; fi
[ "$hits" = 0 ] && echo "clean" || { echo "MATCHES FOUND (see above)"; exit 1; }
