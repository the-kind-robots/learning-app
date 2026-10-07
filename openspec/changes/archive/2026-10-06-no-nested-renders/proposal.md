## Why

A tab that loads the app while it is not on screen shows home. When it is brought on screen, it goes back to «Загружаем...» and stays there (#515). Since GH-500 the status region saved its DOM node into the store from `:replicant/on-mount`. That write happened during the first render and asked for a render while Replicant was still rendering. Replicant does not run a nested render: it keeps its hiccup (the splash) and renders it in the next animation frame. When memory loaded before that frame, the screen was drawn first and the splash was then drawn over it, and nothing rendered again. A tab that is not on screen runs no frames until it is shown, and a busy phone runs them late, so both hit it; since memory starts from a snapshot (#508) it loads fast enough to beat the frame more often. Replicant's development build had reported the nested render on the console ever since.

## What Changes

- The status region no longer writes the store. Its markup and its id move to `application.shell`, which the page views can require. The control that starts an announced action — the collection delete control — puts that id into its payload, and the chain passes it to `:effect/announce`, which looks the region up when it announces. `:effect/hold-status-region` and `:app/status-region` are removed.
- The browser specs fail when Replicant reports a render requested during a render.

## Capabilities

### New Capabilities

### Modified Capabilities
- `main-thread-runtime`: adds a requirement that no life-cycle hook writes the store and that a tab brought on screen keeps the screen it last rendered.

## Impact

- `src/client/application.cljs` (`:effect/announce`, the status region), `src/client/application/shell.cljs` (new: the status region and its id), `src/client/pages/collections/` (the delete control, `:effect/delete-collection`, `:action/show-deleted`).
- `test/browser/fixtures.js`, and every spec imports `test` from it; `test/browser/delayed-frames.spec.js` (new).
