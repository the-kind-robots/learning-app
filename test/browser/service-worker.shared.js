// Page states the specs wait for: the service worker and the dictionary being
// ready on home.

// The first load of a fresh context: the worker installs, activates and
// claims the page. Resolves once the page is controlled — before that nothing
// passes through the worker's fetch handler. `beforeClaim`, if given, runs on
// the loaded page while it may still be uncontrolled.
async function openControlled(page, beforeClaim) {
  await page.goto('/');
  if (beforeClaim) await beforeClaim(page);
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null, null, { timeout: 30000 });
}

// Readiness is reported by the dictionary worker, which starts alongside the
// app; after a reload the metrics global itself has to come back first.
const dictionaryReady = (page) => page.waitForFunction(
  () => typeof window.__metrics === 'function' &&
        window.__metrics().dictionary['ready-ms'] !== undefined,
  null,
  { timeout: 60000 }
);

// Home with the dictionary ready. Home can be on screen before the dictionary
// worker has its database, and a query asked before then is answered with an
// empty list (#312), so a spec about suggestions types only after this.
async function openHome(page) {
  await page.goto('/home');
  await dictionaryReady(page);
}

module.exports = { openControlled, openHome };
