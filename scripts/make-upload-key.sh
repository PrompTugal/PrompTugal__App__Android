#!/usr/bin/env bash
# Run on Dinis's own machine, never in an agent session.
# Makes the UPLOAD key for Play App Signing (Google keeps the app-signing key).
# Prints nothing secret: it writes upload.jks and upload.jks.b64 next to this script's working dir,
# then tells you which environment secrets and variables to add. Keep a backup of upload.jks off this machine.
#
# Without a local JDK (works, used on 8 November 2026): run it in a container with no network. The keys
# land in ~/riskmandate-keys on the host (on Linux they are owned by root, mode 600: chown them to yourself).
#   docker run --rm -it --network none -v ~/riskmandate-keys:/keys -v "$PWD/scripts":/scripts:ro -w /keys \
#     eclipse-temurin:17-jdk bash /scripts/make-upload-key.sh
set -euo pipefail
umask 077   # upload.jks and upload.jks.b64 are readable by you only
command -v keytool >/dev/null || { echo "keytool not found (install a JDK 17)"; exit 1; }
[ -e upload.jks ] && { echo "upload.jks already exists here; not overwriting"; exit 1; }
read -r -s -p "Keystore password (min 6 chars): " P1; echo
read -r -s -p "Again: " P2; echo
[ "$P1" = "$P2" ] || { echo "passwords differ"; exit 1; }
# The password goes by environment variable, not on the command line (visible in ps).
export KS_PASS="$P1"
keytool -genkeypair -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass:env KS_PASS -keypass:env KS_PASS -dname "CN=PrompTugal Android upload key" >/dev/null
unset KS_PASS
base64 < upload.jks | tr -d '\n' > upload.jks.b64
unset P1 P2
cat <<MSG
Done. In GitHub: PrompTugal/PrompTugal__App__Android > Settings > Environments > release > Environment secrets, add:
  UPLOAD_KEYSTORE_BASE64   = contents of upload.jks.b64
  UPLOAD_KEYSTORE_PASSWORD = the password you typed
  UPLOAD_KEY_ALIAS         = upload
  UPLOAD_KEY_PASSWORD      = the same password
In the same place, under Environment variables (these are not secret), add the Firebase ones:
  FIREBASE_APP_ID          = the Firebase app ID (a secret works too)
  GCP_WIF_PROVIDER         = projects/<number>/locations/global/workloadIdentityPools/<pool>/providers/<provider>
  GCP_SERVICE_ACCOUNT      = the service account email
Then delete upload.jks.b64 and store upload.jks somewhere safe (it is needed to rotate at Google if lost).
MSG
