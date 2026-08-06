const { defineConfig } = require('@playwright/test');

// Headless suite for WSL. Runs against a locally started backend
// (see test/browser/README.md); never against the shared dev stand.
module.exports = defineConfig({
  testDir: './test/browser',
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:8301',
    channel: 'chrome',
    headless: true,
    trace: 'retain-on-failure',
  },
});
