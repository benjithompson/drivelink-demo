#!/usr/bin/env python3
"""Run a Perforce Autonomous Testing (PAT) suite and wait for its test runs.

The pipeline calls the PAT MCP server over HTTP, the same server that AI assistants use.
The execution tool starts the suite; the analytics tool reports the test runs.

Environment:
  PAT_AUTHORIZATION   value of the Authorization header for the PAT MCP server
  PAT_CLOUD           optional, default "demo" (https://<cloud>.autonomous-testing.perforce.com)
  PAT_APPLICATION_ID  default 227
  PAT_SUITE_ID        default 587 ("DriveLink – CI")
  PAT_ENVIRONMENT_ID  default 493 ("DriveLink Android")
  TIMEOUT_S           optional, default 1500
  POLL_S              optional, default 60

Exit 0 = every test run passed, 1 = a run failed or timed out, 2 = settings missing.
"""
import json
import os
import re
import sys
import time
import urllib.request

PENDING = {"", "none", "pending", "queued", "running", "in_progress", "started", "scheduled", "created", "executing"}


class Pat:
    def __init__(self, cloud: str, authorization: str):
        self.url = f"https://{cloud}.autonomous-testing.perforce.com/mcp"
        self.auth = authorization
        self.next_id = 0

    def rpc(self, method: str, params: dict) -> dict:
        self.next_id += 1
        body = json.dumps({"jsonrpc": "2.0", "id": self.next_id, "method": method, "params": params}).encode()
        req = urllib.request.Request(self.url, data=body, method="POST", headers={
            "Authorization": self.auth,
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
            "MCP-Protocol-Version": "2025-06-18",
        })
        with urllib.request.urlopen(req, timeout=300) as resp:
            text = resp.read().decode()
        if text.lstrip().startswith("{"):
            msg = json.loads(text)
        else:  # server-sent events: the last data line is the response
            data = [line[5:].strip() for line in text.splitlines() if line.startswith("data:")]
            msg = json.loads(data[-1])
        if "error" in msg:
            raise RuntimeError(f"{method}: {msg['error']}")
        return msg["result"]

    def tool(self, name: str, intent: str, task: str) -> dict:
        result = self.rpc("tools/call", {"name": name, "arguments": {"intent": intent, "task": task}})
        if result.get("structuredContent"):
            return result["structuredContent"]
        text = result["content"][0]["text"]
        return json.loads(text) if text.lstrip()[:1] in "{[" else {"summary": text}


def find(obj, pattern: str):
    """First value whose key matches the pattern, depth first."""
    if isinstance(obj, dict):
        for key, value in obj.items():
            if re.fullmatch(pattern, key, re.I) and value not in (None, ""):
                return value
        for value in obj.values():
            hit = find(value, pattern)
            if hit is not None:
                return hit
    elif isinstance(obj, list):
        for value in obj:
            hit = find(value, pattern)
            if hit is not None:
                return hit
    return None


def summary(lines: list[str]) -> None:
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a") as out:
            out.write("\n".join(lines) + "\n")


def main() -> int:
    authorization = os.environ.get("PAT_AUTHORIZATION", "")
    if not authorization:
        print("Set PAT_AUTHORIZATION.", file=sys.stderr)
        return 2
    cloud = os.environ.get("PAT_CLOUD") or "demo"
    app = os.environ.get("PAT_APPLICATION_ID") or "227"
    suite = os.environ.get("PAT_SUITE_ID") or "587"
    env = os.environ.get("PAT_ENVIRONMENT_ID") or "493"
    deadline = time.time() + int(os.environ.get("TIMEOUT_S", "1500"))
    poll = int(os.environ.get("POLL_S", "60"))
    pat = Pat(cloud, authorization)

    pat.rpc("initialize", {"protocolVersion": "2025-06-18", "capabilities": {},
                           "clientInfo": {"name": "drivelink-ci", "version": "1.0"}})
    started = pat.tool("execution", "run_suite",
                       f"Application ID {app}. Run suite ID {suite} now, exactly once, on environment ID {env}. "
                       "No policies. No BlazeMeter run. Do not schedule. Return the new suite run ID as suite_run_id.")
    run_id = find(started, r"suite_?run_?id") or find(started.get("data", {}), r"id")
    if not run_id:
        print(f"No suite run ID in the answer: {json.dumps(started)[:500]}", file=sys.stderr)
        return 1
    link = f"https://{cloud}.autonomous-testing.perforce.com/application/{app}"
    print(f"Suite {suite} started: suite run {run_id}")

    runs: list[dict] = []
    while time.time() < deadline:
        time.sleep(poll)
        answer = pat.tool("analytics", "browse_test_runs",
                          f"Application ID {app}. List the test runs of suite run ID {run_id}. "
                          "Return data.test_runs with test_run_id, scenario_id, scenario name and status. Read only.")
        runs = find(answer, r"test_runs") or []
        states = [str(r.get("status", "")).lower() for r in runs]
        print(f"  {time.strftime('%H:%M:%S')}  " + (", ".join(states) or "no test runs yet"))
        if runs and not any(s in PENDING for s in states):
            break
    else:
        runs = runs or []

    done = runs and all(str(r.get("status", "")).lower() not in PENDING for r in runs)
    ok = bool(done) and all(str(r.get("status", "")).lower() in ("pass", "passed") for r in runs)
    rows = ["### Mobile AI tests (Perforce Autonomous Testing on Perfecto)", "",
            f"Suite {suite}, suite run {run_id} · [Open in PAT]({link})", "",
            "| Scenario | Result |", "| --- | --- |"]
    for r in runs:
        name = r.get("scenario_name") or r.get("name") or r.get("scenario_id")
        status = str(r.get("status", "?"))
        rows.append(f"| {name} | {'✅' if status.lower() in ('pass', 'passed') else '❌'} {status} |")
    if not done:
        rows.append("| (timeout) | ❌ the suite was still running |")
    summary(rows + [""])
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
