# user-db secondary indexes

## Why

user-db has no secondary index: `getIndexes()` returns only `_all_docs`, so every `db/find`
scans the whole database and filters in JS on the main thread. Opening the themes screen runs
three such scans (collections, vocab, reviews with a `$in` over every vocab id). On a local
synthetic dataset of 10 501 docs (1509 vocab, 9000 review) the three queries took 2073, 1851
and 4669 ms and the screen first painted after 9 953 ms with one 3 519 ms long task (#404).

## What Changes

- user-db carries two secondary indexes, on `type` and on `type` + `word_id`, created at
  start-up so an existing installation gets them on its next start.
- The review lookup behind retention levels runs one indexed query over reviews and groups
  in memory instead of a `$in` over every vocab id.
- `list-words` filters by `word-ids` and `search` before computing retention, so excluded docs
  are never priced. The retention sort itself still needs retention for every candidate.
- `list-collections` reads all collections with `find-all` instead of `find`'s default page
  of 25 (#308).
- user-db carries two mapreduce views, `reviews-by-word` (`word_id` → `[created_at retained]`)
  and `vocab-preview` (`_id` → `[kind value translation]`); retention levels and word lists
  are read from view rows, not from documents.
- Design documents do not replicate: a sync pass filters `_design/` ids in both directions.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `data-model`: user-db SHALL carry secondary indexes on `type` and on `type` + `word_id`,
  created at start-up; after start-up `getIndexes()` lists them.

## Impact

- Affected specs: `data-model`
- Affected code: `src/client/db/pouch.cljs` (index creation at `init!`),
  `src/client/adapters/progress_store.cljs`, `src/client/adapters/collections.cljs`
- Not affected: `lib/db` (`create-index` already exists), device-db, replication.
