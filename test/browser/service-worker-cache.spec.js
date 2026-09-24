const { test, expect } = require('@playwright/test');

// What the worker's cache keeps and what it hands back (#315, #299). Both
// tests need the page under a controller first: before that nothing passes
// through the worker's fetch handler at all.
async function openControlled(page) {
  await page.goto('/');
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null, null, { timeout: 30000 });
}

const ASSET = '/css/styles.css';

test('an asset that failed once is fetched again, not served from cache', async ({ context, page }) => {
  await openControlled(page);

  // Precached at install, so it has to leave the bucket before the worker
  // will go to the network for it.
  await page.evaluate(async (path) => {
    const [bucket] = await caches.keys();
    await (await caches.open(bucket)).delete(path);
  }, ASSET);

  // The worker's own fetch passes through context routing (the registration
  // fetch in service-worker-update.spec.js does too). One failure, then the
  // real file.
  let failed = false;
  await context.route(`**${ASSET}`, async (route) => {
    if (failed) return route.fallback();
    failed = true;
    await route.fulfill({ status: 500, body: 'boom' });
  });

  const statusOf = (path) => page.evaluate(async (p) => (await fetch(p)).status, path);
  expect(await statusOf(ASSET)).toBe(500);
  expect(failed).toBe(true);

  // Had the 500 been kept, it would answer this one too.
  expect(await statusOf(ASSET)).toBe(200);
  const cached = await page.evaluate(async (path) => {
    const response = await caches.match(path);
    return response && response.status;
  }, ASSET);
  expect(cached).toBe(200);
});

test('a failed manifest leaves the cached one in place', async ({ context, page }) => {
  await openControlled(page);
  const manifest = '/dictionary/manifest';
  // Put a known copy in the bucket through the worker itself.
  expect(await page.evaluate(async (p) => (await fetch(p)).status, manifest)).toBe(200);
  const cachedBody = (p) => page.evaluate(async (path) => {
    const response = await caches.match(path);
    return response && response.text();
  }, p);
  const before = await cachedBody(manifest);
  expect(before).toContain('hash');

  await context.route(`**${manifest}`, (route) => route.fulfill({ status: 500, body: 'boom' }));
  expect(await page.evaluate(async (p) => (await fetch(p)).status, manifest)).toBe(500);
  expect(await cachedBody(manifest)).toBe(before);
});

const dictionaryReady =(page) => page.waitForFunction(
  () => typeof window.__metrics === 'function' &&
        window.__metrics().dictionary['ready-ms'] !== undefined,
  null,
  { timeout: 60000 }
);

const phasesSeen = (page) => page.evaluate(() =>
  (window.__metrics().dictionary.phases || []).map((p) => p.phase));

test('the dictionary worker keeps its query string under a controlled page', async ({ context, page }) => {
  await openControlled(page);
  // The first load imports the dictionary into OPFS; the reload finds it.
  await dictionaryReady(page);
  // The first load's worker was fetched before any controller existed; the
  // reload's comes through the worker's cache, found by bare path.
  await page.reload();
  expect(await page.evaluate(() => navigator.serviceWorker.controller !== null)).toBe(true);

  // `telemetry=1` rides in the query string, and without it the worker emits
  // no phases. `cache-hit` is the phase of a start that finds the file in
  // OPFS already.
  await dictionaryReady(page);
  expect(await phasesSeen(page)).toContain('cache-hit');

  // Offline, everything the worker needs comes from the cache: the shell,
  // the worker script, the last manifest.
  await context.setOffline(true);
  await page.reload();
  await dictionaryReady(page);
  expect(await phasesSeen(page)).toContain('cache-hit');
});
