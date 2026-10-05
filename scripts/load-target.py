#!/usr/bin/env python3
"""Write load/build/target.csv: the host name of the drivelink-proto virtual service.

load/drivelink-api.jmx reads the host from target.csv, so the URL is not in the repo.
The source is MOCK_BASE_URL, first from the environment, then from secrets.properties
(gitignored). The script never prints the URL. load/build/ is gitignored.
"""
import os
import sys
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "load" / "build" / "target.csv"


def mock_base_url() -> str | None:
    if os.environ.get("MOCK_BASE_URL"):
        return os.environ["MOCK_BASE_URL"].strip()
    props = ROOT / "secrets.properties"
    if not props.exists():
        return None
    for line in props.read_text().splitlines():
        key, sep, value = line.partition("=")
        if sep and key.strip() == "MOCK_BASE_URL":
            return value.strip() or None
    return None


def main() -> int:
    url = mock_base_url()
    if not url:
        print("Set MOCK_BASE_URL in the environment or in secrets.properties.", file=sys.stderr)
        return 1
    u = urllib.parse.urlparse(url)
    # The JMX sends https to the default port and puts /v1 in each path.
    if u.scheme != "https" or u.port or u.path.strip("/") or not u.hostname:
        print("MOCK_BASE_URL must be https://<host> with no port and no path.", file=sys.stderr)
        return 1
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(u.hostname + "\n")
    print(f"Wrote {OUT.relative_to(ROOT)} (host of MOCK_BASE_URL, not shown).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
