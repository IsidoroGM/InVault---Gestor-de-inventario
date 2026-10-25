import { defineConfig, devices } from '@playwright/test';
import { join } from 'node:path';

const baseURL = process.env.INVAULT_TEST_URL ?? 'http://localhost:1';
const target = new URL(baseURL);
if (target.protocol !== 'http:' || !['localhost', '127.0.0.1', '[::1]'].includes(target.hostname))
  throw new Error('Browser verification only accepts a local disposable stack.');
const reports = process.env.INVAULT_QUALITY_REPORT ?? 'test-results';
export default defineConfig({
  testDir: './e2e',
  workers: 1,
  fullyParallel: false,
  retries: 0,
  maxFailures: 3,
  timeout: 60000,
  expect: { timeout: 12000 },
  forbidOnly: !!process.env.CI,
  outputDir: join(reports, 'browser-output'),
  reporter: [['list'], ['junit', { outputFile: join(reports, 'browser-junit.xml') }]],
  use: {
    baseURL,
    actionTimeout: 10000,
    navigationTimeout: 20000,
    serviceWorkers: 'block',
    trace: 'off',
    video: 'off',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop-chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'android-chromium', use: { ...devices['Pixel 5'] } },
    { name: 'ipad-webkit', use: { ...devices['iPad Pro 11'] } },
  ],
});
