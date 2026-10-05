#!/usr/bin/env python3
"""Run the BlazeMeter API Monitoring bucket "DriveLink" and wait for the result.

The bucket holds two tests against the virtual service:
  - "Virtual service health": GET /v1/health is 200 and "UP". A 15-minute schedule also runs it.
  - "API smoke: sign in, vehicles, status": sign in, list vehicles, read the vehicle status.

Environment:
  BZM_APITEST_TRIGGER_URL  trigger URL of the bucket (bucket settings -> Trigger URL)
  BZM_APITEST_TOKEN        API Monitoring access token (to read the results)
  BZM_APITEST_BUCKET       optional, default 1wvb0vzyyuid (used when the trigger answer has no bucket key)
  TIMEOUT_S                optional, default 300

Exit 0 = every test passed, 1 = a test failed or timed out, 2 = settings missing.
"""
import json
import os
import sys
import time
import urllib.request

API = "https://api.runscope.com"


def request(method: str, url: str, token: str | None = None) -> dict:
    req = urllib.request.Request(url, method=method, headers={"Accept": "application/json"})
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)


def summary(lines: list[str]) -> None:
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a") as out:
            out.write("\n".join(lines) + "\n")


def main() -> int:
    trigger = os.environ.get("BZM_APITEST_TRIGGER_URL", "")
    token = os.environ.get("BZM_APITEST_TOKEN", "")
    if not trigger or not token:
        print("Set BZM_APITEST_TRIGGER_URL and BZM_APITEST_TOKEN.", file=sys.stderr)
        return 2
    deadline = time.time() + int(os.environ.get("TIMEOUT_S", "300"))

    runs = request("POST", trigger)["data"]["runs"]
    print(f"Started {len(runs)} API test(s).")
    results: dict[str, dict] = {}
    while time.time() < deadline:
        for run in runs:
            if run["test_run_id"] in results:
                continue
            bucket = run.get("bucket_key") or os.environ.get("BZM_APITEST_BUCKET") or "1wvb0vzyyuid"
            url = f"{API}/buckets/{bucket}/tests/{run['test_id']}/results/{run['test_run_id']}"
            data = request("GET", url, token)["data"]
            if data.get("result") in ("pass", "fail"):
                results[run["test_run_id"]] = data
                print(f"  {data['result'].upper():4}  {run['test_name']}  "
                      f"({data.get('assertions_passed', 0)}/{data.get('assertions_defined', 0)} assertions)")
        if len(results) == len(runs):
            break
        time.sleep(5)

    rows = ["### API tests (BlazeMeter API Monitoring)", "",
            "| Test | Result | Assertions | Report |", "| --- | --- | --- | --- |"]
    ok = len(results) == len(runs)
    for run in runs:
        data = results.get(run["test_run_id"])
        result = data["result"] if data else "timeout"
        ok = ok and result == "pass"
        asserts = f"{data.get('assertions_passed', 0)}/{data.get('assertions_defined', 0)}" if data else "-"
        rows.append(f"| {run['test_name']} | {'✅' if result == 'pass' else '❌'} {result} | {asserts} "
                    f"| [run]({run.get('test_run_url', '')}) |")
    summary(rows + [""])
    if not ok:
        print("API tests failed or timed out.", file=sys.stderr)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
