"""Response resolution for the DriveLink reference mock (scripts/mock-server.py).

This module is the reference for the BlazeMeter virtual-service transactions (Phase 3).
It loads api/openapi.yaml, api/scenarios.yaml and api/examples/index.json, and it selects
one index entry for a request. It does no I/O after the load.

Resolution rules:
  1. Route. Strip the base path of the first `servers` URL (/v1). Find the operation by
     method and path template. A path parameter {x} matches exactly one path segment.
     No operation -> 404 problem body, code NOT_FOUND.
  2. Scenario. The scenario is the X-Scenario request header, else "default".
     A name that is not in scenarios.yaml -> 400 problem body, code INVALID_REQUEST.
  3. Candidates. Entries with the operationId and entry.scenario == scenario, where
     entry.vin is null or equal to the {vin} path value, and every entry.match regex
     matches the request header of that name (re.fullmatch, header names are not case
     sensitive). A missing header fails its matcher. Entries with scenario null are
     documentation only and are never candidates.
  4. Priority. specificity = (entry has match) + (entry has vin). The highest specificity
     wins. Two or more candidates at the top specificity is an ambiguity: the mock uses the
     first one in index order, and scripts/check-resolution.py reports it.
  5. Fallback. No candidate in the requested scenario -> repeat steps 3-4 with "default".
     No candidate in "default" either -> 404 problem body, code NOT_FOUND (the mock's own
     answer; the contract has no example for the request).
  6. Response. Status and Content-Type from the entry, body = the exact bytes of
     api/examples/<file> (no body when file is null, for example 204), headers from
     entry.headers, and X-Correlation-Id (the request value, else a new uuid4).
  7. Think time. Uniform random in thinkTimeMs of the REQUESTED scenario, also when the
     response comes from "default". getCommand uses pollThinkTimeMs when the scenario has it.

Usage (library):
  from mock_resolver import load_contract, resolve
  contract = load_contract()
  entry, info = resolve(contract, "GET", "/v1/vehicles/DLEV26AURA0000101/status", {"X-Scenario": "low-battery"})
"""
import json
import random
import re
from pathlib import Path
from urllib.parse import urlsplit

import yaml

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "api" / "openapi.yaml"
SCENARIOS = ROOT / "api" / "scenarios.yaml"
EXAMPLES = ROOT / "api" / "examples"
INDEX = EXAMPLES / "index.json"
METHODS = ("get", "put", "post", "delete", "patch")
DEFAULT_SCENARIO = "default"
POLL_OPERATION = "getCommand"
PROBLEM_TYPE = "https://drivelink.test/problems/"


def deref(spec, node):
    """Follow a local $ref ("#/components/...") until a concrete node is reached."""
    while isinstance(node, dict) and "$ref" in node:
        target = spec
        for part in node["$ref"].lstrip("#/").split("/"):
            target = target[part]
        node = target
    return node


def template_regex(template):
    """Compile an OpenAPI path template; each {name} matches one segment."""
    parts = re.split(r"\{(\w+)\}", template)
    pattern = "".join(re.escape(p) if i % 2 == 0 else f"(?P<{p}>[^/]+)" for i, p in enumerate(parts))
    return re.compile(pattern)


def load_operations(spec):
    ops = []
    for template, item in spec["paths"].items():
        for method in METHODS:
            op = item.get(method)
            if not op:
                continue
            params = [deref(spec, p) for p in item.get("parameters", []) + op.get("parameters", [])]
            ops.append({
                "operationId": op["operationId"],
                "method": method.upper(),
                "path": template,
                "regex": template_regex(template),
                "headerParams": [p["name"] for p in params if p["in"] == "header"],
            })
    return ops


def load_contract():
    """Load spec, scenario catalog and example index into one dict."""
    spec = yaml.safe_load(SPEC.read_text())
    return {
        "basePath": urlsplit(spec["servers"][0]["url"]).path.rstrip("/"),
        "operations": load_operations(spec),
        "scenarios": yaml.safe_load(SCENARIOS.read_text())["scenarios"],
        "index": json.loads(INDEX.read_text()),
    }


def find_operation(contract, method, path):
    """Return (operation, path params) or (None, {})."""
    base = contract["basePath"]
    path = path.split("?", 1)[0]
    if base:
        if path != base and not path.startswith(base + "/"):
            return None, {}
        path = path[len(base):] or "/"
    for op in contract["operations"]:
        if op["method"] == method.upper():
            m = op["regex"].fullmatch(path)
            if m:
                return op, m.groupdict()
    return None, {}


def specificity(entry):
    return bool(entry["match"]) + bool(entry["vin"])


def candidates(contract, op_id, scenario, params, headers):
    """Entries of one scenario that match the request (rule 3)."""
    found = []
    for entry in contract["index"]:
        if entry["operationId"] != op_id or entry["scenario"] != scenario:
            continue
        if entry["vin"] is not None and entry["vin"] != params.get("vin"):
            continue
        if all(name.lower() in headers and re.fullmatch(rx, headers[name.lower()])
               for name, rx in entry["match"].items()):
            found.append(entry)
    return found


def top_tier(found):
    """Candidates at the highest specificity (rule 4). More than one = ambiguity."""
    if not found:
        return []
    best = max(specificity(e) for e in found)
    return [e for e in found if specificity(e) == best]


def resolve(contract, method, path, headers):
    """Select the index entry for a request.

    Returns (entry, info). entry is None when the request does not resolve; info["status"]
    then says why ("no-route", "bad-scenario", "no-match"). info["top"] holds all candidates
    at the winning specificity, so len(info["top"]) > 1 means an ambiguity.
    """
    headers = {k.lower(): v for k, v in headers.items()}
    scenario = headers.get("x-scenario") or DEFAULT_SCENARIO
    info = {"status": "ok", "operationId": None, "scenario": scenario,
            "servedScenario": None, "params": {}, "top": []}
    op, params = find_operation(contract, method, path)
    if op is None:
        info["status"] = "no-route"
        return None, info
    info["operationId"], info["params"] = op["operationId"], params
    if scenario not in contract["scenarios"]:
        info["status"] = "bad-scenario"
        return None, info
    for name in dict.fromkeys((scenario, DEFAULT_SCENARIO)):
        top = top_tier(candidates(contract, op["operationId"], name, params, headers))
        if top:
            info["servedScenario"], info["top"] = name, top
            return top[0], info
    info["status"] = "no-match"
    return None, info


def problem(status, code, title, detail, correlation_id):
    """Problem body (components.schemas.Problem) for the mock's own errors."""
    return {
        "type": PROBLEM_TYPE + code.lower().replace("_", "-"),
        "title": title,
        "status": status,
        "detail": detail,
        "code": code,
        "correlationId": correlation_id,
    }


def error_for(info, method, path, correlation_id):
    """(status, problem body) for a request that did not resolve."""
    if info["status"] == "no-route":
        return 404, problem(404, "NOT_FOUND", "Not found", f"No operation for {method} {path}.", correlation_id)
    if info["status"] == "bad-scenario":
        return 400, problem(400, "INVALID_REQUEST", "Invalid request",
                            f"Unknown X-Scenario '{info['scenario']}'.", correlation_id)
    return 404, problem(404, "NOT_FOUND", "Not found",
                        f"No example for {info['operationId']} in scenario '{info['scenario']}'.", correlation_id)


def body_bytes(entry):
    """Exact bytes of the example file, or b"" for a bodiless entry."""
    return (EXAMPLES / entry["file"]).read_bytes() if entry["file"] else b""


def think_time_ms(contract, scenario, op_id, rng=random):
    """Delay for the requested scenario (rule 7). Unknown scenario -> 0."""
    entry = contract["scenarios"].get(scenario)
    if not entry:
        return 0
    low, high = (op_id == POLL_OPERATION and entry.get("pollThinkTimeMs")) or entry["thinkTimeMs"]
    return rng.uniform(low, high)
