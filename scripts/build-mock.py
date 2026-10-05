#!/usr/bin/env python3
"""Build the DriveLink BlazeMeter virtual service from the API contract.

Input:  api/examples/index.json, api/scenarios.yaml (through scripts/mock_resolver.py)
Output: mock/transactions.json        the planned SV transactions (--plan)
        mock/proto/drivelink-proto-service.json, mock/drivelink-service.json
                                      the exported service definition (--export)
        secrets.properties            MOCK_BASE_URL=<endpoint> (--apply)

Targets (--target, default proto):
  proto   service "drivelink-proto", virtual service "drivelink-proto". The app default (MOCK_BASE_URL).
          The service also has stateful transactions, named "drivelink-proto <operation>", made with
          the BlazeMeter SV MCP (mock/proto/README.md). The script does not create or delete them.
          It assigns them with priority 4 and the default think time, so a scenario transaction
          (priority 2-3) still wins and the stateful answer beats the fixed default (5-6).
  legacy  service "drivelink", virtual service "drivelink-mock". No state. LEGACY_MOCK_BASE_URL.

Plan rules:
  - One transaction per index entry with a scenario. Name: "drivelink <operationId> <status> <example>"
    (SV rejects "." and brackets in transaction names).
    Matchers: method, url "matches_url" urlRegex, X-Scenario "equals" <scenario> (not for
    "default"), and one "matches" header matcher per entry.match regex.
    Priority: entry.priority (scenario band 1-3, default band 4-6, lowest wins).
    Delay: uniform entry.thinkTimeMs.
  - Think-time copies (DECISIONS.md D-14). A default transaction keeps its own delay when it
    answers another scenario. For each scenario S that the resolver answers with default entry D
    and that has a different think time, add "drivelink <operationId> <status> <example> @S":
    the response of D, plus X-Scenario equals S, delay of S, priority D.priority - 3.
  - The plan is checked offline: for every request in the scripts/check-resolution.py grid,
    an emulation of SV (url regex, X-Scenario equals, header regexes, lowest priority wins,
    ties are errors) must select the resolver's example with the think time of the requested
    scenario.

Apply (idempotent; touches only the service and virtual service of the target, transactions
whose name starts with "drivelink ", and the priority and delay of the stateful transactions):
  find or create the service; create, update or delete transactions by name; find or create the
  virtual service; assign every transaction with its priority; deploy (or configure when the
  virtual service runs); wait for RUNNING; write the endpoint to secrets.properties.

Credentials: env BLAZEMETER_API_KEY = path to a JSON file with "id" and "secret" (Basic auth).
Key values are never printed.

Usage:
  scripts/build-mock.py --plan                    compute, check and write mock/transactions.json
  scripts/build-mock.py --apply [--target T]      plan, then sync to BlazeMeter and deploy
  scripts/build-mock.py --export [--target T]     write the service export of the target (all
                                                  transactions; timestamps and user names removed)
  scripts/build-mock.py --reset                   reset the state of drivelink-proto to the seed data
                                                  (all cars), wait until it runs again
"""
import argparse
import base64
import importlib.util
import io
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import zipfile
from http import HTTPStatus
from pathlib import Path

import mock_resolver

ROOT = Path(__file__).resolve().parent.parent
EXAMPLES = ROOT / "api" / "examples"
PLAN_FILE = ROOT / "mock" / "transactions.json"
SECRETS = ROOT / "secrets.properties"

API = "https://mock.blazemeter.com/api/v1"
WORKSPACE = 2194183
TARGETS = {
    "proto": {"service": "drivelink-proto", "vs": "drivelink-proto", "secret": "MOCK_BASE_URL",
              "export": ROOT / "mock" / "proto" / "drivelink-proto-service.json",
              "stateful_prefix": "drivelink-proto "},
    "legacy": {"service": "drivelink", "vs": "drivelink-mock", "secret": "LEGACY_MOCK_BASE_URL",
               "export": ROOT / "mock" / "drivelink-service.json", "stateful_prefix": None},
}
SERVICE_NAME = VS_NAME = SECRET_KEY = EXPORT_FILE = STATEFUL_PREFIX = None  # set by use_target()
STATEFUL_PRIORITY = 4  # between the scenario band (2-3) and the default band (5-6)
STATEFUL_THINK_MS = (300, 800)  # the default think time of the contract
PREFIX = "drivelink "
VS_SETTINGS = {  # BlazeMeter cloud, US East, HTTPS endpoint, no match -> 404
    "harborId": "5c544422c7dc9735767b23ce",
    "shipId": "5d3ccab3526ad28f53205574",
    "endpointPreference": "HTTPS",
    "noMatchingRequestPreference": "return404",
    "httpRunnerEnabled": True,  # without it, deploy fails: "must have at least one active runner"
}
SCENARIO_HEADER = "X-Scenario"
BAND = 3
VOLATILE = {"created", "updated", "createdBy", "updatedBy", "createdDate", "updatedDate", "link"}


# ---------------------------------------------------------------- plan

def load_check_resolution():
    """Import scripts/check-resolution.py (file name has a hyphen) for request_grid()."""
    path = Path(__file__).resolve().parent / "check-resolution.py"
    spec = importlib.util.spec_from_file_location("check_resolution", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def entry_key(entry):
    return (entry["operationId"], entry["status"], entry["example"])


def scenario_think(contract, scenario, op_id):
    s = contract["scenarios"][scenario]
    return list((op_id == mock_resolver.POLL_OPERATION and s.get("pollThinkTimeMs")) or s["thinkTimeMs"])


def transaction(entry, scenario=None, think=None, priority=None):
    """Planned transaction for an index entry. scenario/think/priority are set for a copy."""
    name = f"{PREFIX}{entry['operationId']} {entry['status']} {entry['example']}"  # SV rejects "." in names
    matcher_scenario = scenario or (None if entry["scenario"] == "default" else entry["scenario"])
    headers = {"Content-Type": entry["contentType"]} if entry["contentType"] else {}
    headers.update(entry["headers"])
    return {
        "name": name + (f" @{scenario}" if scenario else ""),
        "operationId": entry["operationId"],
        "example": f"{entry['status']}.{entry['example']}",
        "scenario": matcher_scenario,
        "priority": priority if priority is not None else entry["priority"],
        "request": {
            "method": entry["method"],
            "urlRegex": entry["urlRegex"],
            "headers": ([{"key": SCENARIO_HEADER, "matcher": "equals", "value": matcher_scenario}]
                        if matcher_scenario else [])
                       + [{"key": k, "matcher": "matches", "value": v} for k, v in sorted(entry["match"].items())],
        },
        "response": {
            "status": entry["status"],
            "headers": headers,
            "file": entry["file"],
        },
        "thinkTimeMs": list(think if think is not None else entry["thinkTimeMs"]),
    }


def plan(contract, grid):
    """All transactions: one per scenario entry, plus the think-time copies (D-14)."""
    entries = [e for e in contract["index"] if e["scenario"] is not None]
    txns = [transaction(e) for e in entries]
    copies = {}
    for op, scenario, _vin, _attempt, path, headers in grid:
        if scenario == "default":
            continue
        entry, info = mock_resolver.resolve(contract, op["method"], path, headers)
        if entry is None or info["servedScenario"] != "default":
            continue
        think = scenario_think(contract, scenario, entry["operationId"])
        if think != list(entry["thinkTimeMs"]):
            copies[(entry_key(entry), scenario)] = transaction(entry, scenario, think, entry["priority"] - BAND)
    txns += [copies[k] for k in sorted(copies, key=str)]
    txns.sort(key=lambda t: t["name"])
    return txns


def sv_select(txns, method, path, headers):
    """Emulate SV over planned transactions: all that match, lowest priority wins."""
    headers = {k.lower(): v for k, v in headers.items()}
    found = []
    for t in txns:
        req = t["request"]
        if req["method"] != method or not re.fullmatch(req["urlRegex"], path):
            continue
        ok = True
        for m in req["headers"]:
            value = headers.get(m["key"].lower())
            if value is None or (m["matcher"] == "equals" and value != m["value"]) or \
                    (m["matcher"] == "matches" and not re.fullmatch(m["value"], value)):
                ok = False
                break
        if ok:
            found.append(t)
    if not found:
        return []
    best = min(t["priority"] for t in found)
    return [t for t in found if t["priority"] == best]


def check_plan(contract, txns, grid):
    """Problems where the planned transactions disagree with the resolver."""
    problems = []
    count = 0
    for op, scenario, vin, attempt, path, headers in grid:
        count += 1
        entry, _info = mock_resolver.resolve(contract, op["method"], path, headers)
        where = f"{op['operationId']} scenario={scenario} vin={vin} attempt={attempt}"
        top = sv_select(txns, op["method"], path, headers)
        if entry is None:
            if top:
                problems.append(f"{where}: resolver has no example, SV selects {top[0]['name']}")
            continue
        if len(top) != 1:
            got = " = ".join(t["name"] for t in top) or "nothing"
            problems.append(f"{where}: SV selects {got}")
            continue
        t = top[0]
        if (t["operationId"], t["example"]) != (entry["operationId"], f"{entry['status']}.{entry['example']}"):
            problems.append(f"{where}: SV selects {t['name']}, resolver {entry['status']}.{entry['example']}")
        want = scenario_think(contract, scenario, op["operationId"])
        if t["thinkTimeMs"] != want:
            problems.append(f"{where}: delay {t['thinkTimeMs']} from {t['name']}, want {want}")
    names = [t["name"] for t in txns]
    for dup in sorted({n for n in names if names.count(n) > 1}):
        problems.append(f"duplicate transaction name: {dup}")
    return count, problems


def build_plan():
    contract = mock_resolver.load_contract()
    grid = list(load_check_resolution().request_grid(contract))
    txns = plan(contract, grid)
    count, problems = check_plan(contract, txns, grid)
    copies = [t for t in txns if "@" in t["name"]]
    by_scenario = {}
    for t in copies:
        s = t["name"].rsplit("@", 1)[1]
        by_scenario[s] = by_scenario.get(s, 0) + 1
    print(f"Plan: {len(txns)} transactions ({len(txns) - len(copies)} from index entries, "
          f"{len(copies)} think-time copies: "
          + ", ".join(f"{s} {n}" for s, n in sorted(by_scenario.items())) + ").")
    print(f"Plan check: {count} requests emulated. Problems: {len(problems)}.")
    if problems:
        print("\n".join("  " + p for p in problems))
        sys.exit(1)
    PLAN_FILE.parent.mkdir(parents=True, exist_ok=True)
    PLAN_FILE.write_text(json.dumps(txns, indent=2, ensure_ascii=False) + "\n")
    print(f"Wrote {PLAN_FILE.relative_to(ROOT)}.")
    return txns


# ---------------------------------------------------------------- BlazeMeter REST

class ApiError(Exception):
    pass


def auth_header():
    path = os.environ.get("BLAZEMETER_API_KEY")
    if not path or not Path(path).is_file():
        sys.exit("BLAZEMETER_API_KEY must be the path to a JSON file with 'id' and 'secret'.")
    key = json.loads(Path(path).read_text())
    return "Basic " + base64.b64encode(f"{key['id']}:{key['secret']}".encode()).decode()


AUTH = None


def call(method, path, body=None, raw=False, retries=5):
    """One REST call. Retries on 429 and 5xx. Raises ApiError with status and body (no auth)."""
    global AUTH
    AUTH = AUTH or auth_header()
    url = path if path.startswith("http") else f"{API}/workspaces/{WORKSPACE}{path}"
    data = json.dumps(body).encode() if body is not None else None
    for attempt in range(retries):
        req = urllib.request.Request(url, data=data, method=method, headers={
            "Authorization": AUTH, "Content-Type": "application/json", "Accept": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                payload = resp.read()
            time.sleep(0.1)
            return payload if raw else (json.loads(payload) if payload else None)
        except urllib.error.HTTPError as e:
            text = e.read().decode(errors="replace")
            if e.code == 429 or e.code >= 500:
                if attempt < retries - 1:
                    time.sleep(2 ** attempt)
                    continue
            raise ApiError(f"{method} {url} -> {e.code}: {text[:2000]}") from None
        except urllib.error.URLError as e:
            if attempt < retries - 1:
                time.sleep(2 ** attempt)
                continue
            raise ApiError(f"{method} {url} -> {e.reason}") from None


def paged(path, limit=100):
    """All results of a list call with skip/limit."""
    out, skip = [], 0
    sep = "&" if "?" in path else "?"
    while True:
        page = call("GET", f"{path}{sep}skip={skip}&limit={limit}")
        result = page.get("result") or []
        out += result
        skip += len(result)
        if not result or skip >= (page.get("total") or 0):
            return out


def use_target(name):
    global SERVICE_NAME, VS_NAME, SECRET_KEY, EXPORT_FILE, STATEFUL_PREFIX
    t = TARGETS[name]
    SERVICE_NAME, VS_NAME, SECRET_KEY = t["service"], t["vs"], t["secret"]
    EXPORT_FILE, STATEFUL_PREFIX = t["export"], t["stateful_prefix"]


def find_service():
    return next((s for s in paged(f"/services?name={SERVICE_NAME}") if s["name"] == SERVICE_NAME), None)


def find_or_create_service():
    service = find_service()
    if service:
        print(f"Service '{SERVICE_NAME}': {service['id']} (exists).")
        return service["id"]
    service = call("POST", "/services", {"name": SERVICE_NAME,
                                         "description": "DriveLink demo API (built by scripts/build-mock.py)"})["result"]
    print(f"Service '{SERVICE_NAME}': {service['id']} (created).")
    return service["id"]


def content_type_kind(content_type):
    if not content_type:
        return "text"
    return "json" if "json" in content_type else "text"


def dsl(t):
    """SV transaction DSL for a planned transaction."""
    body = (EXAMPLES / t["response"]["file"]).read_bytes() if t["response"]["file"] else b""
    low, high = t["thinkTimeMs"]
    status = t["response"]["status"]
    return {
        "type": "HTTP",
        "priority": t["priority"],
        "requestDsl": {
            "method": t["request"]["method"],
            "url": {"key": "url", "matcherName": "matches_url", "matchingValue": t["request"]["urlRegex"],
                    "optional": False},
            "headers": [{"key": m["key"], "matcherName": m["matcher"], "matchingValue": m["value"],
                         "optional": False} for m in t["request"]["headers"]],
            "queryParams": [],
            "cookies": [],
            "body": [],
        },
        "responseDsl": {
            "binary": False,
            "status": status,
            "statusMessage": HTTPStatus(status).phrase,
            "headers": [{"name": k, "value": v} for k, v in t["response"]["headers"].items()],
            "contentType": content_type_kind(t["response"]["headers"].get("Content-Type")),
            "charset": "UTF-8",
            "content": base64.b64encode(body).decode(),
            "responseDelay": {"type": "uniform", "lower": low, "upper": high},
        },
    }


def dsl_fingerprint(d):
    """The DSL fields this script owns, normalized, to detect changes."""
    req, resp = d.get("requestDsl") or {}, d.get("responseDsl") or {}
    delay = resp.get("responseDelay") or {}
    return json.dumps({
        "priority": d.get("priority"),
        "method": req.get("method"),
        "url": [(req.get("url") or {}).get(k) for k in ("matcherName", "matchingValue")],
        "headers": [[h.get("key"), h.get("matcherName"), h.get("matchingValue")] for h in req.get("headers") or []],
        "status": resp.get("status"),
        "respHeaders": [[h.get("name"), h.get("value")] for h in resp.get("headers") or []],
        "contentType": resp.get("contentType"),
        "content": resp.get("content") or "",
        "delay": [delay.get("type"), delay.get("lower"), delay.get("upper")],
    }, sort_keys=True)


def sync_transactions(service_id, txns):
    """Create, update and delete transactions by name. Returns ({name: id}, stale ids, changed)."""
    existing = {t["name"]: t for t in paged(f"/transactions?serviceId={service_id}")
                if t["name"].startswith(PREFIX)}
    ids, changed = {}, False
    create = []
    updated = 0
    for t in txns:
        body = {"name": t["name"], "type": "HTTP", "serviceId": service_id, "tags": ["drivelink"],
                "description": f"{t['operationId']} {t['example']} (scripts/build-mock.py)", "dsl": dsl(t)}
        old = existing.get(t["name"])
        if old is None:
            create.append(body)
            continue
        ids[t["name"]] = old["id"]
        if dsl_fingerprint(old["dsl"]) != dsl_fingerprint(body["dsl"]):
            call("PUT", f"/transactions/{old['id']}", dict(body, id=old["id"]))
            updated += 1
    for i in range(0, len(create), 25):
        chunk = create[i:i + 25]
        result = call("POST", f"/transactions?serviceId={service_id}", {"transactions": chunk})["result"]
        for t in result:
            ids[t["name"]] = t["id"]
    missing = [t["name"] for t in txns if t["name"] not in ids]
    if missing:
        raise ApiError(f"transactions not created: {missing}")
    stale = [t["id"] for name, t in existing.items() if name not in ids]
    changed = bool(create or updated or stale)
    print(f"Transactions: {len(create)} created, {updated} updated, "
          f"{len(txns) - len(create) - updated} unchanged, {len(stale)} stale.")
    return ids, stale, changed


def sync_stateful(service_id):
    """Priority and delay of the stateful transactions (made outside this script). Returns ({id: priority}, changed)."""
    if not STATEFUL_PREFIX:
        return {}, False
    found = [t for t in paged(f"/transactions?serviceId={service_id}") if t["name"].startswith(STATEFUL_PREFIX)]
    if not found:
        raise ApiError(f"no stateful transactions ('{STATEFUL_PREFIX}*') in service {service_id}")
    updated = 0
    command_example = json.loads((EXAMPLES / "sendCommand" / "202.default.json").read_text())
    for t in found:
        d = t["dsl"]
        low, high = STATEFUL_THINK_MS
        delay = {"type": "uniform", "lower": low, "upper": high}
        content = command_content(d["responseDsl"].get("content") or "", command_example)
        stored = {k: (d["responseDsl"].get("responseDelay") or {}).get(k) for k in delay}  # SV adds other keys
        if d.get("priority") != STATEFUL_PRIORITY or stored != delay \
                or content != d["responseDsl"].get("content"):
            d["priority"] = STATEFUL_PRIORITY
            d["responseDsl"]["responseDelay"] = delay
            d["responseDsl"]["content"] = content
            call("PUT", f"/transactions/{t['id']}", {k: t[k] for k in ("id", "name", "type", "serviceId", "dsl")
                                                     if k in t} | {"tags": t.get("tags") or []})
            updated += 1
    print(f"Stateful transactions: {len(found)} ({updated} updated to priority {STATEFUL_PRIORITY}).")
    return {t["id"]: STATEFUL_PRIORITY for t in found}, bool(updated)


def command_content(content_b64, example):
    """A stateful sendCommand body becomes the contract example with its own "type"; other bodies stay."""
    try:
        body = json.loads(base64.b64decode(content_b64))
    except ValueError:
        return content_b64  # a template such as ${request.body}
    if not isinstance(body, dict) or "commandId" not in body:
        return content_b64
    aligned = dict(example, type=body.get("type", example["type"]))
    if aligned == body:
        return content_b64
    return base64.b64encode((json.dumps(aligned, indent=2) + "\n").encode()).decode()


def find_vs():
    found = paged(f"/service-mocks?name={VS_NAME}")
    return next((v for v in found if v["name"] == VS_NAME), None)


def sync_virtual_service(service_id, assignments):
    """Find or create the VS and set its transaction list with priorities. Returns (vs, changed)."""
    wanted = sorted(({"txnId": i, "priority": p} for i, p in assignments.items()), key=lambda a: a["txnId"])
    vs = find_vs()
    if vs is None:
        body = dict(VS_SETTINGS, name=VS_NAME, serviceId=service_id, type="TRANSACTIONAL",
                    description="DriveLink demo virtual service (scripts/build-mock.py)",
                    mockServiceTransactions=wanted)
        vs = call("POST", "/service-mocks", body)["result"]
        print(f"Virtual service '{VS_NAME}': {vs['id']} (created).")
        changed = True
    else:
        print(f"Virtual service '{VS_NAME}': {vs['id']} (exists, {vs['status']}).")
        vs = call("GET", f"/service-mocks/{vs['id']}")["result"]
        current = {a["txnId"]: a["priority"] for a in vs.get("mockServiceTransactions") or []}
        settings = {k: v for k, v in VS_SETTINGS.items() if vs.get(k) != v}
        changed = current != assignments or bool(settings)
        if changed:
            vs = call("PATCH", f"/service-mocks/{vs['id']}", dict(settings, mockServiceTransactions=wanted))["result"]
    vs = call("GET", f"/service-mocks/{vs['id']}")["result"]
    current = {a["txnId"]: a["priority"] for a in vs.get("mockServiceTransactions") or []}
    wrong = {i: p for i, p in assignments.items() if current.get(i) != p}
    for txn_id, priority in wrong.items():
        if txn_id not in current:
            raise ApiError(f"transaction {txn_id} is not assigned to virtual service {vs['id']}")
        call("PATCH", f"/service-mocks/{vs['id']}/transactions/{txn_id}", {"priority": priority})
    extra = set(current) - set(assignments)
    if wrong or extra:
        vs = call("GET", f"/service-mocks/{vs['id']}")["result"]
        current = {a["txnId"]: a["priority"] for a in vs.get("mockServiceTransactions") or []}
        if current != assignments:
            raise ApiError(f"assignments differ after update: {len(set(current) ^ set(assignments))} ids, "
                           f"{sum(current.get(i) != p for i, p in assignments.items())} priorities")
        changed = True
    print(f"Assignments: {len(current)} transactions, priorities set ({len(wrong)} fixed per transaction).")
    return vs, changed


def wait_tracking(tracking):
    uuid = tracking["trackingId"]
    for _ in range(120):
        t = call("GET", f"{API}/trackings/{uuid}")["result"]
        if t["status"] == "FINISHED":
            if t.get("errors"):
                raise ApiError(f"tracking {uuid} errors: {t['errors']}")
            return t
        time.sleep(5)
    raise ApiError(f"tracking {uuid} did not finish")


def wait_running(vs_id):
    for _ in range(120):
        vs = call("GET", f"/service-mocks/{vs_id}")["result"]
        if vs["status"] == "RUNNING":
            return vs
        if vs["status"] == "FAILED":
            raise ApiError(f"virtual service {vs_id} FAILED")
        time.sleep(5)
    raise ApiError(f"virtual service {vs_id} is not RUNNING")


def write_secret(key, value):
    lines = SECRETS.read_text().splitlines() if SECRETS.exists() else []
    lines = [ln for ln in lines if not ln.startswith(f"{key}=")] + [f"{key}={value}"]
    SECRETS.write_text("\n".join(lines) + "\n")


def apply(txns):
    service_id = find_or_create_service()
    ids, stale, txn_changed = sync_transactions(service_id, txns)
    stateful, stateful_changed = sync_stateful(service_id)
    txn_changed = txn_changed or stateful_changed
    assignments = {ids[t["name"]]: t["priority"] for t in txns} | stateful
    vs, vs_changed = sync_virtual_service(service_id, assignments)
    for txn_id in stale:
        call("DELETE", f"/transactions/{txn_id}")
    if vs["status"] == "RUNNING" and (txn_changed or vs_changed):
        print("Configuring the running virtual service ...")
        wait_tracking(call("GET", f"/service-mocks/{vs['id']}/configure")["result"])
    elif vs["status"] != "RUNNING":
        print(f"Deploying the virtual service (status {vs['status']}) ...")
        wait_tracking(call("GET", f"/service-mocks/{vs['id']}/deploy")["result"])
    vs = wait_running(vs["id"])
    endpoint = next(e["endpoint"] for e in vs["endpoints"] if e.get("runnerSubType") == "HTTPS")
    write_secret(SECRET_KEY, endpoint)
    print(f"Running: service {service_id}, virtual service {vs['id']}.")
    print(f"Wrote {SECRET_KEY} to {SECRETS.name} (the endpoint is not printed).")


def reset():
    """Regenerates the data of the running virtual service from its seed (configure, keepBlazeData=false)."""
    if not STATEFUL_PREFIX:
        sys.exit(f"Target has no state: '{VS_NAME}'.")
    vs = find_vs()
    if vs is None or vs["status"] != "RUNNING":
        sys.exit(f"Virtual service '{VS_NAME}' is not RUNNING. Run --apply first.")
    start = time.monotonic()
    wait_tracking(call("GET", f"/service-mocks/{vs['id']}/configure?keepBlazeData=false")["result"])
    wait_running(vs["id"])
    print(f"Reset: virtual service {vs['id']} has the seed data again ({time.monotonic() - start:.0f} s).")


# ---------------------------------------------------------------- export

def strip_volatile(node):
    if isinstance(node, dict):
        return {k: strip_volatile(v) for k, v in sorted(node.items()) if k not in VOLATILE}
    if isinstance(node, list):
        return [strip_volatile(v) for v in node]
    return node


def export():
    service = find_service()
    if not service:
        sys.exit(f"Service '{SERVICE_NAME}' not found. Run --apply first.")
    archive = zipfile.ZipFile(io.BytesIO(call("GET", f"/services/{service['id']}/export", raw=True)))
    data = json.loads(archive.read(archive.namelist()[0]))  # one JSON file: {"transactions": [...]}
    data = strip_volatile(data)
    data["transactions"].sort(key=lambda t: t["name"])
    text = json.dumps(data, indent=2, ensure_ascii=False, sort_keys=True) + "\n"
    if re.search(r"(?i)(api[_-]?key|secret|password|token)\"\s*:\s*\"[^\"]+", text):
        print("Warning: the export has a key/secret/password/token field with a value. Check before commit.")
    EXPORT_FILE.parent.mkdir(parents=True, exist_ok=True)
    EXPORT_FILE.write_text(text)
    print(f"Wrote {EXPORT_FILE.relative_to(ROOT)} (service {service['id']}).")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--plan", action="store_true")
    group.add_argument("--apply", action="store_true")
    group.add_argument("--export", action="store_true")
    group.add_argument("--reset", action="store_true")
    parser.add_argument("--target", choices=sorted(TARGETS), default="proto")
    args = parser.parse_args()
    use_target(args.target)
    try:
        if args.export:
            export()
            return
        if args.reset:
            reset()
            return
        txns = build_plan()
        if args.apply:
            apply(txns)
    except ApiError as e:
        sys.exit(f"BlazeMeter API error: {e}")


if __name__ == "__main__":
    main()
