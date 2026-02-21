import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  globalTimeout: 60_000,
  use: {
    baseURL: 'http://localhost:3000',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
