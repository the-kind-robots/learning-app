## 1. Dependency

- [ ] 1.1 Add `web-vitals` to `package.json` dependencies and install
- [ ] 1.2 Confirm shadow-cljs resolves the module in a dev build (`npx shadow-cljs compile app` with the import in place)

## 2. Long frames

- [ ] 2.1 Replace the `longtask` observer in `src/client/instrumentation.cljs` with `long-animation-frame`, recording duration, `blockingDuration` and attributed scripts
- [ ] 2.2 Rename the metric key from `long-tasks` to `long-frames` and update `zero-metrics`
- [ ] 2.3 Confirm an unsupported entry type still degrades quietly through `observe!`

## 3. Standard web metrics

- [ ] 3.1 Wire `web-vitals` (attribution build) into `install!` for LCP, FCP, TTFB, CLS and INP
- [ ] 3.2 Store each reported metric with its value and attribution under a `web-vitals` key in the metric map
- [ ] 3.3 Remove the hand-rolled `layout-shift` observer and the `event`/`slow-interactions` observer, now superseded
- [ ] 3.4 Make `__metricsReset!` reset the web-vitals accumulator without detaching the library's listeners

## 4. Dictionary readiness

- [ ] 4.1 Mark `dictionary-start` in `db.sqlite/init!` before the worker is constructed
- [ ] 4.2 On the worker's `ready` message, record `performance.measure("dictionary-ready", "dictionary-start")` and push the duration into the metrics
- [ ] 4.3 Accumulate the worker's existing `phase` messages (including `cache-hit`) into the metrics alongside the readiness figure
- [ ] 4.4 Leave the readiness figure absent — not zero — when the worker reports an error instead of readiness

## 5. Storage

- [ ] 5.1 Expose `window.__storage()` returning a promise of usage, quota and `usageDetails`
- [ ] 5.2 Keep `window.__metrics()` synchronous

## 6. Release build

- [ ] 6.1 Produce a release build and grep the output for the instrumentation namespace and for `web-vitals`
- [ ] 6.2 If either survives, switch to a dynamic import so the module loads only on the DEBUG path, and re-run the check

## 7. Browser suite

- [ ] 7.1 Update `test/browser/` specs that read `long-tasks` or `slow-interactions` to the new shape
- [ ] 7.2 Restore a long-frame assertion in the mobile spec, anchored to `blockingDuration` within the interaction window
- [ ] 7.3 Add a spec asserting the dictionary readiness measure exists and that storage usage grows across a cold load
- [ ] 7.4 Document the new metric shape in `test/browser/README.md`

## 8. Verification

- [ ] 8.1 Run the browser suite (`npm run test:browser`) against a locally booted backend
- [ ] 8.2 Verify cold-versus-warm dictionary readiness in a real browser through the CDP skill, reporting what was and was not proven
- [ ] 8.3 Run `openspec validate standard-page-metrics --strict`
