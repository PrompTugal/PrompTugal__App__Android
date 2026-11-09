# dev.stand.android status

> Feature D (stand mode) built on `work/dev.stand`. Gate green: `./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease`.

## Doing
Waiting for review (architect, QA, security) and Dinis's word on the open questions below.

## Done
- `StandHost` now does the work, still the same signature (MainActivity untouched). No Feature object and no home-screen entry were needed.
- **Switch**: long-press on a 48dp invisible corner target, bottom-end (inside safe-drawing insets). Nothing is drawn while off; a 6dp faint dot shows while on. It has a TalkBack long-click label ("Staff controls"). Opens a dialog: on/off, idle minutes (1 to 30, default 3, stepper), status line (pinned or not), Android's unpin gesture.
- **On**: `startLockTask()` once per task (not again on rotation, not if already pinned), `FLAG_KEEP_SCREEN_ON`, idle timer. **Off**: `stopLockTask()` only if stand mode was on in this run (never unpins a pin we did not ask for, e.g. a future M2 kiosk), clears the flag.
- **Idle**: any touch (watched, not consumed) restarts the countdown; at N idle minutes `goHome()` once (nothing if already home), re-armed by the next touch. Counts only while the app is in front; returning from the browser after a long time resets at once.
- **Refusal / unavailable**: every call to Android is wrapped, nothing can crash. If the user refuses the prompt or screen pinning is off in Settings, the dialog says "not pinned"; keep-awake and idle reset still work.
- **Rotation / process death**: settings in private SharedPreferences (`stand_mode`); stand mode comes back on after a restart and asks to pin only if not already pinned.
- **Staff exit**: (1) Android's own gesture, written in the dialog and here: three-button navigation, hold Back and Overview together; gesture navigation, swipe up and hold (enter the lock-screen PIN if asked); (2) in-app long-press corner.
- **No PIN** (security checklist D.2: none unless Dinis asks). Removed after the first push, which had an optional salted-SHA-256 PIN. Exits: Android's own gesture, or the long-press corner. If Dinis wants a PIN: PBKDF2 in Keystore-backed storage, lockout, and a privacy-page change first.
- Tests (`app/src/test/.../features/stand/`): `IdleTimerTest` (pure JVM, fake clock), `StandSettingsTest` and `StandHostTest` (Robolectric/Compose: long-press, on/off, PIN gate, idle reset, refusal, throwing lock, pt-PT), `StandLocalisationTest`. English and pt-PT in `feature_stand.xml`.

## Next
Fixes from reviews. Not tested on a real tablet: the screen-pinning prompt itself needs a device (QA: first run per tablet, accept the prompt once; if "Screen pinning" is off in Settings, Security, turn it on).

## Asks
- Dinis: stand mode is off by default; staff turn it on per tablet by long-pressing the bottom-end corner. OK, or do you want it on by default for the summit build?
- app.android / architect: the corner target (48dp, bottom-end) overlaps whatever the home or feature screens put there. If a screen needs that corner, tell me which corner to use instead. design.android: any preference on corner or on a visible-for-staff cue?
- Not done by design: no home-screen entry or settings screen (not needed).
