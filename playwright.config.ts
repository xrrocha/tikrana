import { defineConfig, devices } from '@playwright/test';

const USE_KOTLIN = process.env.USE_KOTLIN === '1';
const HEADED = process.env.HEADED === '1';  // Show browser window: HEADED=1 bun run test:e2e

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,

  reporter: [
    ['html'],
    ['json', { outputFile: 'test-results/results.json' }],
  ],

  use: {
    baseURL: USE_KOTLIN
      ? 'http://localhost:7070'   // Kotlin excel2erp server (port from wb-server.yaml)
      : 'http://localhost:5173',  // Vite dev server (TypeScript)
    trace: 'on-first-retry',
    headless: !HEADED,            // Show browser when HEADED=1
    launchOptions: {
      slowMo: HEADED ? 300 : 0,   // Slow down for visual inspection
    },
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  // Conditionally start the appropriate server
  webServer: USE_KOTLIN
    ? {
        // Start Kotlin server (excel2erp)
        // Run from pedidos-sap dir so ./assets path resolves correctly
        command: 'cd /home/ricardo/Workspace/tikrana/local/repos/pedidos-sap && /home/ricardo/.sdkman/candidates/java/17.0.12-graal/bin/java -jar /home/ricardo/Workspace/tikrana/local/repos/excel2erp/build/libs/shadow-1.0-SNAPSHOT-all.jar wb-server.yaml',
        url: 'http://localhost:7070',  // Port from wb-server.yaml
        reuseExistingServer: !process.env.CI,
        timeout: 30000,
      }
    : {
        // Start TypeScript dev server
        command: 'bun run dev',
        url: 'http://localhost:5173',
        reuseExistingServer: !process.env.CI,
        timeout: 10000,
      },
});
