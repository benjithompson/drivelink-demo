# DriveLink — Perfecto AI Scriptless tests

The Perfecto AI Scriptless tests come from plain-language prompts. They do not come from a test framework. Perforce Autonomous Testing (PAT) turns each scenario prompt into a Scriptless test with AI User Action and AI Validation steps.

The prompts are in [../autonomous/README.md](../autonomous/README.md) (application 227, suite 583). Change a prompt there, in PAT, and not here.

## How to write a prompt

- Say what a user wants to do: "Unlock the car from the home screen using PIN 1234".
- Say what the user must see, and start with "Verify": "Verify the app confirms that the car is unlocked".
- Do not use package names, test tags, locators, coordinates or if/then logic. The AI finds the controls.
- Start each scenario with the same sign-in prompts, so that the scenarios do not depend on their order.
- When a scenario changes the Demo console scenario, end it with a prompt that sets the scenario back to `default`.

## Setup

| Item | Value |
| --- | --- |
| App | Debug APK, package `com.drivelink.demo`. A build with `secrets.properties` calls the cloud virtual service by default. |
| Scenario selection | In the app: Menu → Demo console → Scenario. A Scriptless test cannot pass instrumentation arguments. |
| Devices | Environment 493: Pixel 9 Pro |

## Run

A single scenario (one device, dry run):

```js
callTool("autonomous-testing__execution", { intent: "dry_run_scenario",
  task: "Application ID 227. Dry-run scenario ID 1494 on the real device 48071FDAP003CY (Pixel 9 Pro)." })
```

The full suite: see [../autonomous/README.md](../autonomous/README.md). Scriptless tests that exist in Perfecto can also run through the Perfecto MCP:

```text
perfecto_ai_scriptless list_tests     {"test_name": "DriveLink"}
perfecto_ai_scriptless execute_test   {"test_id": "<id>", "device_type": "real",
                                       "device_under_test": {"device_id": "48071FDAP003CY"}}
```

## Limits

- The virtual service returns fixed bodies. A reload shows the charge limits 80 % and 90 % again.
- Do not write a prompt that depends on the Maps tab. The public map tile servers can block a device farm.
