# M3 guide: installing on a device we do not own

For Dinis and for the person lending the device. Written 8 November 2026 by play.android@promptugal.ai. English, then pt-PT. M3 is "install from a link (Play internal testing or Firebase)" (`briefs/2026-11-08__riskmandate-android/README.md`). Nothing has been set up: no Play account, no Firebase project exists yet.

## Facts (read 8 November 2026)

**Play internal testing**, https://support.google.com/googleplay/android-developer/answer/9845334:
- "An internal test can have up to 100 testers per app."
- "Users need a Google Account or a Google Workspace account to join a test."
- Testers are listed "by email address" (typed with commas, or a CSV). Google Groups are described only for closed tests, not internal.
- The opt-in link "appears only when the app status is Published"; after clicking it, "testers receive an explanation of tester responsibilities and a link to opt in".
- Testers "cannot find your app by searching Google Play" for internal or closed tests, so "You must share the app's Play Store URL with testers".
- After they install, "their app automatically updates to the test version within a few minutes". A new bundle "becomes available to testers within minutes". First upload: temporary app details for up to 48 hours. "Internal tests might not be subject to standard Play policy or security reviews."
- Leaving a test: Play Store lists the app under "You're a beta tester"; to return to the public build you uninstall and reinstall it (https://support.google.com/googleplay/answer/15654751, from a search excerpt).

**Firebase App Distribution**, https://firebase.google.com/docs/app-distribution/android/distribute-console: invitation by email, 30 days to accept, email on each new build, builds kept 150 days. Steps for the tester: `tester-guide-firebase.md`.

**Not found:** whether Play internal opt-in works on a work-profile or school-managed Google account (organisation policy can block testing programs; I found no page); the exact Play Console wording of the opt-in URL (the Console shows it).

## Which to use when

| | Firebase App Distribution | Play internal testing |
|---|---|---|
| Needs from the person | A Google account (any); installing the App Tester app; allowing installs from unknown sources | A Google account (or Workspace); the Play Store; opt-in once |
| Needs from us | A Firebase project and the keyless sign-in variables (`GCP_WIF_PROVIDER`, `GCP_SERVICE_ACCOUNT`, `FIREBASE_APP_ID`; `docs/ci.md`) plus the upload-key environment secrets. **No Play Console account** | A Play Console developer account, the first AAB uploaded by hand, the tester list (`README.md` steps 1 to 5) |
| Signed with | The upload key | Google's app-signing key |
| Update | Email, then Install in App Tester | Play Store updates it automatically, within minutes |
| Limits | None found; 30-day invitation, 150-day build life | 100 testers; Google account required; app not searchable |
| Review by Google | None | "might not be subject to standard Play policy or security reviews" |
| Friction for a stranger | Higher: unknown-sources prompt | Lower: it is the Play Store they know |
| Works today | After M1 secrets | After the Play account (D-U-N-S wait) |

**Rules of thumb**
- **A person who just needs to try it, soon:** Firebase (no Play account is needed).
- **A person who should not change phone security settings, or an organisation's staff:** Play internal testing, once it exists. An employer's managed phone may block unknown sources: say so up front.
- **Devices Dinis lends or that the agent owns (M2):** neither; managed install (`devices/README.md`).
- **Never mix them on one device** (signatures differ): uninstall first.
- **No secrets or personal data change hands in either route.** The app collects no personal data.

## What to tell the person lending the device (English)

1. "I will send you a link. It installs a test copy of an app called RiskMandate. It opens a website, riskmandate.ai, and shows whether the device is managed. It collects no data and has no account."
2. Firebase: open the email on this device, tap Get Started, accept with your Google account, install App Tester, install RiskMandate. If asked, allow installs from this source. Undo it afterwards under Special access, Install unknown apps.
3. Play: open the link on this device, signed in to the Google account I added, tap Become a tester, then install from the Play Store.
4. To remove it: Settings, Apps, RiskMandate, Uninstall. If you were in the Play test, also leave it (Play Store, search "RiskMandate" via the link, "Leave").
5. Tell me the build number at the bottom of the home screen if anything is wrong.

## O que dizer à pessoa que empresta o dispositivo (pt-PT)

1. "Vou enviar-lhe uma ligação. Instala uma cópia de teste de uma aplicação chamada RiskMandate. Abre um site, riskmandate.ai, e mostra se o dispositivo é gerido. Não recolhe dados e não tem conta."
2. Firebase: abra o email neste dispositivo, toque em Get Started, aceite com a sua conta Google, instale o App Tester e depois o RiskMandate. Se pedir, permita instalações desta origem. Desfaça depois em Acesso especial, Instalar aplicações desconhecidas.
3. Play: abra a ligação neste dispositivo, com a conta Google que adicionei, toque em Tornar-se testador e instale pela Play Store.
4. Para remover: Definições, Aplicações, RiskMandate, Desinstalar. Se estava no teste da Play, saia também do teste (Play Store, pela ligação, "Sair").
5. Diga-me o número da versão em baixo no ecrã inicial se algo estiver errado.

*Português escrito por uma IA; deve ser revisto por um falante nativo. Os nomes dos botões do Android variam por versão e fabricante.*
