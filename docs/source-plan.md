# Connected-Car Demo App — Implementation Plan

Oct 2, 2026 · @Ben Thompson

## Overview and goals

Build a native Android connected-car companion app modeled on the feature set of a modern OEM car app. Every backend call goes over real HTTP to BlazeMeter virtual services, so response content, latency and failures are fully controllable. The app is a demo and test target for service virtualization, mobile testing on Perfecto, and API load testing on BlazeMeter.

**Goals**

- A believable, polished app: remote commands, vehicle status, EV charging, car finder, trips, maintenance, alerts.
- Real network traffic only. No in-app fakes in the shipping build; the backend is always a virtual service.
- Switchable behavior at runtime through a scenario header, so a demo can move from happy path to failure in seconds.
- Visible traffic: an in-app network inspector that shows each request, response, status and latency.
- Repeatable tests: unit tests, UI tests on real Perfecto devices, and a BlazeMeter load test that simulates fleet traffic against the same API.

**Non-goals**

- Talking to any real vehicle or any real OEM backend.
- Reverse-engineering or mirroring any OEM's actual API. The API in this plan is original.
- Publishing to the Play Store.

**Branding guardrails (hard rules for the agent)**

- Original app name, icon, color palette and copy. Working name: **DriveLink Demo** (placeholder, see Open decisions).
- No OEM logos, wordmarks, model names, fonts, screenshots or UI assets. Vehicles use invented model names (for example "Aurora EV").
- Layout and flows can follow common connected-car patterns; visual design must be our own.

## Tooling and environment

The implementing session has Android Studio plus three MCP servers; each has one clear job. MCP tool names vary by version, so the agent discovers the actual tools and confirms capabilities before relying on them.

| Tool | Job in this project |
| --- | --- |
| Android Studio + Gradle | Build, run and debug the app; emulator for local runs; produce debug and release APKs |
| Service Virtualization MCP | Create the virtual service from the OpenAPI spec, add scenario transactions, set request matchers, think time and response bodies, start the service and return its endpoint URL |
| BlazeMeter MCP | Workspace and project setup, upload and run the API load test, pull test results |
| Perfecto MCP | Upload the APK, select real devices, run the UI test suite, collect reports, video and device logs |

**App stack**

- Kotlin, Jetpack Compose with Material 3, single-activity with Navigation Compose.
- Coroutines and Flow; MVVM with unidirectional state (`UiState` per screen).
- Hilt for DI.
- Retrofit + OkHttp + kotlinx.serialization.
- DataStore for settings (endpoint, scenario, vehicle ID).
- minSdk 26; compileSdk and targetSdk = latest stable available in the installed SDK.
- Gradle version catalog (`libs.versions.toml`); Kotlin DSL build files.
- Testing: JUnit 5 or JUnit 4, Turbine for Flow, OkHttp MockWebServer, Compose UI test / Espresso for instrumentation.
- Map: osmdroid (no API key) by default; Maps Compose only if a Google Maps key is provided.

**Secrets**

- BlazeMeter and Perfecto credentials stay in the MCP config or environment, never in the repo.
- The mock service URL and any API key go in `local.properties` or a gitignored `secrets.properties`, injected through `BuildConfig`, and are editable at runtime in Settings.

## App architecture

The app is a thin, well-layered client: Compose screens talk to ViewModels, ViewModels talk to repositories, and repositories make real calls to whatever endpoint is configured. Nothing above the network layer knows it is talking to a virtual service.

&#91;embedded content: system architecture · app, virtual service, Perfecto, load test\]

The app, the Perfecto device runs and the load test all hit the same virtual service; only the X-Scenario header changes what comes back.

**Layers**

1. **UI** (`:app` + `:feature:*`): Compose screens, ViewModels, navigation.
2. **Domain** (`:core:domain`): models (`Vehicle`, `VehicleStatus`, `Command`, `ChargeSchedule`, `Trip`, `ServiceItem`, `Alert`) and use cases (`SendCommandAndAwait`, `RefreshStatus`).
3. **Data** (`:core:data`): repositories, DTO-to-domain mappers, caching of the last known status.
4. **Network** (`:core:network`): Retrofit service, OkHttp client, interceptors, error mapping.
5. **Settings** (`:core:settings`): DataStore for base URL, scenario, vehicle ID, auth token.

**OkHttp interceptor chain (in order)**

1. `BaseUrlInterceptor`: rewrites scheme, host, port and path prefix to the active endpoint profile on every request, so the target changes without a rebuild or restart (see Configurable endpoints).
2. `AuthInterceptor`: adds `Authorization: Bearer <token>`; on 401 triggers a single re-login, then fails cleanly.
3. `ScenarioInterceptor`: adds `X-Scenario: <name>` from Settings (omitted when set to `default`).
4. `CorrelationInterceptor`: adds `X-Correlation-Id` (UUID per request) and `X-Client-Version`.
5. `InspectorInterceptor`: records method, URL, headers, body (truncated to 64 KB), status, duration into an in-memory ring buffer of the last 200 calls for the Network Inspector screen.
6. `HttpLoggingInterceptor`: debug builds only.

**Timeouts**: connect 10 s, read 30 s (long enough to demo slow-vehicle scenarios), overridable in Settings.

**Configurable endpoints**

No host is hard-coded anywhere in the app; every call resolves its host from the active endpoint profile at request time.

- **Profile model**: `name`, `baseUrl` (scheme + host + optional port + optional path prefix, for example `https://mock.example.com:8443/drivelink`), optional `apiKey` header name and value, optional `trustUserCerts` flag.
- **Per-API-group overrides** (optional): `auth`, `vehicle`, `alerts` can each point at a different host. Empty override = use the profile's `baseUrl`. This supports splitting the API across several virtual services.
- **Built-in profiles**: "BlazeMeter cloud" (default, from `secrets.properties`), "Private location" (empty until set), "Local" (`http://10.0.2.2:8080` for an emulator talking to the host machine). Users can add, edit, duplicate and delete profiles.
- **Switching**: pick the active profile in the Demo console; it applies to the next request with no restart. The active host shows in the scenario chip so the audience sees where traffic is going.
- **Validation and check**: reject malformed URLs on save; a "Test connection" button calls `GET /v1/health` (add to the spec) and shows status and latency.
- **External setup without the UI**: accept `baseUrl` and `scenario` as instrumentation arguments for Perfecto and CI, and through a debug-only intent, for example `adb shell am start -n <package>/.DemoConfigActivity --es baseUrl https://… --es scenario slow-vehicle`. Optionally, scan a QR code containing a profile as JSON.
- **Network security**: release builds allow HTTPS only. Debug builds allow cleartext HTTP and user-installed CAs through `network_security_config.xml`, so private locations or local mocks with self-signed certificates work.
- **Persistence**: profiles in DataStore; API key values encrypted with Android Keystore; export and import profiles as JSON from the Demo console.

**Remote command pattern (core behavior)**

1. `POST /v1/vehicles/{vin}/commands` with `{"type":"LOCK", …}` → `202 Accepted` with `{commandId, status:"QUEUED"}`.
2. Poll `GET /v1/commands/{commandId}`, sending `X-Poll-Attempt: n` (1, 2, 3 …).
3. Poll interval 2 s, backoff to 4 s after attempt 5, hard stop after 60 s total → `TIMED_OUT` in the UI.
4. Terminal states: `SUCCEEDED`, `FAILED` (with `reason`), `TIMED_OUT`.
5. On `SUCCEEDED`, refresh vehicle status.
6. UI shows a per-command progress state: Sending → Waiting for vehicle → Done or Failed, with a retry action.

The `X-Poll-Attempt` header lets virtual-service matchers return `PENDING` for early attempts and a terminal state later, without depending on stateful mock features.

**Error mapping**: every non-2xx response maps to a typed `AppError` (`Unauthorized`, `VehicleOffline`, `RateLimited(retryAfter)`, `Conflict`, `Server`, `Network`, `Parse`). Each screen has an explicit error state with a human message and a retry. Malformed JSON never crashes the app.

## Features and screens

The app has 12 screens; bottom navigation holds Home, Remote, Map and More. Every screen has loading, content, empty and error states.

| # | Screen | What it does | API calls |
| --- | --- | --- | --- |
| 1 | Splash / Login | Mock sign-in with email + PIN; stores token | `POST /v1/auth/token` |
| 2 | Vehicle picker | Shown when the account has more than one vehicle | `GET /v1/vehicles` |
| 3 | Home dashboard | Hero card: lock state, range, fuel or battery %, climate on/off, last updated; quick actions (lock, unlock, start, stop); alert banner | `GET /v1/vehicles/{vin}/status` |
| 4 | Remote controls | Lock, unlock, remote start with climate presets (temp, defrost, heated seats, duration), stop, horn and lights; per-command progress | `POST …/commands`, `GET /v1/commands/{id}` |
| 5 | Vehicle status | Doors, trunk, hood, windows, tire pressures (4), oil life, 12 V battery, odometer | `GET …/status` |
| 6 | EV charging | Plug state, charge %, rate kW, time to target, charge limit slider, start/stop charging, schedules (departure time, off-peak window) | `GET/PUT …/charge-settings`, charge commands |
| 7 | Car finder | Map pin of the vehicle, distance from phone, "refresh location" | `GET …/location` |
| 8 | Trips | List of recent trips with distance, duration, efficiency; detail view | `GET …/trips` |
| 9 | Maintenance | Upcoming and overdue service items, recall notices, schedule-service request | `GET …/maintenance`, `POST …/service-requests` |
| 10 | Alerts | Notification inbox: door left unlocked, low tire, charge complete, theft alarm | `GET /v1/alerts`, `POST /v1/alerts/{id}/read` |
| 11 | Settings | Account, units (mi/km, °F/°C), PIN, about | local + `GET /v1/me` |
| 12 | Demo console | Base URL, vehicle ID, scenario picker, timeouts, Network Inspector, "reset session" | none (local) |

**Demo console details**

- Hidden behind 5 taps on the app version in Settings, plus a debug-build-only shortcut.
- Scenario picker lists the scenario catalog below; change applies to the next request.
- Network Inspector: list of calls (method, path, status, ms, color by status class); detail view with request and response headers and pretty-printed body; copy-as-cURL; clear.
- Optional floating chip showing the active scenario, so the audience always knows what the backend is simulating.

**Vehicle types**: the status payload carries `powertrain: EV | ICE | HYBRID`. Charging appears only for EV and HYBRID; fuel gauge only for ICE and HYBRID. The default demo account has one EV and one ICE vehicle.

**Polish items**: haptics on command success, pull-to-refresh, dark theme, relative timestamps ("updated 2 min ago"), accessibility labels on every control, and a stale-data banner when the last status is older than 15 minutes.

## API contract

The agent writes `api/openapi.yaml` (OpenAPI 3.0.3, since 3.0 imports most reliably into mock tooling) before any app code. It is the single source of truth: the virtual service is generated from it, and the app's DTOs match it.

**Common headers**

| Header | Direction | Purpose |
| --- | --- | --- |
| `Authorization: Bearer <token>` | request | Mock auth; any non-empty token is accepted except in the `auth-expired` scenario |
| `X-Scenario` | request | Selects the virtual-service behavior; absent = default happy path |
| `X-Poll-Attempt` | request | Poll count on command status calls |
| `X-Correlation-Id` | request + response | Trace a call across app logs, the inspector and BlazeMeter |
| `X-Client-Version` | request | App version, for filtering traffic |
| `Retry-After` | response | Seconds, on 429 and 503 |

**Endpoints** (base path `/v1`; `{vin}` is a 17-character fake VIN)

| Method | Path | Success | Notes |
| --- | --- | --- | --- |
| POST | `/auth/token` | 200 `{accessToken, expiresIn, refreshToken}` | Body `{email, pin}` |
| POST | `/auth/refresh` | 200 same as above |  |
| GET | `/me` | 200 `{userId, name, email, units}` |  |
| GET | `/vehicles` | 200 `[Vehicle]` |  |
| GET | `/vehicles/{vin}/status` | 200 `VehicleStatus` |  |
| POST | `/vehicles/{vin}/commands` | 202 `{commandId, type, status:"QUEUED", createdAt}` | Body `{type, params?}`; types: `LOCK`, `UNLOCK`, `START`, `STOP`, `HORN_LIGHTS`, `CHARGE_START`, `CHARGE_STOP` |
| GET | `/commands/{commandId}` | 200 `{commandId, type, status, reason?, updatedAt}` | `status`: `QUEUED`, `PENDING`, `SUCCEEDED`, `FAILED` |
| GET | `/vehicles/{vin}/location` | 200 `{lat, lon, accuracyM, updatedAt}` |  |
| GET | `/vehicles/{vin}/charge-settings` | 200 `ChargeSettings` | EV/HYBRID only; 404 for ICE |
| PUT | `/vehicles/{vin}/charge-settings` | 200 `ChargeSettings` | Limit %, schedules |
| GET | `/vehicles/{vin}/trips?limit=20` | 200 `[Trip]` |  |
| GET | `/vehicles/{vin}/maintenance` | 200 `{items:[ServiceItem], recalls:[Recall]}` |  |
| POST | `/vehicles/{vin}/service-requests` | 201 `{requestId, status}` |  |
| GET | `/alerts` | 200 `[Alert]` |  |
| POST | `/alerts/{id}/read` | 204 |  |

**Key schemas** (all timestamps ISO 8601 UTC)

- `Vehicle`: `vin, nickname, model, year, color, powertrain, imageKey`
- `VehicleStatus`: `vin, updatedAt, locked, engineOn, climate{on, setTempF, defrost, heatedSeats}, doors{fl, fr, rl, rr, trunk, hood}` (open/closed), `windows{…}`, `fuelPct?`, `batteryPct?`, `rangeMi`, `charging{pluggedIn, active, rateKw, minutesToTarget}?`, `tires{flPsi, frPsi, rlPsi, rrPsi, lowWarning}`, `odometerMi`, `oilLifePct?`, `aux12vOk`
- `ChargeSettings`: `targetPct, schedules[{id, days[], startTime, endTime, enabled}], departureTime?`
- `Trip`: `id, startedAt, endedAt, distanceMi, durationMin, avgMph, efficiency (mi/kWh or mpg)`
- `ServiceItem`: `id, name, dueMi?, dueDate?, status (UPCOMING | DUE | OVERDUE)`
- `Alert`: `id, type, severity (INFO | WARN | CRITICAL), title, body, createdAt, read`

**Error model**: `application/problem+json` with `{type, title, status, detail, code, correlationId}`. App-relevant codes: `VEHICLE_OFFLINE`, `VEHICLE_ASLEEP`, `COMMAND_CONFLICT`, `TOKEN_EXPIRED`, `RATE_LIMITED`, `INTERNAL`.

**Examples**: each operation carries named examples (`default` plus one per relevant scenario) in the spec. The virtual-service transactions are built from these examples, so the spec, the mock and the app tests stay in sync.

## Scenario catalog

Fourteen scenarios cover the demo story; each is selected by the `X-Scenario` value and is implemented as virtual-service transactions with header matchers. Think times are starting points to tune live.

| `X-Scenario` | Affects | Virtual-service behavior | What the app should show |
| --- | --- | --- | --- |
| (absent) / `default` | All | 200/202, realistic data, think time 300–800 ms; command polls return `PENDING` on attempts 1–2, `SUCCEEDED` from attempt 3 | Normal happy path |
| `fast` | All | Same as default, think time \~50 ms; commands succeed on attempt 1 | Snappy flow for quick demos |
| `slow-vehicle` | Commands | Polls `PENDING` through attempt 8, then `SUCCEEDED`; think time 2–3 s per poll | Long "Waiting for vehicle" state, still succeeds |
| `vehicle-asleep` | Commands | First poll `PENDING` with `reason: WAKING`; success on attempt 5 | "Waking vehicle…" sub-state |
| `vehicle-offline` | Commands, status | `POST …/commands` → 409 problem `VEHICLE_OFFLINE`; status returns stale `updatedAt` (3 days old) | Offline message, stale-data banner, no retry spam |
| `command-fails` | Commands | Polls `PENDING` then `FAILED` with `reason: DOOR_OPEN` | Specific failure message and suggested fix |
| `command-timeout` | Commands | Polls always `PENDING` | App stops at 60 s and shows `TIMED_OUT` |
| `low-battery` | Status, charging | `batteryPct: 8`, `rangeMi: 19`, low-range alert in `/alerts` | Warning styling, alert banner |
| `door-ajar` | Status, alerts | Rear-left door open, `locked: false`, door alert | Status highlights door; lock action prompts |
| `tire-low` | Status | One tire at 24 psi, `lowWarning: true` | Tire diagram highlight |
| `auth-expired` | All except auth | 401 `TOKEN_EXPIRED`; `/auth/refresh` also 401 | One refresh attempt, then back to login |
| `rate-limited` | All | 429 `RATE_LIMITED` with `Retry-After: 30` | Countdown before retry; no hammering |
| `server-error` | All | 500 problem `INTERNAL`, think time 200 ms | Generic error with correlation ID shown |
| `bad-payload` | Status | 200 with a missing required field and a wrong type | Parse error state; app does not crash |

**Matcher rules**

- Specific scenario transactions match on path + method + `X-Scenario` exact value; they must take priority over the default transaction for the same path.
- Default transactions match on path + method only and act as the catch-all.
- Command poll transactions additionally match `X-Poll-Attempt` (exact value per attempt, or a regex such as `^[12]$` for the pending range, if the matcher supports regex).
- Where the mock supports dynamic response templating, echo `commandId`, `vin` and `X-Correlation-Id` from the request and stamp `updatedAt` with the current time. Otherwise use fixed values that still look plausible.

**Demo script (suggested order)**: `default` → `slow-vehicle` → `command-fails` → `vehicle-offline` → `rate-limited` → back to `default`. Each switch happens live in the Demo console while the Network Inspector is visible.

## Virtual service build

The agent builds the virtual service through the Service Virtualization MCP and checks every step with real HTTP calls before the app depends on it.

1. Confirm the target BlazeMeter account, workspace and project with Ben; name everything with the `drivelink-` prefix.
2. Validate `api/openapi.yaml` locally (for example with a Spectral or openapi-cli lint) and fix errors before import.
3. Import the spec to create the service and its default transactions from the `default` examples.
4. Add one transaction per scenario-row in the catalog, built from the matching named example, with the matcher rules above and the listed think time.
5. Check transaction priority so scenario matchers win over defaults.
6. Choose where the mock runs: BlazeMeter cloud by default, because Perfecto cloud devices and the emulator must both reach it over the public internet. A private location is an option only if the devices have a network path to it.
7. Start the mock service and record its HTTPS endpoint URL in `secrets.properties` (gitignored) and in `docs/mock-service.md`.
8. Write `scripts/smoke.sh`: a cURL suite that hits every endpoint once per scenario and asserts status codes. Run it; all checks must pass.
9. Export the configured service (or keep the setup script) in `mock/` so it can be recreated from scratch in another workspace.

**Done when**: the smoke script passes for all 14 scenarios, and a poll sequence with `X-Poll-Attempt` 1→3 returns `PENDING`, `PENDING`, `SUCCEEDED` under `default`.

## Testing strategy

Four layers of tests run against progressively more real infrastructure: local mocks, the virtual service, real devices, then load.

**1. Unit tests (local, every commit)**

- ViewModels and use cases with fake repositories; Turbine for Flow assertions.
- Network layer against OkHttp MockWebServer, fed with the same example JSON files as the spec (`api/examples/*.json`).
- Must cover: polling state machine (success, fail, timeout, backoff), error mapping for every problem code, interceptor headers, DTO parsing including `bad-payload`.
- Target: 80% line coverage on `:core:*` modules.

**2. Contract check**

- A test that loads `openapi.yaml` and verifies each DTO can parse each named example. A spec change that breaks the app fails the build.

**3. UI tests on Perfecto (real devices)**

- Compose UI tests / Espresso in `androidTest`, running against the live virtual service. Each test sets its scenario through a test hook (instrumentation argument `scenario=…`) rather than tapping through the Demo console.
- Core suite (about 10 tests): login; dashboard loads; lock succeeds; remote start with climate succeeds; `slow-vehicle` shows waiting then success; `command-fails` shows reason; `vehicle-offline` shows banner; `auth-expired` returns to login; EV charge limit update; Network Inspector lists calls.
- Device matrix to start: 4 devices, covering Android 10 through the latest, one small and one large screen, one Samsung and one Pixel. Agent confirms available devices through the Perfecto MCP.
- Collect per-run report, video and device logs; attach the correlation IDs from failures to the report.

**4. Load test on BlazeMeter (fleet simulation)**

- A JMeter or Taurus script in `load/` that models app traffic: login, status refresh every 30 s, a command with polling every few minutes.
- Profile to start: ramp to 500 virtual users over 5 minutes, hold 15 minutes, against the virtual service with `default` and a 5% mix of `command-fails`.
- Watch response times, error rates and the mock's think-time fidelity under load.
- Combined demo: run the Perfecto UI suite while the load test runs, to show real-device behavior with the backend under pressure.

## CI pipeline

Every pull request builds the app, checks the API against the virtual service, runs UI tests on Perfecto devices and a short BlazeMeter load test, and fails the build if any threshold breaks. Nightly runs scale the same pipeline up.

&#91;embedded content: CI pipeline · 6 stages, gate at the end\]

Stages run in order on pull requests; on the nightly run, Perfecto and BlazeMeter run at the same time to show real devices under load.

**Platform**: GitHub Actions (`.github/workflows/ci.yml` and `nightly.yml`).

**How CI talks to the tools**: the MCP servers are for the interactive agent session only. CI uses each product's own non-interactive integration, which the agent confirms against current docs:

- BlazeMeter: Taurus (`bzt`) with cloud execution, or the BlazeMeter REST API, to run the test and read its pass/fail status.
- Service virtualization: the BlazeMeter API to update transactions and start the mock service when the spec changes.
- Perfecto: the Perfecto Gradle integration or REST API to upload the APK and run the instrumentation suite, with results in Perfecto reporting.

**Triggers**

| Trigger | Perfecto | BlazeMeter | Gate |
| --- | --- | --- | --- |
| Pull request | Core suite on 2 devices | 50 users, 5 min, `default` scenario | Blocks merge |
| Merge to main | Core suite on 2 devices | 50 users, 5 min | Alerts on failure |
| Nightly | Full matrix, in parallel with load | 500 users, 15 min, 5% `command-fails` mix | Report + trend vs baseline |
| Manual | Inputs: devices, scenario | Inputs: users, duration, endpoint profile | Optional |

**Pass/fail thresholds (starting points)**

- API: p95 response time under 1,500 ms for status calls; error rate under 1% excluding scenario-intended errors.
- App: end-to-end command time (tap to Done) under 10 s on `default`; cold start under 2 s on the reference device.
- Any Perfecto test failure fails the build.
- Thresholds live in the repo (`load/thresholds.yml`, test config), so changes are reviewed like code.

**Results and artifacts**

- A PR comment with links to the Perfecto report and the BlazeMeter test report, plus a one-line pass/fail summary for each.
- Build artifacts: debug APK, test APK, JUnit XML, exported HAR files from UI runs.
- Correlation IDs from failed UI tests are listed in the summary, so a device failure can be matched to the same request in BlazeMeter.

**Secrets** (CI secret store only): BlazeMeter API key ID and secret, Perfecto security token and cloud name, mock service URL.

**Optional showcase: environment per PR**. The pipeline creates a mock service from the PR's `openapi.yaml`, points the app and load test at it through the endpoint profile, and deletes it when the PR closes. This shows API changes tested before any backend exists.

## Phased milestones

Eight phases, each ending in a check the agent runs before moving on. The contract and the mock come first so the app is never built against guesses.

1. **Phase 0: Scaffold**
   - Multi-module Gradle project, version catalog, Hilt, Compose theme with original palette and icon, CI-style `./gradlew check` task.
   - Done when: the app launches to a placeholder Home on the emulator and `./gradlew check` passes.
2. **Phase 1: API contract**
   - `openapi.yaml`, named examples for every scenario, lint clean.
   - Done when: lint passes and Ben has reviewed the endpoint list.
3. **Phase 2: Virtual service**
   - Build per the Virtual service section.
   - Done when: `scripts/smoke.sh` passes for all 14 scenarios.
4. **Phase 3: Network core**
   - Retrofit service, interceptors, error mapping, polling use case, Settings in DataStore, Network Inspector buffer.
   - Done when: unit tests for polling and error mapping pass, and a debug call from the emulator to the mock shows up in the inspector, and switching endpoint profiles redirects the next call to the new host without a restart.
5. **Phase 4: Screens**
   - Build in this order: Login → Home → Remote → Status → Demo console + Inspector → EV charging → Car finder → Trips → Maintenance → Alerts → Settings.
   - Done when: every screen works against `default` and shows correct states for each scenario that affects it.
6. **Phase 5: Device testing**
   - UI suite, scenario test hook, Perfecto runs on the device matrix.
   - Done when: the core suite is green on all matrix devices, with reports linked in `docs/test-runs.md`.
7. **Phase 6: Load and demo polish**
   - Load script and first BlazeMeter run; polish items; `docs/demo-script.md` with the demo order and talking points.
   - Done when: a 500-user run completes with results recorded, and the full demo script runs end to end without a code change.
8. **Phase 7: CI pipeline**
   - PR and nightly workflows per the CI pipeline section; thresholds in the repo; PR summary comment. Adds app timers for cold start and tap-to-Done command time, logged so UI tests can assert on them.
   - Done when: a PR that breaks a UI test or a load threshold is blocked, a clean PR passes with both report links posted, and one nightly run completes with Perfecto and BlazeMeter in parallel.

**Checkpoints with Ben**: after Phase 1 (API shape), after Phase 4's Home and Remote screens (look and feel), and before the first load test (account and limits).

## Repo structure and agent conventions

One repo holds the app, the contract, the mock setup, and the tests, so a single session can rebuild everything.

```
drivelink-demo/
  app/                    # Activity, navigation, DI entry
  feature/
    home/ remote/ status/ charging/ finder/
    trips/ maintenance/ alerts/ settings/ democonsole/
  core/
    domain/ data/ network/ settings/ designsystem/ testing/
  api/
    openapi.yaml
    examples/             # one JSON per operation x scenario
  mock/                   # exported service or setup notes
  load/                   # JMeter / Taurus scripts, thresholds.yml
  .github/workflows/      # ci.yml, nightly.yml
  scripts/
    smoke.sh
  docs/
    PLAN.md               # this document
    mock-service.md  test-runs.md  demo-script.md  DECISIONS.md
  gradle/libs.versions.toml
```

**Conventions for the AI agent**

- Work phase by phase; do not start a phase until the previous one's check passes.
- Spec first: change `openapi.yaml` and its examples before changing DTOs or mock transactions, and keep all three in sync in the same change.
- Discover MCP tools before using them; if a needed capability is missing (for example regex matchers or dynamic templating), use the fallback in this plan and record it in `docs/DECISIONS.md`.
- Never commit secrets, endpoint keys or device-cloud tokens.
- Never add OEM names, logos or assets, even as placeholders.
- Small commits with clear messages; update `docs/` when behavior changes.
- Ask Ben before anything that costs money or quota at scale: large load tests, long Perfecto sessions, or creating resources outside the agreed workspace.
- When something fails, report the correlation ID, scenario, endpoint and status, not just "it failed".

## Open decisions for Ben

The defaults below let the agent start immediately; change any of them before Phase 1.

| Decision | Default in this plan | Alternatives |
| --- | --- | --- |
| App name and palette | DriveLink Demo, deep teal + warm gray | Any original name |
| Mock service location | BlazeMeter cloud | Private location (devices need a network path) |
| Map provider | osmdroid, no key | Google Maps Compose with a key |
| Default vehicles | One EV, one ICE | EV only, or three vehicles incl. hybrid |
| Perfecto device matrix | 4 devices, Android 10 to latest | Larger matrix for a coverage story |
| Load profile | 500 users, 15-minute hold | Smaller first run if quota is tight |
| Units | Miles and °F | Kilometers and °C |

- [ ] Confirm BlazeMeter account, workspace and project for the mock and load test
- [ ] Confirm Perfecto cloud and device availability
- [ ] Approve the API endpoint list after Phase 1
