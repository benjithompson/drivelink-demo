import { defineConfig } from '@playwright/test';

// One Perfecto device session per run: one worker, the tests in order.
export default defineConfig({
  testDir: './tests',
  workers: 1,
  fullyParallel: false,
  retries: 0,
  // An APK upload and install over the Perfecto WebSocket can take a few minutes.
  timeout: 5 * 60_000,
  expect: { timeout: 30_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  // Traces would hold the WebSocket URL, which carries the security token.
  use: { trace: 'off' },
});
