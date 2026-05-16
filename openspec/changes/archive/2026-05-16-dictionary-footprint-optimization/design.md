## Context

The offline dictionary previously lived as 154k entry docs and 695k surface-form docs replicated into PouchDB. PouchDB imposed 445 MB of IndexedDB storage for a dataset that changes as a whole. The implemented design ships one SQLite artifact (~36 MB on disk in the current build) and stores/query-runs it through official SQLite WASM in OPFS.

**Build pipeline shape:**

```text
kaikki dump -> dictionary.sqlite   (primary artifact, deployable as-is)
                    ↓  [optional async enrichment]
              enrichment-output.jsonl   (durable translation record)
                    ↓
              apply-enrichment!   (gap-fill INSERT in one transaction)
                    ↓
              dictionary.sqlite   (with extra translations filled in)
```

There is no shipped raw SQLite artifact and no per-document client replication. `enrichment-meta.jsonl` is a build/enrichment side file; `enrichment-output.jsonl` is the durable record of expensive API calls and can be reapplied after a Kaikki rebuild.

## Goals / Non-Goals

**Goals:**
- Replace active dictionary autocomplete lookup with SQLite/OPFS.
- Reduce client dictionary storage from the measured 445 MB PouchDB footprint to the SQLite file footprint.
- Make dictionary versioning content-addressed and atomic from the user's perspective.
- Keep `user-db` and `device-db` PouchDB/CouchDB sync unchanged.

**Non-Goals:**
- Migrating user data away from PouchDB.
- Adding detail views, full-text search, or fuzzy matching beyond the existing surface-form prefix lookup.
- Requiring enrichment before deploy; the base SQLite file is valid without the optional translation patch.
- Keeping the old `dictionary.cljs` API shape. After the Replicant/Nexus migration the active API is `repo.dictionary/completions`.

## Decisions

### Enrichment pipeline: side-file authoritative, apply in-place with gap-fill

`dictionary.sqlite` is built from Kaikki and is usable without enrichment. The Go enrichment tool queries the SQLite file for untranslated lemmas and loads sense/context data from `enrichment-meta.jsonl`. It writes results to `enrichment-output.jsonl`, which survives Kaikki rebuilds.

`apply-enrichment!` reads `enrichment-output.jsonl`, maps side-file lemma ids back to current lemma rows by stable lemma key, and inserts only translations for lemmas with no existing translations. Kaikki-sourced translations are not overwritten. New labels are inserted as needed.

**Rationale:** Translation API calls are expensive and long-running. The side-file decouples that work from the Kaikki parse/materialize cycle.

### SQLite schema

```sql
CREATE TABLE lemmas (
  id    INTEGER PRIMARY KEY,
  value TEXT    NOT NULL,
  pos   TEXT,
  rank  INTEGER NOT NULL
);

CREATE TABLE translations (
  lemma_id INTEGER NOT NULL REFERENCES lemmas(id),
  rank     INTEGER NOT NULL,
  value    TEXT    NOT NULL,
  PRIMARY KEY (lemma_id, rank)
) WITHOUT ROWID;

CREATE TABLE labels (
  id   INTEGER PRIMARY KEY,
  name TEXT    NOT NULL UNIQUE
);

CREATE TABLE translation_labels (
  lemma_id INTEGER NOT NULL,
  rank     INTEGER NOT NULL,
  label_id INTEGER NOT NULL REFERENCES labels(id),
  FOREIGN KEY (lemma_id, rank) REFERENCES translations(lemma_id, rank),
  PRIMARY KEY (lemma_id, rank, label_id)
) WITHOUT ROWID;

CREATE TABLE surface_forms (
  normalized_form TEXT    NOT NULL,
  lemma_id        INTEGER NOT NULL REFERENCES lemmas(id),
  PRIMARY KEY (normalized_form, lemma_id)
) WITHOUT ROWID;
```

`text_id` stays out of the shipped SQLite artifact. It exists only in the build/enrichment side data. `surface_forms` is clustered by `(normalized_form, lemma_id)`, so prefix/exact lookup is a key-range scan without a separate index.

### Official SQLite WASM in a plain Dedicated Worker

The browser loads `resources/public/js/sqlite3-worker.js` as a plain JavaScript Dedicated Worker. The worker imports `sqlite3.js`, installs `opfs-sahpool`, imports the current SQLite file into the pool if absent, opens it via `sqlite3.oo1.DB`, and answers `db.exec` messages by request id.

**Rationale:** The current app runtime is now main-thread Replicant/Nexus. Keeping SQLite in a Dedicated Worker preserves OPFS `SyncAccessHandle` support and keeps synchronous SQLite APIs off the UI thread. A plain JS worker avoids CLJS/WASM bundling friction.

### Main-thread proxy and active autocomplete API

`src/client/dbs.cljs` owns worker lifecycle and exposes a minimal `exec` proxy as `:dictionary/db`. `src/client/repo/dictionary.cljs` runs the autocomplete SQL and returns completion maps `{:lemma :translations :exact?}`. Home effects only call the SQLite path when `dictionary/ready?` is true; before readiness, no completions are shown.

PouchDB remains required for user progress (`user-db` / `device-db`). Legacy dictionary lookup may remain isolated in `src/client/repo/dictionary_legacy.cljs` and `legacy-dictionary-db`, but active home autocomplete no longer calls it.

### Versioning: content hash in filename

Filename format is `dict.{hash12}.sqlite`, derived from the SHA-256 recorded in `manifest.edn`. The server returns that filename through `/dictionary/manifest`.

**Rationale:** Same content yields the same URL, immutable caching is safe, and stale worker pools can be cleaned by comparing filenames.

### OPFS pool cleanup

The worker keeps only the current `dict.{hash12}.sqlite` in the `opfs-sahpool` pool, unlinks old dictionary files, and reduces pool capacity to one.

**Rationale:** This preserves atomic old/new behavior during import while preventing unbounded OPFS growth after successful upgrades.

### Transport: HTTP GET + immutable cache headers

The backend serves the content-addressed SQLite file with `ETag`, `Content-Hash`, and `Cache-Control: public, max-age=31536000, immutable`. Compression is delegated to the reverse proxy. WASM files are served as `application/wasm` with immutable cache headers.

## Risks / Trade-offs

- **Worker not ready on first keystrokes** -> Home autocomplete silently returns no completions until `dictionary-ready?` is true.
- **First import cost (~36 MB current SQLite file)** -> Happens in the worker and does not block initial Replicant render.
- **OPFS quota/browser bugs** -> Worker init failure is logged; user data sync remains independent.
- **PouchDB still present** -> intentional: user progress currently depends on PouchDB user/device databases. Do not remove it in this change.
- **Telemetry missing** -> implement worker lifecycle/download/OPFS telemetry before closeout.
- **Tests broken** -> restore `:node-test` before closeout.

## Migration Plan

1. Build pipeline emits `dictionary.sqlite` directly and records SHA-256 in manifest.
2. Server serves `/dictionary/manifest`, `/dictionary/dict.{hash12}.sqlite`, and SQLite WASM assets.
3. Browser boots main-thread app, then starts the SQLite worker via `dbs/init!`.
4. Worker imports/opens current SQLite in OPFS and posts `ready`.
5. Home autocomplete calls `repo.dictionary/completions` through the worker exec proxy.
6. Keep PouchDB user/device storage in place; only ensure active dictionary lookup uses SQLite.
7. Add telemetry for worker lifecycle, download, and OPFS success/failure.
8. Fix the broken test build before closeout.

## Open Questions

- Do we want a visible UI state for dictionary-worker readiness/failure, or is silent no-completions acceptable?
- What telemetry sink/shape should be used for dictionary worker lifecycle events?
