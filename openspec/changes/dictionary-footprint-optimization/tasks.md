## 1. SQLite Build Artifact

Single artifact produced by the build pipeline: `dictionary.sqlite` — built directly from kaikki,
deployable as-is with nullable translations. Translation enrichment is optional and applied in-place.

Schema:
- `lemmas`: id INTEGER PK (rowid), value, pos, rank
- `translations`: lemma_id INTEGER FK, seq INTEGER, value TEXT — PK (lemma_id, seq), nullable
- `labels`: id INTEGER PK, name TEXT UNIQUE — 73 canonical English names
- `translation_labels`: lemma_id INTEGER, seq INTEGER, label_id INTEGER — WITHOUT ROWID, PK (lemma_id, seq, label_id)
- `surface_forms`: normalized_form TEXT, lemma_id INTEGER — WITHOUT ROWID, PK (normalized_form, lemma_id)

- [x] 1.1 Extend build tool to emit SQLite artifact
- [x] 1.2 Create `entries` table
- [x] 1.4 Populate entries from kaikki source
- [x] 1.5 Translations as 1:many table (lemma_id, seq, value) — nullable; kaikki-sourced translations populate at build time
- [x] 1.7 Build surface_forms, assign INTEGER rowids, emit `dictionary.sqlite` directly (no intermediate raw file)
- [x] 1.8 Verify client artifact: ~41 MB on-disk (targets: ~40–45 MB / ~12–15 MB)
- [x] 1.10 Add labels/translation_labels tables; strip label abbrevs from translation values at build time
- [x] 1.9 Add `dictionary.sqlite` to gitattributes as binary; remove `dictionary-raw.sqlite` from .gitignore
- [x] 1.11 Replace `emit-enrichment-input!` with `emit-enrichment-meta!`: emit `{id, value, pos, forms_count, meta: {senses, cefr_level, sense_count}}` per lemma; build-machine only, not shipped to clients
- [x] 1.12 Remove `export-client-dictionary!` and `dictionary-raw.sqlite` write step; rewrite `write-dictionary!` to emit `dictionary.sqlite` directly from in-memory lemmas; add `text_id TEXT NOT NULL UNIQUE` to lemmas table
- [x] 1.13 Update Go enrichment tool: query `dictionary.sqlite` for lemmas absent from `translations`; load senses context from `enrichment-meta.jsonl`; update `corePriority` to read `forms_count` integer from side-file instead of `len(forms)`
- [x] 1.14 Add `apply-enrichment!`: open `dictionary.sqlite`, gap-fill INSERT into `translations` for lemmas absent from that table, keyed by `text_id`; run in single transaction; log skipped (unmatched) records

## 2. Server Route

- [x] 2.1 Add `GET /dictionary/:filename` route in Ring/Reitit backend
- [x] 2.2 Serve the SQLite file with `ETag` / `X-Content-Hash` and `Cache-Control: immutable`; gzip delegated to reverse proxy
- [x] 2.3 Return HTTP 404 for unknown version requests
- [x] 2.4 Add `wasm-handler` intercepting `*.wasm` requests: sets `Content-Type: application/wasm` and `Cache-Control: immutable`

## 3. Client Worker

- [x] 3.0 Add `GET /dictionary/manifest` JSON endpoint (`{hash, filename}`) to the Ring backend so the SW can discover the current version without parsing the full manifest.edn
- [x] 3.1 Add `@sqlite.org/sqlite-wasm` to `package.json`; copy `sqlite3.wasm` from node_modules to `resources/public/js/app/` so `wasm-handler` can serve it with correct Content-Type
- [x] 3.2 Create `dictionary_sqlite.cljs` in the SW build (no separate Worker — the SW IS the Worker context); `init!` fetches `/dictionary/manifest`, checks opfs-sahpool pool for the hash-addressed file, downloads and `importDb`s if absent, opens db via oo1.DB API
- [x] 3.3 Use `opfs-sahpool` VFS for all OPFS access — SyncAccessHandle lifecycle managed by the official WASM runtime
- [x] 3.4 Version tracking via hash-addressed pool filename (`dict.{hash12}.sqlite`): `getFileNames()` tells us if the current version is already in the pool; old files are left in place (pool capacity = 6; explicit cleanup deferred)
- [x] 3.5 Implement `suggest` in `dictionary_sqlite.cljs`: SQL join over `surface_forms`, `lemmas`, `translations`; returns `{:suggestions [...] :prefill string-or-nil}` — same shape as the PouchDB `dictionary/suggest`; direct synchronous OO1 API call (no postMessage)
- [x] 3.6 Wire `dictionary-sqlite/init!` into sw.cljs activate event (fire-and-forget before the waitUntil chain; does not block activation)

## 4. dictionary.cljs Shim

- [ ] 4.1 Add rollout flag `dictionary-sqlite-enabled?`
- [ ] 4.2 `attach-translations` is no longer needed for the SQLite path (translations are included in the `suggest` SQL join); remove the call when flag is on
- [ ] 4.3 Wrap `suggest` in `dictionary.cljs`: when flag is on, call `dictionary-sqlite/suggest` directly (same SW context — no postMessage); when off, use existing PouchDB path
- [ ] 4.4 Test both paths manually: flag-off (PouchDB) and flag-on (SQLite) produce equivalent results for the same inputs

## 5. Teardown PouchDB Dictionary Path

- [ ] 5.1 Remove `dictionary-db` initialisation from `src/client/dbs.cljs`
- [ ] 5.2 Remove dictionary replication logic from `src/client/dictionary-sync.cljs`
- [ ] 5.3 Remove rollout flag and dead PouchDB branch from `dictionary.cljs`
- [ ] 5.4 Verify app boots without `dictionary-db` being created or synced
- [ ] 5.5 Verify sync cycle no longer attempts CouchDB replication for dictionary documents

## 6. Telemetry and Rollout

- [ ] 6.1 Check existing telemetry for `dictionary-sync` quota failures (informs urgency)
- [ ] 6.2 Add telemetry event for Worker init time and download size
- [ ] 6.3 Add telemetry event for OPFS write success/failure
- [ ] 6.4 Ship behind rollout flag to 10% of users; monitor for quota errors and Worker failures
- [ ] 6.5 Full rollout after stable observation period
