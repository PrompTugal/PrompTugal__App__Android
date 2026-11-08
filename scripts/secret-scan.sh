#!/usr/bin/env bash
# Greps the working tree and EVERY commit reachable from ANY ref (all local branches, all origin/*
# branches, tags, stashes: `git rev-list --all`) for secret-shaped strings and forbidden file names.
# Prints file/commit and the pattern class only, never the matched text. Exit 1 if anything matches.
# Run before every push and every release:  scripts/secret-scan.sh [--fetch]
#   --fetch  runs `git fetch --all --prune --quiet` first so other sessions' branches are included.
# Only the script itself is excluded (it contains the patterns). No test directory is excluded: test
# fixtures must not look like real secrets (build keys at run time, as devices/test does).
set -uo pipefail
cd "$(dirname "$0")/.."
[ "${1:-}" = "--fetch" ] && git fetch --all --prune --quiet

# name|extended regex  (names are printed, matches are not)
classes=(
  'private key block|PRIVATE KEY-----'
  'Google API key|AIza[0-9A-Za-z_-]{35}'
  'Google OAuth access token|ya29\.[0-9A-Za-z_-]{20,}'
  'service-account JSON|"type"[[:space:]]*:[[:space:]]*"service_account"'
  'service-account private_key|"private_key"[[:space:]]*:[[:space:]]*"-----'
  'GitHub token|gh[pousr]_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{20,}'
  'AWS access key id|(AKIA|ASIA)[0-9A-Z]{16}'
  'Slack token|xox[abprs]-[0-9A-Za-z-]{10,}'
  'npm token|npm_[A-Za-z0-9]{36}'
  'Firebase CI token|1//[0-9A-Za-z_-]{40,}'
  'Anthropic or OpenAI key|sk-(ant-)?[A-Za-z0-9_-]{32,}'
  'password in a URL|[a-z]+://[^/[:space:]:@]+:[^/[:space:]@]{4,}@[a-z0-9.-]+'
  'keystore password assignment|(store|key)(Password|_password)[[:space:]]*[=:][[:space:]]*["'"'"'][^"'"'"'$ {]{4,}["'"'"']'
)
excl=(-- . ':!scripts/secret-scan.sh')
hits=0

refs=$(git for-each-ref --format='%(refname:short)' | tr '\n' ' ')
ncommits=$(git rev-list --all | wc -l | tr -d ' ')
echo "refs scanned: ${refs:-none}"
echo "commits scanned: $ncommits"

echo "working tree (tracked files):"
for c in "${classes[@]}"; do
  name=${c%%|*}; re=${c#*|}
  files=$(git grep -lIE "$re" "${excl[@]}" 2>/dev/null) && { echo "  [$name] $files"; hits=1; }
done

echo "history (class: commit: file):"
# git grep per commit, tree by tree; identical trees are skipped.
declare -A seen=()
for c in $(git rev-list --all); do
  t=$(git rev-parse "$c^{tree}")
  [ -n "${seen[$t]:-}" ] && continue
  seen[$t]=1
  for k in "${classes[@]}"; do
    name=${k%%|*}; re=${k#*|}
    out=$(git grep -lIE "$re" "$c" "${excl[@]}" 2>/dev/null) && { echo "$out" | sed "s|^|  [$name] |"; hits=1; }
  done
done

echo "forbidden file names ever committed (any ref):"
if git log --all --name-only --format= | sort -u | grep -E '\.(jks|keystore|p12|pfx|pem|key)$|(^|/)google-services\.json$|service[-_]account.*\.json$|\.b64$|(^|/)\.env(\..*)?$|(^|/)out/(enrol|web-token)-'; then hits=1; fi

[ "$hits" = 0 ] && echo "clean" || { echo "MATCHES FOUND (see above)"; exit 1; }
