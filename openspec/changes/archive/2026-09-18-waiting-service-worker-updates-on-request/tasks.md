## 1. Service worker

- [x] 1.1 `sw.js`: a `message` listener that calls `skipWaiting` on `{type: "activate-waiting"}`; the comment records why the worker waits by default and what makes activation safe (every page reloads on `controllerchange`)

## 2. Client

- [x] 2.1 `service-worker` namespace: register, detect a waiting worker (`registration.waiting`, `updatefound` → `installed` with a controller), offer it — saves `:pwa/update-waiting?` in every build, never activates unasked; `controllerchange` reloads once for a page that started controlled; `visibilitychange` → `registration.update()`; `:effect/activate-waiting-worker` and `:effect/force-reload`
- [x] 2.2 Presenter `:show-update?`; the shell shows «Обновить» in the actions row; CSS for the text button
- [x] 2.3 The build mark dispatches the forced reload; the trace export dispatches `:effect/export-trace` from the actions row
- [x] 2.4 Node tests for the presenter flag

## 3. Verification

- [x] 3.1 Browser spec: register the worker, serve a changed `sw.js` on the update check, assert the installed → waiting state, that no page reloaded before the tap, and that «Обновить» activates the worker and the page ends under it
- [x] 3.2 `docs/dev/mobile-pwa-testing.md`: the update control, the forced reload and the trace export
