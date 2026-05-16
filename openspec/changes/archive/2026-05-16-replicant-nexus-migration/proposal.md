## Why

The previous app runtime lived inside a Service Worker: htmx fetches were handled by a SW Ring/Reitit app that read PouchDB, rendered HTML, and returned fragments for DOM swaps. This made every UI action a cross-context round trip and blocked the dictionary SQLite work from using a normal Dedicated Worker owned by the main thread.

## What Changes

- **NEW** `src/client/main.cljs` — main-thread entry point; boots Replicant render loop, Nexus action/effect dispatch, client routing, migrations, tasks, dictionary worker, and service-worker registration.
- **NEW** `src/client/pages/**` — page-local actions/effects/views for home, words, and lesson.
- **NEW** `resources/public/js/sqlite3-worker.js` — plain Dedicated Worker running official SQLite WASM with `opfs-sahpool`; `src/client/dbs.cljs` exposes an `exec` proxy.
- **REWRITE** `resources/public/js/sw.js` — passive cache layer only; no app logic imports or HTML rendering.
- **REWRITE** `src/client/application.cljs` — shared Nexus effects/actions/placeholders instead of Ring-style request/response handlers.
- **REWRITE** former `src/client/views/*` into Replicant hiccup page views with Nexus `:on` actions instead of `hx-*` attributes.
- **REMOVE** htmx runtime scripts and client reitit HTTP/sieppari stubs. `reitit.frontend` remains for main-thread navigation.
- **BREAKING** SW no longer serves app HTML. The backend default handler serves a static app shell with `/js/app/main.js`; Replicant owns UI after load.

## Capabilities

### New Capabilities

- `main-thread-runtime`: Main-thread ClojureScript entry point that mounts Replicant and initialises Nexus, replacing the Service Worker as the application runtime.
- `sqlite-dictionary-worker`: Dedicated Worker running SQLite via `opfs-sahpool`; main thread queries it via `postMessage` exec proxy.

### Modified Capabilities

- `lesson`: Lesson UI interactions transition from htmx fragment swaps to Replicant re-renders driven by Nexus state; multi-region updates become one state write.
- `task-runner`: `tasks/start!` lifecycle moves from SW `activate` event to main-thread app boot.
- `home-add-form-focus`: Focus behaviour previously wired via `hx-on:htmx:after-swap` is re-expressed as a Replicant lifecycle hook.

## Impact

- **shadow-cljs.edn**: now has `:app` and `:node-test`; the old CLJS `:sw` build is gone. SW is plain JS served by backend with an injected version constant.
- **package/assets**: `replicant`, `nexus`, `dataspex`, and SQLite WASM assets are present.
- **PouchDB user-data sync**: remains in the main thread; user/device database protocol unchanged.
- **SW offline behaviour**: precache/cache-first for listed static assets survives; app routes fall through to backend/network.
- **htmx scripts** (`htmx.min.js`, `class-tools.js`) and old imperative helper scripts are removed from active shell/precache.
