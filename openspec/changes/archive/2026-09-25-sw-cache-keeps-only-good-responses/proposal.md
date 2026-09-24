## Why

The service worker stores whatever the network answers for a static asset or the dictionary manifest, a 404 or a 500 included, and the bucket lives until the next deploy — one bad fetch is then served on every later load (#315). It also answers a request carrying a query string with the precached bare-path entry, whose URL has no query; the dictionary worker built from it loses `sqlite3.dir` and `telemetry`, so its phase timings vanish on every load after the first (#299).

## What Changes

- Only a successful same-origin response is written to the cache.
- A response found by bare path is served under the URL it was requested by.

## Capabilities

### New Capabilities

### Modified Capabilities
- `main-thread-runtime`: adds two requirements on what the service worker caches and under which URL it serves it.

## Impact

- `resources/public/js/sw.js` (`cacheFirst`, `networkFirstCached`).
- `test/browser/service-worker-cache.spec.js`.
- No `skipWaiting`: the fix reaches an open tab only after the user takes the waiting worker, as every other worker change does.
