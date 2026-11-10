#!/usr/bin/env bash
# Resolves a SHA that a person typed (7 to 40 hex digits) to exactly ONE full commit SHA, ignoring every ref.
# `git rev-parse <hex>` prefers a branch or tag with that name over the object, so a ref named like 7 hex digits
# could make a short input mean another (even an approved) commit (security's R-rel-9). This asks git for the
# objects whose NAME starts with the digits (`--disambiguate`, no refs involved), keeps the commits, and
# requires exactly one. Usage: scripts/resolve-commit.sh <hex>   -> prints the full SHA, exit 1 otherwise.
# MIN_HEX (default 7) exists for the tests only: the workflow never sets it.
set -euo pipefail
p=${1:?hex digits}; min=${MIN_HEX:-7}
if ! [[ "$p" =~ ^[0-9a-f]+$ ]] || [ "${#p}" -lt "$min" ] || [ "${#p}" -gt 40 ]; then
  echo "must be $min to 40 lower-case hex digits (a commit SHA), got: $p" >&2; exit 1
fi
found=""; n=0
for o in $(git rev-parse --disambiguate="$p"); do
  if [ "$(git cat-file -t "$o" 2>/dev/null)" = commit ]; then found=$o; n=$((n + 1)); fi
done
[ "$n" -eq 1 ] || { echo "$p names $n commits in this repository (it must name exactly one)" >&2; exit 1; }
echo "$found"
