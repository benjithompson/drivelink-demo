# DriveLink — Test runs

Newest first. All values are measured unless the row says otherwise.

## Phase 7 — Performance

### API load test (`load/drivelink-api.jmx`)

| Date | Where | Load | Result | Report |
| --- | --- | --- | --- | --- |
| Oct 4, 2026 | Local Taurus (`bzt`, JMeter 5.5) | 2 users, 40 s hold, 5 s refresh, command every 2nd refresh | 37 samples, 0 % failures, average 607 ms | Local only |
| Oct 4, 2026 | Local JMeter 5.5 CLI | 2 users, 75 s, 5 s refresh, command every 2nd refresh | 56 samples, 0 errors. Status p50 678 ms, max 873 ms. 10 commands, 10 of 10 "locked after command" checks pass | Local only |

The first poll of each command ended the poll loop (10 polls for 10 commands). So the virtual service did not return `QUEUED` or `PENDING` to these polls (inferred from the count).

## Phase 6 — Perfecto tests

### Espresso core suite (package `com.drivelink.demo.core`)

| Date | Where | Endpoint | Result | Time | Report |
| --- | --- | --- | --- | --- | --- |
| Oct 4, 2026 | Emulator, API 37 | Stateful cloud service `drivelink-proto` | 10/10 pass (first run 9/10: the dashboard saw the car unlocked by `slowVehicle`; fixed with a state reset before each test) | 170 s wall | Local only |
| Oct 3, 2026 | Emulator, API 37 | Cloud virtual service | 10/10 pass | 231 s wall | Local only |
| Oct 3, 2026 | Emulator, API 37 | Local mock (3 runs) | 10/10 pass each run | 147–184 s (sum of tests) | Local only |

Test times on the cloud virtual service (seconds): slow-vehicle 66.0, remote start 34.9, lock 27.8, auth-expired 19.9, command-fails 19.6, login 16.1, vehicle-offline 11.1, charge limit 10.3, inspector 10.2, dashboard 7.7.

### Contract smoke (`scripts/smoke.sh`)

| Date | Endpoint | Result |
| --- | --- | --- |
| Oct 4, 2026 | `drivelink-proto` | 299/299 pass (first run 290/299: the stateful command bodies had another `commandId`; the build script now aligns them) |

### Other device tests (MockWebServer on the device)

| Date | Where | Result |
| --- | --- | --- |
| Oct 3, 2026 | Emulator, API 37, fresh install, no arguments | 16/16 pass; the core suite is left out (0 skipped) |

### Perfecto Autonomous Testing (application 227)

| Date (UTC) | Run | Device | APK | Result |
| --- | --- | --- | --- | --- |
| Oct 4, 2026 03:38 | Suite 584 "DriveLink – Smoke" (scenario 1494), suite run 3082, test run 9859 | Pixel 9 Pro | `bbcd44c` | FAILED, 39 s. "Start Application" failed; the AI did not find DriveLink in the app drawer. |
| Oct 4, 2026 03:38 | Same suite run, test run 9860 | Galaxy S25 | `bbcd44c` | FAILED, 13 s, before any device step: "Failed to configure application binary … Descriptor file … not found". The Galaxy is now removed from environment 493. |
| Oct 4, 2026 03:32 | Dry run, scenario 1494 | Pixel 9 Pro | none | Stopped. `dry_run_scenario` cannot attach an app binary. |
| Oct 4, 2026 03:28 | Dry run, scenario 1494 | Pixel 9 Pro | none | FAILED, 42 s. Same failure as test run 9859. |

Inferred, not verified: the install step of the Scriptless test did not run, or the AI looked for "DriveLink" when the app is named "DriveLink Demo".
