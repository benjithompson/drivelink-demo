# DriveLink: APK install test with Playwright on Perfecto

This test installs the DriveLink APK on a real Android device in the Perfecto cloud and checks that the app works. It uses the Playwright Android API (`_android.connect`) over the Perfecto WebSocket. Perfecto marks Playwright for Android as experimental. It supports only the Node.js SDK and Playwright 1.51–1.60. This project pins 1.60.0.

## What the test checks

The three tests run in order in one device session.

| Test | Checks |
| --- | --- |
| installs the APK | The app is not on the device before the install. After `installApk`, `pm list packages` shows `com.drivelink.demo`. The SHA-256 of the installed `base.apk` is equal to the SHA-256 of the local APK. The launcher intent resolves to `com.drivelink.demo/.MainActivity`. `dumpsys package` shows a `versionName`. |
| starts on the sign-in screen | `am start -W` with the launcher intent returns `Status: ok`. The screens `screen_login` and `login_submit` show. |
| signs in and shows the vehicle status from the virtual service | Sign-in with the prefilled demo user, PIN setup (1234 twice), then Home. `stat_level` shows 72 (the demo EV, scenario `default`). This value comes from `GET /status` on the virtual service, so the app reached the network. |

Before the tests, the test removes an earlier copy of the app. After the tests, it removes the app again.

The selectors use the Compose test tags, which the app exposes as resource IDs (`testTagsAsResourceId`).

## Settings

The settings use the same sources as `scripts/perfecto-espresso.sh`: first the environment, then `perfecto.properties` in the repo root (gitignored). The test never prints the token.

| Setting | Default | Note |
| --- | --- | --- |
| `PERFECTO_SECURITY_TOKEN` | none | Required. Perfecto UI: profile → Security token. |
| `PERFECTO_CLOUD` | `demo` | `demo.perfectomobile.com` |
| `PERFECTO_DEVICE` | `48071FDAP003CY` (Pixel 9 Pro, Android 16) | A device ID, or `any` for any available Android device. |
| `APK_PATH` | `app/build/outputs/apk/debug/app-debug.apk` | Relative to the repo root. |

The third test needs the virtual service `drivelink-proto` to run, because the debug build calls it.

## Run

```sh
./gradlew :app:assembleDebug                 # from the repo root
cd perfecto/playwright
npm install
npm run list                                 # list the tests (no device time)
npm test                                     # one device session, about 2-5 min
PERFECTO_DEVICE=any npm test                 # any available Android device
```

The HTML report is in `playwright-report/` (`npx playwright show-report`). Traces are off, because a trace would hold the WebSocket URL with the token.

## Limits

- Smart Reporting: the Perfecto docs add test steps through `page.evaluate('perfecto:report:...')`. A native-app test has no browser page, so this test sends only the job capabilities (`report.jobName` = `drivelink-playwright`).
- `installApk` and `shell` go through the Perfecto WebSocket. The Perfecto docs show only browser tests on Android, so these calls depend on the Perfecto adb proxy.
