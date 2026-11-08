# PrompTugal__App__Android

Repository for android.app.promptugal.ai: PrompTugal's Android apps. The first is **RiskMandate**.

- `app/`: the RiskMandate app (Kotlin, Jetpack Compose). One native screen; opens riskmandate.ai in a Custom Tab.
- `.github/workflows/android.yml`: on every push to `dev`, unit tests and lint, then release APK and AAB; signed and distributed once the secrets exist.
- `scripts/make-upload-key.sh`: for Dinis to run on his own machine (the upload key; Play App Signing holds the app key).
- `board/`: this repository's board (status, keys, debriefs).

Build locally (JDK 17, Android SDK 35):

```
./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease
```

Work goes to `dev`. `main` is only ever updated by Dinis, through a pull request.
