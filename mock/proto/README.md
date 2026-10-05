# drivelink-proto: the stateful cloud virtual service

`drivelink-proto` is the BlazeMeter virtual service that the app uses by default (since Oct 4, 2026). It serves the full DriveLink API, as `drivelink-mock` did, and it keeps state per car:
- A LOCK or UNLOCK command changes the `locked` value of the next vehicle status.
- A charge-limit update changes the next charge settings.

`MOCK_BASE_URL` in `secrets.properties` (gitignored) is its endpoint. The debug build reads `MOCK_BASE_URL` for the built-in "BlazeMeter cloud" profile.

The stateless service `drivelink-mock` (id 361526) still exists as the target `legacy` of `scripts/build-mock.py`. Its URL is `LEGACY_MOCK_BASE_URL`. Nothing in the repo uses it.

## Resources (workspace 2194183)

| Resource | Id |
| --- | --- |
| Service `drivelink-proto` | 447563 |
| Virtual service `drivelink-proto` | 361664, BlazeMeter cloud, US East, HTTPS, no match → 404 |
| Dataset entity `vehicles_csv` (from [vehicles.csv](vehicles.csv), 501 rows) | data model asset `fd271ff2-…` |
| Service export | [drivelink-proto-service.json](drivelink-proto-service.json) (131 transactions) |

## Transactions

The virtual service has 131 transactions:
- **126 contract transactions.** `scripts/build-mock.py` builds them from the API contract, the same as for `drivelink-mock`. Their names start with `drivelink `. See [docs/mock-service.md](../../docs/mock-service.md).
- **5 stateful transactions.** They were made with the BlazeMeter SV MCP fork (<https://github.com/benjithompson/sv-mcp>) on Oct 3, 2026. Their names start with `drivelink-proto `. The build script does not create or delete them. It sets their priority to 4 and their delay to 300–800 ms. It also makes the command bodies equal to `sendCommand/202.default.json`, with their own `type`.

| Stateful transaction | Id | Request | State change (action id) |
| --- | --- | --- | --- |
| getVehicleStatus | 7757592 | `GET /v1/vehicles/${vin}/status` | none; returns `"locked": ${locked}` |
| getChargeSettings | 7757593 | `GET /v1/vehicles/${vin}/charge-settings` | none; returns `${acTargetPct}`, `${dcTargetPct}` |
| updateChargeSettings | 7757594 | `PUT /v1/vehicles/${vin}/charge-settings` | `UPDATE_OBJECT` sets both limits from the body (40832); echoes the body |
| sendCommand LOCK | 7757595 | `POST /v1/vehicles/${vin}/commands`, body `$.type` = `LOCK` | `UPDATE_OBJECT` sets `locked=true` (40830) |
| sendCommand UNLOCK | 7757596 | same, `$.type` = `UNLOCK` | `UPDATE_OBJECT` sets `locked=false` (40831) |

## Priority: which answer wins

The lowest number wins.

| Priority | Transactions | Effect |
| --- | --- | --- |
| 2–3 | Scenario transactions (`X-Scenario`) and think-time copies | A scenario answer still wins. Example: `low-battery` shows 8 %, and `vehicle-offline` returns 409 for a command. |
| 4 | The 5 stateful transactions | Under `default`, the demo car and the load-test cars get the stateful answer. |
| 5–6 | Fixed default transactions | A VIN that is not in the dataset falls through to these. Measured: the ICE car `DLGS25SLACE000202` gets its fixed status (200, fuel 62 %), and an unknown VIN gets the 404 problem body. |

Results of this rule:
- A scenario that has its own transaction for an operation does not read or change the state. Examples: the status in `low-battery`, `door-ajar`, `tire-low` and `vehicle-offline`; all calls in `fast`.
- A command in a scenario without its own `sendCommand` transaction does change the state. Example: an UNLOCK in `slow-vehicle`.

## How the state is keyed

The URL matcher `/v1/vehicles/${vin}/...` (`equals_url`) selects the dataset row whose `vin` is in the request path. The response reads that row. Each state action updates only the rows where `vin` equals `${vin}`. So the state is per car.

The dataset has the demo car `DLEV26AURA0000101` and 500 load-test cars (`DLEV26PERF0000001`–`0000500`, users `perf001`–`perf500@drivelink.test`). The seed values are `locked=true`, AC 80 %, DC 90 %.
- A load test that gives each virtual user its own VIN does not share state between users.
- All devices and tests that use the demo car share one car.

## Commands

    BLAZEMETER_API_KEY="$PWD/api-key.json" scripts/build-mock.py --apply     # sync and deploy (target proto)
    BLAZEMETER_API_KEY="$PWD/api-key.json" scripts/build-mock.py --export    # write drivelink-proto-service.json
    python3 scripts/build-proto-data.py          # write vehicles.csv (default 500 users)
    scripts/proto-check.sh [VIN] [OTHER_VIN]     # state check against MOCK_BASE_URL
    scripts/smoke.sh -q                          # contract check against MOCK_BASE_URL

## Reset to the seed data

The reset is outside the app. Two kinds exist:

| Reset | Scope | How | Time (measured Oct 4, 2026) |
| --- | --- | --- | --- |
| Full | All 501 cars, all fields | `scripts/build-mock.py --reset`, `./gradlew resetCloudState`, `./gradlew :app:connectedDebugAndroidTest -PresetCloudState`, `scripts/perfecto-espresso.sh -r`, or the MCP tool `virtual_services_state reset` (id 361664) | 20–27 s. `/v1/health` answered 200 for the whole reset (8 of 8 checks). |
| One car | `locked` and the charge limits of one VIN | Normal API calls: LOCK, then PUT the limits. The core suite does this before each test. No key needed. | Under 1 s per call |

- The full reset is the REST call `GET /workspaces/2194183/service-mocks/361664/configure?keepBlazeData=false`, then a wait for `RUNNING`. It needs the BlazeMeter key (`BLAZEMETER_API_KEY`, default `api-key.json`). Do not put the key on a test device.
- A plain configure, without `keepBlazeData`, also resets the state (measured). So an `--apply` that changes something resets the data too.
- A PAT or Scriptless test cannot reset the state. Run a full reset before you start a PAT suite.

To inspect the state, use the MCP tool `virtual_services_state export_data`, then `read_data`.

## Tests and shared state

- The Espresso core suite puts the demo car back to the seed state before each test: it sends LOCK and sets the limits to 80 / 90 (`CoreSuiteBase.resetVehicleState`). The local mock accepts the same calls.
- A PAT or Perfecto run can leave the demo car unlocked or with other limits. The PAT scenarios start with an unlock, so an unlocked car can change what the AI sees. Run a full reset before a run.

## Verification (measured on Oct 4, 2026)

| Check | Result |
| --- | --- |
| `scripts/smoke.sh -q` (299 cases, every operation and scenario) | 299/299 pass |
| `scripts/proto-check.sh` (2 VIN pairs) | 6/6 pass each |
| Espresso core suite, emulator API 37 | 10/10 pass, 170 s wall |
| Second `--apply` | no change |

## Limits

- Command bodies other than `LOCK` and `UNLOCK` (for example `REMOTE_START`) get the fixed default answer and change no state.
- The virtual service uses the SV allowance while it runs. Stop it when it is not in use.
