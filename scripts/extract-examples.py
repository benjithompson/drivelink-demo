#!/usr/bin/env python3
"""Extract named examples from api/openapi.yaml into api/examples/.

Output:
  api/examples/<operationId>/<status>.<exampleName>.json   one file per response example
  api/examples/index.json                                  metadata for the smoke test and the
                                                           virtual-service build

Each index entry: operationId, method, path, status, contentType, example, scenario, match,
vin, headers, file. Examples without x-scenario are documentation only and get scenario null.

Entries with a scenario also carry the fields that map 1:1 to a BlazeMeter SV transaction:
  url          base path + path template (/v1/vehicles/{vin}/status)
  urlRegex     anchored regex for the SV "matches_url" matcher; {vin} becomes the literal
               x-vin value when set, every other parameter matches one segment
  priority     SV transaction priority (lowest number wins). Scenario entries 1-3, default
               entries 4-6; inside each band, more specific (x-match, x-vin) comes first.
               Default entries have no X-Scenario matcher, so they also catch the fallback.
  thinkTimeMs  [min, max] of the entry's scenario (pollThinkTimeMs for getCommand)
scripts/check-resolution.py checks that this priority order selects the same example as the
reference resolver for every request.

Usage:
  scripts/extract-examples.py           write the files
  scripts/extract-examples.py --check   fail if the files on disk are out of date
"""
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit

import yaml

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "api" / "openapi.yaml"
SCENARIOS = ROOT / "api" / "scenarios.yaml"
RAW = ROOT / "api" / "examples-raw"
OUT = ROOT / "api" / "examples"
METHODS = ("get", "put", "post", "delete", "patch")
POLL_OPERATION = "getCommand"


def resolve(spec, node):
    """Follow a local $ref ("#/components/...") until a concrete node is reached."""
    while isinstance(node, dict) and "$ref" in node:
        target = spec
        for part in node["$ref"].lstrip("#/").split("/"):
            target = target[part]
        node = target
    return node


def build(spec, scenarios):
    files = {}
    index = []
    for path, item in spec["paths"].items():
        for method in METHODS:
            op = item.get(method)
            if not op:
                continue
            op_id = op["operationId"]
            for status, response in op.get("responses", {}).items():
                response = resolve(spec, response)
                for content_type, media in response.get("content", {}).items():
                    for name, example in media.get("examples", {}).items():
                        example = resolve(spec, example)
                        rel = f"{op_id}/{status}.{name}.json"
                        files[rel] = example["value"]
                        index.append({
                            "operationId": op_id,
                            "method": method.upper(),
                            "path": path,
                            "status": int(status),
                            "contentType": content_type,
                            "example": name,
                            "scenario": example.get("x-scenario"),
                            "match": example.get("x-match", {}),
                            "vin": example.get("x-vin"),
                            "headers": example.get("x-headers", {}),
                            "file": rel,
                        })
                if not response.get("content") and status.startswith("2"):
                    # Bodiless success (for example 204): one entry so the smoke test covers it.
                    index.append({
                        "operationId": op_id,
                        "method": method.upper(),
                        "path": path,
                        "status": int(status),
                        "contentType": None,
                        "example": "default",
                        "scenario": "default",
                        "match": {},
                        "vin": None,
                        "headers": {},
                        "file": None,
                    })

    ops = {(e["operationId"]): (e["method"], e["path"]) for e in index}
    for scenario, spec_entry in scenarios["scenarios"].items():
        for op_id, raw in (spec_entry.get("rawExamples") or {}).items():
            method, path = ops[op_id]
            rel = f"{op_id}/{raw['status']}.{scenario}.json"
            files[rel] = json.loads((RAW / raw["file"]).read_text())
            index.append({
                "operationId": op_id,
                "method": method,
                "path": path,
                "status": raw["status"],
                "contentType": "application/json",
                "example": scenario,
                "scenario": scenario,
                "match": {},
                "vin": None,
                "headers": {},
                "file": rel,
                "invalidOnPurpose": True,
            })

    base = urlsplit(spec["servers"][0]["url"]).path.rstrip("/")
    for entry in index:
        if entry["scenario"]:
            entry.update(transaction_fields(entry, base, scenarios["scenarios"][entry["scenario"]]))
    index.sort(key=lambda e: (e["path"], e["method"], e["status"], e["example"]))
    rendered = {rel: json.dumps(body, indent=2, ensure_ascii=False) + "\n" for rel, body in files.items()}
    rendered["index.json"] = json.dumps(index, indent=2, ensure_ascii=False) + "\n"
    return rendered


def transaction_fields(entry, base, scenario):
    """SV transaction fields for one entry (see the module docstring)."""
    def segment(m):
        if m.group(1) == "vin" and entry["vin"]:
            return re.escape(entry["vin"])
        return "[^/?]+"
    parts = re.split(r"(\{\w+\})", base + entry["path"])
    url_regex = "".join(re.sub(r"\{(\w+)\}", segment, p) if p.startswith("{") else re.escape(p)
                        for p in parts)
    specificity = bool(entry["match"]) + bool(entry["vin"])
    band = 3 if entry["scenario"] == "default" else 0
    think = (entry["operationId"] == POLL_OPERATION and scenario.get("pollThinkTimeMs")) or scenario["thinkTimeMs"]
    return {
        "url": base + entry["path"],
        "urlRegex": f"^{url_regex}(\\?.*)?$",
        "priority": band + 3 - specificity,
        "thinkTimeMs": think,
    }


def string_enums_with_non_strings(node, where="#"):
    """YAML 1.1 reads unquoted OFF/ON/YES/NO as booleans. Find string enums that lost a value."""
    found = []
    if isinstance(node, dict):
        if node.get("type") == "string" and any(not isinstance(v, str) for v in node.get("enum", [])):
            found.append(where)
        for k, v in node.items():
            found += string_enums_with_non_strings(v, f"{where}/{k}")
    elif isinstance(node, list):
        for i, v in enumerate(node):
            found += string_enums_with_non_strings(v, f"{where}/{i}")
    return found


def validate(spec, scenarios, index):
    """Consistency checks between openapi.yaml and scenarios.yaml. Returns a list of errors."""
    errors = [f"string enum has a non-string value (quote it in YAML): {w}"
              for w in string_enums_with_non_strings(spec)]
    names = set(scenarios["scenarios"])
    header_enum = set(spec["components"]["parameters"]["XScenario"]["schema"]["enum"])
    if names != header_enum:
        errors.append(f"X-Scenario enum and scenarios.yaml differ: {sorted(names ^ header_enum)}")
    used = {e["scenario"] for e in index if e["scenario"]}
    for unknown in sorted(used - names):
        errors.append(f"x-scenario '{unknown}' is not in scenarios.yaml")
    for missing in sorted(names - used):
        errors.append(f"scenario '{missing}' has no example in openapi.yaml or rawExamples")
    op_ids = {e["operationId"] for e in index}
    for name, entry in scenarios["scenarios"].items():
        scope = entry["scope"]
        listed = (scope if isinstance(scope, list) else []) + entry.get("except", [])
        for op_id in listed:
            if op_id not in op_ids:
                errors.append(f"scenario '{name}' names unknown operation '{op_id}'")
    for op_id in sorted(op_ids):
        if not any(e["operationId"] == op_id and e["scenario"] == "default" and 200 <= e["status"] < 300 for e in index):
            errors.append(f"operation '{op_id}' has no default 2xx example")
    return errors


def main():
    spec = yaml.safe_load(SPEC.read_text())
    scenarios = yaml.safe_load(SCENARIOS.read_text())
    rendered = build(spec, scenarios)
    errors = validate(spec, scenarios, json.loads(rendered["index.json"]))
    if errors:
        print("Spec and scenario catalog are not consistent:")
        for e in errors:
            print("  ", e)
        sys.exit(1)
    existing = {str(p.relative_to(OUT)): p for p in OUT.rglob("*.json")} if OUT.exists() else {}

    if "--check" in sys.argv:
        stale = [rel for rel, text in rendered.items()
                 if rel not in existing or existing[rel].read_text() != text]
        extra = sorted(set(existing) - set(rendered))
        if stale or extra:
            print("api/examples is out of date. Run scripts/extract-examples.py.")
            for rel in stale + extra:
                print("  ", rel)
            sys.exit(1)
        print(f"api/examples is up to date ({len(rendered)} files).")
        return

    for rel in set(existing) - set(rendered):
        existing[rel].unlink()
    for rel, text in rendered.items():
        target = OUT / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
    print(f"Wrote {len(rendered)} files to {OUT.relative_to(ROOT)}.")


if __name__ == "__main__":
    main()
