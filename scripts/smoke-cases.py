#!/usr/bin/env python3
"""Generate the smoke-test cases for the DriveLink virtual service (used by scripts/smoke.sh).

The expected responses come from the resolution rules in scripts/mock_resolver.py, so the
contract (api/openapi.yaml, api/scenarios.yaml, api/examples/index.json) is the only source.

Cases:
  - every operation x every scenario, with the EV VIN where the path has {vin}
  - under "default" only: the ICE VIN and an unknown VIN as well
  - getCommand: one case per X-Poll-Attempt from 1 to N, where N is the first attempt of
    the final state of the scenario's poll sequence (default 3, slow-vehicle 9, ...)
Request bodies for POST/PUT are the first requestBody example in api/openapi.yaml, else {}.

Output: one JSON object per line on stdout. Fields: id, scenario, operationId, method, url
(base path included, no host), headers, body (null = no body), status, file (expected body,
relative to api/examples/, null = do not compare), expectHeaders, example.

Usage:
  scripts/smoke-cases.py [--scenario NAME ...]   NAME may be a comma list
"""
import argparse
import importlib.util
import json
import sys
from pathlib import Path

import yaml

import mock_resolver

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("check_resolution", HERE / "check-resolution.py")
check_resolution = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(check_resolution)

VINS = check_resolution.VINS
PARAM_VALUES = check_resolution.PARAM_VALUES
POLL_HEADER = check_resolution.POLL_HEADER
MAX_ATTEMPT = max(check_resolution.ATTEMPTS)
MIN_ATTEMPTS = {"command-timeout": 3}  # show that PENDING repeats


def request_bodies():
    """operationId -> first requestBody example value (application/json)."""
    spec = yaml.safe_load(mock_resolver.SPEC.read_text())
    bodies = {}
    for item in spec["paths"].values():
        for method in mock_resolver.METHODS:
            op = item.get(method)
            if not op or "requestBody" not in op:
                continue
            content = mock_resolver.deref(spec, op["requestBody"]).get("content", {})
            media = content.get("application/json") or next(iter(content.values()), {})
            if "example" in media:
                bodies[op["operationId"]] = media["example"]
            elif media.get("examples"):
                first = next(iter(media["examples"].values()))
                bodies[op["operationId"]] = mock_resolver.deref(spec, first).get("value", {})
    return bodies


def poll_attempts(contract, op, scenario, path, headers):
    """Attempts 1..N, N = first attempt of the final state of the poll sequence."""
    last, last_change = None, 1
    for n in range(1, MAX_ATTEMPT + 1):
        entry, _ = mock_resolver.resolve(contract, op["method"], path, dict(headers, **{POLL_HEADER: str(n)}))
        key = entry and (entry["status"], entry["example"])
        if n > 1 and key != last:
            last_change = n
        last = key
    return range(1, max(last_change, MIN_ATTEMPTS.get(scenario, 1)) + 1)


def cases(contract, scenarios):
    bodies = request_bodies()
    for op in contract["operations"]:
        has_vin = "{vin}" in op["path"]
        for scenario in contract["scenarios"]:
            if scenarios and scenario not in scenarios:
                continue
            vins = list(VINS) if has_vin and scenario == mock_resolver.DEFAULT_SCENARIO else ["EV"]
            for vin in vins:
                path = contract["basePath"] + op["path"].format(**dict(PARAM_VALUES, vin=VINS[vin]))
                headers = {} if scenario == mock_resolver.DEFAULT_SCENARIO else {"X-Scenario": scenario}
                body = None
                if op["method"] in ("POST", "PUT", "PATCH"):
                    body = bodies.get(op["operationId"], {})
                    headers["Content-Type"] = "application/json"
                polls = POLL_HEADER in op["headerParams"]
                attempts = poll_attempts(contract, op, scenario, path, headers) if polls else [None]
                for attempt in attempts:
                    h = dict(headers)
                    case_id = f"{op['operationId']}.{scenario}"
                    if has_vin:
                        case_id += f".{vin}"
                    if attempt is not None:
                        h[POLL_HEADER] = str(attempt)
                        case_id += f".poll{attempt}"
                    entry, info = mock_resolver.resolve(contract, op["method"], path, h)
                    if entry is None:
                        print(f"smoke-cases: skip {case_id}: {info['status']}", file=sys.stderr)
                        continue
                    yield {
                        "id": case_id,
                        "scenario": scenario,
                        "operationId": op["operationId"],
                        "method": op["method"],
                        "url": path,
                        "headers": h,
                        "body": body,
                        "status": entry["status"],
                        "file": entry["file"],
                        "expectHeaders": entry["headers"],
                        "example": f"{entry['status']}.{entry['example']}",
                    }


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--scenario", action="append", default=[],
                        help="only this scenario (repeatable, or a comma list)")
    args = parser.parse_args()
    contract = mock_resolver.load_contract()
    wanted = {s.strip() for arg in args.scenario for s in arg.split(",") if s.strip()}
    unknown = wanted - set(contract["scenarios"])
    if unknown:
        sys.exit(f"smoke-cases: unknown scenario {', '.join(sorted(unknown))}; "
                 f"known: {', '.join(contract['scenarios'])}")
    for case in cases(contract, wanted):
        print(json.dumps(case, separators=(",", ":")))


if __name__ == "__main__":
    main()
