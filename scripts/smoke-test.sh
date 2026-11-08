#!/usr/bin/env bash
# Emulator smoke test: install an APK, launch it, assert the home screen appears.
# Runs inside reactivecircus/android-emulator-runner (adb and a booted emulator are ready).
# Usage: scripts/smoke-test.sh <apk> <outdir>
# Writes <outdir>/screen.png, ui.xml, logcat.txt (the CI uploads them whether it passes or not).
set -uo pipefail
apk=${1:?apk}; out=${2:?outdir}
mkdir -p "$out"

aapt2=$(ls "${ANDROID_HOME:-$ANDROID_SDK_ROOT}"/build-tools/*/aapt2 | sort -V | tail -1)
pkg=$("$aapt2" dump badging "$apk" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
[ -n "$pkg" ] || { echo "could not read the application ID from $apk" >&2; exit 1; }
# What the home screen shows (HomeScreen.kt): the title and the Open button.
expect_title='RiskMandate'
expect_button='Open riskmandate.ai'

adb logcat -c || true
adb install -r "$apk" || { echo "install failed" >&2; adb logcat -d > "$out/logcat.txt"; exit 1; }
adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null

ok=1
for i in $(seq 1 30); do   # up to about 60 s for first launch
  sleep 2
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || continue
  adb pull /sdcard/ui.xml "$out/ui.xml" >/dev/null 2>&1 || continue
  if grep -q "text=\"$expect_button\"" "$out/ui.xml" && grep -q "text=\"$expect_title\"" "$out/ui.xml"; then
    ok=0; break
  fi
done

adb exec-out screencap -p > "$out/screen.png" 2>/dev/null || true
adb logcat -d > "$out/logcat.txt" || true

if [ $ok -ne 0 ]; then
  echo "FAIL: home screen not seen for $pkg (expected '$expect_title' and '$expect_button')" >&2
  grep -E "FATAL EXCEPTION|ANR in" "$out/logcat.txt" | head -5 >&2 || true
  exit 1
fi
# The process must still be alive (no crash right after drawing).
sleep 2
adb shell pidof "$pkg" >/dev/null || { echo "FAIL: $pkg died after launch" >&2; exit 1; }
echo "OK: home screen of $pkg is showing"
