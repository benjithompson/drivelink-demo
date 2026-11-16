# DriveLink: native Android test with Appium on Perfecto

This test installs the DriveLink APK on a real Android device in the Perfecto cloud, opens the native app, and signs in. It uses Appium 2 with the UiAutomator2 driver through WebdriverIO, and Node's built-in test runner. Node 23.6 or later runs the TypeScript files directly.

## What the test checks

The three tests run in order in one device session. Each test is one step in the Perfecto Smart Reporting report (job `drivelink-appium`).

| Test | Checks |
| --- | --- |
| installs the APK from the Perfecto repository | The test removes the app first, and `mobile: isAppInstalled` is false. Then `mobile:application:install` installs the APK from the repository, and `mobile: isAppInstalled` is true. |
| opens the app on the sign-in screen | `mobile:application:open` starts the app. The screens `screen_login` and `login_submit` show. The current package is `com.drivelink.demo`. `mobile:application:info` returns the `versionName` from `app/build.gradle.kts`. |
| signs in and shows the vehicle status from the virtual service | Sign-in with the prefilled demo user, PIN setup (1234 twice), then Home. `stat_level` shows 72 (the demo EV, scenario `default`). This value comes from `GET /status` on the virtual service, so the app reached the network. |

Before the session, the test uploads the APK to the Perfecto repository as `PRIVATE:drivelink/drivelink-demo-debug.apk` and replaces an earlier copy. After the tests, it removes the app from the device.

The selectors use the Compose test tags, which the app exposes as resource IDs (`testTagsAsResourceId`): `android=new UiSelector().resourceId("screen_login")`.

## Settings

The settings use the same sources as `scripts/perfecto-espresso.sh`: first the environment, then `perfecto.properties` in the repo root (gitignored). The test never prints the token, and WebdriverIO logging is off, because WebdriverIO logs the capabilities.

| Setting | Default | Note |
| --- | --- | --- |
| `PERFECTO_SECURITY_TOKEN` | none | Required. Perfecto UI: profile → Security token. |
| `PERFECTO_CLOUD` | `demo` | `demo.perfectomobile.com` |
| `PERFECTO_DEVICE` | `48071FDAP003CY` (Pixel 9 Pro, Android 16) | A device ID, or `any` for any available Android device. |
| `APK_PATH` | `app/build/outputs/apk/debug/app-debug.apk` | Relative to the repo root. |
| `APP_LOCATOR` | none | An APK that is already in the Perfecto repository. The test then does not upload. |
| `EXPECTED_VERSION` | `versionName` in `app/build.gradle.kts` | Set it when `APK_PATH` or `APP_LOCATOR` is a different build. |

The third test needs the virtual service `drivelink-proto` to run, because the debug build calls it.

## Run

```sh
./gradlew :app:assembleDebug                 # from the repo root
cd perfecto/appium
npm install
npm run typecheck                            # no device time
npm test                                     # one device session
PERFECTO_DEVICE=any npm test                 # any available Android device
```

To choose a device, use the Perfecto MCP: `perfecto_devices list_real_devices`. To see the result after a run, use `perfecto_execution list_report_executions` with the report name `DriveLink APK install and sign-in`.
