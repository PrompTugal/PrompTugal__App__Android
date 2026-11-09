#!/usr/bin/env bash
# Did anything the app is built from change since the LATEST DEV RELEASE? (decides whether `release` builds)
# Usage: scripts/changed-since-release.sh <sha>   (needs full history and tags)
# Compares <sha> with the commit of the newest v<number> tag that is an ancestor of it (the last build that was
# actually released), NOT with the previous push: a run that was cancelled or failed before releasing must not
# drop its changes. Same wide file list as changed-build-paths.sh (what CI builds, including tests and CI scripts).
# Exit 0 and print the files (or the reason): yes, build. Exit 1: no, nothing to release.
# No release tag yet (unsigned builds make no tag) counts as changed.
set -euo pipefail
sha=${1:?sha}
here=$(cd "$(dirname "$0")" && pwd)
tag=$(git tag --merged "$sha" --list 'v[0-9]*' --sort=-version:refname | head -n 1)
if [ -z "$tag" ]; then echo "(no release tag is an ancestor of $sha: assuming the build changed)"; exit 0; fi
tagged=$(git rev-parse "$tag^{commit}")
echo "(latest dev release: $tag at ${tagged:0:12})"
bash "$here/changed-build-paths.sh" "$tagged" "$sha"
