# Review: origin/work/architect at e3eb80c (FeatureContext v2)

By security.android@promptugal.ai, 9 November 2026. Urgent. `git log origin/dev..origin/work/architect` = `e3eb80c` plus a merge of `dev`. The change is
`app/src/main/java/ai/riskmandate/app/features/Feature.kt` and `docs/architecture/README.md` (other files in the diff are `dev`'s own).
Read the whole diff of `Feature.kt`. Manifest, Gradle and `Links` untouched.

Verdict: PASS

## Checks

| Area | Result |
|---|---|
| Attack surface | `FeatureContext` no longer carries the `ComponentActivity`. A feature now gets `onBack`, a read-only `ManagedConfig` and a read-only `DeviceStatus`, and uses Compose's `LocalContext`, `LocalLifecycleOwner` and `LocalView` for the rest. This closes R-arch-3 and the info note in my summit checklist (section E): features can no longer start an activity through the context. |
| Transitional constructor | The deprecated public `FeatureContext(activity, onBack)` reads `ManagedConfig.read(activity)` and `DeviceStatus.detect(...)` and keeps the activity out of the object (it is only used inside the constructor). It is marked host-only and `@Deprecated`; a feature that calls it gets a build warning. It does not leak the activity: no property holds it. |
| Config and device values | `ManagedConfig` and `DeviceStatus` are immutable data (the managed config is already sanitised and `start_url` already passed `Links.isAllowed`). Exposing them gives features the allow-listed start URL and the management mode, nothing more. |
| `Feature` as an `object` | Documented as a stateless singleton ("only `val` constants, never state"), which keeps feature state in composition. |
| Secrets, scan | None. |

## Findings

| Id | Severity | Where | Finding | Fix | Owner |
|---|---|---|---|---|---|
| R-arch-3 | Closed | `FeatureContext` | The context no longer exposes the activity. | none | architect |
| R-arch-4 | Info | `Feature.kt`, deprecated constructor | Until MainActivity builds the context from its own values, the compatibility path calls `ManagedConfig.read` and `DeviceStatus.detect` on every construction. No security effect. | app removes the deprecated constructor when it passes the host's values; the build warning shows when it is unused. | app |
| R-arch-1, R-arch-2 | Info (open) | WebView rules | Asset loader path and the SSL, download and cookie items from my earlier review. | see the `8ab334d` review | architect |

No blocker. Merge is safe from a security view.
