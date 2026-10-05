# DriveLink Demo — API

## Summary

The API contract is [api/openapi.yaml](../api/openapi.yaml) (OpenAPI 3.0.3). It has 18 operations under the base path `/v1`. Every operation has a default example. All 14 scenarios have example responses.

The generated index (`api/examples/index.json`) has 110 entries: 95 scenario examples from the spec, 1 raw example (`bad-payload`) and 14 documentation-only examples. `api/examples/` holds 110 files: 109 JSON bodies and `index.json`.

The contract is the single source of truth:

- The build script creates the virtual-service transactions from the named examples.
- The app's data classes match the schemas. A contract test parses every example.
- `scripts/lint-api.sh` checks the contract.

```sh
scripts/lint-api.sh
```

Expected lint output:

```text
No results with a severity of 'error' found!
api/examples is up to date (110 files).
Resolution check: 1092 requests, 18 operations, 14 scenarios. Entries reached: 96/96. Problems: 0.
```

Spectral reports 0 errors and 0 warnings. The consistency checks pass. The resolution check finds one example for each request.

## Endpoints

All operations need `Authorization: Bearer <token>`, except `getHealth`, `createToken` and `refreshToken`.

| # | Method | Path | operationId | Success | Notes |
| --- | --- | --- | --- | --- | --- |
| 1 | GET | `/health` | getHealth | 200 | "Test connection" button. No scenario changes it. |
| 2 | POST | `/auth/token` | createToken | 200 | Body `{email, password}`. 401 `INVALID_CREDENTIALS` (documentation only). |
| 3 | POST | `/auth/refresh` | refreshToken | 200 | Body `{refreshToken}` |
| 4 | GET | `/me` | getMe | 200 | Name, email, units |
| 5 | GET | `/vehicles` | listVehicles | 200 | One EV (Aurora) and one gas car (Solace) |
| 6 | GET | `/vehicles/{vin}/status` | getVehicleStatus | 200 | Connectivity, lock, climate, doors, windows, battery or fuel, range, charging, tires, odometer. 404 for an unknown VIN. |
| 7 | POST | `/vehicles/{vin}/commands` | sendCommand | 202 | Body `{type, pin, params?}`. 403 `INVALID_PIN` (documentation only). |
| 8 | GET | `/commands/{commandId}` | getCommand | 200 | Poll with `X-Poll-Attempt: n` |
| 9 | GET | `/vehicles/{vin}/location` | getVehicleLocation | 200 | Lat, lon, accuracy, address, locality. One location per demo car. |
| 10 | GET | `/vehicles/{vin}/charge-settings` | getChargeSettings | 200 | 404 for the gas car and for an unknown VIN |
| 11 | PUT | `/vehicles/{vin}/charge-settings` | updateChargeSettings | 200 | AC and DC limits, schedules, departure. 404 for the gas car and for an unknown VIN. |
| 12 | GET | `/vehicles/{vin}/climate-presets` | getClimatePresets | 200 | Up to 4 presets |
| 13 | PUT | `/vehicles/{vin}/climate-presets` | updateClimatePresets | 200 | |
| 14 | GET | `/vehicles/{vin}/trips` | listTrips | 200 | `?limit=20`. mi/kWh for the EV, MPG for the gas car. |
| 15 | GET | `/vehicles/{vin}/maintenance` | getMaintenance | 200 | Last and next service, items, recalls, preferred service center. One data set per demo car. |
| 16 | POST | `/vehicles/{vin}/service-requests` | createServiceRequest | 201 | |
| 17 | GET | `/alerts` | listAlerts | 200 | Optional `?vin=` |
| 18 | POST | `/alerts/{alertId}/read` | markAlertRead | 204 | |

**Command types:** `LOCK`, `UNLOCK`, `START`, `STOP`, `HORN_LIGHTS`, `LIGHTS`, `CHARGE_START`, `CHARGE_STOP`.

**Remote-start options (`params`):** `tempMode` (`OFF`, `LO`, `SET`, `HI`), `tempF` (62–82), `frontDefrost`, `rearDefrost`, `heatedSteeringWheel`, `heatedSeats` (0–3), `durationMin` (1–10), `presetId`.

**Demo vehicles:**

| VIN | Vehicle | Powertrain |
| --- | --- | --- |
| `DLEV26AURA0000101` | 2026 Aurora EV, Limited AWD | EV |
| `DLGS25SLACE000202` | 2025 Solace, SEL 2.5L | ICE |

The VINs are invented. They use the 17-character VIN format and do not contain the letters I, O or Q.

Each demo car has its own data for status, trips, maintenance and location. The other operations return the same data for both cars.

**Unknown VIN:** a VIN that is not a demo VIN gets 404 `NOT_FOUND` ("Vehicle not found") from `getVehicleStatus`, `getChargeSettings` and `updateChargeSettings`. All other operations return the default data for an unknown VIN.

**Command type in responses:** the virtual service returns fixed example bodies. The `type` in a `sendCommand` or `getCommand` response can differ from the requested type. The app shows the command label from its local state.

## Scenario coverage

The table shows the operations that each scenario changes. All other operations return the default response with the scenario's think time.

The `low-battery`, `door-ajar`, `tire-low` and `vehicle-offline` status examples apply to the EV only. In these scenarios, the gas car gets its default status.

| Scenario | Operations and status codes |
| --- | --- |
| `default` | All 18 operations (2xx). Charge settings return 404 for the gas car. Status and charge settings return 404 for an unknown VIN. |
| `fast` | `getCommand` 200 (SUCCEEDED on attempt 1). Think time about 50 ms everywhere. |
| `slow-vehicle` | `getCommand` 200: PENDING on attempts 1–8, then SUCCEEDED. 2–3 s per poll. |
| `vehicle-asleep` | `getCommand` 200: PENDING + WAKING on 1–2, PENDING on 3–4, then SUCCEEDED |
| `vehicle-offline` | `sendCommand` 409 `VEHICLE_OFFLINE`; `getVehicleStatus` 200 with data 3 days old and `connectivity: OFFLINE` (EV only) |
| `command-fails` | `getCommand` 200: PENDING on 1, then FAILED + `DOOR_OPEN` |
| `command-timeout` | `getCommand` 200: always PENDING |
| `low-battery` | `getVehicleStatus` (8 %, 19 mi, EV only); `listAlerts` (low range) |
| `door-ajar` | `getVehicleStatus` (rear-left door open, unlocked, EV only); `listAlerts` (door open) |
| `tire-low` | `getVehicleStatus` (rear-left 24 psi, `lowWheels: [rl]`, EV only); `listAlerts` (low tire) |
| `auth-expired` | 401 `TOKEN_EXPIRED` on 16 operations (not `getHealth`, not `createToken`) |
| `rate-limited` | 429 `RATE_LIMITED` + `Retry-After: 30` on 17 operations (not `getHealth`) |
| `server-error` | 500 `INTERNAL` on 17 operations (not `getHealth`) |
| `bad-payload` | `getVehicleStatus` 200 with `rangeMi` missing and `locked` as a string |

## Files

| File | Purpose |
| --- | --- |
| [api/openapi.yaml](../api/openapi.yaml) | The contract: paths, schemas, named examples |
| [api/scenarios.yaml](../api/scenarios.yaml) | Think time, scope and expected app behavior per scenario |
| [api/examples-raw/](../api/examples-raw/) | Payloads that are invalid on purpose (`bad-payload`) |
| [api/examples/](../api/examples/) | Generated: one JSON file per example, plus `index.json`. Do not edit by hand. |
| [scripts/extract-examples.py](../scripts/extract-examples.py) | Generates `api/examples/` and checks spec/catalog consistency |
| [scripts/lint-api.sh](../scripts/lint-api.sh) | Spectral lint + consistency check + generated files up to date + resolution check |
| [scripts/mock_resolver.py](../scripts/mock_resolver.py) | Resolution rules: which example answers a request |
| [scripts/check-resolution.py](../scripts/check-resolution.py) | Checks that each request in the grid resolves to exactly one example |
| [scripts/mock-server.py](../scripts/mock-server.py) | Local reference mock that serves `api/examples/` |
| [.spectral.yaml](../.spectral.yaml) | Lint rules. Examples must match their schemas. |

## How the examples drive the virtual service

Each named example carries metadata in `x-` fields. The build script reads `api/examples/index.json` and creates one transaction per entry.

| Field | Meaning | Example |
| --- | --- | --- |
| `x-scenario` | `X-Scenario` value that returns this response | `command-fails` |
| `x-match` | Extra request matchers (regex) | `X-Poll-Attempt: "^[12]$"` |
| `x-vin` | Limits the example to one VIN in the path | `DLGS25SLACE000202` |
| `x-headers` | Extra response headers | `Retry-After: "30"` |

An example without `x-match` is the fallback for its scenario. An example without `x-scenario` is documentation only.

The generator adds 4 fields to each index entry that has a scenario. These fields map 1:1 to a BlazeMeter SV transaction, so the build does not need its own matching logic.

| Index field | SV transaction field | Example |
| --- | --- | --- |
| `urlRegex` | `requestDsl.url`, matcher `matches_url` | `^/v1/vehicles/DLGS25SLACE000202/status(\?.*)?$` |
| `scenario` (not `default`) | header matcher `X-Scenario`, `equals` | `slow-vehicle` |
| `match` | header matcher, `matches` | `X-Poll-Attempt` `^[1-8]$` |
| `priority` | transaction priority (the lowest number wins) | `2` |
| `thinkTimeMs` | `responseDelay`, type `uniform` | `[2000, 3000]` |

Priority bands:

| Priority | Entry |
| --- | --- |
| 1 | Scenario example with `x-match` and `x-vin` |
| 2 | Scenario example with `x-match` or `x-vin` |
| 3 | Scenario example, no matcher |
| 4–6 | `default` examples, in the same order. They have no `X-Scenario` matcher, so they also answer every scenario that does not change the operation. |

`scripts/check-resolution.py` emulates the SV selection for every request in its grid: method, `urlRegex`, `X-Scenario` and `x-match`, then the lowest priority. The selected example must be the same one that the resolver selects, and no two transactions can tie.

## Local reference mock

The local mock serves the generated examples with the same resolution rules that the BlazeMeter transactions encode.

```sh
python3 scripts/mock-server.py --port 8080 [--no-delay]
curl -H 'X-Scenario: low-battery' http://127.0.0.1:8080/v1/vehicles/DLEV26AURA0000101/status
```

Resolution rules (the full text is in the docstring of [scripts/mock_resolver.py](../scripts/mock_resolver.py)):

1. The scenario is the `X-Scenario` header, else `default`.
2. Candidates are the examples with the same operationId and scenario. The `x-vin` value is absent or equal to the VIN in the path. Each `x-match` regex fully matches its request header.
3. The candidate with the highest specificity wins. Specificity is 1 for `x-match` plus 1 for `x-vin`.
4. If no candidate exists, the mock repeats steps 2–3 with the `default` scenario.
5. Examples without `x-scenario` are documentation only. They are never candidates.

`scripts/lint-api.sh` runs `scripts/check-resolution.py`. The check sends every operation × scenario × VIN (EV, gas car, unknown) × poll attempt through the rules. It fails on a request with no example, on a tie, on an example that no request reaches, and on a request where the SV priority order selects a different example.
