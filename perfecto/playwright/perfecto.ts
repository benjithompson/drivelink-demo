import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

/** The repo root (perfecto/playwright/../..). */
export const ROOT = path.resolve(__dirname, '../..');

/** Default device: Google Pixel 9 Pro, Android 16, NA-US-BOS (see perfecto/README.md). */
export const DEFAULT_DEVICE = '48071FDAP003CY';

export interface PerfectoSettings {
  token: string;
  /** Host name, for example demo.perfectomobile.com. */
  cloud: string;
  /** A Perfecto device ID, or undefined for any available Android device. */
  device?: string;
  apk: string;
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
  if (!existsSync(apk)) {
    throw new Error(`APK not found: ${apk}. Run ./gradlew :app:assembleDebug or set APK_PATH.`);
  }
  return { token, cloud, device: deviceSetting === 'any' ? undefined : deviceSetting, apk };
}

/** Replaces the token in a message, so an error never shows it. */
export function mask(text: string, token: string): string {
  return token ? text.split(token).join('<PERFECTO_SECURITY_TOKEN>') : text;
}
