const { test, expect } = require('@playwright/test');
const { openControlled, readMetrics, dictionaryReady } = require('./service-worker.shared');

// What the worker's cache keeps and what it hands back (#315, #299). Every
// test here starts with the page under a controller.

const ASSET = '/css/styles.css';
const MANIFEST = '/dictionary/manifest';

// A fetch from the page, so it passes through the worker.
const statusOf = (page, path) => page.evaluate(async (p) => (await fetch(p)).status, path);

// What the bucket holds for a path, read straight from the cache.
const cachedEntry = (page, path) => page.evaluate(async (p) => {
  const response = await caches.match(p);
  return response && { status: response.status, body: await response.text() };
}, path);

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
  await context.route(`**${ASSET}`, (route) => route.fulfill({ status: 500, body: 'boom' }), { times: 1 });

  expect(await statusOf(page, ASSET)).toBe(500);
  // Had the 500 been kept, it would answer this one too.
  expect(await statusOf(page, ASSET)).toBe(200);
  // The worker stores without holding the page's answer back.
  await expect.poll(async () => (await cachedEntry(page, ASSET))?.status).toBe(200);
});

test('a failed manifest leaves the cached one in place', async ({ context, page }) => {
  await openControlled(page);
  // Put a known copy in the bucket through the worker itself.
  expect(await statusOf(page, MANIFEST)).toBe(200);
  await expect.poll(async () => (await cachedEntry(page, MANIFEST))?.body).toContain('hash');
  const before = await cachedEntry(page, MANIFEST);

  await context.route(`**${MANIFEST}`, (route) => route.fulfill({ status: 500, body: 'boom' }));
  expect(await statusOf(page, MANIFEST)).toBe(500);
  expect(await cachedEntry(page, MANIFEST)).toEqual(before);
});

const phasesSeen = async (page) =>
  ((await readMetrics(page)).dictionary.phases || []).map((p) => p.phase);

test('the dictionary worker keeps its query string under a controlled page', async ({ context, page }) => {
  await openControlled(page);
  // The first load imports the dictionary into OPFS; the reload finds it.
  await dictionaryReady(page);
  // The first load's worker was fetched before any controller existed; the
  // reload's comes out of the worker's cache.
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
