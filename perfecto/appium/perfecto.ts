import { existsSync, readFileSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { remote } from 'webdriverio';

/** The repo root (perfecto/appium/../..). */
export const ROOT = path.resolve(import.meta.dirname, '../..');

/** Default device: Google Pixel 9 Pro, Android 16, NA-US-BOS (see perfecto/README.md). */
export const DEFAULT_DEVICE = '48071FDAP003CY';

/** Repository item that the test uploads and installs. */
export const DEFAULT_LOCATOR = 'PRIVATE:drivelink/drivelink-demo-debug.apk';

export interface PerfectoSettings {
  token: string;
  /** Host name, for example demo.perfectomobile.com. */
  cloud: string;
  /** A Perfecto device ID, or undefined for any available Android device. */
  device?: string;
  apk: string;
  /** Perfecto repository locator of the APK. */
  locator: string;
  /** False when APP_LOCATOR names an item that is already in the repository. */
  upload: boolean;
  /** versionName that the installed app must report. */
  versionName: string;
}

/** Reads KEY from a properties file. Returns undefined when the file or the key is missing. */
function prop(file: string, key: string): string | undefined {
  if (!existsSync(file)) return undefined;
  const line = readFileSync(file, 'utf8')
    .split(/\r?\n/)
    .reverse()
    .find((l) => new RegExp(`^\\s*${key}\\s*=`).test(l));
  return line?.replace(/^[^=]*=\s*/, '').trim() || undefined;
}

/**
 * Settings, environment first, then the gitignored perfecto.properties (same rules as
 * scripts/perfecto-espresso.sh). Never log the token.
 *
 *   PERFECTO_SECURITY_TOKEN  required
 *   PERFECTO_CLOUD           default "demo"
 *   PERFECTO_DEVICE          device ID; default the Pixel 9 Pro; "any" = any available Android device
 *   APK_PATH                 default app/build/outputs/apk/debug/app-debug.apk
 *   APP_LOCATOR              an APK already in the Perfecto repository; skips the upload
 *   EXPECTED_VERSION         default: versionName in app/build.gradle.kts
 */
export function perfectoSettings(): PerfectoSettings {
  const props = path.join(ROOT, 'perfecto.properties');
  const token = process.env.PERFECTO_SECURITY_TOKEN || prop(props, 'PERFECTO_SECURITY_TOKEN');
  if (!token) {
    throw new Error('Set PERFECTO_SECURITY_TOKEN in the environment or in perfecto.properties (gitignored).');
  }
  let cloud = process.env.PERFECTO_CLOUD || prop(props, 'PERFECTO_CLOUD') || 'demo';
  if (!cloud.includes('.')) cloud = `${cloud}.perfectomobile.com`;
  const deviceSetting = process.env.PERFECTO_DEVICE || DEFAULT_DEVICE;
  const apk = path.resolve(ROOT, process.env.APK_PATH || 'app/build/outputs/apk/debug/app-debug.apk');
  const upload = !process.env.APP_LOCATOR;
  if (upload && !existsSync(apk)) {
    throw new Error(`APK not found: ${apk}. Run ./gradlew :app:assembleDebug, or set APK_PATH or APP_LOCATOR.`);
  }
  const gradle = readFileSync(path.join(ROOT, 'app/build.gradle.kts'), 'utf8');
  const versionName = process.env.EXPECTED_VERSION || gradle.match(/versionName\s*=\s*"([^"]+)"/)?.[1];
  if (!versionName) throw new Error('No versionName in app/build.gradle.kts. Set EXPECTED_VERSION.');
  return {
    token,
    cloud,
    device: deviceSetting === 'any' ? undefined : deviceSetting,
    apk,
    locator: process.env.APP_LOCATOR || DEFAULT_LOCATOR,
    upload,
    versionName,
  };
}

/** Replaces the token in a message, so an error never shows it. */
export function mask(text: string, token: string): string {
  return token ? text.split(token).join('<PERFECTO_SECURITY_TOKEN>') : text;
}

/** Uploads the APK to the Perfecto repository (REST API "Upload Item to Repository"), replacing an earlier copy. */
export async function uploadApk(s: PerfectoSettings): Promise<void> {
  const repoHost = s.cloud.replace(/^([^.]+)\.perfectomobile\.com$/, '$1.app.perfectomobile.com');
  const form = new FormData();
  form.append('inputStream', new Blob([await readFile(s.apk)], { type: 'application/octet-stream' }), path.basename(s.apk));
  form.append(
    'requestPart',
    new Blob(
      [
        JSON.stringify({
          artifactLocator: s.locator,
          artifactName: 'drivelink-demo-debug.apk',
          artifactType: 'ANDROID',
          override: true,
          tags: ['drivelink'],
        }),
      ],
      { type: 'application/json' },
    ),
  );
  const res = await fetch(`https://${repoHost}/repository/api/v1/artifacts`, {
    method: 'POST',
    headers: { 'Perfecto-Authorization': s.token },
    body: form,
  });
  if (!res.ok) {
    throw new Error(`Repository upload failed: HTTP ${res.status} ${mask(await res.text(), s.token).slice(0, 500)}`);
  }
}

/** Opens an Appium 2 (UiAutomator2) session on a Perfecto Android device. No app capability: the test installs the app. */
export async function openSession(s: PerfectoSettings): Promise<WebdriverIO.Browser> {
  try {
    return await remote({
      protocol: 'https',
      hostname: s.cloud,
      port: 443,
      path: '/nexperience/perfectomobile/wd/hub',
      // WebdriverIO logs the capabilities at "info", and they hold the token.
      logLevel: 'silent',
      connectionRetryTimeout: 300_000,
      connectionRetryCount: 1,
      capabilities: {
        platformName: 'Android',
        'appium:automationName': 'UiAutomator2',
        'appium:newCommandTimeout': 300,
        'perfecto:options': {
          securityToken: s.token,
          ...(s.device ? { deviceName: s.device } : {}),
          automationVersion: 'latest',
          scriptName: 'drivelink-appium-install',
          takesScreenshot: false,
          screenshotOnError: true,
        },
      } as WebdriverIO.Capabilities,
    });
  } catch (e) {
    throw new Error(mask(e instanceof Error ? e.message : String(e), s.token));
  }
}

/** Perfecto Smart Reporting through the driver (the commands that the Perfecto Reporting SDK sends). */
export class Report {
  private readonly driver: WebdriverIO.Browser;

  constructor(driver: WebdriverIO.Browser) {
    this.driver = driver;
  }

  private async send(command: string, params: Record<string, unknown>): Promise<void> {
    try {
      await this.driver.execute(`mobile:${command}`, params);
    } catch (e) {
      // A reporting error must not fail the test. Show it, so a broken report is visible.
      console.warn(`Smart Reporting ${command} failed: ${e instanceof Error ? e.message : String(e)}`);
    }
  }

  testStart(name: string, tags: string[]) {
    return this.send('test:start', {
      name,
      tags,
      jobName: 'drivelink-appium',
      jobNumber: Number(process.env.GITHUB_RUN_NUMBER || 0),
      jobBranch: process.env.GITHUB_REF_NAME || 'local',
      projectName: 'DriveLink Demo',
      projectVersion: '0.1',
    });
  }

  stepStart(name: string) {
    return this.send('step:start', { name });
  }

  stepEnd(message: string) {
    return this.send('step:end', { message });
  }

  testEnd(success: boolean, failureDescription?: string) {
    return this.send('test:end', success ? { success } : { success, failureDescription });
  }
}
