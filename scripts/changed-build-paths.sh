#!/usr/bin/env bash
# Did a change between two commits touch anything the app is built from?
# Usage: scripts/changed-build-paths.sh <from> <to>
# Exit 0 and print the files: yes (or <from> is unknown, all zeros, or not an ancestor: assume yes).
# Exit 1: no (docs, board, briefs, devices/, website/ and the like only).
# The same list as the build's docs-only rule (docs/ci.md): app/, gradle/, .github/, scripts/, *.gradle.kts,
# gradle.properties, gradlew, gradlew.bat.
set -euo pipefail
from=${1:?from}; to=${2:?to}
if [[ "$from" =~ ^0+$ ]] || ! git cat-file -e "$from^{commit}" 2>/dev/null || ! git merge-base --is-ancestor "$from" "$to" 2>/dev/null; then
  echo "(base unknown or not an ancestor: assuming the build changed)"; exit 0
fi
files=$(git diff --name-only "$from" "$to" | grep -E '^(app|gradle|\.github|scripts)/|(^|/)[^/]*\.gradle\.kts$|^(gradle\.properties|gradlew|gradlew\.bat)$' || true)
[ -n "$files" ] && { echo "$files"; exit 0; }
exit 1
