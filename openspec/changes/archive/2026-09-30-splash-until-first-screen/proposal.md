## Why

The server sends a styled splash in the body. Replicant's first render clears the body. That render happened at boot, long before the learner's words and collections were in memory, and until then the shell showed `[:div.app-loading "Загружаем..."]`. No CSS styles that element, so a cold start showed bare text in the corner for seconds. `.app-loading` dates from GH-151; since GH-494 it lasts until memory loads.

## What Changes

- Nothing is rendered until the learner's words and collections are in memory. The server's splash stays until then, and the first render replaces it with the screen asked for.
- The shell's `.app-loading` branch goes, along with what existed only for it: the presenter's readiness check on `:page` and the initial `:page/current :page/loading`. A missing page renders no page content.

## Capabilities

### New Capabilities

### Modified Capabilities
- `learner-data-memory`: the splash is the server's, and nothing is shown between it and the first screen.
- `main-thread-runtime`: nothing renders before the learner's words and collections are in memory.

## Impact

- `src/client/main.cljs` (the render function given to the store watch), `src/client/application.cljs` (`:effect/memory-loaded-basic`, the shell), `src/client/application/presenter.cljs`.
- `test/browser/startup-splash.spec.js` (new); the splash watchers in `collections-loading.spec.js` and `consistency.spec.js` look for the server's `.splash`.
