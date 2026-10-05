#!/usr/bin/env python3
"""Write mock/proto/vehicles.csv, the seed data of the stateful virtual service drivelink-proto.

One row per car: the demo car (DLEV26AURA0000101) and 500 load-test cars, each owned by one
load-test user. The virtual service selects the row by the VIN in the request path, so each
caller that uses its own VIN changes only its own car.

Columns are BlazeMeter data parameters (letters and digits only):
    vin, username, locked, acTargetPct, dcTargetPct

Usage: python3 scripts/build-proto-data.py [--users 500]
"""
import argparse
import csv
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "mock" / "proto" / "vehicles.csv"
DEMO_VIN = "DLEV26AURA0000101"
# VIN alphabet excludes I, O and Q (openapi.yaml Vin pattern).
LOAD_VIN_PREFIX = "DLEV26PERF"


def rows(users):
    yield {"vin": DEMO_VIN, "username": "demo@drivelink.test",
           "locked": "true", "acTargetPct": "80", "dcTargetPct": "90"}
    for n in range(1, users + 1):
        yield {"vin": f"{LOAD_VIN_PREFIX}{n:07d}", "username": f"perf{n:03d}@drivelink.test",
               "locked": "true", "acTargetPct": "80", "dcTargetPct": "90"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--users", type=int, default=500)
    args = parser.parse_args()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["vin", "username", "locked", "acTargetPct", "dcTargetPct"])
        writer.writeheader()
        writer.writerows(rows(args.users))
    print(f"wrote {args.users + 1} rows to {OUT}")


if __name__ == "__main__":
    main()
