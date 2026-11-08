# Release notes: guidance and template

For Dinis. Written 8 November 2026 by play.android@promptugal.ai. Covers Firebase App Distribution, Play internal testing, and later production, in English and European Portuguese (pt-PT). Nothing is published.

## Facts checked (8 November 2026)

- Play: "You can enter release notes using up to 500 Unicode characters per language." Each language goes between tags on their own lines (`<en-GB>` ... `</en-GB>`, `<pt-PT>` ... `</pt-PT>`); notes can span several lines. Entered under "What's new in this release?" (https://support.google.com/googleplay/android-developer/answer/9859348). For a language the app's listing does not have, Play shows what the Console accepts; localised notes need the language added to the listing first (not verified; check in the Console, and `listing.md` says the first listing is English only).
- Firebase App Distribution: release notes are added when distributing in the console ("Then, add release notes for the build.", https://firebase.google.com/docs/app-distribution/android/distribute-console). That page states no length limit and does not say where testers see them. Keep Firebase notes short and plain.
- What CI writes today (`.github/workflows/android.yml`): Firebase notes are `RiskMandate <versionName> from <sha>: <last commit subject>`, and the GitHub release notes `Built from <sha> on dev. Signed with the upload key.` The commit subject starts with the role address (for example `play.android@promptugal.ai: ...`), so tester notes are machine-style. Fine for M1. For anything people read, use the template below. A change to the workflow is release.android's to make; see `status/play.md`, "For release".

## Rules for every note

1. Say only what the build does or fixes. No claim for RiskMandate beyond what riskmandate.ai says; no "secure", "compliant", "insured", "enforces" or "protects".
2. No prices, "free", "new", "best" (the same rule as the listing).
3. No emails, names of testers, device IDs, organisation IDs, keys or internal URLs. Notes are public once the app is.
4. Say what changed for the user first. Internal detail (refactors, CI) only for testers.
5. Under 500 characters per language, counting the tags' content. Plain text, no Markdown, no emoji.
6. The English and Portuguese notes say the same thing. Portuguese is pt-PT (European): "ecrã", "transferir", "definições", not pt-BR "tela", "baixar", "configurações".
7. No em-dashes in prose.

## Firebase App Distribution (testers, M1)

Audience: the team's testers. Short, with what to check.

- Say the build number (CI's `versionName`), what changed, and what to try.
- Remind that a Firebase build is signed with the upload key; it cannot be installed over a Play build (uninstall first, `docs/lessons.md`).
- One line, "Known issues", if any.

## Play internal testing (testers, M1 to M3)

Audience: the testers on the internal list (up to 100 per app: "An internal test can have up to 100 testers per app", https://support.google.com/googleplay/android-developer/answer/9845334).

- Same content as Firebase, written as release notes in the Console (CI uploads a draft; Dinis completes the rollout and can edit the notes there).
- Internal builds are available "within minutes" per Google's page.

## Production (later, public)

Audience: anyone. Write for a user who has never heard of the project.

- Lead with the one change that matters to them; one to three short lines in all.
- Never a version number in the note (Play shows it).
- First public release: say what the app is in one line, matching the short description in `listing.md`: the app opens riskmandate.ai. Do not add anything that is not true of that build.
- If a change touches data (a permission, an SDK, a network call), the privacy policy and Data safety form change first (`data-safety.md`); the note mentions it.

## Template

Copy the block, fill in the brackets, delete lines that do not apply. Count the characters of each language body (500 maximum).

### Firebase and internal testing (English)

```
RiskMandate [versionName]
What changed: [one sentence, user-visible].
Please check: [one thing to try, for example: open riskmandate.ai; the Device section shows your label].
Known issues: [none | one sentence].
Note: this build is signed with the upload key; uninstall a Play build first (and the reverse).
```

### Firebase and internal testing (pt-PT)

```
RiskMandate [versionName]
O que mudou: [uma frase, visível para o utilizador].
Por favor, verifique: [uma coisa a experimentar, por exemplo: abrir riskmandate.ai; a secção Dispositivo mostra a sua etiqueta].
Problemas conhecidos: [nenhum | uma frase].
Nota: esta versão está assinada com a chave de carregamento; desinstale primeiro a versão da Play Store (e o inverso).
```

### Play Console, both languages in one box

```
<en-GB>
[One to three short lines. What changed for the user.]
</en-GB>
<pt-PT>
[Uma a três frases curtas. O que mudou para o utilizador.]
</pt-PT>
```

(Use `<en-US>` instead of `<en-GB>` if the listing's default language is American English. The tag must match a language on the listing; confirm in the Console.)

## Examples (not for use as is)

**First internal build**

```
<en-GB>
First internal test build. The app opens riskmandate.ai in your browser and shows whether your device is managed. Please check that the button opens the site and that the Device section is correct.
</en-GB>
<pt-PT>
Primeira versão de teste interno. A aplicação abre riskmandate.ai no seu navegador e mostra se o dispositivo é gerido. Verifique se o botão abre o site e se a secção Dispositivo está correta.
</pt-PT>
```

**A later fix**

```
<en-GB>
Fixed the page address check so that only riskmandate.ai over HTTPS opens. Added a list of open source licences.
</en-GB>
<pt-PT>
Corrigida a verificação do endereço, para que só abra riskmandate.ai por HTTPS. Adicionada uma lista de licenças de código aberto.
</pt-PT>
```

(The second example describes commits already on `dev`: `1da96dc` and `fix-app`. Re-check against the build before reusing.)

**Production, first public release** (wording waits on Dinis and the feature decision, `minimal-functionality.md`)

```
<en-GB>
First release. Opens riskmandate.ai in your browser. [TODO Dinis: any feature added by then.]
</en-GB>
<pt-PT>
Primeira versão. Abre riskmandate.ai no seu navegador. [TODO Dinis: qualquer funcionalidade acrescentada até lá.]
</pt-PT>
```

## Who does what

- Release notes for Firebase: CI writes the default; a person can overwrite them in the Firebase console.
- Play internal and production: Dinis enters them when he completes each rollout. An agent prepares the text (this file) and does not submit.
- Review the Portuguese with a Portuguese speaker before production. I wrote it; I am an AI and may have errors of register.
