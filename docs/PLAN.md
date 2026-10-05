# DriveLink Demo — Phased Implementation Plan

Oct 2, 2026 · Ben Thompson

## Summary

This plan builds an Android connected-car demo app that looks and works like a major OEM owner app (the reference app). The plan then adds four test layers around the app: Perfecto device tests (scripted and AI), BlazeMeter service virtualization, performance tests, and GitHub Actions CI.

The work has 9 phases. Each phase ends with a check. Do not start a phase until the previous check passes.

The detailed design (API contract, scenario catalog, interceptors, error model) is in [source-plan.md](source-plan.md). This plan does not repeat it. This plan adds the visual-fidelity target, the Perfecto AI track, the current tool status, and the phase order.

## Changes from the source plan

| Topic | Source plan | This plan |
| --- | --- | --- |
| Visual target | Original design, common patterns only | Layout, navigation, flows and color mood follow the reference app closely. Brand assets stay out (see Visual fidelity rules). |
| Perfecto tests | Espresso / Compose UI tests only | Three tracks: scripted Espresso, Perfecto AI Scriptless, and Perforce Autonomous Testing |
| Performance | API load test only | API load test, plus client-side timings on real devices, plus HAR capture from Perfecto into a BlazeMeter test |
| CI | Described | Same, plus Scriptless AI job trigger and the Perforce `bzm-*` skills for the gate |
| Phase order | 8 phases | 9 phases; a design-reference phase comes before the screens |

## Current environment (measured on Oct 2, 2026)

| Item | Status | Action |
| --- | --- | --- |
| GitHub CLI | Logged in as `benjithompson`; scopes include `repo` and `workflow` | None |
| Java | Temurin 21.0.3 | None |
| Android SDK, Android Studio, `adb` | Installed Oct 2: Android Studio (Homebrew), SDK in `~/Library/Android/sdk` (platform 37.0, build-tools 37.0.0), AVD `drivelink` (Pixel 8, API 37) | None |
| Taurus (`bzt`) | Installed (Homebrew) | None |
| Node | v26.10.0 | None (used for OpenAPI lint) |
| Perfecto MCP | Connected as the demo-tenant user; device list works | None |
| Perfecto AI Scriptless (through MCP) | `list_tests` and `execute_test` only. The MCP cannot create or edit tests. | Author tests in the Perfecto Scriptless UI; run them through the MCP and CI |
| BlazeMeter MCP (PAG `blazemeter`) | Running (checked Oct 2) | None |
| BlazeMeter SV MCP (PAG `blazemeter-sv`) | Running (checked Oct 2) | None |
| Perforce Autonomous Testing MCP (PAG `autonomous-testing`) | Running. Perfecto (`demo.app.perfectomobile.com`) and BlazeMeter credentials are linked. Active BlazeMeter context is account 514413 / workspace 2259111. | None. Keep separate from Ben T for now. |

## Confirmed decisions (Oct 2, 2026)

| Decision | Value |
| --- | --- |
| BlazeMeter account | BlazeMeter SE Demo (ID 291446) |
| BlazeMeter workspace | Ben T (ID 2194183) |
| BlazeMeter project | Auto (ID 2613758), created Oct 2, 2026 |
| Perfecto cloud | `demo` (`demo.app.perfectomobile.com`) |
| Reference app | A US OEM owner app (not named in this repo) |
| Autonomous Testing track | Included (Phase 6c). It keeps its own BlazeMeter workspace for now; do not switch its context to Ben T. |
| Repo | `benjithompson/drivelink-demo` (private) |
| Brand use | No OEM marks, fonts or images |

**Inferred, not measured:** Some Perfecto devices carry POC labels (for example "<Customer> POC"). These devices are probably reserved. The matrix below uses devices with a "Free" or empty label.

## Visual fidelity rules

The goal is that a viewer recognizes the app pattern at a glance. The goal is not a counterfeit.

**Match closely**

- Information architecture: tab names, tab order, screen order, and the content of each screen.
- Layout: the hero vehicle card, the quick-action row, card grouping, list rows, bottom sheets.
- Flows: the PIN prompt before remote commands, the command progress feedback, the climate-option sheet, the charge-limit sliders.
- Color mood and density: dark or light background, accent color family, corner radii, icon weight.
- Copy tone and labels for generic functions (for example "Remote Start", "Lock", "Charge Limit").

**Do not copy** (unless the OEM or Perforce legal gives written approval)

- The OEM name, logo, app wordmark, and other trademarks.
- The OEM font family. Use a free font with a similar feel.
- Vehicle photos and renders from the OEM, screenshots, and app icons.
- Real model names. Use invented names, for example "Aurora EV" and "Solace".

The app name stays **DriveLink Demo** until you choose another name.

## Visual reference (US OEM owner app, 2026)

The model is the US Android app of the reference OEM (2026). Research on Oct 2, 2026 used the store screenshots, the OEM owner pages and a user manual.

Tags: **[S]** = stated by a source. **[M]** = measured from a store screenshot. **[I]** = inferred. An [I] value must be checked against a real screenshot in Phase 1.

### Navigation

| Element | Reference app | DriveLink Demo |
| --- | --- | --- |
| Top bar | Navy. Brand mark on the left; bell with a badge and a chat bubble on the right [M] | Navy. DriveLink mark on the left; bell (Alerts) and chat bubble (Demo console shortcut in debug builds) |
| Bottom tabs | Home, Car Care, Maps, Menu. White bar, navy active, gray inactive, labels under icons [M] | Same four tabs, same order |
| Vehicle switcher | Model year and name with a caret, trim on a second line [M] | Same pattern with invented model names |

This replaces the tab set in the source plan (Home, Remote, Map, More). Remote controls open from the Home tiles. Status, Charging and Trips open from Home rows. Maintenance moves into Car Care. Settings, Demo console, Digital Key stub and Roadside go into Menu.

### Home screen (top to bottom) [M]

1. Hero: model year and name at the top left, a vehicle image on a light blue-white gradient.
2. Two large numbers: battery % (or fuel %) and estimated range in miles.
3. A gray pill chip, for example "Charge Schedule On ›" or "Doors Locked".
4. Four square action tiles in one row: Lock, Climate, Charge, Controls. An active tile is solid navy with a white icon; other tiles are pale blue with a navy icon.
5. List rows with a leading icon, a value on the right and a chevron: Location, Vehicle Status ("Parked"), Vehicle Health ("All Systems Normal" in cyan).
6. Pull-to-refresh, and "Last updated …" text [S/M].

### Remote command flow [S]

- A 4-digit PIN prompt comes before each remote command and before Find My Car.
- Progress stages: sending → sent → pending → success or failed.
- Remote start opens a preset page first: up to 4 presets plus "Start vehicle without presets".
- Climate options: temperature OFF, LO, 62–82 °F, HI; front defrost; rear defrost; heated steering wheel; heated and ventilated seats; duration 1–10 min.
- Real command time ranges from instant to more than 20 s. The mock scenarios cover 5–30 s, with the 60 s app timeout [I].

### Other screens

| Screen | Reference layout | Notes for DriveLink |
| --- | --- | --- |
| Charging | White rounded sheet with a mint-green header; green progress bar with a limit chip; 2-column stat grid (battery, range, time to limit, rate kW, cost, energy added); "Stop charge" outlined pill; "Find a charging station" navy pill [M] | Separate AC and DC limit sliders, 50–100 % [S]. Schedules: departure time, off-peak window, scheduled climate [S] |
| Vehicle Status | Tabs "Quick View" and "Full List". Top-down car outline with white pill labels ("Door Closed"); an open item shows a red pill [M] | Drive with the `door-ajar` and `tire-low` scenarios |
| Car Care | Green "Everything Looks Good"; card with odometer, last service, next service due; cyan "Schedule Service" pill; preferred-dealer card [M] | Maintenance screen content goes here |
| Maps | Full-screen map, right rail of square buttons, bottom sheet with search and navy circle shortcuts, "My Vehicle" card [M] | osmdroid; shortcuts are static |
| Menu | Profile, settings, dealer, subscription, emergency, Digital Key, Surround View [S] | Settings, Demo console, Digital Key (stub), Roadside (stub), About |

### Design tokens (starting values)

| Token | Hex | Basis |
| --- | --- | --- |
| Primary navy (top bar, active tile, primary button) | `#002C5E` | [M] |
| Background / list row / gray card / divider | `#FFFFFF` / `#F9F9F9` / `#F5F5F5` / `#EBEBEB` | [M] |
| Pale-blue tile | `#C7E2EF` | [M] |
| Hero gradient top | `#E9F7FB` | [M] |
| Cyan accent (links, secondary CTA) | `#00B0DB` | [M] |
| Light-cyan card | `#E5F7FA` | [M] |
| Charge green | `#00D17E` | [M] |
| Error red | `#E63212` | [M] |
| Card radius / tile radius / buttons | 12–16 dp / 14 dp / full pill | [I] |
| Font | Geometric sans, light weight for large numbers. Candidate: Manrope or Outfit (Google Fonts) | [I] |

No source mentions a dark theme in the reference app [I]. The DriveLink dark theme is an original design.

### Pain points to use as test scenarios [S]

| Real complaint | Scenario that shows it |
| --- | --- |
| App state does not match the car (still "charging", wrong engine state) | `bad-payload`, stale `updatedAt` in `vehicle-offline` |
| Remote start fails at random ("request not processed") | `command-fails`, `server-error` |
| Presets fail when doors are unlocked | `command-fails` with `reason: DOOR_OPEN` |
| Frequent re-login prompts | `auth-expired` |
| Slow commands | `slow-vehicle`, `vehicle-asleep`, `command-timeout` |

The EU version of the reference app has a different structure (tabs Home, Remote, Map, Discover, Vehicle). It is out of scope unless you choose the EU reference.

Sources: the public store listings, the OEM owner pages and a user manual (links are not kept in this repo).

## Phase overview

| # | Phase | Main tools | Check (done when) |
| --- | --- | --- | --- |
| 0 | Environment and repo | Homebrew, PAG, `gh` | Emulator boots; SV and BlazeMeter MCPs respond; private repo exists |
| 1 | Design reference and UI spec | Research, Compose previews | `docs/ui-spec.md` approved by Ben |
| 2 | API contract | OpenAPI 3.0.3, Spectral | Lint passes; Ben approves endpoint list |
| 3 | Service virtualization | BlazeMeter SV MCP, cURL | `scripts/smoke.sh` passes for all 14 scenarios |
| 4 | App scaffold and network core | Gradle, Hilt, Retrofit | Unit tests pass; emulator call to the mock shows in the Network Inspector |
| 5 | Screens (lookalike UI) | Jetpack Compose | All 12 screens work on `default` and show the correct state for each scenario |
| 6 | Perfecto tests | Perfecto MCP, Scriptless AI, Autonomous Testing | Espresso core suite and AI Scriptless suite green on the device matrix |
| 7 | Performance | BlazeMeter MCP, Taurus, Perfecto | 500-user run recorded; device timings recorded; HAR-based test runs |
| 8 | CI integration | GitHub Actions, `bzm-*` skills | A bad PR is blocked; a clean PR passes with report links; one nightly run completes |

Checkpoints with Ben: after Phase 1 (look), after Phase 2 (API shape), after the Home and Remote screens in Phase 5 (feel), and before the first 500-user load test (cost).

## Phase 0 — Environment and repo

1. Create the private GitHub repo `benjithompson/drivelink-demo`. Push this plan. *(Done with this plan.)*
2. Install the Android toolchain:
   ```sh
   brew install --cask android-studio      # or android-commandlinetools for headless
   sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0" \
              "emulator" "system-images;android-36;google_apis;arm64-v8a"
   avdmanager create avd -n drivelink -k "system-images;android-36;google_apis;arm64-v8a"
   ```
   Use the latest stable API level that `sdkmanager --list` shows. *(Done: API 37.0, AVD `drivelink`.)*
3. Enable `blazemeter`, `blazemeter-sv` and `autonomous-testing` in PAG. *(Done.)*
4. Create the BlazeMeter project "Auto" in workspace "Ben T". *(Done: ID 2613758.)*
5. Add GitHub Actions secrets in Phase 8, when the workflows exist: `PERFECTO_CLOUD` (`demo`), `PERFECTO_SECURITY_TOKEN`, `BLAZEMETER_API_KEY_ID`, `BLAZEMETER_API_KEY_SECRET`, `MOCK_BASE_URL`.
6. Prefix all BlazeMeter resources with `drivelink-`.

**Check:** the emulator boots; `perfecto_user read_user` returns the user; the SV MCP lists workspaces; `gh repo view` shows the private repo.

**Result (Oct 2, 2026): passed.** The emulator booted in about 14 s (API 37). Perfecto returned the demo-tenant user. The SV MCP read workspace "Ben T" (2194183). The repo is private.

## Phase 1 — Design reference and UI spec

1. Collect the reference app screens (see Visual reference). Map each reference screen to a DriveLink screen.
2. Write `docs/ui-spec.md`: one section per screen with a wireframe (ASCII or Compose preview screenshot), components, states (loading, content, empty, error), and the scenario states that affect it.
3. Define design tokens in `:core:designsystem`: colors (light and dark), type scale, spacing, corner radii, elevation, icon set. Pick a free font that is close in feel.
4. Create original vehicle art: two flat side-profile illustrations (one EV crossover, one sedan) as vector drawables, with color variants.
5. Build a Compose "gallery" screen in debug builds that shows every component. Use it for the look-and-feel review.

**Check:** Ben approves `docs/ui-spec.md` and the component gallery screenshots.

**Status (Oct 2, 2026): approved by Ben.**

- [docs/ui-spec.md](ui-spec.md) and 15 screenshots in [docs/ui/screens/](ui/screens/).
- Modules `:app` and `:core:designsystem` (Gradle 9.8.0, AGP 9.4.1, Kotlin 2.4.20, Compose BOM 2026.09.00, compileSdk 37, minSdk 26).
- The app is a static "lookbook". Launch extras open any screen and scenario state (see the UI spec).
- `scripts/screenshots.sh` captures all screens from a running emulator.
- Manrope font bundled under the SIL Open Font License ([third_party/manrope/OFL.txt](../third_party/manrope/OFL.txt)).

## Phase 2 — API contract

Follow the API contract section of the source plan.

1. Write `api/openapi.yaml` (OpenAPI 3.0.3) and `api/examples/*.json` (one file per operation and scenario).
2. Add `GET /v1/health`.
3. Add any endpoints that Phase 1 shows the lookalike UI needs. Candidates: climate presets (`GET/PUT /vehicles/{vin}/climate-presets`), and separate AC and DC charge limits in `ChargeSettings`. Digital Key stays a static Menu row with no endpoint (D-11).
4. Lint with `npx @stoplight/spectral-cli lint api/openapi.yaml`.

**Check:** lint passes with zero errors; Ben approves the endpoint list.

**Status (Oct 2, 2026): approved by Ben (endpoint list, D-01 to D-15).**

- [docs/api.md](api.md): 18 operations, scenario coverage, review checklist.
- `scripts/lint-api.sh`: 0 errors, 0 warnings; spec and scenario catalog consistent; 110 generated files (109 example bodies and `index.json`); resolution check 0 problems.
- Local reference mock: `scripts/mock-server.py` serves the examples with the rules in `scripts/mock_resolver.py`. `scripts/check-resolution.py` checks that each request (operation × scenario × VIN × poll attempt) resolves to exactly one example.
- `index.json` carries the SV transaction fields (`urlRegex`, `priority`, `thinkTimeMs`). The resolution check confirms that the SV priority order selects the same example as the reference mock for all 1092 requests.
- SV capability check (read-only, Oct 2, 2026): BlazeMeter SV supports header equals and regex matchers, URL regex, priority, uniform think time, response headers and 204. Gaps: the MCP cannot set priority or a think-time range, so Phase 3 uses the REST API for these (see Phase 3).
- Changes from the source plan are in [DECISIONS.md](DECISIONS.md) (D-01 to D-15).

## Phase 3 — Service virtualization

Build the virtual service with the BlazeMeter SV MCP. The source plan's "Virtual service build" section has the full steps and matcher rules.

1. SV tool discovery: done in Phase 2 (D-14). Tools: `blazemeter-sv__virtual_services_service`, `…_http_transaction`, `…_virtual_service` (`create`, `assign_transactions`, `deploy`, `configure`), `…_tracking`, `…_sandbox`.
2. Create the service `drivelink` and the virtual service `drivelink-mock` (US East location, HTTPS, no-match → 404).
3. Create one transaction per `index.json` entry with a scenario: `urlRegex`, `X-Scenario` equals (not for `default`), `match` as `matches` header matchers, status, body, headers, `responseDelay` uniform from `thinkTimeMs`. The MCP creates one transaction per call; loop in `pag__execute`, or use the REST bulk call.
4. Add the think-time copies of the default transactions (D-14).
5. Set each transaction's `priority` with REST `PATCH …/service-mocks/{vsId}/transactions/{txnId}`. The MCP cannot set it.
6. Start the service in BlazeMeter cloud. Store the URL in `secrets.properties` and as the CI secret `MOCK_BASE_URL`.
7. Write `scripts/smoke.sh` (cURL plus `jq`). It calls every endpoint once per scenario and asserts the status code. Run it against the local mock first, then against SV.
8. Export the service definition to `mock/` so it can be recreated.

**Check:** `scripts/smoke.sh` passes for all 14 scenarios. Under `default`, poll attempts 1, 2, 3 return `PENDING`, `PENDING`, `SUCCEEDED`.

**Result (Oct 2, 2026): passed.** Details are in [mock-service.md](mock-service.md).

- Virtual service `drivelink-mock` (id 361526, service 447558) runs in US East. Since Oct 4, 2026, the app uses the stateful `drivelink-proto` (id 361664, service 447563) instead; its endpoint is `MOCK_BASE_URL` in `secrets.properties` (D-51).
- 126 transactions: 96 from `index.json` and 30 think-time copies. `scripts/build-mock.py --plan` emulates all 1092 grid requests with 0 problems. `--apply` is idempotent.
- `scripts/smoke.sh` against SV: 299/299 cases passed in 32 s and 33 s (two runs, 0 retries). Against the local mock: 299/299 in 3 s.
- Under `default`, poll attempts 1, 2, 3 return `PENDING`, `PENDING`, `SUCCEEDED` (measured on SV).
- Findings: SV sometimes stalls a request for 60–100 s (D-16). SV does not echo `X-Correlation-Id` (D-17).

## Phase 4 — App scaffold and network core

1. Multi-module Gradle project with a version catalog: `app`, `core:{domain,data,network,settings,designsystem,testing}`, `feature:*`.
2. Kotlin, Compose with Material 3, Navigation Compose, Hilt, Retrofit, OkHttp, kotlinx.serialization, DataStore.
3. Interceptor chain, endpoint profiles, error mapping and the command polling use case, as the source plan describes.
4. Network Inspector ring buffer.
5. Test hooks for Perfecto: read `baseUrl` and `scenario` from instrumentation arguments and from the debug-only `DemoConfigActivity` intent.
6. Stable test tags: set `Modifier.testTag()` and content descriptions on every control. Perfecto AI tests also use the visible text, so keep labels stable.
7. Timing markers: log `DL_TIMING cold_start_ms=…` and `DL_TIMING command_ms=… type=LOCK` to Logcat. Phase 6 and Phase 7 read these markers.

**Check:** `./gradlew check` passes; unit tests cover polling and error mapping; an emulator call to the mock appears in the inspector; a profile switch redirects the next call without a restart.

**Result (Oct 3, 2026): passed.**

- `./gradlew check` passes, with 0 lint errors. The build has 107 unit tests in 5 modules, and all pass. The contract test decodes 108 examples.
- `connectedDebugAndroidTest` passes 6 of 6 tests on the API 37 emulator. It passes with and without the `baseUrl` and `scenario` instrumentation arguments.
- On the emulator, the debug console sent sign in, health, vehicles, status and Lock to the local mock. The inspector and the mock log showed the same 13 calls. Lock polled PENDING, PENDING, SUCCEEDED.
- A profile switch in the UI sent the next call to the BlazeMeter virtual service. The process ID did not change, so the app did not restart.
- Logcat showed `DL_TIMING cold_start_ms=842` and `DL_TIMING command_ms=8623 type=LOCK result=SUCCEEDED`.
- Navigation Compose is not added yet. Phase 5 adds it with the real screens. The debug console is the start of the Phase 5 Demo console.
- Screenshots: [phase4-console.png](ui/screens/phase4-console.png), [phase4-inspector-detail.png](ui/screens/phase4-inspector-detail.png).

## Phase 5 — Screens (lookalike UI)

Build the screens in this order. Each screen follows `docs/ui-spec.md` from Phase 1. The bottom tabs are Home, Car Care, Maps and Menu (see Visual reference).

1. Login (email, password, PIN setup)
2. Home tab (hero, two large numbers, status chip, four action tiles, list rows)
3. Remote controls (PIN prompt, climate presets sheet, command progress)
4. Vehicle Status (Quick View and Full List)
5. Demo console and Network Inspector (Menu)
6. Charging sheet, charge limits (AC and DC) and schedules
7. Maps tab (vehicle pin, distance, refresh)
8. Trips (from a Home row)
9. Car Care tab (maintenance, recalls, schedule service)
10. Alerts (bell in the top bar)
11. Menu tab and Settings
12. Vehicle picker (caret next to the vehicle name)

After Home and Remote, stop for the look-and-feel checkpoint with Ben.

**Stage 1 (Oct 3, 2026): built.** Login, PIN setup, Home (with vehicle picker, pull to refresh and the scenario states) and the remote command flow (Remote Controls, Remote Start, PIN prompt, command card) use real data. Car Care, Maps, Menu, Charging, Vehicle Status and the Design Gallery still use the static lookbook screens until stage 2. Screenshots: `docs/ui/screens/p5-*.png`, listed in [ui-spec.md](ui-spec.md).

**Stage 2 (Oct 3, 2026): built.** Vehicle Status, Charging (with limit sliders), Charging Schedule, Car Care, Schedule Service, Trips, Alerts, Maps (osmdroid), Menu, Profile, Settings (theme, change PIN) and the Demo console setup tools (profile add, edit, delete, import, export, VIN, reset session) use real data. The lookbook screens are removed. Only the Design Gallery is static. Decisions D-30 to D-45 are in [DECISIONS.md](DECISIONS.md). Screenshots `p5-15` to `p5-33` are listed in [ui-spec.md](ui-spec.md).

**Check:** every screen works against `default`. Every scenario in the catalog produces the expected UI state on the screens it affects. Dark theme works.

**Result (Oct 3, 2026):** (measured on the API 37 emulator against the local mock)

- `./gradlew check`: passes. Lint: 0 errors, 2 warnings (`DataExtractionRules`, `UnusedResources`; both existed before stage 2).
- Unit tests: 365 pass, 0 fail (`:app` 210, `:core:network` 51, `:core:data` 38, `:core:settings` 36, `:core:domain` 30).
- Device tests (`connectedDebugAndroidTest`): 16 of 16 pass, including `MapsMenuFlowTest` and `AppFlowTest`.
- Scenario sweep: 14 of 14 scenarios give the expected UI state on the screens they affect.

| Scenario | Checked on | Result |
| --- | --- | --- |
| `default`, `fast` | all screens; charge command (`fast`: one poll, then Done) | pass |
| `slow-vehicle` | charge command: 8 pending polls, then Done | pass |
| `vehicle-asleep` | charge command: "Waking vehicle…", then Done | pass |
| `vehicle-offline` | Home and Status: stale banner with the data age; Unlock: "The vehicle is offline." | pass |
| `command-fails` | charge command: "Command failed · A door is open. Close all doors and try again." | pass |
| `command-timeout` | charge command: "Vehicle did not respond" after about 60 s | pass |
| `low-battery`, `door-ajar`, `tire-low` | Home rows, Status highlights, Alerts entry | pass |
| `auth-expired` | Status, Charging, Car Care, Trips, Alerts, Maps: Login with the notice | pass |
| `rate-limited`, `server-error` | Home, Status, Charging, Car Care, Trips, Alerts, Maps: error card with the message and the correlation id | pass |
| `bad-payload` | Home, Status, Charging: "The server sent data that the app cannot read." | pass |

- Dark theme: every screen adapts. Two defects were found and fixed (the time and date pickers and the dialogs used default Material 3 colors).
- Screens checked on a device: all, for the EV (Aurora) and for the gas car (Solace).

## Phase 6 — Perfecto tests

Three tracks run against the same app and the same virtual service. Each track sets the scenario without the UI where it can.

### 6a. Scripted tests (Espresso / Compose UI test)

- Core suite of about 10 tests, as listed in the source plan.
- Run on Perfecto through the Perfecto Gradle plugin or the REST API. Upload the app APK and the test APK.
- Pass `baseUrl` and `scenario` as instrumentation arguments.

### 6b. Perfecto AI Scriptless (mobile)

- Create a customer folder in Scriptless. This follows your existing folder pattern.
- Author the tests in the Scriptless UI with AI steps (AI User Action and AI Validation). The Perfecto MCP cannot create Scriptless tests; it can list and run them.
- Suggested tests:

  | Test | AI steps (natural language) |
  | --- | --- |
  | Login and dashboard | "Log in with demo@drivelink.test and PIN 1234." "Validate that the dashboard shows the vehicle range and lock state." |
  | Remote lock | "Tap Lock and enter the PIN." "Validate that the app shows that the vehicle is locked." |
  | Remote start with climate | "Start the vehicle with 72 °F and front defrost on." "Validate that the climate status shows on." |
  | Slow vehicle | Set scenario `slow-vehicle` through the Demo console. "Validate that the app shows a waiting-for-vehicle message." |
  | Vehicle offline | Set scenario `vehicle-offline`. "Validate that the app shows an offline message and a stale-data banner." |
  | Charge limit | "Set the AC charge limit to 80 %." "Validate that the charge limit shows 80 %." |
  | Visual check | AI visual comparison of the Home screen against a baseline (you have an "AI Visual Comparison" example to copy) |

- Run each test through the MCP (`perfecto_ai_scriptless execute_test`) on one device first, then on the matrix.
- Group the tests into a Scriptless job for CI.

### 6c. Perforce Autonomous Testing

- Describe the app, the scenarios, the device environments and the failure criteria in natural language.
- One scenario can run as a Perfecto functional test and as a BlazeMeter performance test. This is a strong demo point.
- Application: [227 "BenT"](https://demo.autonomous-testing.perforce.com/application/227). Create a DriveLink environment, scenarios (AI test prompts) and a suite in it. Do not change the application context.
- Use the PAG `autonomous-testing` server. Keep its current BlazeMeter workspace (account 514413, workspace 2259111). Ben decided on Oct 2, 2026 to keep Autonomous Testing and the Ben T workspace separate for now.
- Upload the APK with `get_binary_upload_url`, then create the application "DriveLink Demo" (Android, automotive, US).

### Device matrix (from the live device list)

| Device | Android | Location | Use |
| --- | --- | --- | --- |
| Google Pixel 9 Pro | 16 | NA-US-BOS | PR + nightly |
| Samsung Galaxy S25 | 15 | NA-US-PHX | PR + nightly |
| Google Pixel 5 | 14 | NA-US-BOS | Nightly (small screen) |
| Motorola Moto G9 Play | 10 | NA-US-PHX | Nightly (oldest supported, low-end) |

Confirm availability before each run. Replace a device if its label changes to a POC reservation.

**Check:** the 6a core suite and the 6b AI suite are green on all four devices. `docs/test-runs.md` links the reports.

**Status (Oct 3, 2026): in progress.** Details are in [perfecto/README.md](../perfecto/README.md) and [test-runs.md](test-runs.md).

| Track | Status |
| --- | --- |
| 6a | The core suite (10 tests) passes on the API 37 emulator against the local mock and against the cloud virtual service. The Perfecto run is ready (`scripts/perfecto-espresso.sh`). It waits for the security token in `perfecto.properties`. |
| 6b | The Scriptless tests come from the 8 PAT scenario prompts (plain language). See [perfecto/scriptless/README.md](../perfecto/scriptless/README.md). |
| 6c | Environment 493, 8 scenarios (plain-language prompts) and suite 583 exist in application 227. No run yet. |

## Phase 7 — Performance

### 7a. API load (BlazeMeter)

- Taurus YAML in `load/` that models app traffic: login, status refresh every 30 s, one command with polling every few minutes.
- Thresholds in `load/thresholds.yml`: p95 under 1,500 ms for status calls; error rate under 1 % (scenario-intended errors excluded).
- First run: 50 users, 5 minutes. After Ben approves cost: 500 users, 15-minute hold, 5 % `command-fails` mix.
- Run through the BlazeMeter MCP interactively and `bzt -cloud` in CI.
- Tests go in project "Auto": [BlazeMeter tests list](https://a.blazemeter.com/app/?#/accounts/291446/workspaces/2194183/projects/2613758/tests).

### 7b. Client-side performance on real devices

- Read the `DL_TIMING` markers from device logs on Perfecto.
- Targets: cold start under 2 s on the Pixel 9 Pro; tap-to-Done command time under 10 s on `default`.
- Record Perfecto device vitals (CPU, memory) during the core suite.

### 7c. Devices under load (combined demo)

- Run the Perfecto suite while the 500-user load test runs. Show the user experience on real devices with the backend under pressure.
- Capture a HAR file from a Perfecto run. Convert it into a BlazeMeter test. You have "har capture" Scriptless examples to copy.

**Check:** a 500-user run completes with results in `docs/test-runs.md`; device timings are recorded; the HAR-based BlazeMeter test runs.

## Phase 8 — CI integration (GitHub Actions)

| Workflow | Trigger | Steps |
| --- | --- | --- |
| `ci.yml` | Pull request, push to `main` | Build, unit tests, contract test, smoke test against the mock, Espresso suite on 2 devices, Scriptless AI job on 1 device, 50-user / 5-minute load test, gate, PR comment |
| `nightly.yml` | Schedule (02:00) | Full device matrix and the 500-user load test in parallel, trend versus baseline |
| `manual.yml` | `workflow_dispatch` | Inputs: devices, scenario, users, duration, endpoint profile |
| `mock-sync.yml` | Change to `api/openapi.yaml` | Update the virtual-service transactions through the BlazeMeter API |

- Use the Perforce skills `perforce:bzm-ci-setup` to generate the BlazeMeter job and `perforce:bzm-pr-gate` for the PR verdict comment. Use `perforce:bzm-set-baseline` to pin the baseline in `.blazemeter/baseline.json`.
- Trigger the Scriptless job through its CI/CD link (Perfecto doc: "Connect a job to CI/CD"). Confirm the API call against current docs.
- The PR comment lists: Perfecto report link, Scriptless job link, BlazeMeter report link, pass/fail per layer, and correlation IDs from failed UI tests.
- Artifacts: debug APK, test APK, JUnit XML, HAR files.
- Branch protection on `main`: require the `ci` check.

**Check:** a PR that breaks a UI test or a load threshold is blocked; a clean PR passes with all report links; one nightly run completes with Perfecto and BlazeMeter in parallel.

## Repo layout

```
drivelink-demo/
  app/  feature/  core/            # Android modules (Phase 4–5)
  api/openapi.yaml  api/examples/  # Phase 2
  mock/                            # Phase 3 export
  scripts/smoke.sh                 # Phase 3
  load/                            # Phase 7 Taurus + thresholds.yml
  perfecto/                        # Phase 6 device configs, Scriptless test notes
  .blazemeter/baseline.json        # Phase 8
  .github/workflows/               # Phase 8
  docs/  PLAN.md  source-plan.md  ui-spec.md  DECISIONS.md
         mock-service.md  test-runs.md  demo-script.md
```

## Open decisions for Ben

| Decision | Default | Alternatives |
| --- | --- | --- |
| App name | DriveLink Demo | Any original name |
| iOS | Out of scope | Add a SwiftUI version later; Perfecto has iOS devices available |
| Load size | 50 users first, then 500 | Smaller if quota is tight |

- [x] Confirm the BlazeMeter account, workspace and project.
- [x] Enable `blazemeter`, `blazemeter-sv` and `autonomous-testing` in PAG.
- [x] Include the Autonomous Testing track.
