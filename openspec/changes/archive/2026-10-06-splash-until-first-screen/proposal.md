## Why

The server sends a styled splash in the body. Replicant's first render clears the body. That render happened at boot, as soon as the router set the page, long before memory was loaded, and until then the shell showed `[:div.app-loading "Загружаем..."]`. No CSS styles that element, so a cold start showed bare text in the corner in place of the splash.

A read of the learner's data that failed at start was tried again for ever, with growing waits, while the shell said «Пробуем снова…». No such failure has been observed, and a reload starts the read anew; the owner's rule is no mechanism for a scenario never seen.

## What Changes

- Nothing is rendered until memory is loaded or the read at start has failed. The server's splash stays until then, and the first render replaces it with the screen asked for.
- The read at start runs once. When it fails, the store is marked `:learner/unreadable?` and the shell shows, in `.app-loading`, that the data cannot be read and asks for a reload; nothing reads again. `:learner/read-failed?` and the retry go.
- «Загружаем...» in `.app-loading` and the initial `:page/current :page/loading` go.

## Capabilities

### New Capabilities

### Modified Capabilities
- `learner-data-memory`: the splash is the server's, and nothing is shown between it and the first screen; a failed read at start asks for a reload and is not tried again.
- `main-thread-runtime`: the first render happens as `learner-data-memory` states.

## Impact

- `src/client/main.cljs` (the render function given to the store watch, the initial store), `src/client/application.cljs` (the shell), `src/client/application/presenter.cljs` (`:read-error`, formerly `:loading-message`), `src/client/adapters/learner/loader.cljs` (the read at start, `start!`).
- `test/browser/startup-splash.spec.js` (new); the startup probes in `collections-loading.spec.js` and `consistency.spec.js` no longer look for `.app-loading`.
