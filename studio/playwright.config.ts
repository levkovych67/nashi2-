import { defineConfig, devices } from '@playwright/test'

const host = 'http://127.0.0.1:4174'

export default defineConfig({
  testDir: './tests/e2e',
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: host, trace: 'retain-on-failure' },
  projects: [
    { name: 'mobile', use: { ...devices['iPhone 13'] } },
    {
      name: 'desktop',
      use: { browserName: 'chromium', viewport: { width: 1440, height: 900 } },
    },
  ],
  webServer: {
    command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 4174 --strictPort',
    url: host,
    reuseExistingServer: !process.env.CI,
  },
})
