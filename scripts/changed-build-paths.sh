#!/usr/bin/env bash
# Did a change between two commits touch anything the app is built from?
# Usage: scripts/changed-build-paths.sh [--shipped] <from> <to>
# Exit 0 and print the files: yes (or <from> is unknown, all zeros, or not an ancestor: assume yes).
# Exit 1: no (docs, board, briefs, devices/, website/ and the like only).
# The same list as the build's docs-only rule (docs/ci.md): app/, gradle/, .github/, scripts/, *.gradle.kts,
# gradle.properties, gradlew, gradlew.bat.
#
# --shipped: only what can change the bytes of the signed APK and AAB, used by find-release-tag.sh to decide
# whether an earlier dev build is still the build of a later commit. It leaves out what is never packaged:
# the instrumented tests (app/src/androidTest/), .github/ and scripts/ (CI), except scripts/release-screenshots.sh
# (it puts the screenshots into the app's assets). It keeps app/src/test/: the Robolectric screenshot tests
# write the PNGs that the release articles carry. Without the flag the wider list decides whether CI builds.
set -euo pipefail
shipped=false
if [ "${1:-}" = --shipped ]; then shipped=true; shift; fi
from=${1:?from}; to=${2:?to}
if [[ "$from" =~ ^0+$ ]] || ! git cat-file -e "$from^{commit}" 2>/dev/null || ! git merge-base --is-ancestor "$from" "$to" 2>/dev/null; then
  echo "(base unknown or not an ancestor: assuming the build changed)"; exit 0
fi
files=$(git diff --name-only "$from" "$to" | grep -E '^(app|gradle|\.github|scripts)/|(^|/)[^/]*\.gradle\.kts$|^(gradle\.properties|gradlew|gradlew\.bat)$' || true)
if $shipped; then
  files=$(printf '%s\n' "$files" | grep -Ev '^(app/src/androidTest/|\.github/|scripts/)' || true)
  extra=$(git diff --name-only "$from" "$to" | grep -E '^scripts/release-screenshots\.sh$' || true)
  files=$(printf '%s\n%s\n' "$files" "$extra" | grep -v '^$' || true)
fi
[ -n "$files" ] && { echo "$files"; exit 0; }
exit 1
