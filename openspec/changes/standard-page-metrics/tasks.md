## 1. Dependency

- [x] 1.1 Add `web-vitals` to `package.json` and install
- [x] 1.2 Confirm shadow-cljs resolves the module in a dev build

## 2. Long frames

- [x] 2.1 Replace the `longtask` observer in `src/client/instrumentation.cljs` with `long-animation-frame`, recording duration, `blockingDuration` and attributed scripts
- [x] 2.2 Rename the metric key from `long-tasks` to `long-frames` and update `zero-metrics`
- [x] 2.3 Confirm an unsupported entry type still degrades quietly through `observe!`

## 3. Standard web metrics

- [x] 3.1 Wire the web-vitals attribution build into `install!` for LCP, FCP, TTFB, CLS and INP
- [x] 3.2 Store each reported metric with its value, rating and attribution under a `web-vitals` key
- [x] 3.3 Remove the hand-rolled `layout-shift` observer and the `event`/`slow-interactions` observer, now superseded
- [x] 3.4 Reset clears our copy of the readings; the library keeps its own state, documented rather than faked

## 4. Dictionary readiness

- [x] 4.1 Mark `dictionary-start` in `db.sqlite/init!` before the worker is constructed
- [x] 4.2 On the worker's `ready` message, record `performance.measure("dictionary-ready", "dictionary-start")` and keep the duration
- [x] 4.3 Accumulate the worker's existing `phase` messages (including `cache-hit`) into the metrics
- [x] 4.4 Leave the readiness figure absent — not zero — when the worker never reports readiness

## 5. Storage

- [x] 5.1 Expose `window.__storage()` returning a promise of usage, quota and `usage-details`
- [x] 5.2 Keep `window.__metrics()` synchronous

## 6. Release build

- [x] 6.1 Produce a release build and inspect the output for the instrumentation and for the library
- [x] 6.2 Library survived the import (10 occurrences of `interactionId`), so it is fetched at runtime from `/js/web-vitals.js` instead; re-inspected and now absent
- [x] 6.3 The instrumentation's own code still survives release DCE — confirmed against `master` sources too, so it predates this change. Filed as #298 rather than widened into this one

## 7. Browser suite

- [x] 7.1 No existing spec read `long-tasks` or `slow-interactions`; nothing to migrate on this branch
- [ ] 7.2 Restore a long-frame assertion in the mobile spec — deferred: `add-form-stability.mobile.spec.js` lives on the #289 branch and does not exist here. Do this on rebase, after PR #291 merges
- [x] 7.3 Add `test/browser/metrics.spec.js` covering web vitals, long frames with attribution, dictionary readiness and storage
- [x] 7.4 Document the metric shape and its two caveats in `test/browser/README.md`

## 8. Verification

- [x] 8.1 Run the browser suite against a locally booted backend — 14/14 pass, including under 4 parallel workers
- [x] 8.2 Cold-versus-warm dictionary comparison — done in the suite by reloading the same context: 1512 ms cold against 448 ms warm
- [x] 8.3 `openspec validate standard-page-metrics --strict`
- [x] 8.4 Found while measuring: the service worker strips the dictionary worker's query string, so the worker's own phase telemetry only ever reaches an uncontrolled load. Filed as #299, spec and test worded to match the actual behaviour
