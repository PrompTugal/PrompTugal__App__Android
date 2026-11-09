#!/usr/bin/env bash
# Release notes from the commit subjects since the last release tag (v<number>...), as Markdown on stdout.
# Usage: scripts/release-notes.sh [version-name]   (needs full history and tags: fetch-depth 0)
# The tag being released (v<version-name>) is excluded when looking for the previous one, so re-running
# on a commit that is already tagged still lists the right range.
# No previous tag: the last MAX_COMMITS commits. Output is capped at MAX_CHARS (Firebase limits notes).
set -euo pipefail
version=${1:-}
MAX_COMMITS=${MAX_COMMITS:-30}
MAX_CHARS=${MAX_CHARS:-3500}

args=(--tags --abbrev=0 --match 'v[0-9]*')
[ -n "$version" ] && args+=(--exclude "v$version")
if prev=$(git describe "${args[@]}" HEAD 2>/dev/null); then
  range="$prev..HEAD"; since="since $prev"
else
  prev=""; range="HEAD"; since="(no earlier release tag: last $MAX_COMMITS commits)"
fi

# Subject format is "<role>@promptugal.ai: <what>" (until 9 Nov 2026, about 11:00 UTC) or
# "<role>@promptugal.ai.dinis: <what>" (after the rename; both are accepted): show "<what> (<role>, <sha>)". Git merges and the
# conductor's "merge <branch>" commits are noise: the commits they bring in are listed themselves.
lines=$(git log --no-merges --max-count="$MAX_COMMITS" --pretty='%h%x09%s' "$range" \
  | awk -F'\t' '
      { sha=$1; subj=$2; role=""
        if (match(subj, /^[a-z0-9._-]+@promptugal\.ai(\.dinis)?: /)) {
          role=substr(subj, 1, index(subj, "@")-1); subj=substr(subj, RLENGTH+1) }
        if (subj ~ /^merge /) next
        printf "- %s (%s%s)\n", subj, (role != "" ? role ", " : ""), sha }')

{
  [ -n "$version" ] && echo "RiskMandate $version $since"
  [ -n "$version" ] && echo
  if [ -n "$lines" ]; then echo "$lines"; else echo "- no new commits"; fi
} | awk -v max="$MAX_CHARS" '
    { len += length($0) + 1; if (len > max) { cut++; next } print }
    END { if (cut) printf "- ...and %d more lines, see the git log\n", cut }'
