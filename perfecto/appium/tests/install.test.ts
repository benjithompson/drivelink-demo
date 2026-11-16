import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { mask, openSession, perfectoSettings, Report, uploadApk, type PerfectoSettings } from '../perfecto.ts';

const PKG = 'com.drivelink.demo';
const PIN = '1234';
const SCREEN_TIMEOUT = 60_000;

let s: PerfectoSettings;
let driver: WebdriverIO.Browser | undefined;
let report: Report | undefined;
const failures: string[] = [];

/** An element by its Compose test tag (the app exposes test tags as resource IDs). */
const byTag = (tag: string) => driver!.$(`android=new UiSelector().resourceId("${tag}")`);

async function waitForTag(tag: string, timeout = SCREEN_TIMEOUT) {
  const el = byTag(tag);
  await el.waitForDisplayed({ timeout, timeoutMsg: `"${tag}" did not show in ${timeout / 1000} s` });
  return el;
}

const tap = async (tag: string) => (await waitForTag(tag)).click();

const isInstalled = () => driver!.execute('mobile: isAppInstalled', { appId: PKG }) as Promise<boolean>;

/** Runs one test as one Smart Reporting step, and records a failure for the test end. */
async function step(name: string, body: () => Promise<void>) {
  assert.ok(driver, 'no Perfecto session');
  await report!.stepStart(name);
  try {
    await body();
    await report!.stepEnd(`${name}: passed`);
  } catch (e) {
    const message = mask(e instanceof Error ? e.message : String(e), s.token);
    failures.push(`${name}: ${message}`);
    await report!.stepEnd(`${name}: failed`);
    throw new Error(message, { cause: e });
  }
}

describe('DriveLink APK on a Perfecto Android device', { timeout: 15 * 60_000 }, () => {
  before(async () => {
    s = perfectoSettings();
    if (s.upload) await uploadApk(s);
    driver = await openSession(s);
    report = new Report(driver);
    await report.testStart('DriveLink APK install and sign-in', ['drivelink', 'appium', 'install']);
    const caps = driver.capabilities as Record<string, unknown>;
    console.log(`Device: ${caps['deviceName'] ?? caps['appium:deviceName'] ?? 'unknown'} (Android ${caps['platformVersion'] ?? '?'})`);
  }, { timeout: 8 * 60_000 });

  after(async () => {
    if (!driver) return;
    try {
      await driver.execute('mobile:application:uninstall', { identifier: PKG });
      await report!.testEnd(failures.length === 0, failures.join('\n'));
    } finally {
      await driver.deleteSession();
    }
  });

  test('installs the APK from the Perfecto repository', { timeout: 6 * 60_000 }, () =>
    step('Install the APK', async () => {
      // Start from a device without the app.
      await driver!.execute('mobile:application:uninstall', { identifier: PKG });
      assert.equal(await isInstalled(), false, 'the app is on the device before the install');

      await driver!.execute('mobile:application:install', { file: s.locator, instrument: 'noinstrument', dataReset: 'reset' });

      assert.equal(await isInstalled(), true, 'the app is not on the device after the install');
    }));

  test('opens the app on the sign-in screen', { timeout: 3 * 60_000 }, () =>
    step('Open the app', async () => {
      await driver!.execute('mobile:application:open', { identifier: PKG, timeout: 30 });
      await waitForTag('screen_login');
      await waitForTag('login_submit');

      assert.equal(await driver!.getCurrentPackage(), PKG);
      const version = await driver!.execute('mobile:application:info', { property: 'version' });
      assert.equal(String(version), s.versionName, 'installed versionName');
    }));

  test('signs in and shows the vehicle status from the virtual service', { timeout: 4 * 60_000 }, () =>
    step('Sign in and see Home', async () => {
      // The demo credentials are prefilled.
      await tap('login_submit');
      await waitForTag('screen_pin_setup');
      for (const _round of ['create', 'confirm']) {
        for (const digit of PIN) await tap(`pin_key_${digit}`);
      }

      await waitForTag('screen_home');
      // stat_level shows a value only after GET /status answers. Default scenario, demo EV: 72 %.
      await waitForTag('stat_level');
      let seen = '';
      await driver!.waitUntil(
        async () => {
          const texts = await driver!
            .$$(`//*[@resource-id="stat_level"]/descendant-or-self::*`)
            .map(async (n) => `${await n.getText()} ${(await n.getAttribute('content-desc')) ?? ''}`);
          seen = texts.join(' ').replace(/\s+/g, ' ').trim();
          return /\b72\b/.test(seen);
        },
        { timeout: SCREEN_TIMEOUT, interval: 2_000, timeoutMsg: 'stat_level did not show 72' },
      ).catch((e: Error) => {
        throw new Error(`${e.message}; stat_level shows "${seen}"`);
      });
    }));
});
