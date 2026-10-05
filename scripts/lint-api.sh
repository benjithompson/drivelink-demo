#!/usr/bin/env bash
# Lints the API contract: OpenAPI rules (Spectral), examples against schemas,
# spec/scenario consistency, generated example files up to date, and one example per
# virtual-service request (resolution check).
set -euo pipefail
cd "$(dirname "$0")/.."
npx --yes @stoplight/spectral-cli@6.17.0 lint api/openapi.yaml --fail-severity=error
python3 scripts/extract-examples.py --check
python3 scripts/check-resolution.py
