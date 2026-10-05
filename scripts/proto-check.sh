#!/usr/bin/env bash
# Check the stateful virtual service drivelink-proto end to end.
# Reads MOCK_BASE_URL (the app default: drivelink-proto) from secrets.properties (gitignored).
# Never prints the URL.
#
# For one VIN (default: a load-test car), it checks:
#   1. UNLOCK, then status shows locked=false; LOCK, then status shows locked=true.
#   2. PUT charge limits, then GET charge settings returns them.
#   3. Another VIN is not changed (state is per VIN).
# Exit code 0 when all checks pass.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE="$(grep -E '^MOCK_BASE_URL=' "$ROOT/secrets.properties" | cut -d= -f2-)"
[ -n "$BASE" ] || { echo "MOCK_BASE_URL is not set in secrets.properties" >&2; exit 2; }

VIN="${1:-DLEV26PERF0000042}"
OTHER="${2:-DLEV26PERF0000043}"
fails=0

field() { python3 -c "import json,sys; print(json.dumps(json.load(sys.stdin)$1))"; }
check() {  # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS  $1 ($3)"; else echo "FAIL  $1: expected $2, got $3"; fails=$((fails+1)); fi
}
req() { curl -sS -H 'Content-Type: application/json' "$@"; }

other_before="$(req "$BASE/v1/vehicles/$OTHER/status" | field '["locked"]')"

req -X POST -d '{"type":"UNLOCK","pin":"1234"}' "$BASE/v1/vehicles/$VIN/commands" >/dev/null
check "status after UNLOCK" false "$(req "$BASE/v1/vehicles/$VIN/status" | field '["locked"]')"
check "other VIN unchanged" "$other_before" "$(req "$BASE/v1/vehicles/$OTHER/status" | field '["locked"]')"

req -X POST -d '{"type":"LOCK","pin":"1234"}' "$BASE/v1/vehicles/$VIN/commands" >/dev/null
check "status after LOCK" true "$(req "$BASE/v1/vehicles/$VIN/status" | field '["locked"]')"

ac=$((60 + RANDOM % 30)); dc=$((60 + RANDOM % 30))
body="$(python3 -c "
import json; d=json.load(open('$ROOT/api/examples/getChargeSettings/200.default.json'))
d['acTargetPct']=$ac; d['dcTargetPct']=$dc; print(json.dumps(d))")"
put_status="$(req -o /dev/null -w '%{http_code}' -X PUT -d "$body" "$BASE/v1/vehicles/$VIN/charge-settings")"
check "PUT charge-settings status" 200 "$put_status"
settings="$(req "$BASE/v1/vehicles/$VIN/charge-settings")"
check "acTargetPct after PUT" "$ac" "$(echo "$settings" | field '["acTargetPct"]')"
check "dcTargetPct after PUT" "$dc" "$(echo "$settings" | field '["dcTargetPct"]')"

echo "$fails failure(s)"
exit "$fails"
