## Why

The entire ClojureScript application runs inside a Service Worker, making every UI interaction a cross-context round-trip (htmx fetch → SW ring handler → PouchDB → HTML → DOM swap). This causes noticeable lag and, critically, blocks the SQLite dictionary migration: `FileSystemSyncAccessHandle` is only available in Dedicated Workers, `new Worker()` is unavailable in Service Workers, so OPFS-backed SQLite is architecturally impossible in the current model.

## What Changes

- **NEW** `src/client/main.cljs` — main-thread entry point; boots Replicant render loop, Nexus action/effect dispatch, and existing `db-migrations` + `tasks` on `DOMContentLoaded`
- **NEW** `src/client/dictionary_worker.cljs` — Dedicated Worker running `@sqlite.org/sqlite-wasm` with `opfs-sahpool`; exposes `suggest` over postMessage RPC
- **NEW** `src/client/dictionary_worker_rpc.cljs` — main-thread shim that sends queries to the dictionary worker and returns promises
- **REWRITE** `src/client/sw.cljs` — stripped to passive cache layer (precache + network-first); all app logic removed
- **REWRITE** `src/client/application.cljs` — reitit HTTP routes replaced by Nexus action/effect registry; ring-style request/response removed
- **REWRITE** `src/client/views/*` — hiccup structure preserved; all `hx-*` attributes replaced with Nexus action data (`:on {:click [...]}`)
- **REMOVE** htmx dependency and `hx-*` attributes throughout
- **REMOVE** `ring.middleware.*`, reitit HTTP interceptors, `sieppari` from client build
- **BREAKING** SW no longer serves app HTML; static `index.html` hosts the app shell

## Capabilities

### New Capabilities

- `main-thread-runtime`: Main-thread ClojureScript entry point that mounts Replicant and initialises Nexus, replacing the Service Worker as the application runtime
- `sqlite-dictionary-worker`: Dedicated Worker running SQLite via `opfs-sahpool`; main thread queries it via postMessage RPC; unblocks the dictionary footprint migration

### Modified Capabilities

- `lesson`: Lesson UI interactions transition from htmx fragment swaps to Replicant vdom re-renders driven by Nexus state; multi-region updates (progress, challenge, footer) become a single state write
- `task-runner`: `tasks/start!` lifecycle moves from SW `activate` event to main-thread app boot
- `home-add-form-focus`: Focus behaviour previously wired via `hx-on:htmx:after-swap` must be re-expressed as a Replicant lifecycle hook

## Impact

- **shadow-cljs.edn**: gains `:app` (browser, main) and `:dictionary-worker` (web-worker) modules alongside the slimmed `:sw`
- **package.json**: `@sqlite.org/sqlite-wasm` already added; no other new runtime deps beyond `replicant` and `nexus`
- **PouchDB user-data sync**: remains in the main thread (moved from SW); no protocol change
- **SW offline behaviour**: precache + network-first fetch survives; only app-logic duties are removed
- **htmx scripts** (`htmx.min.js`, `class-tools.js`) removed from precache and `<script>` tags
