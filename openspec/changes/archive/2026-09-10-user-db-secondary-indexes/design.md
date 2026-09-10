## Context

user-db (PouchDB, `db.pouch/init!`) has only `_all_docs`. `db/find` with a selector and no
usable index scans every document and filters in JS on the main thread (pouchdb-find's
`_all_docs` fallback). Retention levels for N words are fetched with `word-id {:$in ids}`,
which is a scan times a linear `$in` check per document.

## Goals / Non-Goals

**Goals:**
- Every `type`-selected query on user-db reads through an index.
- The themes screen and `/words` pay for the docs they show, not for every review times every word.

**Non-Goals:**
- device-db indexes (tasks has its own; examples are out of scope).
- Changing sort semantics: the retention sort still needs retention for every candidate.

## Decisions

- Indexes are created in `db.pouch/init!` right after `ensure-migrated!`, not as a migration:
  `createIndex` is idempotent, and creating on every start is what gives an existing
  installation the indexes on its next start. Failure is logged and swallowed, as in
  `tasks/ensure-task-index!`, so a broken index never blocks start-up.
- Two indexes, `[type]` and `[type, word_id]`, with design-document names `by-type` and
  `by-type-word-id`. pouchdb-find scores both equally for a `{type}` selector and takes the
  first in `getIndexes()` order, which is design-document id order, so the single-field index
  wins for `{type}` and the two-field one for `{type, word-id}`.
- `word-retention-levels` fetches all reviews with `{type "review"}` and groups by `word-id`
  in memory. Reviews are the bulk of user-db, so one indexed read is the floor for a screen
  that needs retention for every word.
- `list-words` applies `word-ids` and `search` before retention. `total` keeps its meaning
  (count after `word-ids`, before `search`).

## Risks / Trade-offs

- [First query after the upgrade builds the index over every doc] → one-time cost on the
  start after the update, then incremental.
- [A collection of few words still reads every review] → accepted; per-word indexed queries
  would trade one read for N round trips, and no measured case needs it.
