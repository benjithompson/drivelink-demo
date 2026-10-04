import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import type { AndroidDevice } from '@playwright/test';
import { test, expect } from '../setup-android';

const PKG = 'com.drivelink.demo';
const ACTIVITY = `${PKG}/.MainActivity`;
const PIN = '1234';

/** Runs an adb shell command on the device and returns its output as text. */
async function sh(device: AndroidDevice, command: string): Promise<string> {
  return (await device.shell(command)).toString('utf8').trim();
}

test.describe.configure({ mode: 'serial' });

test.describe('DriveLink APK on a Perfecto Android device', () => {
  test.beforeAll(async ({ device }) => {
    // A fresh install: remove an earlier copy. "Failure [not installed ...]" is fine.
    await sh(device, `pm uninstall ${PKG}`);
  });

  test.afterAll(async ({ device }) => {
    await sh(device, `pm uninstall ${PKG}`);
  });

  test('installs the APK', async ({ device, settings }, testInfo) => {
    testInfo.annotations.push({ type: 'device', description: `${await device.model()} (${device.serial()})` });

    expect(await sh(device, `pm list packages ${PKG}`), 'not installed before the test').not.toMatch(
      new RegExp(`^package:${PKG}$`, 'm'),
    );

    await device.installApk(settings.apk);

    expect(await sh(device, `pm list packages ${PKG}`)).toMatch(new RegExp(`^package:${PKG}$`, 'm'));

    // The installed base.apk has the same bytes as the local APK.
    const basePath = (await sh(device, `pm path ${PKG}`))
      .split('\n')
      .map((l) => l.replace(/^package:/, '').trim())
      .find((p) => p.endsWith('/base.apk'));
    expect(basePath, 'pm path shows base.apk').toBeTruthy();
    const deviceSha = (await sh(device, `sha256sum ${basePath}`)).split(/\s+/)[0];
    const localSha = createHash('sha256').update(readFileSync(settings.apk)).digest('hex');
    expect(deviceSha).toBe(localSha);

    // The launcher intent resolves to the main activity.
    const launcher = await sh(device, `cmd package resolve-activity --brief -c android.intent.category.LAUNCHER ${PKG}`);
    expect(launcher.split('\n').pop()).toBe(ACTIVITY);

    const versionName = (await sh(device, `dumpsys package ${PKG}`)).match(/versionName=(\S+)/)?.[1];
    expect(versionName, 'versionName in dumpsys').toBeTruthy();
    testInfo.annotations.push({ type: 'versionName', description: versionName! });
  });

  test('starts on the sign-in screen', async ({ device }) => {
    const start = await sh(device, `am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n ${ACTIVITY}`);
    expect(start).toContain('Status: ok');

    await device.wait({ pkg: PKG, res: 'screen_login' }, { timeout: 60_000 });
    await device.wait({ pkg: PKG, res: 'login_submit' });
  });

  test('signs in and shows the vehicle status from the virtual service', async ({ device }) => {
    // The demo credentials are prefilled.
    await device.tap({ pkg: PKG, res: 'login_submit' });
    await device.wait({ pkg: PKG, res: 'screen_pin_setup' }, { timeout: 60_000 });
    for (const _step of ['create', 'confirm']) {
      for (const digit of PIN) await device.tap({ pkg: PKG, res: `pin_key_${digit}` });
    }

    await device.wait({ pkg: PKG, res: 'screen_home' }, { timeout: 60_000 });
    // stat_level shows a value only after GET /status answers. Default scenario, demo EV: 72 %.
    await device.wait({ pkg: PKG, res: 'stat_level' }, { timeout: 60_000 });
    await expect
      .poll(async () => {
        const info = await device.info({ pkg: PKG, res: 'stat_level' });
        return `${info.text ?? ''} ${info.desc ?? ''}`;
      }, { timeout: 60_000 })
      .toMatch(/\b72\b/);
  });
});
