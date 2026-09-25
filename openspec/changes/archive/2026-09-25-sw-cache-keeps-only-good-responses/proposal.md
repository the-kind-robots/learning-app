## Why

The service worker stores whatever the network answers for a static asset or the dictionary manifest, a 404 or a 500 included, and the bucket lives until the next deploy — one bad fetch is then served on every later load (#315). It also answers a request carrying a query string with the precached bare-path entry, whose URL has no query; the dictionary worker read `sqlite3.dir` and `telemetry` from that URL, so its phase timings vanished on every load after the first (#299).

## What Changes

- Only a successful same-origin response is written to the cache.
- Static assets are cached by path, lookup and store alike.
- The dictionary worker is started without a query string: the SQLite engine is found beside the worker's script, and a development build asks for phase timings by message.

## Capabilities

### New Capabilities

### Modified Capabilities
- `main-thread-runtime`: adds two requirements, on what the service worker caches and on the dictionary worker taking no configuration from its script URL.

## Impact

- `resources/public/js/sw.js` (`cacheFirst`, `networkFirstCached`).
- `resources/public/js/sqlite3-worker.js`, `sqlite3-dictionary.js`, `src/client/db/sqlite.cljs`.
- `test/browser/service-worker-cache.spec.js`.
- No `skipWaiting`: the fix reaches an open tab only after the user takes the waiting worker, as every other worker change does.
