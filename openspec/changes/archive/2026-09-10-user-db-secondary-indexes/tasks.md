## 1. Indexes

- [x] 1.1 Create the `type` and `type` + `word-id` indexes on user-db in `db.pouch/init!` after `ensure-migrated!`, logging and swallowing a creation failure

## 2. Queries

- [x] 2.1 Replace the `$in` review query in `word-retention-levels` with one query over reviews grouped by `word-id`; keep the return shape
- [x] 2.2 Apply `word-ids` and `search` filtering in `list-words` before computing retention
- [x] 2.3 Read all collections with `find-all` in `list-collections` (#308)

## 3. Verification

- [x] 3.1 Unit test: `word-retention-levels` over >25 reviews across several words matches the previous implementation's output
- [x] 3.2 Browser: `getIndexes()` after start-up lists both indexes; per-query ms and first paint at ~10 000 docs on a synthetic dataset
