## Why

The offline German dictionary (848k PouchDB docs) consumes **445 MB of client IndexedDB storage** (measured 2026-05-06) due to per-document overhead on a static, read-only, monolithically-versioned dataset. Raw JSONL is ~215 MB; PouchDB overhead is ~2.1×. This causes quota failures on low-storage devices and contributes to the "brick risk" (failed sync leaving the app in a broken state) because PouchDB's non-atomic update path is the wrong transport for a file that changes as a unit.

## What Changes

Replace the dictionary PouchDB path with a single versioned `.sqlite` file served over HTTP, stored in OPFS, and queried via a Web Worker. Reduces IndexedDB footprint from 445 MB to ~60 MB and eliminates brick risk for the dictionary atomically.

- New build artifact: `dict.v{epoch}.sqlite`
- New server route: `GET /dictionary/v{epoch}` (gzip + content-hash)
- New client Worker: download → write OPFS → open via sql.js; atomic swap on version bump
- `attach-translations` and `suggest` in `dictionary.cljs` replaced by worker-postMessage shims with identical external API
- `dictionary-db` PouchDB path torn down in `dictionary-sync.cljs` and `dbs.cljs`; `user-db` / `device-db` untouched

## Capabilities

### New Capabilities
- `dictionary-storage`: How the dictionary dataset is stored, versioned, transported, and queried on the client. Covers the SQLite schema, OPFS write/swap protocol, Worker API, and server delivery contract.

### Modified Capabilities
<!-- No existing spec-level requirements change. dictionary-sync behaviour (PouchDB path) is being removed, not modified — no delta spec needed. -->

## Impact

- **Build tooling:** `tools/dictionary/` — extend to emit `.sqlite` alongside JSONL; add field-stripping step for Phase 1.
- **Server:** new route in backend (Ring/Reitit) to serve the versioned sqlite file with gzip and content-hash headers.
- **Client:** `src/client/dictionary.cljs`, `src/client/dictionary-sync.cljs`, `src/client/dbs.cljs` — Worker shim replaces direct PouchDB calls; dictionary-db initialisation removed.
- **Dependencies:** `sql.js` (WASM, ~1 MB gzipped) added; PouchDB dictionary-db removed from client bundle after Phase 2.
- **Service Worker / Cache API:** versioned file cached on first fetch; old version purged after atomic swap.
