const { test, expect } = require('@playwright/test');
const shared = require('./service-worker.shared');

// The worker's update path (ADR-0017). The backend prepends SW_VERSION to
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

// The first load, marked before the worker claims it: the mark survives only
// if that claim did not reload the page.
const openControlled = (page) =>
  shared.openControlled(page, (p) => p.evaluate(() => { window.__firstLoad = true; }));

async function cacheBuckets(page) {
  return page.evaluate(() => caches.keys());
}

// A development build has the metrics globals; a release build has none.
async function developmentBuild(page) {
  return page.evaluate(() => typeof window.__metrics === 'function');
}

// A new build arriving: the check on return to the app fetches the real sw.js,
// which differs from test-v1, and it installs. Resolves once it waits. The
// install precaches the whole shell, which took longer than the default 5 s
// with the suite running in parallel.
async function newBuildWaiting(page) {
  await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
  await expect.poll(() => page.evaluate(async () => {
    const registration = await navigator.serviceWorker.getRegistration();
    return registration.waiting && registration.waiting.state;
  }), { timeout: 30000 }).toBe('installed');
}

// DevTools' «Update on reload» (#517): every load of a controlled page makes
// the browser install a worker once more and put it in charge at once, waiting
// skipped. The flag belongs to the storage partition, so one CDP session sets
// it for every page of the context.
async function updateOnReload(context, page) {
  const cdp = await context.newCDPSession(page);
  await cdp.send('ServiceWorker.enable');
  await cdp.send('ServiceWorker.setForceUpdateOnPageLoad', { forceUpdateOnPageLoad: true });
}

// Controller changes as the current document saw them, and whether it has
// started to reload: `beforeunload` fires when the reload is asked for, in the
// document that asks, long before the next one loads. The listeners come
// before the app's, and the app has nothing that waits before reloading, so
// by the time a change is counted a reload it caused would be marked.
const markReloads = (page) => page.addInitScript(() => {
  window.__controllerChanges = 0;
  window.__reloading = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => { window.__controllerChanges++; });
  addEventListener('beforeunload', () => { window.__reloading = true; });
});

// The page saw its controller change to a worker that is now activated, and
// did not start to reload for it.
async function stayedThroughTakeover(page) {
  await page.waitForFunction(() => window.__controllerChanges >= 1 &&
                                   navigator.serviceWorker.controller.state === 'activated');
  expect(await page.evaluate(() => window.__reloading)).toBe(false);
}

const updateControl = (page) => page.getByRole('button', { name: 'Обновить' });

test('a new build waits, nothing offers it, and a forced takeover reloads no page', async ({ context }) => {
  await registerWorkerVersion(context, 'test-v1');
  const pages = [];
  for (let i = 0; i < 3; i++) {
    const page = await context.newPage();
    await markReloads(page);
    await openControlled(page);
    pages.push(page);
  }
  // From here the registration's own fetch brings the real build too, as a
  // deployed one does: test-v1 was the build before it.
  await context.unrouteAll();
  const [first, ...others] = pages;
  await newBuildWaiting(first);
  for (const page of pages) await expect(updateControl(page)).toHaveCount(0);

  // DevTools forces a build in under the open pages: their controller
  // changes, and none of them reloads for it.
  await updateOnReload(context, first);
  await first.reload();
  for (const page of others) {
    await stayedThroughTakeover(page);
    await expect(updateControl(page)).toHaveCount(0);
  }
  expect(await first.evaluate(() => window.__reloading)).toBe(false);
});

test('the new build runs once every page of the app has closed', async ({ context }) => {
  await registerWorkerVersion(context, 'test-v1');
  const first = await context.newPage();
  await openControlled(first);
  await context.unrouteAll();
  await newBuildWaiting(first);

  // A page opened now gets the build already running.
  const second = await context.newPage();
  await second.goto('/');
  expect(await second.evaluate(async () => {
    const registration = await navigator.serviceWorker.getRegistration();
    return navigator.serviceWorker.controller.scriptURL === registration.active.scriptURL &&
           registration.active.state === 'activated' &&
           registration.waiting !== null;
  })).toBe(true);
  expect(await cacheBuckets(second)).toContain('test-v1');

  await first.close();
  await second.close();
  const next = await context.newPage();
  await openControlled(next);
  await expect.poll(() => cacheBuckets(next)).toHaveLength(1);
  expect(await cacheBuckets(next)).not.toContain('test-v1');
  expect(await next.evaluate(async () =>
    (await navigator.serviceWorker.getRegistration()).waiting)).toBeNull();
});

test('«Update on reload» and one reload give one load', async ({ context, page }) => {
  await openControlled(page);
  await updateOnReload(context, page);
  await markReloads(page);
  const loads = [];
  page.on('load', () => loads.push(Date.now()));

  await page.reload();
  // The same build, installed again, has taken this document over.
  await stayedThroughTakeover(page);
  expect(loads.length).toBe(1);
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
