import { test as base, _android, type AndroidDevice } from '@playwright/test';
import { mask, perfectoSettings, type PerfectoSettings } from './perfecto';

/**
 * Playwright fixture: one Perfecto Android device for the worker.
 * Perfecto docs: "Set up Playwright with Perfecto – Android" (experimental, Node.js only).
 */
export const test = base.extend<{}, { settings: PerfectoSettings; device: AndroidDevice }>({
  settings: [async ({}, use) => use(perfectoSettings()), { scope: 'worker' }],

  device: [
    async ({ settings }, use) => {
      const caps = {
        securityToken: settings.token,
        ...(settings.device ? { deviceName: settings.device } : { platformName: 'Android' }),
        'report.jobName': 'drivelink-playwright',
        'report.jobNumber': Number(process.env.GITHUB_RUN_NUMBER || 0),
        'report.jobBranch': process.env.GITHUB_REF_NAME || 'local',
      };
      const endpoint = `wss://${settings.cloud}/websocket?${encodeURIComponent(JSON.stringify(caps))}`;
      let device: AndroidDevice;
      try {
        device = await _android.connect(endpoint, { timeout: 180_000 });
      } catch (e) {
        // The error text can hold the endpoint, and the endpoint holds the token.
        const message = e instanceof Error ? e.message : String(e);
        throw new Error(mask(mask(message, encodeURIComponent(settings.token)), settings.token));
      }
      try {
        await use(device);
      } finally {
        await device.close();
      }
    },
    { scope: 'worker', timeout: 240_000 },
  ],
});

export { expect } from '@playwright/test';
