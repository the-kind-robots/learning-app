## Why

The in-page counters answer questions the browser stopped asking. `slow-interactions` collects raw `event` entries without grouping them by `interactionId`, so one tap counts as three and the number is not INP — the metric that replaced FID as a Core Web Vital in March 2024. `layout-shift` is a running sum rather than the session-window score, so it cannot be read against the published CLS thresholds. `longtask` observes script time only and never style, layout or paint, which is exactly where this app's glitches live; the mobile suite had to drop its long-task assertion because a bare `duration` flakes under parallel browsers and names no culprit.

Load is not measured at all. The app paints early and stays unusable until the SQLite dictionary is open in OPFS, and that interval — the one the user actually waits through — has no number. Storage is unmeasured too, though it is the quantity behind the dictionary footprint work.

## What Changes

- Replace the `longtask` observer with `long-animation-frame`: whole-frame timing, `blockingDuration`, and per-script attribution through `scripts[]`. **BREAKING** for anything reading `long-tasks` from `window.__metrics()`; the only consumers are this repo's own browser specs.
- Report LCP, FCP, TTFB, CLS and INP through the `web-vitals` package instead of hand-rolled observers. Session windows and `interactionId` grouping are what the hand-rolled versions got wrong; the attribution build additionally names the shifting element and the dominant INP phase.
- Mark dictionary readiness with User Timing, so "time to usable" is a number a test can assert rather than a claim made by eye.
- Report `navigator.storage.estimate()` — usage, quota, and `usageDetails` where the browser supplies it.
- Keep the app-level counters (renders, dispatches, nested dispatches, frames). No library knows about Nexus, and they answer a question web metrics do not.

Lab only. Nothing is transmitted; the instrumentation stays behind `goog.DEBUG` and is eliminated from release builds exactly as it is today.

Explicitly out of scope: `performance.measureUserAgentSpecificMemory()`. It requires cross-origin isolation (COOP + COEP), which neither nginx config sets today and which breaks every cross-origin resource that does not opt in. The JS heap is also the wrong quantity — the dictionary lives in OPFS, which `storage.estimate()` reports directly.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repo-browser-debugging`: the requirement describing the development-build page metrics changes shape. It currently names "layout shift excluding input-caused entries, long tasks, slow interactions"; it will instead name the standard web metrics reported through a maintained library, long animation frames with attribution, a dictionary-readiness measure, and a storage estimate — while keeping the app-level counters and the release-build exclusion.

## Impact

- `src/client/instrumentation.cljs` — the observers, the metric map, and the `window.__metrics()` shape.
- `src/client/db/sqlite.cljs` — the worker already reports per-phase durations (`wasm-init`, `pool-install`, `manifest`, `download`, `import`, `db-open`, `cache-hit`) and a `ready` message; today they only reach the log. They get routed into the metrics as well, and the main thread brackets the whole interval with User Timing.
- `package.json` — adds `web-vitals` as a dependency. It is imported only under `goog.DEBUG`, so release output is unaffected.
- `test/browser/` — the mobile spec's dropped long-task assertion can return, now anchored to `blockingDuration`.
- Depends on #289 (PR #291), which is in review and edits the same observer block in `instrumentation.cljs`.
