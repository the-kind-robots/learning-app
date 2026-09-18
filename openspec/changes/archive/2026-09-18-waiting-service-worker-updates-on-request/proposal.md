## Why

The service worker keeps waiting until every tab of the origin is gone (#278: activating early deletes the cache bucket a live tab still loads from). On Android a swiped-away PWA is not a closed tab, so after a refresh the old build stays until the system kills the process at some unknown moment. The app has to be able to update on request without giving up #278's guarantee.

## What Changes

- The service worker activates on a plainly named message from a page (`{type: "activate-waiting"}`); it still never activates on its own.
- The app detects a waiting worker — one already waiting at registration, or one that finishes installing while a controller exists — and offers the update: an «Обновить» control in the app shell, in every build. No build activates a worker unasked; a development watch writes a new worker on every recompile.
- Every page reloads itself once on `controllerchange`, so no tab keeps running on a deleted bucket after the activation.
- The registration checks for an update every time the document becomes visible, so a phone coming back sees the new build.
- Development build only: the trace export joins the shell's actions row, and the red D after the word mark goes back to being a letter of the name.

## Capabilities

### New Capabilities

- `service-worker-update`: how a waiting service worker is offered, activated and followed by a reload.

### Modified Capabilities

(none)

## Impact

- Affected specs: `service-worker-update` (new).
- Affected code: `resources/public/js/sw.js` (message handler), a `service-worker` client namespace started by `main.cljs`, `application.presenter/shell-props` and the shell view (control), `instrumentation` (the trace export), `resources/public/css/blocks/app-shell.css`, a browser spec registering the worker and varying its version, `docs/dev/mobile-pwa-testing.md`.
