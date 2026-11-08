#!/usr/bin/env bash
# Run on Dinis's own machine, never in an agent session.
# Makes the UPLOAD key for Play App Signing (Google keeps the app-signing key).
# Prints nothing secret: it writes upload.jks and upload.jks.b64 next to this script's working dir,
# then tells you which repository secrets to add. Keep a backup of upload.jks off this machine.
set -euo pipefail
command -v keytool >/dev/null || { echo "keytool not found (install a JDK 17)"; exit 1; }
[ -e upload.jks ] && { echo "upload.jks already exists here; not overwriting"; exit 1; }
read -r -s -p "Keystore password (min 6 chars): " P1; echo
read -r -s -p "Again: " P2; echo
[ "$P1" = "$P2" ] || { echo "passwords differ"; exit 1; }
keytool -genkeypair -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$P1" -keypass "$P1" -dname "CN=PrompTugal Android upload key" >/dev/null
base64 < upload.jks | tr -d '\n' > upload.jks.b64
unset P1 P2
cat <<MSG
Done. In GitHub: PrompTugal/PrompTugal__App__Android > Settings > Secrets and variables > Actions, add:
  UPLOAD_KEYSTORE_BASE64   = contents of upload.jks.b64
  UPLOAD_KEYSTORE_PASSWORD = the password you typed
  UPLOAD_KEY_ALIAS         = upload
  UPLOAD_KEY_PASSWORD      = the same password
Then delete upload.jks.b64 and store upload.jks somewhere safe (it is needed to rotate at Google if lost).
MSG
