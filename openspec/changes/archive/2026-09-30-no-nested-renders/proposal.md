## Why

A tab that loads the app while it is not on screen shows home. When it is brought on screen, it goes back to «Загружаем...» and stays there (#515). Since GH-500 the status region saved its DOM node into the store from `:replicant/on-mount`. That write happened during the first render and asked for a render while Replicant was still rendering. Replicant does not run a nested render: it keeps its hiccup (the splash) and renders it in the next animation frame. A tab that is not on screen runs no frames, so the splash waited and was rendered over home when the tab was shown. Replicant's development build had reported this on the console ever since.

## What Changes

- The status region no longer writes the store. It carries the id `app-status`. The action that announces passes that id to `:effect/announce`, which looks the region up when it announces. `:effect/hold-status-region` and `:app/status-region` are removed.
- The browser specs fail when Replicant reports a render requested during a render.

## Capabilities

### New Capabilities

### Modified Capabilities
- `main-thread-runtime`: adds a requirement that no life-cycle hook writes the store and that a tab brought on screen keeps the screen it last rendered.

## Impact

- `src/client/application.cljs` (`:effect/announce`, the status region), `src/client/pages/collections/actions.cljs` (`:action/show-deleted`).
- `test/browser/fixtures.js`, and every spec imports `test` from it.
