# DriveLink virtual service (BlazeMeter SV)

`scripts/build-mock.py` builds the DriveLink API for all 14 scenarios into a BlazeMeter virtual service: 126 transactions from the API contract. It can rebuild a service at any time.

The app uses the stateful virtual service `drivelink-proto` (target `proto`, the default). It has the same 126 transactions plus 5 stateful ones. Its endpoint is `MOCK_BASE_URL`. See [mock/proto/README.md](../mock/proto/README.md).

The service `drivelink-mock` (target `legacy`) has no state. Its endpoint is `LEGACY_MOCK_BASE_URL`. The app does not use it by default. The rest of this page describes the contract transactions, which are the same in both services. The measurements were made on `drivelink-mock`. A live check of 1,092 requests gave the same status and body as the local reference mock for every request that completed.

## What exists

| Item | Value |
| --- | --- |
| Account / workspace | 291446 / 2194183 |
| Service | target `proto`: `drivelink-proto`, id 447563. Target `legacy`: `drivelink`, id 447558. |
| Virtual service | target `proto`: `drivelink-proto`, id 361664. Target `legacy`: `drivelink-mock`, id 361526. Type TRANSACTIONAL, no template. |
| Location | BlazeMeter cloud, US East (Virginia), 1 replica |
| Endpoint | HTTPS. `MOCK_BASE_URL` (proto) and `LEGACY_MOCK_BASE_URL` (legacy) in `secrets.properties` (gitignored). Paths start with `/v1/`. |
| No-match answer | 404 with the text body `No match Found` |
| Transactions | 126: 96 from `api/examples/index.json`, 30 think-time copies |
| Transaction names | `drivelink <operationId> <status> <example>`, copies end with ` @<scenario>` |

The names use spaces, not `<status>.<example>`. SV rejects a `.` and brackets in transaction names (HTTP 400, "Transaction name contains invalid characters").

## How to rebuild

The script needs Python 3 with PyYAML. `BLAZEMETER_API_KEY` must contain the path to a JSON file with the fields `id` and `secret`.

```sh
scripts/build-mock.py --plan                     # compute and check the transactions, write mock/transactions.json
scripts/build-mock.py --apply                    # --plan, then sync drivelink-proto, deploy, write MOCK_BASE_URL
scripts/build-mock.py --export                   # write mock/proto/drivelink-proto-service.json
scripts/build-mock.py --apply --target legacy    # the same for drivelink-mock; writes LEGACY_MOCK_BASE_URL
scripts/build-mock.py --export --target legacy   # write mock/drivelink-service.json
```

The target `proto` also assigns the 5 stateful transactions with priority 4. It does not create them (see [mock/proto/README.md](../mock/proto/README.md)).

`--apply` is idempotent. A second run with no contract change makes no change in BlazeMeter.

1. It finds or creates the service `drivelink`.
2. It compares each transaction by name. It creates new transactions (bulk, 25 for each call), updates changed ones, and deletes stale ones. It touches only names that start with `drivelink `.
3. It finds or creates the virtual service of the target and sets the transaction list with the priority of each transaction.
4. It deploys a stopped virtual service, or it configures a running one when something changed.
5. It waits for `RUNNING` and writes the endpoint to `secrets.properties` (`MOCK_BASE_URL` or `LEGACY_MOCK_BASE_URL`). Other lines in that file stay. It does not print the endpoint.

All steps use the REST API (`https://mock.blazemeter.com/api/v1/...`). No step uses the MCP. CI can run the same script.

| Step | REST call |
| --- | --- |
| Find or create the service | `GET/POST /workspaces/{ws}/services` |
| Create transactions | `POST /workspaces/{ws}/transactions?serviceId={sid}` with `{"transactions": [...]}` |
| Update or delete a transaction | `PUT/DELETE /workspaces/{ws}/transactions/{id}` |
| Create the virtual service | `POST /workspaces/{ws}/service-mocks` with `mockServiceTransactions: [{txnId, priority}]` and `httpRunnerEnabled: true` |
| Change assignments | `PATCH /workspaces/{ws}/service-mocks/{vsId}` with `mockServiceTransactions` |
| Fix one priority (fallback) | `PATCH /workspaces/{ws}/service-mocks/{vsId}/transactions/{txnId}` with `{"priority": N}` |
| Deploy / configure | `GET .../service-mocks/{vsId}/deploy` or `/configure`, then `GET /api/v1/trackings/{uuid}` until `FINISHED` |
| Export | `GET /workspaces/{ws}/services/{sid}/export` (a zip file with one JSON file) |

A virtual service without `httpRunnerEnabled: true` does not deploy. The tracking error is "Mock service must have at least one active runner."

## Files

| File | Content |
| --- | --- |
| `scripts/build-mock.py` | The build script. |
| `mock/transactions.json` | The planned transactions: name, priority, matchers, response status, headers, body file and delay. |
| `mock/proto/drivelink-proto-service.json` | The export of `drivelink-proto` (131 transactions). |
| `mock/drivelink-service.json` | The export of `drivelink-mock`. The script removes timestamps and user names and sorts the transactions by name. Neither export has keys or tokens. |

## Matchers and priority

Each transaction has these matchers:

- The method.
- The URL matcher `matches_url` with `urlRegex` from `index.json`, for example `^/v1/vehicles/[^/?]+/status(\?.*)?$`.
- `X-Scenario` `equals` `<scenario>`. Default transactions do not have this matcher.
- One `matches` header matcher for each `match` regex, for example `X-Poll-Attempt` `^[12]$`.

The lowest priority number wins. The script sets the same number on the virtual-service assignment and in the transaction DSL.

| Priority | Band | Transactions |
| --- | --- | --- |
| 2 | Scenario, specific (`vin` or `match`) | 17 |
| 3 | Scenario, general; and think-time copies of general default transactions | 81 |
| 5 | Default, specific | 10 |
| 6 | Default, general | 18 |

Priorities 1 and 4 are not used, because no entry has both `vin` and `match`. Default transactions have no `X-Scenario` matcher. Thus they also answer each scenario that has no transaction of its own for a request.

## Think-time copies

A default transaction keeps its own delay (300–800 ms) when it answers another scenario. The script adds a copy of a default transaction for each scenario S when both conditions are true:

1. The resolver (`scripts/mock_resolver.py`) answers a request with `X-Scenario: S` with that default entry.
2. The think time of S for that operation is not the default think time.

A copy has the response of the default entry, the matcher `X-Scenario` equals S, the delay of S, and the priority of the default entry minus 3.

| Scenario | Think time (ms) | Copies |
| --- | --- | --- |
| fast | 40–60 | 26 |
| auth-expired | 100–200 | 2 (`getHealth`, `createToken`) |
| rate-limited | 50–100 | 1 (`getHealth`) |
| server-error | 200 | 1 (`getHealth`) |
| slow-vehicle | 2000–3000 for polls | 0 |

`slow-vehicle` needs no copy. Its own `getCommand` transactions answer all poll attempts, so a default transaction never answers a `slow-vehicle` poll.

## Offline check

`--plan` emulates SV over the planned transactions for each request in the `scripts/check-resolution.py` grid (1,092 requests). The emulation uses a full regex match on the path, `X-Scenario` equals, full regex matches on headers, and the lowest priority. The selected response must be the resolver's example. The delay must be the think time of the requested scenario (`pollThinkTimeMs` for `getCommand`). A tie is an error. The script stops with exit code 1 when a request fails. The last run reported 0 problems.

When the copies are removed, the check reports 41 delay problems. Thus the check detects a missing copy.

## Verification (measured)

All values in this section are measured against the live endpoint.

| Request | Expected | Actual |
| --- | --- | --- |
| `GET /v1/health` | 200 | 200 |
| `GET /v1/vehicles/DLEV26AURA0000101/status` | 200, `200.default.json` | 200, body byte-identical |
| Same, `X-Scenario: low-battery` | 200, `200.low-battery.json` | 200, same JSON |
| `GET /v1/vehicles/DLGS25SLACE000202/status` (ICE) | 200, `200.default-ice.json` | 200, same JSON |
| `GET /v1/vehicles/ZZZZZZZZZZZZZZZZZ/status` | 404 | 404, `404.unknown-vin` problem body |
| `GET /v1/commands/cmd-1`, `X-Poll-Attempt` 1, 2, 3 | PENDING, PENDING, SUCCEEDED | PENDING, PENDING, SUCCEEDED |
| Same, `X-Scenario: vehicle-asleep`, attempts 1–5 | PENDING/WAKING ×2, PENDING ×2, SUCCEEDED | PENDING/WAKING, PENDING/WAKING, PENDING, PENDING, SUCCEEDED |
| `GET /v1/me`, `X-Scenario: rate-limited` | 429, `Retry-After: 30` | 429, `retry-after: 30`, `application/problem+json` |
| `POST /v1/alerts/alt-0001/read` | 204, no body | 204, 0 bytes |
| `GET /v1/me?foo=1` | 200 (the URL regex allows a query string) | 200 |
| `X-Scenario: nope` (not in the catalog) | default data | default status body |
| `x-scenario: low-battery` (lowercase header name) | low-battery data | low-battery body |
| `X-Scenario: Low-Battery` (other case in the value) | default data (`equals` is case sensitive) | default status body |
| Full grid: 1,092 requests, 8 in parallel | resolver status and body | 1,069 equal, 0 different, 23 transport errors (see below); the 23 passed on a second run |

Delay, five samples for each `/v1/me` row, three for the last row. "Server" is the time from request sent to first byte (`time_starttransfer` − `time_pretransfer`). "Total" includes a new TLS connection for each request.

| Request | Server (ms) | Total (s) |
| --- | --- | --- |
| No match (`/v1/nothing`, no delay) | 75–79 | 0.24–0.25 |
| `X-Scenario: fast` (40–60 ms) | 115–138 | 0.28–0.41 |
| default (300–800 ms) | 614–804 | 0.78–0.97 |
| `X-Scenario: server-error` (200 ms) | 276–349 (one sample: 75,089) | 0.44–0.52 |
| `X-Scenario: auth-expired` on `/v1/health` (copy, 100–200 ms) | 198–213 | — |

The server time is the configured delay plus approximately 75 ms of SV overhead. Thus SV applies the delay of the selected transaction, and the copies work.

## Differences from the local mock

| Topic | Local mock (`scripts/mock-server.py`) | BlazeMeter SV |
| --- | --- | --- |
| Unknown `X-Scenario` value | 400 `INVALID_REQUEST` | Default data |
| No route | 404 problem JSON, code `NOT_FOUND` | 404, text body `No match Found` |
| `X-Correlation-Id` response header | Echoes the request value, or a new UUID | Not sent. The bodies keep the fixed `correlationId` of the examples. |
| `X-Scenario` value case | Exact | Exact (`equals` is case sensitive) |

## Findings about SV

- SV behaves as the emulation in `--plan` expects. `matches_url` sees the path and the query string, not the host. Header names are not case sensitive. Header regexes and the lowest-priority rule give the same result as the resolver for all 1,069 completed grid requests.
- Transport stalls (measured): some requests wait 68–97 s. This occurred in each of four test sessions (more than 1,200 requests in total). In the parallel grid run, 23 requests in two groups failed after 68–97 s with a timeout or a connection reset. In two sequential runs, one request completed with the correct response after 68 s and one after 75 s. A first parallel run stopped when one request did not answer in 120 s. The cause is not known. Requests before and after each stall were normal. Inferred: the app and `scripts/smoke.sh` need a request timeout and one retry so that a stall does not fail a test run.
- The response delay type `uniform` accepts `lower` equal to `upper` (server-error, 200 ms).
- The virtual service setting `priorityMode` is `DEFAULT`. The other value is `UNIQUE_PRIORITY`. The build does not need it, because the plan has no ties.

## Cost

A running virtual service uses the SV allowance of the account while it runs. The API shows `metadata.limits.percentage: 0` for `drivelink-mock`; it does not show a price. To stop the service when nobody uses it:

```sh
# GET https://mock.blazemeter.com/api/v1/workspaces/2194183/service-mocks/<361664 or 361526>/stop (Basic auth)
```

`scripts/build-mock.py --apply` deploys it again and writes the endpoint to `secrets.properties`. Inferred, not measured: the endpoint host stays the same, because it contains the virtual-service id and the service id.
