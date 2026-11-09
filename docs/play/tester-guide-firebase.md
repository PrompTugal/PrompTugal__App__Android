# Tester guide: installing RiskMandate from Firebase App Distribution (M1)

For testers and for Dinis. Written 8 November 2026 by play.android@promptugal.ai. English first, then European Portuguese (pt-PT). Nothing here has been run on a device by me: the Firebase project and the signing secrets do not exist yet (`REALITY.md`), so there is no build to install. Menu names on phones differ by maker and Android version; where I give them they are from my own knowledge unless a URL is shown.

## What was checked, and when (read 8 November 2026)

| Fact | Source |
|---|---|
| When a build is distributed the tester "automatically receives an email invitation to test the app". Testers have 30 days to accept before the invitation expires; the developer can resend it. | https://firebase.google.com/docs/app-distribution/android/distribute-console |
| Existing testers "receive email notifications that a new build is ready to test". Builds stay in the console 150 days; expiry notices show in the console and on testers' devices 30 days before. | same page |
| The tester opens the invitation email on the Android device, taps Get Started, signs in with a Google account and taps Accept invitation in the tester web app. | https://firebase.google.com/codelabs/appdistribution-android (via search result, 8 November 2026) |
| On the Android device, go to appdistribution.firebase.google.com, sign in, and use **Download App Tester** to install the App Tester app. If prompted, enable installs from unknown sources, then tap Install. | same codelab (search result excerpt) |
| The Google account used in App Tester must match the address that was invited. A tester who accepted with another address may not see the right builds; fix: remove the tester in Firebase, re-invite, accept with the invited address. Emails can land in spam; a tester who has not accepted the first invitation gets no release emails. Pending invitations can also be seen in the "Firebase App Distribution web clip", if the account matches. | https://firebase.google.com/docs/app-distribution/troubleshooting?platform=android |

**Not found in Firebase's pages:** the minimum Android version for App Tester, the exact on-device screens for Android 8 to 15, and anything about installing over a build signed with a different key. Those parts below are marked "from my knowledge" and must be checked on the first real device (`device-test-checklist.md`).

## English

### Before you start
- You need a Google account and an Android phone or tablet (Android 8.0 or later is the app's minimum: `minSdk 26`).
- Dinis adds your Google account email to the tester group (`team`). You get an email from Firebase App Distribution. It may be in spam. It expires after 30 days; ask for a new one if it has.
- Use the same Google account everywhere below.

### First time
1. On the device you will test on, open the invitation email and tap **Get Started**.
2. Sign in with the invited Google account and tap **Accept invitation**.
3. The page offers **Download App Tester**. Tap it and install the App Tester app. If the phone asks to allow installs from this source ("unknown sources", on Android 8 and later it is a switch per app: "Allow from this source" for your browser or for App Tester), allow it. Turn it off again afterwards if you prefer (Settings, Apps, Special access, Install unknown apps; names vary).
4. Open App Tester. Find RiskMandate, tap the latest build, and tap **Install**. Confirm any security prompt (Play Protect may ask to scan the app; allow the scan).
5. Open RiskMandate. The home screen shows "Build 0.1.<number>" at the bottom: that number is what to quote when you report a problem.

### Every later build
- You get an email, and App Tester shows the new build. Open it and tap Install again; the new build replaces the old one and keeps the app's few small settings (side menu, stand mode, What's new, chat history if you used the chat).
- Builds stay available for 150 days.

### What you should see, by Android version (from my knowledge, to be confirmed on devices)
- **Android 8 to 12:** the "install unknown apps" prompt is a per-app setting; after you allow it, the system installer shows Install.
- **Android 13 to 15:** the same, and App Tester may ask permission to show notifications; allow it to hear about new builds, or rely on the email. Play Protect may show "Scan app" for an app from outside the Play Store.
- The app itself looks the same on every version; the layout changes with screen size (two columns in landscape on tablets), and follows light and dark and the language (English or Portuguese) of the device.

### If you have the Play Store version of RiskMandate
A Firebase build is signed with the **upload key**. A build from Google Play is signed by **Google's** app-signing key (`docs/lessons.md`). Android refuses to install one over the other ("App not installed" or "Package conflicts with an existing package"; the wording varies). So:
1. Uninstall RiskMandate first (long-press the icon, Uninstall; or Settings, Apps, RiskMandate, Uninstall).
2. Then install the other one.
The app keeps only a few small settings and, if you used it, the chat history, and an uninstall deletes them; nothing else is lost. The same applies the other way round, and when leaving a Play test (Play's help: to go back to the public build "you uninstall and reinstall the public version", https://support.google.com/googleplay/answer/15654751).

### What the app keeps on your device
A few small settings (side menu, stand mode, What's new, the chosen model), the chat history if you used the chat, and, only if staff use the Workbench (Diagnostics, Preview), a run log with the settings and figures of each test run and the first 200 characters of each prompt and 300 of each answer (500 runs at most; a Clear button in the Workbench deletes it). None of it is sent anywhere or backed up. An uninstall deletes it.

### If something goes wrong
| Problem | Try |
|---|---|
| No email | Look in spam; ask Dinis to resend. Check the invited address. |
| App Tester shows no apps | You are signed in with a different Google account than the invited one. Sign in with the invited one, or ask Dinis to re-invite you. |
| "App not installed" | Uninstall a Play build, or any older copy, then retry (signature conflict, above). |
| Install blocked | Allow installs from this source for App Tester or your browser. Work devices managed by an organisation may block this; say so. |
| Nothing happens when you tap Open riskmandate.ai | No browser installed or the browser is disabled; the app then shows a message. Tell Dinis the message. |

When you report: build number (bottom of the home screen), device model, Android version (Settings, About), what you did, what you saw. Never send a password, a key or a screenshot of an account page.

## Português (pt-PT)

### Antes de começar
- Precisa de uma conta Google e de um telemóvel ou tablet Android (Android 8.0 ou posterior é o mínimo da aplicação: `minSdk 26`).
- O Dinis adiciona o email da sua conta Google ao grupo de testadores (`team`). Recebe um email do Firebase App Distribution, que pode ir parar ao spam. O convite expira ao fim de 30 dias; peça um novo, se for o caso.
- Use sempre a mesma conta Google.

### Primeira vez
1. No dispositivo em que vai testar, abra o email do convite e toque em **Get Started** (Começar).
2. Inicie sessão com a conta Google convidada e toque em **Accept invitation** (Aceitar convite).
3. A página oferece **Download App Tester** (Transferir o App Tester). Toque e instale a aplicação App Tester. Se o telemóvel pedir para permitir instalações desta origem ("fontes desconhecidas"; no Android 8 e posteriores é uma opção por aplicação: "Permitir desta origem" para o navegador ou para o App Tester), permita. Pode voltar a desativá-la depois (Definições, Aplicações, Acesso especial, Instalar aplicações desconhecidas; os nomes variam).
4. Abra o App Tester. Encontre o RiskMandate, toque na versão mais recente e em **Install** (Instalar). Confirme qualquer aviso de segurança (o Play Protect pode pedir para verificar a aplicação; permita a verificação).
5. Abra o RiskMandate. O ecrã inicial mostra "Build 0.1.<número>" em baixo: indique este número ao comunicar um problema.

### Cada versão seguinte
- Recebe um email e o App Tester mostra a nova versão. Abra-a e toque em Instalar; a nova versão substitui a anterior e mantém as poucas definições da aplicação (menu lateral, modo de suporte, Novidades, histórico do chat se o usou).
- As versões ficam disponíveis durante 150 dias.

### O que deve ver, por versão do Android (do meu conhecimento, a confirmar nos dispositivos)
- **Android 8 a 12:** o pedido de "instalar aplicações desconhecidas" é uma definição por aplicação; depois de a permitir, o instalador do sistema mostra Instalar.
- **Android 13 a 15:** o mesmo, e o App Tester pode pedir permissão para mostrar notificações; permita para saber de novas versões, ou conte com o email. O Play Protect pode mostrar "Verificar aplicação" para uma aplicação fora da Play Store.
- A aplicação é igual em todas as versões; a disposição muda com o tamanho do ecrã (duas colunas na horizontal nos tablets) e segue o modo claro ou escuro e o idioma (inglês ou português) do dispositivo.

### Se tem a versão da Play Store do RiskMandate
Uma versão do Firebase é assinada com a **chave de carregamento**. Uma versão da Google Play é assinada pela **chave de assinatura da Google**. O Android recusa instalar uma por cima da outra ("Aplicação não instalada" ou "Conflito com um pacote existente"; o texto varia). Por isso:
1. Desinstale primeiro o RiskMandate (prima o ícone, Desinstalar; ou Definições, Aplicações, RiskMandate, Desinstalar).
2. Depois instale a outra.
A aplicação guarda apenas algumas definições pequenas e, se o usou, o histórico do chat, e a desinstalação apaga-os; não se perde mais nada. O mesmo vale no sentido inverso e ao sair de um teste da Play.

### O que a aplicação guarda no seu dispositivo
Algumas definições pequenas (menu lateral, modo de suporte, Novidades, o modelo escolhido), o histórico do chat se o usou e, só se a equipa usar o Workbench (Diagnóstico, Pré-visualização), um registo das execuções de teste com as definições e os números de cada uma e os primeiros 200 caracteres de cada pergunta e 300 de cada resposta (no máximo 500 execuções; o botão Limpar no Workbench apaga-o). Nada disto é enviado nem copiado para a nuvem. A desinstalação apaga tudo.

### Se algo correr mal
| Problema | Experimente |
|---|---|
| Não chegou o email | Veja o spam; peça ao Dinis para reenviar. Confirme o endereço convidado. |
| O App Tester não mostra aplicações | Está com uma conta Google diferente da convidada. Entre com a convidada, ou peça novo convite. |
| "Aplicação não instalada" | Desinstale a versão da Play ou qualquer cópia antiga e tente de novo (conflito de assinatura, acima). |
| Instalação bloqueada | Permita instalações desta origem para o App Tester ou o navegador. Dispositivos de trabalho geridos por uma organização podem bloquear; diga que é o caso. |
| Nada acontece ao tocar em Open riskmandate.ai | Não há navegador ou está desativado; a aplicação mostra então uma mensagem. Diga ao Dinis qual. |

Ao comunicar: número da versão (em baixo no ecrã inicial), modelo do dispositivo, versão do Android (Definições, Acerca), o que fez, o que viu. Nunca envie uma palavra-passe, uma chave ou uma captura de ecrã de uma página de conta.

*O texto em português foi escrito por mim, uma IA; peça a um falante nativo que o reveja antes de o enviar a testadores.*
