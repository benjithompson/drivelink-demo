# DriveLink — Perfecto tests

Three test tracks run against the same app and the same virtual service.

| Track | Where | How it runs |
| --- | --- | --- |
| Espresso core suite | `app/src/androidTest/.../demo/core/` | `scripts/perfecto-espresso.sh` (Perfecto Gradle plugin) |
| AI Scriptless | Perfecto Scriptless, your customer folder | Perfecto MCP or the Scriptless job. See [scriptless/README.md](scriptless/README.md). |
| Autonomous Testing | Application 227, items named `DriveLink …` | PAG `autonomous-testing` server |

## Device matrix

Example device IDs on the demo cloud. Replace them with devices from your own cloud.

| Device | Android | Location | Device ID | Device sets |
| --- | --- | --- | --- | --- |
| Google Pixel 9 Pro | 16 | NA-US-BOS | `48071FDAP003CY` | single, pr, matrix |
| Samsung Galaxy S25 | 15 | NA-US-PHX | `R3CY90QKKFX` | pr, matrix |
| Google Pixel 5 | 14 | NA-US-BOS | `0C101FDD4006EH` | matrix |
| Motorola Moto G9 Play | 10 | NA-US-PHX | `ZY327WM8S9` | matrix |

Before a run, check the device list (`perfecto_devices list_real_devices`). If a device gets a POC label, replace it in `espresso/config-*.json`.

## Espresso core suite on Perfecto

The suite runs against a live endpoint, which it gets from the instrumentation argument `baseUrl`. Without that argument the suite is skipped, so `./gradlew connectedCheck` still passes on a local emulator. Each test sets its own scenario through `DemoConfig` and starts signed out.

### Settings

| Setting | Source | Note |
| --- | --- | --- |
| `PERFECTO_SECURITY_TOKEN` | Environment, or `perfecto.properties` in the repo root | Gitignored. Never commit it. |
| `PERFECTO_CLOUD` | Environment, or `perfecto.properties` | Default `demo` (`demo.perfectomobile.com`) |
| `MOCK_BASE_URL` | Environment, or `secrets.properties` | The https URL of the virtual service. Devices cannot reach a local mock. |

`perfecto.properties` format:

```properties
PERFECTO_SECURITY_TOKEN=<token from the Perfecto UI: profile → Security token>
PERFECTO_CLOUD=demo
```

The script sends these values to Gradle as `ORG_GRADLE_PROJECT_*` environment variables, so they are not on the command line. It masks the token and the URL in the console output.

### Run

```sh
scripts/perfecto-espresso.sh -k            # check the settings only (no device time)
scripts/perfecto-espresso.sh               # Pixel 9 Pro
scripts/perfecto-espresso.sh -d pr         # Pixel 9 Pro + Galaxy S25 (pull requests)
scripts/perfecto-espresso.sh -d matrix     # all four devices (nightly)
```

The script builds `app-debug.apk` and `app-debug-androidTest.apk`, then runs the Gradle task `perfecto-android-inst` in the standalone build `perfecto/espresso/`. The plugin uploads both APKs, installs them on each device, runs the package `com.drivelink.demo.core`, and writes a report to Perfecto Smart Reporting (job `drivelink-espresso`).

### Local run (emulator, local mock)

```sh
python3 scripts/mock-server.py --port 8081 &
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.baseUrl=http://10.0.2.2:8081 \
  -Pandroid.testInstrumentationRunnerArguments.package=com.drivelink.demo.core
```

### Repository copy

A Perfecto repository folder holds a copy of the suite that runs without Gradle. Set the folder as `PERFECTO_REPO_FOLDER` in `perfecto.properties` (gitignored) or in the environment, for example `PUBLIC:<you>/Customers/<customer>`.

| Item | Repository | Source |
| --- | --- | --- |
| `DriveLink-debug.apk` | Media | `scripts/perfecto-upload.sh` |
| `DriveLink-debug-androidTest.apk` | Media | `scripts/perfecto-upload.sh` |
| `DriveLink Core Suite.xml` | Media (Miscellaneous) | [espresso/DriveLink Core Suite.xml](espresso/DriveLink%20Core%20Suite.xml), with `@REPO_FOLDER@` replaced by the folder; `scripts/perfecto-upload.sh` |

The script runs `CoreSuiteTest` on the Pixel 9 Pro with the command `espresso execute`. That command cannot pass instrumentation arguments. So `scripts/perfecto-upload.sh` builds the test APK with `-PembedBaseUrl`: the APK then carries `MOCK_BASE_URL` as an asset, and `DriveLinkTestRunner` uses it when no `baseUrl` argument is given. A normal build has no such asset.

```sh
scripts/perfecto-upload.sh        # build with the endpoint built in, upload both APKs and the XML (overwrite)
```

The upload uses the Perfecto repository API v1, with the token in a header. That API writes only to the media repository, so the XML is a Miscellaneous item there, not an item in the scripts repository.

### Why a standalone Gradle build

The Perfecto Gradle plugin uses the old `buildscript` / `apply plugin` form, and the docs require the latest version (`plugin:+`). A separate build keeps it away from the app build (AGP 9, version catalog). The plugin needs only the two APK paths.

## Cost

Each Espresso run uses one device session per device for the length of the suite. A matrix run uses four devices.
