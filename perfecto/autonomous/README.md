# DriveLink — Perforce Autonomous Testing (Phase 6c)

The items are in application 227 ("BenT") at <https://demo.autonomous-testing.perforce.com/application/227>. They were created on Oct 3, 2026 through the PAG `autonomous-testing` server. The application settings and the BlazeMeter context (account 514413, workspace 2259111) did not change.

## Items

| Item | ID | Note |
| --- | --- | --- |
| APK | `PRIVATE:AITS/drivelink-demo-debug.apk-DriveLink%20Android-BenT` | Debug build of commit `f5adc28` with `MOCK_BASE_URL` = `drivelink-proto` (uploaded Oct 4, 2026, 22,981,129 bytes). It calls the stateful cloud virtual service by default. The app name on the device is "DriveLink Demo". |
| Environment "DriveLink Android" | 493 | Perfecto real device: Pixel 9 Pro (`48071FDAP003CY`). The Galaxy S25 (`R3CY90QKKFX`) was removed on Oct 4, 2026. No load configuration. |
| Scenario "DriveLink – Sign in and dashboard" | 1494 | Battery 72 %, range 226 mi, vehicle name |
| Scenario "DriveLink – Remote unlock and lock" | 1496 | Command reaches Done; the tile state changes |
| Scenario "DriveLink – Remote start with climate" | 1497 | 72 °F, front defrost |
| Scenario "DriveLink – Slow vehicle" | 1498 | Demo console scenario `slow-vehicle`; resets to `default` |
| Scenario "DriveLink – Vehicle offline" | 1499 | Offline banner and command error; resets to `default` |
| Scenario "DriveLink – Command fails" | 1500 | Failure reason "door open"; resets to `default` |
| Scenario "DriveLink – EV charge limit" | 1501 | AC limit 90 % |
| Scenario "DriveLink – Switch vehicle" | 1502 | 2025 Solace shows fuel; switches back |
| Suite "DriveLink – Core" | 583 | All 8 scenarios. No policies. |
| Suite "DriveLink – Smoke" | 584 | Scenario 1494 only. No policies. Use it to check a new APK. |

## Prompts

PAT turns each scenario into a Perfecto AI Scriptless test. A scenario is a list of plain-language prompts: what a user wants to do and what the user must see. It is not a test script. It has no package names, locators, test tags or if/then logic. The style follows the other scenarios in application 227 (for example "Add Galaxy S6 to cart", "Verify user is on the AE app home page").

Every scenario starts with these three prompts, so the scenarios do not depend on their order:

1. Open the DriveLink app
2. Sign in with the demo account that is already filled in
3. Create the PIN 1234 if the app asks for one

| ID | Scenario | Prompts after the sign-in (A = action, V = validation) |
| --- | --- | --- |
| 1494 | Sign in and dashboard | V: Verify the home screen shows the 2026 Aurora EV with 72% battery and 226 mi of range |
| 1496 | Remote unlock and lock | A: Unlock the car from the home screen using PIN 1234 · V: Verify the app confirms that the car is unlocked · A: Lock the car again using PIN 1234 · V: Verify the app confirms that the car is locked |
| 1497 | Remote start with climate | A: Start the car remotely with the cabin set to 72°F and the front defrost on, using PIN 1234 · V: Verify the app confirms that the car started and that the climate is on |
| 1498 | Slow vehicle | A: Open the Demo console from the Menu tab and switch the scenario to slow-vehicle · A: Go back to the home screen and unlock the car using PIN 1234 · V: Verify the app tells the user that it is waiting for the vehicle · V: Verify the unlock finishes successfully after the wait · A: Open the Demo console from the Menu tab and switch the scenario back to default |
| 1499 | Vehicle offline | A: Open the Demo console from the Menu tab and switch the scenario to vehicle-offline · A: Go back to the home screen and pull down to refresh · V: Verify the home screen warns that the vehicle is offline and that the status may be out of date · A: Try to unlock the car using PIN 1234 · V: Verify the app shows that the command could not reach the vehicle because it is offline · A: (scenario back to default) |
| 1500 | Command fails | A: Open the Demo console from the Menu tab and switch the scenario to command-fails · A: Go back to the home screen and unlock the car using PIN 1234 · V: Verify the app shows that the command failed because a door is open · A: (scenario back to default) |
| 1501 | EV charge limit | A: Open the charging screen from the home screen · A: Set the AC charge limit to 90% and save · V: Verify the app shows the AC charge limit saved at 90% |
| 1502 | Switch vehicle | A: Switch to the 2025 Solace from the vehicle picker on the home screen · V: Verify the home screen shows the 2025 Solace with a fuel level instead of a battery charge · A: Switch back to the 2026 Aurora EV |

The car is locked in the default data, so the command scenarios start with an unlock. The default AC limit is 80 %, so test 1501 sets 90 %.

A suite has no environment field. The run selects the environment.

## Run (costs device time; ask first)

```js
callTool("autonomous-testing__execution", {
  intent: "run_suite",
  task: "Application ID 227. Run suite ID 583 'DriveLink – Core' now on environment ID 493 'DriveLink Android'. No policies. Do not schedule."
})
```

## Phase 7 link

The `execution` tool has the intent `dry_run_bzm_test`. It converts a scenario into a BlazeMeter performance test (2 virtual users, 3 minutes) and needs no device. A full load run needs a `load_config` on the environment (concurrency, location) and a performance policy (response time, error rate). The load goes to the virtual service API, not to the UI.

## Known risks

- The cloud virtual service keeps state per car (D-51). A run can leave the demo car unlocked or with other charge limits, and all devices share the demo car. Reset the state before a run: `./gradlew resetCloudState` (about 25 s).

- A dry run (`dry_run_scenario`) cannot attach an app binary. Use suite 584 on environment 493 to test a new APK.
- The first runs (Oct 4, 2026) did not find the app on the device. See [test-runs.md](../../docs/test-runs.md).

- The app shows "Waiting for vehicle…" and "Vehicle is offline. Status may be out of date (updated …)". The validations describe these messages in plain words. The first run will show if the AI accepts them.
- The environment's cloud and location fields were not read back after the save.
