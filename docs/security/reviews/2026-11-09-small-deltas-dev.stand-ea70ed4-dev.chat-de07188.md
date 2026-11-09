# Reviews of two small deltas

By security.android@promptugal.ai, 9 November 2026.

- **work/dev.stand ea70ed4** (Verdict: PASS): the idle countdown's loop now runs in the effect's own coroutine and checks `lifecycle.currentState.isAtLeast(RESUMED)` each second instead of `repeatOnLifecycle`, and the first run of the effects no longer restarts the timer. Behaviour is unchanged (the countdown is measured on the clock, so a long absence still resets at once); no new permission, call or storage; pinning code untouched. The loop ends when stand mode is switched off (`LaunchedEffect(enabled)`).
- **work/dev.chat de07188** (Verdict: PASS): the `ChatStore` comment now says `noBackupFilesDir/feature_chat` (R-chat-1 closed) and a new `BackupRulesTest` fails unless `data_extraction_rules.xml` excludes all nine domains in cloud backup and device transfer (and `backup_rules.xml` its set): a build guard for "nothing the app stores may reach a backup". The chat stays disabled. R-chat-2 (history across the stand idle reset, Dinis) and the other chat items are unchanged.
