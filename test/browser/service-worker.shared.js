// Page states the service worker and metrics specs wait for. Shared by
// service-worker-update, service-worker-cache and metrics.

// The first load of a fresh context: the worker installs, activates and
// claims the page. Resolves once the page is controlled — before that nothing
// passes through the worker's fetch handler. `beforeClaim`, if given, runs on
// the loaded page while it may still be uncontrolled.
async function openControlled(page, beforeClaim) {
  await page.goto('/');
  if (beforeClaim) await beforeClaim(page);
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null, null, { timeout: 30000 });
}

// `clj->js` keeps ClojureScript keyword names as written, so these arrive as
// kebab-case. Reading them as camelCase silently yields undefined, which has
// already cost one debugging round.
const readMetrics = (page) => page.evaluate(() => window.__metrics());

// Readiness is reported by the dictionary worker, which starts alongside the
// app; after a reload the metrics global itself has to come back first.
const dictionaryReady = (page) => page.waitForFunction(
  () => typeof window.__metrics === 'function' &&
        window.__metrics().dictionary['ready-ms'] !== undefined,
  null,
  { timeout: 60000 }
);

module.exports = { openControlled, readMetrics, dictionaryReady };
