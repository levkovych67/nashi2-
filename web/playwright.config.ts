import { defineConfig, devices } from '@playwright/test'

const host = 'http://127.0.0.1:5174'

// Both projects run on Chromium; PLAYWRIGHT_CHROMIUM_EXECUTABLE points at a local browser when set.
const launchOptions = { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined }

export default defineConfig({
  testDir: './e2e',
  reporter: [['list']],
  use: { baseURL: host, trace: 'retain-on-failure' },
  projects: [
    { name: 'mobile', use: { ...devices['iPhone 13'], browserName: 'chromium', launchOptions } },
    {
      name: 'desktop',
      use: { browserName: 'chromium', viewport: { width: 1440, height: 900 }, launchOptions },
    },
  ],
  webServer: {
    command: 'npm run dev -w web',
    cwd: '..',
    url: host,
    reuseExistingServer: !process.env.CI,
    env: { VITE_API_MOCK: '1', VITE_MAP_STYLE: 'blank' },
  },
})
