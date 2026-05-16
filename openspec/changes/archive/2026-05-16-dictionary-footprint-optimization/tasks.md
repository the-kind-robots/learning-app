## 1. SQLite Build Artifact

Single artifact produced by the build pipeline: `dictionary.sqlite` — built directly from Kaikki, deployable as-is. Translation enrichment is optional and applied in-place.

Current schema:
- `lemmas`: id INTEGER PK (rowid), value, pos, rank
- `translations`: lemma_id INTEGER FK, rank INTEGER, value TEXT — WITHOUT ROWID, PK (lemma_id, rank)
- `labels`: id INTEGER PK, name TEXT UNIQUE
- `translation_labels`: lemma_id INTEGER, rank INTEGER, label_id INTEGER — WITHOUT ROWID, PK (lemma_id, rank, label_id)
- `surface_forms`: normalized_form TEXT, lemma_id INTEGER — WITHOUT ROWID, PK (normalized_form, lemma_id)

- [x] 1.1 Extend build tool to emit SQLite artifact
- [x] 1.2 Create `lemmas`, `translations`, `labels`, `translation_labels`, and `surface_forms` tables
- [x] 1.3 Populate `lemmas` from Kaikki source
- [x] 1.4 Populate 1:many translations as `(lemma_id, rank, value)` rows
- [x] 1.5 Build clustered `surface_forms` lookup table
- [x] 1.6 Verify client artifact size: current `resources/dictionary/dictionary.sqlite` is ~36 MB on disk
- [x] 1.7 Add `dictionary.sqlite` to gitattributes as binary; remove raw/intermediate SQLite ignore path
- [x] 1.8 Emit `enrichment-meta.jsonl` for enrichment context; keep it build-machine/support only
- [x] 1.9 Remove shipped raw JSONL/gzip dictionary artifacts from the active delivery path
- [x] 1.10 Update Go enrichment tool to query `dictionary.sqlite`, use `enrichment-meta.jsonl`, and write durable `enrichment-output.jsonl`
- [x] 1.11 Add `apply-enrichment!`: patch `dictionary.sqlite` from `enrichment-output.jsonl` with gap-fill semantics in one transaction

## 2. Server Route

- [x] 2.1 Add `GET /dictionary/:filename` route in Ring/Reitit backend
- [x] 2.2 Add `GET /dictionary/manifest` returning `{hash, filename}` for the current SQLite artifact
- [x] 2.3 Serve `dict.{hash12}.sqlite` with `ETag`, `Content-Hash`, and immutable cache headers; compression delegated to reverse proxy
- [x] 2.4 Return HTTP 404 for unknown dictionary filenames
- [x] 2.5 Add `wasm-handler` intercepting `*.wasm` requests: sets `Content-Type: application/wasm` and immutable cache headers

## 3. Client SQLite Worker

- [x] 3.1 Add official SQLite WASM assets (`sqlite3.js`, `sqlite3.wasm`) under `resources/public/js/`
- [x] 3.2 Add plain JS Dedicated Worker `resources/public/js/sqlite3-worker.js`
- [x] 3.3 Worker fetches `/dictionary/manifest`, imports missing `dict.{hash12}.sqlite` into `opfs-sahpool`, opens DB with `sqlite3.oo1.DB`
- [x] 3.4 Worker removes stale `dict.*.sqlite` pool files and reduces pool capacity after current file opens
- [x] 3.5 Worker handles SQL `exec` messages over `postMessage` with request ids and result/error responses
- [x] 3.6 `src/client/dbs.cljs` starts the worker, tracks readiness, logs worker errors, and exposes an `exec` proxy as `:dictionary/db`

## 4. Active Autocomplete Path

- [x] 4.1 Add `src/client/repo/dictionary.cljs` SQLite completions query
- [x] 4.2 Include translations in the SQL result; active path no longer calls legacy `attach-translations`
- [x] 4.3 Wire home suggest effect through `repo.dictionary/completions` when worker is ready
- [x] 4.4 Return no completions while worker is not ready instead of blocking the UI
- [x] 4.5 Implementation accepted by review; no extra autocomplete browser-proof task tracked here

## 5. PouchDB Retention Boundary

PouchDB stays in place for now because it stores user progress (`user-db` / `device-db`). This change only removes dictionary lookup from the active PouchDB path.

- [x] 5.1 Keep PouchDB user/device databases in place for user progress
- [x] 5.2 Keep legacy dictionary PouchDB helpers in place for now; do not turn cleanup into current scope
- [x] 5.3 Remove active dictionary replication logic from client runtime (`dictionary_sync.cljs` no longer exists)
- [x] 5.4 Verify by code search that active home autocomplete no longer calls PouchDB dictionary lookup

## 6. Telemetry and Rollout

Dev-only telemetry via glogi. Worker posts `{type:"phase"}` messages; main thread logs them under `:dict-worker/<phase>`. Prod logs are silenced (glogi level `:off`). No remote sink — event shape is stable so a sink can slot in later.

- [x] 6.1 Add telemetry/event path for dictionary worker lifecycle if no reusable client telemetry path exists
- [x] 6.2 Emit telemetry for worker init/import/open duration and SQLite download size
- [x] 6.3 Emit telemetry for manifest fetch, SQLite download/import, and OPFS open success/failure
- [x] 6.4 Validate telemetry events in browser/dev logs
