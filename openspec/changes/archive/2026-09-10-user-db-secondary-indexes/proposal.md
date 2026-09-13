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
- The engine takes its document types from schema values the adapters declare; the
  adapters are one per aggregate (words, reviews, lessons, collections, examples) and
  speak `:id` outward, with the words-with-retention join in the use-case.
- The themes screen switches at once and shows a loading state until its cards arrive.
- Collection cards own their touch gestures, and a tap the browser cancels without
  movement still opens the collection.
- A pull that writes no document leaves the current screen untouched, and route entry
  does not pull again within 30 s of the last completed pass.
- A development build keeps a trace of the last 500 events and exports it from the red D.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `data-model`: user-db SHALL carry secondary indexes on `type` and on `type` + `word_id`,
  created at start-up; after start-up `getIndexes()` lists them. A reviews view; design
  documents not replicated.
- `push-sync`: a pull that writes nothing changes nothing on screen; no pass within 30 s of
  the last one on route entry.
- `collections-navigation`: the screen switches at once with a loading state; cards own
  their touch gestures; a cancelled tap without movement still opens the collection.

## Impact

- Affected specs: `data-model`, `push-sync`, `collections-navigation`
- Affected code: `src/client/db/pouch.cljs` (schemas, indexes and views at `init!`, the
  replication filter), `lib/db` (`query`, the sync `filter`), the adapters per aggregate
  (`words`, `reviews`, `lessons`, `collections`, `examples`) and their ports,
  `use-cases.vocabulary`, `sync`, the themes screen (`pages.collections`),
  `instrumentation`.
- device-db gains the example indexes; replication now filters `_design/` documents.
