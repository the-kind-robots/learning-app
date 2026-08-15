const { test, expect } = require('@playwright/test');

// `clj->js` keeps ClojureScript keyword names as written, so these arrive as
// kebab-case. Reading them as camelCase silently yields undefined, which has
// already cost one debugging round.
const readMetrics = (page) => page.evaluate(() => window.__metrics());
const readStorage = (page) => page.evaluate(() => window.__storage());

// The instrumentation installs with the render component, which the runtime
// starts asynchronously. Waiting for a rendered element is what proves it is
// up; a bare goto is not enough.
async function waitForApp(page) {
  await page.goto('/home');
  await expect(page.getByLabel('Слово (немецкий)')).toBeVisible();
  await page.waitForFunction(() => typeof window.__metrics === 'function');
}

test('the standard web metrics are reported under their own names', async ({ page }) => {
  await waitForApp(page);

  // The library is fetched as a script, so it lands a beat after install.
  await page.waitForFunction(() => {
    const vitals = window.__metrics()['web-vitals'];
    return vitals && Object.keys(vitals).length > 0;
  });

  // One interaction, so INP has something to report.
  await page.getByLabel('Слово (немецкий)').click();
  await page.getByLabel('Слово (немецкий)').fill('Haus');

  const vitals = (await readMetrics(page))['web-vitals'];

  // TTFB and FCP always resolve on a loaded page; the rest depend on what the
  // run happened to do, so only these two are asserted as present.
  expect(vitals.ttfb).toBeDefined();
  expect(vitals.ttfb.value).toBeGreaterThan(0);
  expect(vitals.fcp).toBeDefined();
  expect(vitals.fcp.value).toBeGreaterThan(0);

  // A rating is what separates a number from a judgement.
  expect(['good', 'needs-improvement', 'poor']).toContain(vitals.fcp.rating);
});

test('long frames are recorded with attribution and no long-task list remains', async ({ page }) => {
  await waitForApp(page);

  const metrics = await readMetrics(page);

  expect(metrics['long-frames']).toBeDefined();
  expect(Array.isArray(metrics['long-frames'])).toBe(true);
  expect(metrics['long-tasks']).toBeUndefined();
  expect(metrics['slow-interactions']).toBeUndefined();

  // Block the main thread long enough to produce a long animation frame, then
  // let the browser deliver the entry.
  await page.evaluate(() => {
    const until = performance.now() + 200;
    while (performance.now() < until) { /* hold the thread */ }
  });
  await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => setTimeout(r, 50))));

  const frames = (await readMetrics(page))['long-frames'];
  expect(frames.length).toBeGreaterThan(0);

  const blocking = frames.map((f) => f['blocking-duration']);
  expect(Math.max(...blocking)).toBeGreaterThan(0);

  // Attribution is the whole reason for the switch: an entry must be able to
  // name the script it blames.
  const scripts = frames.flatMap((f) => f.scripts);
  expect(scripts.every((s) => 'source-url' in s && 'invoker' in s)).toBe(true);
});

test('dictionary readiness and its phases are measured', async ({ page }) => {
  await waitForApp(page);

  // Readiness is reported by the worker, which starts alongside the app.
  await page.waitForFunction(
    () => window.__metrics().dictionary['ready-ms'] !== undefined,
    null,
    { timeout: 60000 }
  );

  const dictionary = (await readMetrics(page)).dictionary;
  expect(dictionary['ready-ms']).toBeGreaterThan(0);

  // The measure exists on the document's timeline, not only in our map.
  const measures = await page.evaluate(() =>
    performance.getEntriesByName('dictionary-ready', 'measure').length
  );
  expect(measures).toBe(1);

  // The worker's own phase timings ride along.
  const phases = dictionary.phases.map((p) => p.phase);
  expect(phases).toContain('wasm-init');
  expect(phases).toContain('db-open');
  expect(dictionary.phases.every((p) => typeof p['duration-ms'] === 'number')).toBe(true);
});

test('a warm start is distinguishable from a cold one', async ({ page }) => {
  await waitForApp(page);
  await page.waitForFunction(
    () => window.__metrics().dictionary['ready-ms'] !== undefined,
    null,
    { timeout: 60000 }
  );
  const cold = (await readMetrics(page)).dictionary;
  expect(cold.phases.map((p) => p.phase)).toContain('download');

  // The same context keeps OPFS, so the reload finds the file already there.
  await page.reload();
  await expect(page.getByLabel('Слово (немецкий)')).toBeVisible();
  await page.waitForFunction(
    () => typeof window.__metrics === 'function' &&
          window.__metrics().dictionary['ready-ms'] !== undefined,
    null,
    { timeout: 60000 }
  );
  const warm = (await readMetrics(page)).dictionary;

  // Readiness is measured on the main thread, so it survives where the
  // worker's own telemetry does not: once the service worker controls the
  // page it serves the worker script by bare path and the `telemetry=1`
  // parameter is lost with the query string (#299). Hence no assertion on
  // `cache-hit` here — only on the duration, which is the point anyway.
  expect(warm['ready-ms']).toBeGreaterThan(0);
  expect(warm['ready-ms']).toBeLessThan(cold['ready-ms']);
});

test('storage is a separate asynchronous read', async ({ page }) => {
  await waitForApp(page);

  // The synchronous entry point stays synchronous.
  const isPromise = await page.evaluate(
    () => window.__metrics() instanceof Promise
  );
  expect(isPromise).toBe(false);

  const storage = await readStorage(page);
  expect(storage.usage).toBeGreaterThan(0);
  expect(storage.quota).toBeGreaterThan(storage.usage);
});
