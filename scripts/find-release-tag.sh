#!/usr/bin/env bash
# The dev GitHub release whose build is the one to upload to Google Play for a main commit.
# Usage: scripts/find-release-tag.sh <sha>   (needs full history and tags)
# Prints the newest v<number> tag that is an ancestor of (or equal to) <sha> with NO build-relevant change
# between the tag and <sha> (scripts/changed-build-paths.sh), so a docs-only commit on top of a built commit
# uses that build. Exit 1 if there is none.
set -euo pipefail
sha=${1:?sha}
here=$(cd "$(dirname "$0")" && pwd)
for tag in $(git tag --merged "$sha" --list 'v[0-9]*' --sort=-version:refname); do
  tagged=$(git rev-parse "$tag^{commit}")
  if ! bash "$here/changed-build-paths.sh" "$tagged" "$sha" >/dev/null; then echo "$tag"; exit 0; fi
done
echo "no release tag matches $sha: no dev build of this exact app exists" >&2
exit 1
