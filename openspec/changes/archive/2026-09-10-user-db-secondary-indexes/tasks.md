## 1. Indexes

- [x] 1.1 Create the `type` and `type` + `word-id` indexes on user-db in `db.pouch/init!` after `ensure-migrated!`, logging and swallowing a creation failure

## 2. Queries

- [x] 2.1 Replace the `$in` review query in `word-retention-levels` with one query over reviews grouped by `word-id`; keep the return shape
- [x] 2.2 Apply `word-ids` and `search` filtering in `list-words` before computing retention
- [x] 2.3 Read all collections with `find-all` in `list-collections` (#308)

## 3. Views and replication

- [x] 3.1 `_design/reviews-by-word` and `_design/vocab-preview` created in `db.pouch/init!`, rewritten only when the map differs; `word-retention-levels` and `vocab-docs` read the views, not the documents
- [x] 3.2 `db/sync` passes a `filter`; `sync-once!` keeps `_design/` documents out of both directions

## 4. Verification

- [x] 3.1 Unit test: `word-retention-levels` over >25 reviews across several words matches the previous implementation's output
- [x] 3.2 Browser: `getIndexes()` after start-up lists both indexes; per-query ms and first paint at ~10 000 docs on a synthetic dataset
