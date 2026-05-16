# 0001. Dictionary storage: SQLite/OPFS replaces PouchDB

- Status: accepted
- Date: 2026-05-08

## Context

The offline dictionary (154k entry docs, 695k surface-form docs) was replicated into PouchDB via CouchDB. PouchDB imposes per-document overhead on a dataset that never changes at the individual-document level — the whole dictionary is versioned and replaced as a unit. Measured IndexedDB cost was 445 MB for data that compresses to ~6 MB gzipped. Additionally, incremental CouchDB sync writes partial state to IndexedDB, creating a brick risk where an interrupted sync leaves the dictionary in an unusable state.

The dictionary is fundamentally different from user data: it is static, monolithic, and version-replaced rather than incrementally mutated. PouchDB/CouchDB is the wrong tool for this access pattern.

## Decision

The dictionary is stored as a single versioned SQLite file (`dict.{hash12}.sqlite`) delivered over HTTP and persisted in OPFS. PouchDB is not used for dictionary data. The existing `attach-translations` / `suggest` API in `dictionary.cljs` is preserved; callers are unchanged.

## Consequences

- IndexedDB dictionary footprint reduced from ~445 MB to ~60 MB.
- Brick risk eliminated: atomic version swap (write new file → update pointer → delete old) means a partial download never corrupts the running dictionary.
- CouchDB replication overhead eliminated for dictionary data.
- `dictionary-sync.cljs` replication of dictionary documents removed.
- PouchDB remains in use for user data (`user-db`, `device-db`) — this decision applies only to the dictionary.
- Future dictionary schema changes require a full file rebuild and re-download, not incremental document updates.
