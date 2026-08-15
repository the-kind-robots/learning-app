## Context

`src/client/instrumentation.cljs` installs four `PerformanceObserver`s and two app-level counters behind `goog.DEBUG`, exposing everything through `window.__metrics()`. Three of the four observers approximate a standard metric rather than report it: `layout-shift` sums instead of scoring session windows, `event` entries are listed instead of being grouped into interactions, and `longtask` sees script time without style, layout or paint. The mobile Playwright suite already had to drop its long-task assertion for lack of attribution.

Two quantities that matter here are not measured at all. Load: the app paints long before the dictionary is usable. Storage: the dictionary's OPFS footprint drives the footprint work and the quota risk behind the migration concerns.

The load story is better instrumented than it looks. `resources/public/js/sqlite3-worker.js` wraps every startup phase in `measure(...)` and posts `{type: "phase", phase, status, durationMs}` — `wasm-init`, `pool-install`, `manifest`, `download`, `import`, `cleanup`, `db-open`, plus a zero-duration `cache-hit` — and posts `{type: "ready"}` when the pool lock is held and the DB is open. `src/client/db/sqlite.cljs` receives these and forwards them to glogi. Nothing carries them into the metrics.

In-force ADRs: 0001–0010, none superseded. 0002 (worker-first OPFS) and 0005 (client runtime system and component graph) bound this design — the dictionary lives behind a worker boundary, and instrumentation attaches through the runtime's component graph rather than by reaching into components.

## Goals / Non-Goals

**Goals:**

- Report the standard web metrics under their real definitions, not approximations.
- Attribute a slow frame to the script that caused it.
- Give "time until the dictionary is usable" a single number, and keep the per-phase breakdown that already exists.
- Report OPFS usage against quota.
- Keep release builds free of all of it.

**Non-Goals:**

- Field collection. Nothing is transmitted; no endpoint, no beacon, no identifiers.
- `performance.measureUserAgentSpecificMemory()` and the COOP/COEP headers it needs.
- Replacing the app-level counters. Renders, dispatches and nested dispatches answer a question about Nexus that no web metric covers.
- Changing dictionary startup behaviour. The worker's phases are read, not reshaped.

## Decisions

### Report web metrics through `web-vitals`, not hand-rolled observers

The library is maintained by the Chrome team, is about 2 KB, and implements the parts that were got wrong twice: CLS session windows (5 s window, 1 s gap, maximum rather than sum), INP's grouping by `interactionId` and its drop-worst-per-50 percentile, bfcache restores, and the reporting lifecycle.

Its `web-vitals/attribution` build additionally names the largest shift's element and which INP phase (input delay, processing, presentation) dominated — the information a failing test needs in order to be actionable.

*Alternative considered:* keep hand-rolling. Rejected — the two existing metrics are both subtly wrong in ways that took a careful reading to notice, and the correct algorithms are non-obvious enough that a reimplementation would need its own tests.

*Alternative considered:* strip the library and vendor only the INP grouping. Rejected — it saves ~2 KB in a build that is discarded anyway and forfeits the attribution build.

### Keep the release build clean by proving it, not by assuming it

Today the whole namespace disappears from release output through dead-code elimination. An npm dependency is a weaker case: Closure's DCE reasons about JavaScript modules less aggressively than about ClojureScript, so a plain `(:require ["web-vitals" ...])` may survive into the bundle even when nothing calls it.

The decision is to write the straightforward `:require` and then **verify the release bundle by grep**, treating the result as the deciding evidence. If `web-vitals` survives, switch the import to a dynamic one (`shadow.lazy` / `shadow.esm/dynamic-import`) so the module is fetched only when the DEBUG path runs. Either way the requirement "release builds SHALL NOT contain the instrumentation" is checked rather than asserted.

*Alternative considered:* go straight to the dynamic import. Rejected as premature — it makes `install!` asynchronous, which complicates every caller and test, for a cost that may not exist.

### Replace `longtask` with `long-animation-frame`

LoAF reports the whole frame — script, style, layout, paint — where `longtask` reports only the long task inside it. It carries `blockingDuration` (the part that actually blocks input, and therefore the stable quantity for an assertion) and `scripts[]` with `invoker`, `invokerType` and `sourceURL`.

Replace rather than keep both: they overlap, and two entries for one event invites the reader to double-count. Chrome-only is acceptable — every consumer of these metrics is Chrome (Playwright runs `channel: "chrome"`, the CDP skill drives Windows Chrome), and `observe!` already swallows unsupported entry types, so a non-supporting browser simply reports an empty list.

### Storage is a separate asynchronous read, not part of `__metrics()`

`navigator.storage.estimate()` returns a Promise. Making `window.__metrics()` async would break every existing caller and every existing spec.

Expose `window.__storage()` returning a Promise of `{usage, quota, usage-details}` instead. Two functions with honest signatures beat one function that lies about being synchronous.

### Dictionary readiness: one measure on the main thread, phases carried alongside

The worker's `performance.now()` runs on the worker's own time origin, so its durations are comparable to each other but not directly placeable on the document's timeline. Therefore:

- The main thread marks `dictionary-start` in `db.sqlite/init!` before constructing the worker, and on `ready` records `performance.measure("dictionary-ready", "dictionary-start")`. That measure is "time until the dictionary is usable", on the document's clock, visible in a DevTools trace as a User Timing entry.
- The per-phase `durationMs` values already arriving from the worker are accumulated into the metrics as a vector, so the breakdown survives without any new measurement code in the worker.
- `cache-hit` distinguishes a warm start from a cold one, which is what makes the readiness number interpretable.

*Alternative considered:* have the worker emit its own User Timing marks. Rejected — worker marks live on a separate timeline and would need `timeOrigin` arithmetic to align, for no gain over the existing phase messages.

### Keep the kebab-case key convention

`clj->js` leaves ClojureScript keyword names alone, so map keys reach JavaScript as `layout-shift`, not `layoutShift`. This has already cost one debugging round. New keys follow the same convention, and the browser specs read them with bracket notation.

## Risks / Trade-offs

- **`web-vitals` survives into the release bundle** → grep the release output as an explicit verification task; fall back to a dynamic import if it does.
- **LoAF is Chrome-only, so the long-frame list is silently empty elsewhere** → the metric map documents this, and `observe!` already degrades quietly rather than throwing.
- **A `blockingDuration` assertion could flake the way the `duration` one did** → assert only within a bounded interaction window and use attribution to name the script, so a failure is diagnosable instead of being deleted.
- **Adding a runtime npm dependency for a dev-only concern** → offset by the release-bundle check above; if the check fails and the dynamic import proves awkward, the fallback is to drop the library and keep the corrected observers, at the cost of maintaining the CLS and INP algorithms ourselves.
- **The dictionary measure depends on the worker reaching `ready`** → when the worker reports `another-tab-open` or crashes, no measure is recorded; the absence is itself informative and must not be reported as zero.
- **Overlap with #289 (PR #291)** → that branch edits the same observer block to add an unfiltered shift counter. Once `web-vitals` reports CLS properly, the hand-rolled pair is redundant and this change removes both. Rebase after #291 merges rather than reconciling by hand.

## Migration Plan

No deployment concerns: the code is dev-only and eliminated from release builds. The `window.__metrics()` shape changes, which affects the repo's own browser specs and nothing else — `long-tasks` becomes `long-frames`, `slow-interactions` gives way to the web-vitals report, and new keys appear. Specs are updated in the same change.

Rollback is a revert; there is no persisted state and no server component.

## Open Questions

- Whether the readiness measure should start at `performance.timeOrigin` (page load) rather than at worker construction. Worker construction is the honest boundary for "what the dictionary costs"; page load is the honest boundary for "what the user waits". Both are cheap; the implementation records worker construction and can add the page-load variant if the numbers argue for it.
