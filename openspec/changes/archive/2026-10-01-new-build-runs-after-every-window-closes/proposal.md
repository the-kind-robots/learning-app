## Why

After «Обновить», a desktop tab could reload over and over for minutes (#517, under #515). The page reloaded on every `controllerchange`. With DevTools' «Update on reload» on, every load installs and activates the build again, which fires the next change. Beyond the loop, taking a waiting build while windows are open lets two builds run at once, and both write the same local databases under data models that can differ.

## What Changes

- **BREAKING (user-visible):** a release build no longer offers «Обновить». A new build installs, waits, and runs on the next open after every window of the app is closed.
- No page reloads on a controller change.
- The development build mark keeps taking the new build: it activates the waiting worker and reloads its own tab once that worker is activated.
- Removed: the `:pwa/new-build-waiting?` announcement, its component and its control.

The requirements are in `specs/service-worker-update/spec.md`.

## Capabilities

### New Capabilities

### Modified Capabilities
- `service-worker-update`: activation follows the browser's lifecycle in a release build. The offer and the reload on controller change are removed, and the build mark reloads after activation.

## Impact

- `resources/public/js/sw.js` (comments only; the message handler stays for the development build).
- `src/client/service_worker.cljs`, `src/client/main.cljs`, `src/client/application.cljs`, `src/client/application/presenter.cljs`, `resources/public/css/blocks/app-shell.css`.
- `test/browser/service-worker-update.spec.js`, `test/client/presenter/shell_test.cljs`; `test/client/service_worker_test.cljs` removed.
- ADR-0017 supersedes ADR-0014 in part.
- Cost, accepted: an app kept open all the time — a pinned tab, a PWA left running in the background — updates only after all its windows are closed.
