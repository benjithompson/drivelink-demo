#!/usr/bin/env python3
"""Check that every virtual-service request resolves to exactly one example.

Runs the rules in scripts/mock_resolver.py over a request grid:
  every operation x every scenario in api/scenarios.yaml
  x vin in [EV, ICE, unknown]            (operations with {vin} only)
  x X-Poll-Attempt in [absent, 1..40]    (operations with an X-Poll-Attempt header only)

Problems (exit 1):
  - a request with no example, or with two or more examples at the top specificity (tie)
  - an index entry with a scenario that no request in the grid reaches
  - a command-poll sequence that differs from the scenario descriptions in scenarios.yaml
  - a scenario that changes an operation outside its scope, or (auth-expired, rate-limited,
    server-error) does not return 401/429/500 for an operation in its scope
  - the SV transaction order (method + urlRegex + X-Scenario equals + x-match regexes,
    lowest priority wins) selects a different example than the resolver, or ties

Usage:
  scripts/check-resolution.py
"""
import json
import re
import sys
from collections import defaultdict

import mock_resolver

VINS = {"EV": "DLEV26AURA0000101", "ICE": "DLGS25SLACE000202", "unknown": "ZZZZZZZZZZZZZZZZZ"}
PARAM_VALUES = {"commandId": "cmd-5b1e7c40", "alertId": "alt-0001"}
POLL_HEADER = "X-Poll-Attempt"
ATTEMPTS = range(1, 41)
ERROR_STATUS = {"auth-expired": 401, "rate-limited": 429, "server-error": 500}


def expected_poll(scenario, attempt):
    """(status, reason) of getCommand per scenarios.yaml descriptions; None = not checked."""
    rules = {
        "default": lambda n: ("PENDING", None) if n <= 2 else ("SUCCEEDED", None),
        "fast": lambda n: ("SUCCEEDED", None),
        "slow-vehicle": lambda n: ("PENDING", None) if n <= 8 else ("SUCCEEDED", None),
        "vehicle-asleep": lambda n: (("PENDING", "WAKING") if n <= 2 else
                                     ("PENDING", None) if n <= 4 else ("SUCCEEDED", None)),
        "command-fails": lambda n: ("PENDING", None) if n == 1 else ("FAILED", "DOOR_OPEN"),
        "command-timeout": lambda n: ("PENDING", None),
    }
    rule = rules.get(scenario)
    return rule(attempt) if rule else None


def request_grid(contract):
    """Yield (op, scenario, vin label, attempt, path, headers) for every request in the grid."""
    for op in contract["operations"]:
        vins = list(VINS) if "{vin}" in op["path"] else [None]
        attempts = [None, *ATTEMPTS] if POLL_HEADER in op["headerParams"] else [None]
        for scenario in contract["scenarios"]:
            for vin in vins:
                values = dict(PARAM_VALUES, vin=VINS.get(vin, ""))
                path = contract["basePath"] + op["path"].format(**values)
                for attempt in attempts:
                    headers = {} if scenario == "default" else {"X-Scenario": scenario}
                    if attempt is not None:
                        headers[POLL_HEADER] = str(attempt)
                    yield op, scenario, vin, attempt, path, headers


def sv_select(contract, method, path, headers):
    """Emulate BlazeMeter SV: all transactions that match, lowest priority wins. Returns the top tier."""
    headers = {k.lower(): v for k, v in headers.items()}
    found = []
    for e in contract["index"]:
        if e["scenario"] is None or e["method"] != method or not re.fullmatch(e["urlRegex"], path):
            continue
        if e["scenario"] != "default" and headers.get("x-scenario") != e["scenario"]:
            continue
        if all(name.lower() in headers and re.fullmatch(rx, headers[name.lower()])
               for name, rx in e["match"].items()):
            found.append(e)
    if not found:
        return []
    best = min(e["priority"] for e in found)
    return [e for e in found if e["priority"] == best]


def key(entry):
    return (entry["operationId"], entry["status"], entry["example"])


def label(entry):
    return f"{entry['status']}.{entry['example']}"


def check(contract):
    problems = []
    reached = set()
    results = {}
    count = 0
    for op, scenario, vin, attempt, path, headers in request_grid(contract):
        count += 1
        entry, info = mock_resolver.resolve(contract, op["method"], path, headers)
        where = (op["operationId"], scenario, vin, attempt)
        results[where] = entry
        if entry is None:
            problems.append(("no example", *where, info["status"]))
            continue
        reached.add(key(entry))
        if len(info["top"]) > 1:
            problems.append(("tie", *where, " = ".join(label(e) for e in info["top"])))
        sv = sv_select(contract, op["method"], path, headers)
        if len(sv) != 1 or key(sv[0]) != key(entry):
            got = " = ".join(f"{e['operationId']}/{label(e)}" for e in sv) or "nothing"
            problems.append(("sv order", *where, f"SV selects {got}, resolver {label(entry)}"))

    for (op_id, scenario, vin, attempt), entry in results.items():
        if entry is None:
            continue
        where = (op_id, scenario, vin, attempt)
        if op_id == mock_resolver.POLL_OPERATION and attempt is not None:
            want = expected_poll(scenario, attempt)
            if want and entry["file"]:
                body = json.loads(mock_resolver.body_bytes(entry))
                got = (body.get("status"), body.get("reason"))
                if got != want:
                    problems.append(("poll sequence", *where, f"got {got}, want {want} ({label(entry)})"))
        spec = contract["scenarios"][scenario]
        scope = spec["scope"]
        in_scope = (op_id not in spec.get("except", [])) if scope == "all" else op_id in scope
        default = results.get((op_id, "default", vin, attempt))
        if not in_scope and entry is not default:
            problems.append(("out of scope", *where, f"{label(entry)} differs from default"))
        if in_scope and scenario in ERROR_STATUS and entry["status"] != ERROR_STATUS[scenario]:
            problems.append(("in scope", *where, f"{label(entry)}, want {ERROR_STATUS[scenario]}"))

    entries = [e for e in contract["index"] if e["scenario"] is not None]
    for e in entries:
        if key(e) not in reached:
            problems.append(("unreachable", e["operationId"], e["scenario"], e["vin"], None, label(e)))
    return count, len({key(e) for e in entries} & reached), len(entries), problems


def ranges(values):
    """Compact a set of attempts: [None, 1, 2, 3, 7] -> 'absent,1-3,7'."""
    nums = sorted(v for v in values if v is not None)
    out = ["absent"] if None in values else []
    start = prev = None
    for n in nums + [None]:
        if start is not None and n != prev + 1:
            out.append(str(start) if start == prev else f"{start}-{prev}")
            start = None
        if start is None:
            start = n
        prev = n
    return ",".join(out)


def report(problems):
    """Group problems that differ only in scenario or attempt."""
    groups = defaultdict(lambda: (set(), set()))
    for kind, op_id, scenario, vin, attempt, detail in problems:
        scenarios, attempts = groups[(kind, op_id, vin, detail)]
        scenarios.add(scenario)
        attempts.add(attempt)
    lines = []
    for (kind, op_id, vin, detail), (scenarios, attempts) in sorted(groups.items(), key=str):
        parts = [f"{kind}: {op_id}", f"scenarios={','.join(sorted(scenarios))}"]
        if vin:
            parts.append(f"vin={vin}")
        if attempts != {None}:
            parts.append(f"attempt={ranges(attempts)}")
        lines.append("  " + " ".join(parts) + f" -> {detail}")
    return lines


def main():
    contract = mock_resolver.load_contract()
    count, reached, total, problems = check(contract)
    print(f"Resolution check: {count} requests, {len(contract['operations'])} operations, "
          f"{len(contract['scenarios'])} scenarios. Entries reached: {reached}/{total}. "
          f"Problems: {len(problems)}.")
    if problems:
        lines = report(problems)
        print(f"{len(lines)} problem groups (requests that differ only in scenario or attempt):")
        print("\n".join(lines))
        sys.exit(1)


if __name__ == "__main__":
    main()
