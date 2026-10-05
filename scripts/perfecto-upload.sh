#!/usr/bin/env bash
# Builds the DriveLink app and test APKs with the endpoint built in (-PembedBaseUrl) and uploads them,
# with the Espresso script perfecto/espresso/"DriveLink Core Suite.xml" (as a Miscellaneous item), to
# the Perfecto repository folder PERFECTO_REPO_FOLDER. The script's @REPO_FOLDER@ placeholders are
# replaced with that folder. Overwrites the earlier upload.
#
# Usage: scripts/perfecto-upload.sh [-n]
#   -n   no build: upload the APKs that exist in app/build/outputs/apk/
#
# Settings: the same as scripts/perfecto-espresso.sh (PERFECTO_SECURITY_TOKEN, PERFECTO_CLOUD,
# MOCK_BASE_URL in secrets.properties), plus PERFECTO_REPO_FOLDER (env or perfecto.properties),
# for example PUBLIC:<you>/Customers/<customer>. The token goes in a header, never in a URL. Values are never printed.
#
# Note: a later scripts/perfecto-espresso.sh run rebuilds the APKs without the endpoint. Upload from
# this script only, so the repository copy always has it.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD=1
while getopts "nh" opt; do
  case "$opt" in
    n) BUILD=0 ;;
    h) sed -n '2,/^set -euo/{/^set -euo/d;s/^# \{0,1\}//;p;}' "$0"; exit 0 ;;
    *) exit 2 ;;
  esac
done

prop() { [[ -f "$2" ]] && sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$2" | tail -1 || true; }
jkey() { [[ -f "$1" ]] && python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("key", ""))' "$1" || true; }

TOKEN="${PERFECTO_SECURITY_TOKEN:-$(prop PERFECTO_SECURITY_TOKEN "$ROOT/perfecto.properties")}"
TOKEN="${TOKEN:-$(jkey "$ROOT/perfecto-api-key.json")}"
CLOUD="${PERFECTO_CLOUD:-$(prop PERFECTO_CLOUD "$ROOT/perfecto.properties")}"
CLOUD="${CLOUD:-demo}"
CLOUD="${CLOUD%%.*}"
FOLDER="${PERFECTO_REPO_FOLDER:-$(prop PERFECTO_REPO_FOLDER "$ROOT/perfecto.properties")}"
[[ -n "$TOKEN" ]] || { echo "No Perfecto security token (see scripts/perfecto-espresso.sh)." >&2; exit 2; }
[[ "$FOLDER" == PUBLIC:* || "$FOLDER" == PRIVATE:* ]] \
  || { echo "Set PERFECTO_REPO_FOLDER (PUBLIC:... or PRIVATE:...) in the environment or perfecto.properties." >&2; exit 2; }

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
if [[ "$BUILD" == 1 ]]; then
  (cd "$ROOT" && ./gradlew -q :app:assembleDebug :app:assembleDebugAndroidTest -PembedBaseUrl)
fi
unzip -l "$TEST_APK" | grep -q 'assets/drivelink-base-url.txt' \
  || { echo "The test APK has no built-in endpoint. Run without -n." >&2; exit 2; }

TENANT="$CLOUD-perfectomobile-com"
upload() { # local file, repository name, artifact type
  local req; req="$(mktemp)"
  printf '{"tenantId":"%s","artifactLocator":"%s/%s","artifactType":"%s","override":true}' "$TENANT" "$FOLDER" "$2" "$3" > "$req"
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST \
    -H "Perfecto-Authorization: $TOKEN" -H "Perfecto-TenantId: $TENANT" \
    -F "requestPart=@$req;type=application/json" -F "inputStream=@$1;type=application/octet-stream" \
    "https://$CLOUD.app.perfectomobile.com/repository/api/v1/artifacts")
  rm -f "$req"
  echo "$FOLDER/$2: HTTP $code"
  [[ "$code" == 200 ]]
}

upload "$APK" DriveLink-debug.apk ANDROID
upload "$TEST_APK" DriveLink-debug-androidTest.apk ANDROID
XML="$(mktemp -d)/DriveLink Core Suite.xml"
sed "s|@REPO_FOLDER@|$FOLDER|g" "$ROOT/perfecto/espresso/DriveLink Core Suite.xml" > "$XML"
upload "$XML" "DriveLink Core Suite.xml" GENERAL
rm -rf "$(dirname "$XML")"
