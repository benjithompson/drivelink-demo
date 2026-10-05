#!/usr/bin/env bash
# Runs the DriveLink Espresso core suite (package com.drivelink.demo.core) on Perfecto devices
# with the Perfecto Gradle plugin (standalone build in perfecto/espresso/).
#
# Usage: scripts/perfecto-espresso.sh [-d single|pr|matrix] [-n] [-k] [-r]
#   -d SET   device set: perfecto/espresso/config-SET.json (default: single = Pixel 9 Pro)
#   -n       no build: use the APKs that exist in app/build/outputs/apk/
#   -k       check only: show the settings (no secret values) and stop before the run
#   -r       reset the cloud virtual service to its seed data before the run (needs BLAZEMETER_API_KEY,
#            default api-key.json; scripts/build-mock.py --reset)
#
# Settings (environment first, then the gitignored files; values are never printed):
#   PERFECTO_SECURITY_TOKEN   env, or PERFECTO_SECURITY_TOKEN=... in perfecto.properties,
#                             or {"key": "..."} in perfecto-api-key.json
#   PERFECTO_CLOUD            env, or perfecto.properties; default "demo" (demo.perfectomobile.com)
#   MOCK_BASE_URL             env, or secrets.properties: the virtual service the devices call
#   GITHUB_RUN_NUMBER, GITHUB_REF_NAME   optional: job number and branch in the Perfecto report
#
# The secrets reach Gradle as ORG_GRADLE_PROJECT_* variables, so they are not on the command line.
# Exit code: 0 = all tests passed, non-zero = a failure (failBuildOnFailure is true).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SET="single"
BUILD=1
CHECK=0
RESET=0

usage() { sed -n '2,/^set -euo/{/^set -euo/d;s/^# \{0,1\}//;p;}' "$0"; exit "${1:-2}"; }

while getopts "d:nkrh" opt; do
  case "$opt" in
    d) SET="$OPTARG" ;;
    n) BUILD=0 ;;
    k) CHECK=1 ;;
    r) RESET=1 ;;
    h) usage 0 ;;
    *) usage 2 ;;
  esac
done

CONFIG="$ROOT/perfecto/espresso/config-$SET.json"
[[ -f "$CONFIG" ]] || { echo "No device set '$SET' ($CONFIG)." >&2; exit 2; }

# Reads KEY from a properties file without printing it.
prop() { [[ -f "$2" ]] && sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$2" | tail -1 || true; }

# Reads "key" from a JSON file without printing it.
jkey() { [[ -f "$1" ]] && python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("key", ""))' "$1" || true; }

TOKEN="${PERFECTO_SECURITY_TOKEN:-$(prop PERFECTO_SECURITY_TOKEN "$ROOT/perfecto.properties")}"
TOKEN="${TOKEN:-$(jkey "$ROOT/perfecto-api-key.json")}"
CLOUD="${PERFECTO_CLOUD:-$(prop PERFECTO_CLOUD "$ROOT/perfecto.properties")}"
CLOUD="${CLOUD:-demo}"
[[ "$CLOUD" == *.* ]] || CLOUD="$CLOUD.perfectomobile.com"
BASE_URL="${MOCK_BASE_URL:-$(prop MOCK_BASE_URL "$ROOT/secrets.properties")}"

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

echo "Device set: $SET ($(python3 -c 'import json,sys; print(", ".join(d.get("model", d.get("deviceName", "any")) for d in json.load(open(sys.argv[1]))["devices"]))' "$CONFIG"))"
echo "Cloud: $CLOUD"
echo "Security token: $([[ -n "$TOKEN" ]] && echo set || echo MISSING)"
echo "Virtual service URL: $([[ -n "$BASE_URL" ]] && echo set || echo MISSING)"

missing=0
[[ -n "$TOKEN" ]] || { echo "Set PERFECTO_SECURITY_TOKEN in the environment, perfecto.properties or perfecto-api-key.json (gitignored)." >&2; missing=1; }
[[ "$BASE_URL" == https://* ]] || { echo "MOCK_BASE_URL must be the https virtual service URL (devices cannot reach a local mock)." >&2; missing=1; }
[[ "$missing" == 0 ]] || exit 2
[[ "$CHECK" == 0 ]] || { echo "Check only: stop before the run."; exit 0; }

if [[ "$BUILD" == 1 ]]; then
  (cd "$ROOT" && ./gradlew -q :app:assembleDebug :app:assembleDebugAndroidTest)
fi
[[ -f "$APK" && -f "$TEST_APK" ]] || { echo "APKs not found. Run without -n." >&2; exit 2; }
if [[ "$RESET" == 1 ]]; then
  BLAZEMETER_API_KEY="${BLAZEMETER_API_KEY:-$ROOT/api-key.json}" python3 "$ROOT/scripts/build-mock.py" --reset
fi

export ORG_GRADLE_PROJECT_cloudURL="$CLOUD"
export ORG_GRADLE_PROJECT_securityToken="$TOKEN"
export ORG_GRADLE_PROJECT_apkPath="$APK"
export ORG_GRADLE_PROJECT_testApkPath="$TEST_APK"
export ORG_GRADLE_PROJECT_instrumentationArgs="baseUrl=$BASE_URL;package=com.drivelink.demo.core"
export ORG_GRADLE_PROJECT_jobNumber="${GITHUB_RUN_NUMBER:-0}"
export ORG_GRADLE_PROJECT_branch="${GITHUB_REF_NAME:-$(git -C "$ROOT" branch --show-current)}"

cd "$ROOT/perfecto/espresso"
# The plugin can echo its parameters; mask the secret values in the console output and the CI log.
"$ROOT/gradlew" -p . perfecto-android-inst -PconfigFileLocation="$CONFIG" 2>&1 \
  | BASE_URL="$BASE_URL" TOKEN="$TOKEN" python3 -u -c '
import os, sys
pairs = [(os.environ["TOKEN"], "<PERFECTO_SECURITY_TOKEN>"), (os.environ["BASE_URL"], "<MOCK_BASE_URL>")]
for line in sys.stdin:
    for secret, mask in pairs:
        line = line.replace(secret, mask)
    sys.stdout.write(line)
'
