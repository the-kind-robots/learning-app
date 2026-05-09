## Context

The offline dictionary comprises 154k entry docs and 695k surface-form docs, currently replicated into PouchDB. PouchDB imposes 3–4× per-document overhead on a dataset that never changes at the individual-document level — the whole dictionary is versioned and replaced as a unit. Raw JSONL is ~215 MB (99 MB entries + 116 MB surface-forms); measured IndexedDB cost is **445 MB** (2.1× overhead, not the 3–4× originally estimated). Data itself compresses to ~6.3 MB gzipped. Phase 1 strips unused fields at build time for immediate relief. Phase 2 retires the PouchDB path entirely in favour of a single versioned SQLite file delivered over HTTP and stored in OPFS.

**Build pipeline shape (Phase 2):**
```
kaikki dump → dictionary.sqlite   (primary artifact, nullable translations, deployable as-is)
                    ↓  [optional, async, week-long on separate machine]
              enrichment-output.jsonl   (durable translation record)
                    ↓
              apply-enrichment!   (gap-fill UPDATE in single transaction)
                    ↓
              dictionary.sqlite   (with translations filled in)
```

There is no intermediate "raw" artifact. `dictionary.sqlite` is the single mutable build output. `enrichment-output.jsonl` is independently durable: if `dictionary.sqlite` is rebuilt from a new kaikki dump, the patch is reapplied without re-running the translation API.

## Goals / Non-Goals

**Goals:**
- Reduce IndexedDB dictionary footprint from 445 MB to ~60 MB (SQLite/OPFS)
- Eliminate brick risk for the dictionary via atomic version swap
- Preserve the external `attach-translations` / `suggest` API in `dictionary.cljs` — callers are unchanged

**Non-Goals:**
- Changing `user-db` or `device-db` — PouchDB/CouchDB sync stays for user data
- Field stripping at build time — skipped; Phase 2 replaces the storage model entirely so intermediate savings are not worth the extra step
- Detail view (senses, full forms, glosses) — not on near-term roadmap
- Full-text search or fuzzy matching beyond the existing surface-form index

## Decisions

### Enrichment pipeline: side-file authoritative, apply in-place with gap-fill

`dictionary.sqlite` is built from kaikki and is usable without enrichment. The translation enrichment tool queries `dictionary.sqlite` directly (`SELECT … WHERE ru_translation IS NULL`) — no separately emitted input file. It writes results to `enrichment-output.jsonl`, which is the authoritative translation record and survives kaikki rebuilds.

`apply-enrichment!` applies the patch in a single `BEGIN … COMMIT` with `UPDATE … WHERE ru_translation IS NULL` — gap-fill semantics, never overwriting kaikki-sourced translations. Unmatched patch rows (lemma absent from current build) are silently skipped and logged.

**Rationale:** `enrichment-output.jsonl` represents expensive API calls (week-long run). Keeping it as the durable artifact decouples translation work from the kaikki parse cycle. If kaikki one day ships complete Russian translations, the enrichment step is simply not run — no pipeline changes required.

### SQLite schema

```sql
CREATE TABLE lemmas (
  id    INTEGER PRIMARY KEY,
  value TEXT    NOT NULL,
  pos   TEXT,
  rank  INTEGER NOT NULL
);

-- WITHOUT ROWID: hot path is WHERE lemma_id = ? ORDER BY rank —
-- a free clustered B-tree scan on the composite PK.
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

-- (lemma_id, rank) mirrors the translations PK; prefix lookup is free.
CREATE TABLE translation_labels (
  lemma_id INTEGER NOT NULL,
  rank     INTEGER NOT NULL,
  label_id INTEGER NOT NULL REFERENCES labels(id),
  FOREIGN KEY (lemma_id, rank) REFERENCES translations(lemma_id, rank),
  PRIMARY KEY (lemma_id, rank, label_id)
) WITHOUT ROWID;

-- WITHOUT ROWID: normalized_form is the leading PK column — lookups are clustered.
CREATE TABLE surface_forms (
  normalized_form TEXT    NOT NULL,
  lemma_id        INTEGER NOT NULL REFERENCES lemmas(id),
  PRIMARY KEY (normalized_form, lemma_id)
) WITHOUT ROWID;
```

`lemmas.value` stores the lemma including article for nouns ("der Hund"). `rank` encodes word importance: lower is more common (frequency-derived) or pedagogically prioritised (CEFR-derived). `text_id` (a build-pipeline join key) is intentionally absent from the shipped artifact — no client query references it.

**Rationale for `WITHOUT ROWID`:** `translations`, `translation_labels`, and `surface_forms` are append-only reference tables. Every client query hits them with a leading-column prefix (`WHERE lemma_id = ?` or `WHERE normalized_form = ?`). `WITHOUT ROWID` stores rows in a clustered B-tree on the composite PK, making these lookups range scans with no secondary index needed. `translations` in particular benefits most: the previous synthetic `id` column would have required a separate index on `(lemma_id)` to avoid a full-table scan.

### Official SQLite WASM (`@sqlite.org/sqlite-wasm`)
Use the official SQLite WASM build with the `opfs-sahpool` VFS. This is Worker-only and stores the database directly in OPFS via `SyncAccessHandle`, eliminating the need for a separate download-then-write step.

**Rationale:** The official build is maintained by the SQLite team, has native OPFS support, and the `opfs-sahpool` VFS handles the `SyncAccessHandle` lifecycle. No third-party wrapper needed. `sql.js` loads the full db into memory and requires a manual OPFS write step; the official WASM avoids both.

### OPFS Worker-first
The SQLite file is opened exclusively inside a Worker using `SyncAccessHandle`. The main thread communicates via `postMessage`.

**Rationale:** `SyncAccessHandle` is Worker-only in Safari and Firefox. Designing Worker-first from the start avoids a re-architecture later. The existing `attach-translations` / `suggest` calls become async `postMessage` shims — no semantic change from the caller's perspective.

### Versioning: content hash in filename
Filename format: `dict.{hash12}.sqlite` where `hash12` is the first 12 hex chars of the SHA-256 of the file. Written to a temp file, hashed, then renamed — consistent with how `emit/sha256` already fingerprints the JSONL files. Hash is recorded in the manifest so the server and Worker agree on the current file without a separate version-pointer document.

**Rationale:** Content-addressed — same content always yields the same URL. Deterministic builds don't generate new files. Cache-busting is free.

### Atomic version swap
Versioning protocol: write `dict.{new-hash12}.sqlite` → update manifest pointer → delete `dict.{old-hash12}.sqlite`.

**Rationale:** Eliminates the partial-write failure mode that causes brick risk in the current PouchDB sync path. If the download is interrupted, the old version remains valid and the app continues to function.

### Transport: HTTP GET + Service Worker cache
Serve `dict.v{epoch}.sqlite` from the backend with `Content-Encoding: gzip` and `ETag` / `Content-Hash` headers. The Service Worker intercepts the request and caches the file; subsequent boots skip the download entirely.

**Rationale:** Standard HTTP caching is simpler than CouchDB attachment replication for a monolithic binary blob. No CouchDB overhead, no attachment base64 inflation.

## Risks / Trade-offs

- **sql.js memory footprint (~60 MB heap)** → Acceptable on modern devices; monitor via telemetry. Fallback: switch to wa-sqlite streaming reads.
- **WASM cold-start latency** → Worker initialises in background on app boot; dictionary is not on the critical path for first render.
- **Safari OPFS quota** → Same quota constraints as today; the total bytes written are smaller. Check existing `dictionary-sync` quota failure telemetry before Phase 2 rollout.
- **Phase 1 strips fields that may be needed later** → Acceptable: if a detail view is added, re-populate stripped fields in the build step. The issue's key discriminator question was answered: detail view is not on the near-term roadmap.
- **Phase 1 ships first; Phase 2 may be deferred** → Phase 1 stands alone as a valid optimisation. Phase 2 completes the job and fixes brick risk.

## Migration Plan

1. Build pipeline emits `dictionary.sqlite` directly (no intermediate "raw" file); server route added.
2. Ship behind a rollout flag (`dictionary-sqlite-enabled?`).
3. On first flag-enabled boot: Worker downloads file → writes to OPFS → opens db → signals ready.
4. `dictionary.cljs` routes through Worker shim when flag is on; falls back to PouchDB path when off.
5. Once stable, remove flag and tear down PouchDB dictionary path.
6. Run enrichment tool against deployed `dictionary.sqlite`; apply patch via `apply-enrichment!`.
7. Rollback: flip flag off — PouchDB path resumes. OPFS file left in place (harmless until next flag-on init).

## Open Questions

- Does telemetry show active `dictionary-sync` quota failures today? (Informs urgency of Phase 2.)
- Confirm no consumer of `meta.cefr_level` or `meta.senses` in any current client path before Phase 1 ships.
