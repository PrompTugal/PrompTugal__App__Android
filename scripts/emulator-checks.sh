#!/usr/bin/env bash
# Everything the emulator job runs on one booted emulator, so the boot cost is paid once:
#   1. scripts/smoke-test.sh: the release APK installs and shows the home screen;
#   2. ./gradlew connectedDebugAndroidTest: app.android's SmokeTest (home, licences, diagnostics screens).
# Both always run; the script fails if either did. Usage: scripts/emulator-checks.sh <release-apk> <outdir>
set -uo pipefail
apk=${1:?release apk}; out=${2:?outdir}
mkdir -p "$out"
aapt2=$(ls "${ANDROID_HOME:-$ANDROID_SDK_ROOT}"/build-tools/*/aapt2 | sort -V | tail -1)
pkg=$("$aapt2" dump badging "$apk" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")

bash scripts/smoke-test.sh "$apk" "$out"; launch=$?
# The debug build has the same application ID but another signing key: remove the release APK first,
# or the install of the instrumented build fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE.
[ -n "$pkg" ] && adb uninstall "$pkg" >/dev/null 2>&1

./gradlew --no-daemon --console=plain connectedDebugAndroidTest; inst=$?

res() { [ "$1" -eq 0 ] && echo ok || echo FAILED; }
echo "launch of the release APK: $(res $launch)" | tee "$out/result.txt"
echo "instrumented SmokeTest (connectedDebugAndroidTest): $(res $inst)" | tee -a "$out/result.txt"
[ $launch -eq 0 ] && [ $inst -eq 0 ]
