#!/usr/bin/env bash
# Smoke test for the DriveLink virtual service (BlazeMeter SV or scripts/mock-server.py).
# Sends every operation once per scenario (EV VIN; ICE and unknown VIN under default; each
# poll attempt of the getCommand sequences) and checks the status, the JSON body (jq -S,
# against api/examples/) and the expected response headers (for example Retry-After).
# The cases come from scripts/smoke-cases.py (the resolver rules), generated at run time.
#
# Usage: scripts/smoke.sh [-j N] [-s SCENARIO]... [-q] [BASE_URL]
#   BASE_URL     host without /v1, e.g. https://vs123svc456.mock.blazemeter.com
#                default: $MOCK_BASE_URL, else MOCK_BASE_URL in secrets.properties
#   -j N         parallel requests (default 8)
#   -s SCENARIO  only this scenario; repeatable or a comma list
#   -q           quiet: print only failures and the total
# Needs: bash 3.2+, curl, jq, python3 with PyYAML. Exit 0 = all pass, 1 = failures, 2 = usage.
# Each request has a 20 s limit. A transport error (no HTTP status) gets one retry; the
# summary counts the retries (BlazeMeter SV sometimes stalls a request for 60-100 s).
# Do not test unknown X-Scenario values: the local mock returns 400, SV returns default data.
#
# Local example:
#   scripts/mock-server.py --port 9090 --no-delay &
#   scripts/smoke.sh http://127.0.0.1:9090
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JOBS=8
QUIET=0
SCENARIOS=""
MAX_FAILURES=20

usage() { sed -n '2,/^set -euo/{/^set -euo/d;s/^# \{0,1\}//;p;}' "$0"; exit "${1:-2}"; }

while getopts "j:s:qh" opt; do
  case "$opt" in
    j) JOBS="$OPTARG" ;;
    s) SCENARIOS="${SCENARIOS:+$SCENARIOS,}$OPTARG" ;;
    q) QUIET=1 ;;
    h) usage 0 ;;
    *) usage 2 ;;
  esac
done
shift $((OPTIND - 1))
[ $# -le 1 ] || usage 2
case "$JOBS" in '' | *[!0-9]* | 0) echo "smoke: -j needs a positive number" >&2; exit 2 ;; esac

for tool in curl jq python3; do
  command -v "$tool" >/dev/null || { echo "smoke: $tool is not installed" >&2; exit 2; }
done
python3 -c 'import yaml' 2>/dev/null || { echo "smoke: python3 needs PyYAML (pip3 install pyyaml)" >&2; exit 2; }

BASE_URL="${1:-${MOCK_BASE_URL:-}}"
if [ -z "$BASE_URL" ] && [ -f "$ROOT/secrets.properties" ]; then
  BASE_URL="$(sed -n 's/^[[:space:]]*MOCK_BASE_URL[[:space:]]*=[[:space:]]*//p' "$ROOT/secrets.properties" \
    | tail -n 1 | tr -d '"\r' | sed 's/[[:space:]]*$//')"
fi
if [ -z "$BASE_URL" ]; then
  echo "smoke: no base URL. Pass one (scripts/smoke.sh http://127.0.0.1:9090), export MOCK_BASE_URL," >&2
  echo "       or add MOCK_BASE_URL=https://<id>.mock.blazemeter.com to secrets.properties." >&2
  exit 2
fi
BASE_URL="${BASE_URL%/}"
case "$BASE_URL" in */v1) BASE_URL="${BASE_URL%/v1}"; [ "$QUIET" = 1 ] || echo "smoke: removed /v1 from the base URL (case paths include it)" ;; esac

WORK="$(mktemp -d "${TMPDIR:-/tmp}/smoke.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
trap 'exit 130' INT TERM
mkdir -p "$WORK/res" "$WORK/out"

set --
[ -z "$SCENARIOS" ] || set -- --scenario "$SCENARIOS"
python3 "$ROOT/scripts/smoke-cases.py" ${1+"$@"} > "$WORK/cases.jsonl" || exit 2
TOTAL="$(wc -l < "$WORK/cases.jsonl" | tr -d ' ')"
[ "$TOTAL" -gt 0 ] || { echo "smoke: no cases" >&2; exit 2; }

# One case: $1 = line number in cases.jsonl. Writes one result line to res/<n>:
#   PASS|FAIL <tab> scenario <tab> id <tab> expected status <tab> actual status <tab> hint
run_case() {
  local n="$1" line id scenario method url status file example hasbody hint actual out hdr name value got
  local -a args
  line="$(sed -n "${n}p" "$WORK/cases.jsonl")"
  eval "$(jq -r '@sh "id=\(.id) scenario=\(.scenario) method=\(.method) url=\(.url) status=\(.status) file=\(.file // "") example=\(.example) hasbody=\(.body != null)"' <<<"$line")"
  out="$WORK/out/$n.body"
  hdr="$WORK/out/$n.hdr"
  args=(-sS --connect-timeout 10 --max-time 20 -o "$out" -D "$hdr" -w '%{http_code}' -X "$method"
        -H "Accept: application/json" -H "X-Correlation-Id: smoke-$id")
  while IFS= read -r name; do
    [ -n "$name" ] && args+=(-H "$name")
  done < <(jq -r '.headers | to_entries[] | "\(.key): \(.value)"' <<<"$line")
  if [ "$hasbody" = true ]; then
    jq -c .body <<<"$line" > "$WORK/out/$n.req"
    args+=(--data-binary "@$WORK/out/$n.req")
  fi
  hint=""
  actual="$(curl "${args[@]}" "$BASE_URL$url" 2>"$WORK/out/$n.err")" || true
  [ -n "$actual" ] || actual=000
  if [ "$actual" = 000 ]; then
    head -n 1 "$WORK/out/$n.err" > "$WORK/out/$n.retry"
    actual="$(curl "${args[@]}" "$BASE_URL$url" 2>"$WORK/out/$n.err")" || true
    [ -n "$actual" ] || actual=000
  fi
  if [ "$actual" = 000 ]; then
    hint="$(head -n 1 "$WORK/out/$n.err")"
  elif [ "$actual" != "$status" ]; then
    hint="want example $example; got body: $(head -c 120 "$out" 2>/dev/null | tr '\n\t' '  ')"
  fi
  if [ -z "$hint" ] && [ -n "$file" ]; then
    if ! jq -S . "$out" > "$WORK/out/$n.got" 2>/dev/null; then
      hint="body is not JSON: $(head -c 80 "$out" | tr '\n\t' '  ')"
    else
      jq -S . "$ROOT/api/examples/$file" > "$WORK/out/$n.want"
      if ! cmp -s "$WORK/out/$n.want" "$WORK/out/$n.got"; then
        hint="body differs from $file: $(diff "$WORK/out/$n.want" "$WORK/out/$n.got" | grep '^[<>]' | head -n 2 | tr '\n\t' '  ')"
      fi
    fi
  fi
  if [ -z "$hint" ]; then
    while IFS=$'\t' read -r name value; do
      [ -n "$name" ] || continue
      got="$(tr -d '\r' < "$hdr" | awk -v n="$name" 'BEGIN{n=tolower(n)} {i=index($0,":")} i && tolower(substr($0,1,i-1))==n {v=substr($0,i+1); sub(/^[ \t]+/,"",v); sub(/[ \t]+$/,"",v); print v}' | tail -n 1)"
      if [ "$got" != "$value" ]; then
        hint="header $name: want '$value', got '${got:-<missing>}'"
        break
      fi
    done < <(jq -r '.expectHeaders | to_entries[] | "\(.key)\t\(.value)"' <<<"$line")
  fi
  if [ -z "$hint" ]; then
    printf 'PASS\t%s\t%s\t%s\t%s\t\n' "$scenario" "$id" "$status" "$actual" > "$WORK/res/$n"
  else
    printf 'FAIL\t%s\t%s\t%s\t%s\t%s\n' "$scenario" "$id" "$status" "$actual" "$hint" > "$WORK/res/$n"
  fi
}
export -f run_case
export WORK ROOT BASE_URL

[ "$QUIET" = 1 ] || echo "smoke: $TOTAL cases against $BASE_URL (-j $JOBS)"
START=$SECONDS
seq 1 "$TOTAL" | xargs -P "$JOBS" -n 1 bash -c 'run_case "$1"' _
ELAPSED=$((SECONDS - START))

RESULTS="$WORK/results.tsv"
for n in $(seq 1 "$TOTAL"); do
  if [ -f "$WORK/res/$n" ]; then cat "$WORK/res/$n"
  else printf 'FAIL\t%s\t%s\t-\t-\tno result (worker error)\n' "$(sed -n "${n}p" "$WORK/cases.jsonl" | jq -r .scenario)" "case $n"
  fi
done > "$RESULTS"
FAILED="$(grep -c '^FAIL' "$RESULTS" || true)"

if [ "$QUIET" != 1 ]; then
  echo
  awk -F'\t' '
    !($2 in seen) { seen[$2] = 1; order[++k] = $2 }
    { total[$2]++; if ($1 == "PASS") pass[$2]++ }
    END {
      printf "%-18s %6s %6s %6s\n", "scenario", "cases", "pass", "fail"
      for (i = 1; i <= k; i++) { s = order[i]; printf "%-18s %6d %6d %6d\n", s, total[s], pass[s], total[s] - pass[s] }
    }' "$RESULTS"
fi
if [ "$FAILED" -gt 0 ]; then
  echo
  if [ "$FAILED" -gt "$MAX_FAILURES" ]; then echo "Failures (first $MAX_FAILURES of $FAILED): id, expected -> actual status, hint"
  else echo "Failures ($FAILED): id, expected -> actual status, hint"; fi
  grep '^FAIL' "$RESULTS" | head -n "$MAX_FAILURES" | awk -F'\t' '{ printf "  %s  %s -> %s  %s\n", $3, $4, $5, $6 }'
fi
echo
RETRIED="$(find "$WORK/out" -name '*.retry' | wc -l | tr -d ' ')"
echo "smoke: $((TOTAL - FAILED))/$TOTAL passed, $FAILED failed, $RETRIED retried, ${ELAPSED}s, $BASE_URL"
[ "$FAILED" -eq 0 ]
