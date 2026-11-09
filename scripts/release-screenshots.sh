#!/usr/bin/env bash
# Puts the Robolectric screenshots into the in-app release articles (feature F), so CI builds them
# into the APK and AAB without anybody committing a generated PNG.
#
# Contract: each article is a folder app/src/main/assets/releases/<dir>/. If it has a file
# screenshots.txt, every line is   <target>.png <- <source>.png   where <source> is a file the
# screenshot tests wrote to app/build/screenshots/ and <target> is the name the article uses; the copy
# is app/src/main/assets/releases/<dir>/screenshots/<target>.png. Blank lines and # comments are
# ignored. Names are [A-Za-z0-9._-] only, ending .png. A listed source that does not exist is an error.
#
# Usage: scripts/release-screenshots.sh           copy (release job, before assembleRelease/bundleRelease)
#        scripts/release-screenshots.sh --verify  only check that every source exists (check job)
# The copies are generated and must not be committed: app/src/main/assets/releases/*/screenshots/ belongs in .gitignore.
set -euo pipefail
cd "$(dirname "$0")/.."
mode=${1:-copy}
src_dir=app/build/screenshots
rel_dir=app/src/main/assets/releases
ok='^[A-Za-z0-9._-]+\.png$'
errors=0; copied=0; articles=0

for manifest in "$rel_dir"/*/screenshots.txt; do
  [ -f "$manifest" ] || continue
  dir=$(dirname "$manifest"); articles=$((articles+1))
  while IFS= read -r line || [ -n "$line" ]; do
    line=${line%%#*}
    [ -z "${line//[[:space:]]/}" ] && continue
    if [[ "$line" =~ ^[[:space:]]*([^[:space:]]+)[[:space:]]*\<-[[:space:]]*([^[:space:]]+)[[:space:]]*$ ]]; then
      target=${BASH_REMATCH[1]}; source=${BASH_REMATCH[2]}
    else
      echo "ERROR $manifest: cannot read line: $line (want: <target>.png <- <source>.png)" >&2; errors=$((errors+1)); continue
    fi
    if ! [[ "$target" =~ $ok && "$source" =~ $ok ]]; then
      echo "ERROR $manifest: bad name in: $line" >&2; errors=$((errors+1)); continue
    fi
    if [ ! -f "$src_dir/$source" ]; then
      echo "ERROR $manifest: $src_dir/$source does not exist (the screenshot tests did not write it)" >&2; errors=$((errors+1)); continue
    fi
    if [ "$mode" != --verify ]; then
      mkdir -p "$dir/screenshots"
      cp "$src_dir/$source" "$dir/screenshots/$target"
    fi
    copied=$((copied+1))
  done < "$manifest"
done

[ $errors -eq 0 ] || { echo "release screenshots: $errors error(s)" >&2; exit 1; }
if [ "$mode" = --verify ]; then echo "release screenshots: $copied listed in $articles article(s), all sources exist"
else echo "release screenshots: $copied copied into $articles article(s)"; fi
