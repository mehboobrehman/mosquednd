import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for MosqueDND E2E tests.
 * Includes failure screenshots, video recording, and trace logs on failure.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false, // Reduced parallelization to 1 for reliability
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1, 
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['json', { outputFile: 'playwright-report/results.json' }],
    ['list']
  ],
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npx http-server ./e2e/harness -p 3000 --silent',
    port: 3000,
    reuseExistingServer: false,
  },
});
