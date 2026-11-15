import fs from 'node:fs';
import { defineConfig, devices } from '@playwright/test';

const localChromiumPath = '/home/mahou-dev/.cache/ms-playwright/chromium-1248/chrome-linux64/chrome';

export default defineConfig({
  testDir: './e2e',
  timeout: 30000,
  fullyParallel: false,
  retries: 0,
  workers: 1,
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000',
    launchOptions: {
      ...( !process.env.CI && fs.existsSync(localChromiumPath)
        ? { executablePath: localChromiumPath }
        : {} ),
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
    },
    trace: 'off',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
