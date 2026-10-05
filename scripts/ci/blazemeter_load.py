#!/usr/bin/env python3
"""Start a BlazeMeter load test, wait for it to end, and fail if its failure criteria fail.

Environment:
  BLAZEMETER_API_KEY_ID, BLAZEMETER_API_KEY_SECRET   BlazeMeter API key
  BLAZEMETER_TEST_ID                                 the test to start (drivelink-api-ci)
  TIMEOUT_S                                          optional, default 900

Exit 0 = the test passed, 1 = it failed or timed out, 2 = settings missing.
"""
import base64
import json
import os
import sys
import time
import urllib.request

API = "https://a.blazemeter.com/api/v4"


def call(method: str, path: str, auth: str) -> dict:
    req = urllib.request.Request(f"{API}{path}", method=method,
                                 headers={"Authorization": f"Basic {auth}", "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)["result"]


def summary(lines: list[str]) -> None:
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a") as out:
            out.write("\n".join(lines) + "\n")


def main() -> int:
    key_id = os.environ.get("BLAZEMETER_API_KEY_ID", "")
    secret = os.environ.get("BLAZEMETER_API_KEY_SECRET", "")
    test_id = os.environ.get("BLAZEMETER_TEST_ID", "")
    if not (key_id and secret and test_id):
        print("Set BLAZEMETER_API_KEY_ID, BLAZEMETER_API_KEY_SECRET and BLAZEMETER_TEST_ID.", file=sys.stderr)
        return 2
    auth = base64.b64encode(f"{key_id}:{secret}".encode()).decode()
    deadline = time.time() + int(os.environ.get("TIMEOUT_S", "900"))

    master = call("POST", f"/tests/{test_id}/start", auth)["id"]
    report = f"https://a.blazemeter.com/app/#/masters/{master}"
    print(f"Started test {test_id}: {report}")

    status = ""
    while time.time() < deadline:
        status = call("GET", f"/masters/{master}/status", auth).get("status", "")
        print(f"  {time.strftime('%H:%M:%S')}  {status}")
        if status == "ENDED":
            break
        time.sleep(20)
    if status != "ENDED":
        print("Timeout. The test is still running in BlazeMeter.", file=sys.stderr)
        summary(["### Load test (BlazeMeter)", "", f"❌ Timeout. [Report]({report})", ""])
        return 1

    passed = call("GET", f"/masters/{master}", auth).get("passed")
    rows = ["### Load test (BlazeMeter)", "", f"{'✅ Passed' if passed else '❌ Failed'} · [Report]({report})", ""]
    try:
        stats = call("GET", f"/masters/{master}/reports/default/summary", auth)["summary"][0]
        rows += ["| Samples | Errors | Avg (ms) | p90 (ms) | Max users |", "| --- | --- | --- | --- | --- |",
                 f"| {stats.get('hits', '-')} | {stats.get('failed', '-')} | {round(stats.get('avg') or 0)} "
                 f"| {stats.get('tp90', '-')} | {stats.get('maxUsers', '-')} |", ""]
    except Exception as err:  # the summary is optional; the result is not
        print(f"No summary: {err}")
    summary(rows)
    print("Passed." if passed else "Failure criteria failed.")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
