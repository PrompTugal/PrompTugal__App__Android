#!/usr/bin/env bash
# Test for scripts/release-notes.sh, in a throwaway git repository: both subject forms
# (@promptugal.ai: and @promptugal.ai.dinis:), the role and sha shown, merge commits dropped, the range
# since the last v* tag, and the tag being released excluded. Run: bash scripts/test-release-notes.sh
set -euo pipefail
script=$(cd "$(dirname "$0")" && pwd)/release-notes.sh
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
cd "$tmp"
git init -q . && git config user.email t@example.invalid && git config user.name t && git config commit.gpgsign false
c() { git commit -q --allow-empty -m "$1"; }
c "conductor.android@promptugal.ai: old commit before the tag"
git tag v0.1.5
c "app.android@promptugal.ai: old-form subject"
c "app.android@promptugal.ai.dinis: new-form subject"
c "conductor.android@promptugal.ai.dinis: merge work/app at abc1234 (security: PASS)"
c "conductor.android@promptugal.ai: merge work/play"
c "a plain subject with no role"
out=$(bash "$script" 0.1.9)
fail() { echo "FAIL: $1" >&2; echo "$out" >&2; exit 1; }
echo "$out" | grep -q '^RiskMandate 0.1.9 since v0.1.5$' || fail "header"
echo "$out" | grep -Eq '^- old-form subject \(app\.android, [0-9a-f]+\)$' || fail "old form not parsed"
echo "$out" | grep -Eq '^- new-form subject \(app\.android, [0-9a-f]+\)$' || fail "new form not parsed (role must not keep the domain)"
echo "$out" | grep -Eq '^- a plain subject with no role \([0-9a-f]+\)$' || fail "plain subject"
echo "$out" | grep -q 'promptugal' && fail "an address leaked into the notes"
echo "$out" | grep -q 'merge ' && fail "a merge commit was listed"
echo "$out" | grep -q 'old commit before the tag' && fail "a commit from before the tag was listed"
# The tag being released is excluded: tagging HEAD as v0.1.9 and asking for 0.1.9 still lists the range.
git tag v0.1.9
out=$(bash "$script" 0.1.9)
echo "$out" | grep -q '^RiskMandate 0.1.9 since v0.1.5$' || fail "tag being released not excluded"
echo "release-notes test: ok"
