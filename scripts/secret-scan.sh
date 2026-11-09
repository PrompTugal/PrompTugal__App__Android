#!/usr/bin/env bash
# Greps the working tree and EVERY commit reachable from ANY ref (all local branches, all origin/*
# branches, tags, stashes: `git rev-list --all`) for secret-shaped strings and forbidden file names.
# Prints file/commit and the pattern class only, never the matched text. Exit 1 if anything matches.
# Run before every push and every release:  scripts/secret-scan.sh [--fetch]
#   --fetch  runs `git fetch --all --prune --quiet` first so other sessions' branches are included.
# The script itself is excluded (it contains the patterns); two exact website files are excluded for the
# "private key block" class only (see excl_pkb). No test directory is excluded: test fixtures must not look
# like real secrets (build keys at run time, as devices/test does).
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
  'OpenRouter key|sk-or-v[0-9]+-[A-Za-z0-9]{20,}'
  'Hugging Face token|hf_[A-Za-z0-9]{30,}'
  'password in a URL|[a-z]+://[^/[:space:]:@]+:[^/[:space:]@$<{][^/[:space:]@]{3,}@[a-z0-9.-]+'
  'keystore password assignment|(store|key)(Password|_password)[[:space:]]*[=:][[:space:]]*["'"'"'][^"'"'"'$ {]{4,}["'"'"']'
)
# A password that starts with $, { or < is a variable or template reference (${TOKEN}, {{ x }}, <token>), not a literal.
excl=(-- . ':!scripts/secret-scan.sh')
# Files that DEFINE a secret pattern as a regex string (the website validator and its test) would match the
# "private key block" class on their own text. They are skipped for that one class only, and still scanned for
# every other class. Keep this list to exact paths; any new entry is reviewed by security.
excl_pkb=(-- . ':!scripts/secret-scan.sh' ':!website/validate.py' ':!website/test_site.py')
exclfor() { [ "$1" = 'private key block' ] && echo "excl_pkb" || echo "excl"; }
hits=0

refs=$(git for-each-ref --format='%(refname:short)' | tr '\n' ' ')
ncommits=$(git rev-list --all | wc -l | tr -d ' ')
echo "refs scanned: ${refs:-none}"
echo "commits scanned: $ncommits"

echo "working tree (tracked files):"
for c in "${classes[@]}"; do
  name=${c%%|*}; re=${c#*|}
  if [ "$name" = 'private key block' ]; then ex=("${excl_pkb[@]}"); else ex=("${excl[@]}"); fi
  files=$(git grep -lIE "$re" "${ex[@]}" 2>/dev/null) && { echo "  [$name] $files"; hits=1; }
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
    if [ "$name" = 'private key block' ]; then ex=("${excl_pkb[@]}"); else ex=("${excl[@]}"); fi
    out=$(git grep -lIE "$re" "$c" "${ex[@]}" 2>/dev/null) && { echo "$out" | sed "s|^|  [$name] |"; hits=1; }
  done
done

echo "forbidden file names ever committed (any ref):"
if git log --all --name-only --format= | sort -u | grep -E '\.(jks|keystore|p12|pfx|pem|key)$|(^|/)google-services\.json$|service[-_]account.*\.json$|\.b64$|(^|/)\.env(\..*)?$|(^|/)out/(enrol|web-token)-'; then hits=1; fi

[ "$hits" = 0 ] && echo "clean" || { echo "MATCHES FOUND (see above)"; exit 1; }
