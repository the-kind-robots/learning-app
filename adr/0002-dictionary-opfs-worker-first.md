# 0002. Dictionary SQLite accessed via Worker-first OPFS

- Status: accepted
- Date: 2026-05-08

## Context

`SyncAccessHandle` — the OPFS API needed for efficient SQLite access — is Worker-only in Safari and Firefox. Designing the SQLite layer to run on the main thread would require a re-architecture on those browsers. sql.js loads the full database into memory; at ~60 MB this is acceptable on modern devices and simpler than streaming reads.

## Decision

The SQLite file is opened exclusively inside a dedicated Web Worker using `SyncAccessHandle`. The main thread communicates with the Worker via `postMessage`. sql.js is used as the SQLite engine. The switch to wa-sqlite (which provides streaming reads from OPFS) is deferred unless RAM becomes a measured problem.

## Consequences

- All dictionary query code lives in the Worker; the main thread only sends/receives messages.
- `attach-translations` and `suggest` in `dictionary.cljs` become async `postMessage` shims — no semantic change for callers.
- Cross-browser compatibility is ensured from the start without a future re-architecture.
- sql.js adds ~60 MB heap pressure; acceptable on modern devices, monitored via telemetry.
- WASM cold-start latency is off the critical path: the Worker initialises in the background on app boot.
