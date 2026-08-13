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
  projects: [
    // No `use` of its own: the default 1280x720, exactly what every spec here
    // has been running under.
    {
      name: 'desktop',
      testIgnore: '**/*.mobile.spec.js',
    },
    // A phone, for the specs that are about the phone layout. 390 px alone
    // satisfies the `max-width: 600px` arm of the autocomplete media query, so
    // the layout under test does not depend on pointer emulation behaving as
    // expected; hasTouch lights up the `pointer: coarse` and `hover: none`
    // arms too, the way a real phone would. isMobile and deviceScaleFactor are
    // left off on purpose — neither changes these media queries, and a 3x
    // scale would triple raster work under a spec that asserts on long tasks.
    // `dependencies` is not about ordering for its own sake: these specs
    // measure long tasks, and a long task is main-thread time, so a browser
    // sharing the machine with several others manufactures them. Measured
    // five runs each way: alone, zero long tasks every time; running five in
    // parallel, up to nine per run and one of 200 ms. Waiting for the desktop
    // project to finish costs nothing here and buys a quiet machine.
    // Iterating on one spec: `--project=mobile --no-deps`.
    {
      name: 'mobile',
      testMatch: '**/*.mobile.spec.js',
      dependencies: ['desktop'],
      use: {
        viewport: { width: 390, height: 844 },
        hasTouch: true,
      },
    },
  ],
});
