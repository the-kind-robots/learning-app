const { test, expect } = require('@playwright/test');

// The worker's update path (ADR-0014). The backend prepends SW_VERSION to
// sw.js from a hash of resources/public, and a build "changes" here by
// handing the registration a script with another version line. Only the
// registration's fetch can be rewritten: it passes through context.route
// (page.route never sees it — the real hash came through, measured), while
// the fetch behind registration.update() bypasses routing altogether and
// brings the backend's real build. That is the changed sw.js. Everything
// else about the worker — precache, waiting, activate, claim — runs for real
// in the browser.
async function registerWorkerVersion(context, version) {
  await context.route('**/js/app/sw.js', async (route) => {
    const response = await route.fetch();
    const body = (await response.text()).replace(/^const SW_VERSION="[^"]*"/, `const SW_VERSION="${version}"`);
    await route.fulfill({ response, body });
  });
}

// The first load of a fresh context: the worker installs, activates and
// claims the page. Resolves once the page is controlled.
async function openControlled(page) {
  await page.goto('/');
  await page.evaluate(() => { window.__firstLoad = true; });
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null, null, { timeout: 30000 });
}

// The worker's states as the page saw them, kept in localStorage because the
// page that records them is about to reload.
async function recordWorkerStates(page) {
  await page.evaluate(async () => {
    localStorage.setItem('sw-states', '[]');
    const registration = await navigator.serviceWorker.getRegistration();
    registration.addEventListener('updatefound', () => {
      const worker = registration.installing;
      worker.addEventListener('statechange', () => {
        const states = JSON.parse(localStorage.getItem('sw-states'));
        states.push(worker.state);
        localStorage.setItem('sw-states', JSON.stringify(states));
      });
    });
  });
}

async function cacheBuckets(page) {
  return page.evaluate(() => caches.keys());
}

// A development build has the metrics globals; a release build has none.
async function developmentBuild(page) {
  return page.evaluate(() => typeof window.__metrics === 'function');
}

test('a changed worker waits, and the page reloads once onto it after the tap', async ({ context, page }) => {
  const loads = [];
  page.on('load', () => loads.push(Date.now()));
  await registerWorkerVersion(context, 'test-v1');
  await openControlled(page);

  // The first worker claimed a page that started uncontrolled: no reload.
  expect(await page.evaluate(() => window.__firstLoad)).toBe(true);
  expect(await cacheBuckets(page)).toEqual(['test-v1']);

  await recordWorkerStates(page);
  // Which build ran is part of the evidence; the update path no longer
  // differs between them, so nothing below branches on it.
  const dev = await developmentBuild(page);
  console.log(`service-worker-update: ${dev ? 'development' : 'release'} build`);

  // Nothing is waiting yet, so nothing is offered.
  const update = page.getByRole('button', { name: 'Обновить' });
  await expect(update).toHaveCount(0);

  // Coming back to the app is what asks for the update check; the check
  // brings the real build, which differs from test-v1.
  const reloaded = page.waitForEvent('load');
  await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));

  // The new worker is offered, never taken by itself — in this build too:
  // it waits, no page has reloaded, and the tap is what activates it.
  await expect(update).toBeVisible();
  expect(loads.length).toBe(1);
  await update.click();
  await reloaded;

  await page.waitForFunction(() => navigator.serviceWorker.controller !== null);
  await expect.poll(async () => (await cacheBuckets(page)).length).toBe(1);
  expect(await cacheBuckets(page)).not.toEqual(['test-v1']);
  const states = await page.evaluate(() => JSON.parse(localStorage.getItem('sw-states')));
  expect(states).toEqual(['installed', 'activating', 'activated']);
  expect(await page.evaluate(() => window.__firstLoad)).toBeUndefined();

  // One reload, not two: the bucket assertion above already let a second
  // controller change — had there been one — fire, so this is not a race.
  expect(loads.length).toBe(2);
});

// The build mark and the trace export exist in a development build only; on a
// release build these three skip.
const buildMark = (page) => page.getByRole('button', { name: 'Перезагрузить сборку' });
const traceExport = (page) => page.getByRole('button', { name: 'Экспортировать трассу' });

test('a tap on the build mark reloads onto a new build', async ({ context, page }) => {
  await registerWorkerVersion(context, 'test-v1');
  await openControlled(page);
  test.skip(!(await developmentBuild(page)), 'the build mark exists in a development build only');

  // The update check behind the tap brings the real build.
  const reloaded = page.waitForEvent('load');
  await buildMark(page).click();
  await reloaded;

  await page.waitForFunction(() => navigator.serviceWorker.controller !== null);
  await expect.poll(async () => (await cacheBuckets(page)).length).toBe(1);
  expect(await cacheBuckets(page)).not.toEqual(['test-v1']);
  expect(await page.evaluate(() => window.__firstLoad)).toBeUndefined();
});

test('a tap on the build mark with nothing new reloads the page', async ({ page }) => {
  // No rewritten registration: the real build is registered, and the update
  // check finds it unchanged.
  await openControlled(page);
  test.skip(!(await developmentBuild(page)), 'the build mark exists in a development build only');
  const [bucket] = await cacheBuckets(page);

  const reloaded = page.waitForEvent('load');
  await buildMark(page).click();
  await reloaded;

  expect(await page.evaluate(() => window.__firstLoad)).toBeUndefined();
  await expect.poll(() => cacheBuckets(page)).toEqual([bucket]);
});

test('a tap on the trace export in the actions row exports and does not reload', async ({ page }) => {
  // Headless Chrome has no share sheet; the clipboard is stubbed so the
  // export takes its second path and announces itself in an alert. The alert
  // is recorded rather than shown: a blocking dialog raised from inside the
  // tap deadlocks the after-action snapshot of `trace: retain-on-failure` —
  // measured, the test hangs to its timeout with tracing on and passes with
  // `--trace off` — and a dialog listener does not free it.
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'clipboard', { value: { writeText: () => Promise.resolve() } });
    window.__alerts = [];
    window.alert = (message) => { window.__alerts.push(message); };
  });
  await openControlled(page);
  test.skip(!(await developmentBuild(page)), 'the trace export exists in a development build only');

  await traceExport(page).click();
  await expect.poll(() => page.evaluate(() => window.__alerts)).toEqual(['Трасса скопирована']);
  // The export is its own control, so nothing reloaded the page out from
  // under it.
  expect(await page.evaluate(() => window.__firstLoad)).toBe(true);
});
