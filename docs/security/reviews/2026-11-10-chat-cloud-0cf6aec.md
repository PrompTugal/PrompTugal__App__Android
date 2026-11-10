# Security review: chat's cloud use (dev.chat 0cf6aec), checklist M.3 to M.7

Date: 10 Nov 2026. Reviewer: security.android@promptugal.ai.dinis. Code: `features/chat/` as on dev f624e4e. Architect's review: `docs/architecture/reviews/2026-11-10-dev.chat-0cf6aec-cloud.md` (PASS).

Verdict: PASS (the flip may be made once the condition below is met)

This verdict covers turning `CLOUD_REVIEWED` to true. It is not an approval of a build: the flip is its own commit and its own head review.

## Against checklist M

| Item | Result |
|---|---|
| M.3.1 what is sent | The current message plus the system block; earlier messages only with "send earlier messages" (off, never saved, reset on new chat, clear and source switch). `previewSend` and the real send use one builder (`buildRequest`), tested (`whatWillBeSentIsExactlyWhatIsSent`). No device, version, account or page data. The checklist's "view before the first send": the view is a button, not a gate (R-chat-5) |
| M.3.4 model allow-list | fixed list in `CloudEndpoint`; Chat offers only what the cloud service reports ready |
| M.4.1 and M.4.2 off by default, stand mode | the host gives Chat a cloud service only with key stored, switch on (memory only) and stand mode off; Chat is hidden in stand mode by the allow-list as well; `standModeLeavesOnlyTheDevice` |
| M.4.3 badge and on-device lines | a chip says "Cloud: openrouter.ai" and the model or "Runs on this device", never both; each saved answer carries `via`; on-device lines are not shown with a cloud answer on screen (`noOnDeviceLineIsShownWhileACloudAnswerIsOnScreen`); Chat strings no longer claim "stays on this device" |
| M.5 spend | caps are in `CloudLimits`, not on any screen; cost comes from the service, shown per answer; `CAP_REACHED` is a failure with fixed text and one tap to the device (`aCapStopIsAFailureWithItsReasonAndTheDeviceIsOneTapAway`). Numbers still unconfirmed (R-cloud-11) |
| M.6 response | Chat renders plain text, links only through `Links.isAllowed`, no content logging; the transport caps and redirect refusal are in `CloudTransport`/`CloudLlmService` already reviewed |
| M.7 tests | present for: history not in the request unless chosen, preview equals send, base plus reference only on the cloud route, source label and cost saved, cap stop, stand mode, a non-cloud service handed in as cloud ignored, pt-PT |
| Dormant today | `CLOUD_REVIEWED = false`; "chat" not yet in `CloudHostTest` (app's list) |

## Findings

| Id | Severity | Location | Fix | Owner |
|---|---|---|---|---|
| R-chat-5 | LOW | `ChatController.start`: when the cloud is available it is the default, and the first send of a session does not show the preview | Acceptable for staff-only use with the badge always visible. Better: first cloud send of a session opens "What the cloud would receive" once, as M.3.1 says | dev.chat |
| R-chat-6 | LOW | the system block on the cloud route carries the bundled riskmandate.ai texts | Not secret (public pages); the Data safety answer must say the message and these texts go to OpenRouter (the play text does) | play |

## Condition for the flip

1. app adds "chat" to `CloudHostTest`'s reviewed list (the host test, with FeatureContextTest already updated).
2. dev.chat flips `CLOUD_REVIEWED` in a commit that changes nothing else.
3. Before any store build that contains the cloud: Dinis answers the Data safety form (R-play-9) and the credit limit is set on each tablet's OpenRouter key.

## Summary

Chat's cloud route sends only what staff chose, says where each answer came from, is refused in stand mode and stops at spend caps. I found two LOW items and no blocker. PASS for the flip once the two lists name chat.
