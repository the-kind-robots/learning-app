## Why

The offline German dictionary (848k PouchDB docs) consumed **445 MB of client IndexedDB storage** (measured 2026-05-06) because PouchDB adds per-document overhead to a static, read-only dataset. Raw JSONL was ~215 MB; PouchDB overhead was ~2.1x. This caused quota risk on low-storage devices and made dictionary updates non-atomic.

## What Changes

Replace the active dictionary lookup path with a single content-addressed `dictionary.sqlite` file served over HTTP, stored in OPFS, and queried from a Dedicated Worker.

Current implementation shape:

- Build tool emits `resources/dictionary/dictionary.sqlite` directly from Kaikki data, plus `enrichment-meta.jsonl` for the enrichment tool.
- Translation enrichment writes durable `enrichment-output.jsonl`; `apply-enrichment!` patches SQLite in place using gap-fill semantics.
- Server exposes `GET /dictionary/manifest` and `GET /dictionary/dict.{hash12}.sqlite` with immutable cache headers and content hash metadata.
- Browser starts `resources/public/js/sqlite3-worker.js`, loads official SQLite WASM, installs `opfs-sahpool`, imports the current SQLite file if missing, and serves SQL `exec` requests over `postMessage`.
- Main-thread ClojureScript uses `src/client/dbs.cljs` as the worker proxy and `src/client/repo/dictionary.cljs` for autocomplete completions.
- PouchDB remains in place for user progress (`user-db` / `device-db`); active home autocomplete no longer queries PouchDB dictionary documents.

## Capabilities

### New Capabilities
- `dictionary-storage`: How the dictionary dataset is built, versioned, transported, cached, and queried on the client. Covers the SQLite schema, OPFS worker protocol, server delivery contract, telemetry needs, and PouchDB retention boundary.

### Modified Capabilities
<!-- No existing spec-level requirements change. The PouchDB dictionary path is replaced/isolated rather than extended. -->

## Impact

- **Build tooling:** `tools/dictionary/` now materializes SQLite tables, strips/normalizes labels, emits enrichment metadata, and can apply enrichment output back into SQLite.
- **Server:** `src/backend/core.clj` serves manifest, content-addressed SQLite, and WASM with correct content type/cache headers.
- **Client:** `src/client/dbs.cljs`, `src/client/repo/dictionary.cljs`, and home effects use the SQLite worker for completions.
- **Dependencies/assets:** official SQLite WASM is tracked as `resources/public/js/sqlite3.js` and `resources/public/js/sqlite3.wasm`; `@sqlite.org/sqlite-wasm` remains the source package.
- **PouchDB boundary:** user progress stays in PouchDB for now; legacy dictionary helpers may remain present but are no longer on the active autocomplete path.
