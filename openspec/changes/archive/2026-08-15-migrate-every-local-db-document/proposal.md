## Why

The local-db split migration copies at most 25 documents per type. `copy-type!` calls
`db/find` with no `:limit`, and pouchdb-find defaults to `limit = 25`. The migration then
writes its marker unconditionally, so it never runs again: every word, review, example,
lesson and task past the 25th is stranded in `local-db` and invisible to the app. The
task-data payload migration has the same unbounded `db/find` and the same
write-the-marker-anyway ending.

Nobody saw it because the test seeds two documents per type, well under the default.

## What Changes

- Migrations read every matching document instead of the first page: `db/find-all`
  replaces `db/find` in `copy-type!` and in the task-data payload rewrite.
- A repair migration heals browsers that already recorded a truncated run. Those browsers
  have the marker but not the data, so the existing marker check would skip them forever.
  A new marker re-runs both copy passes once; the passes are idempotent, so browsers that
  migrated completely pay one no-op scan and lose nothing.
- The migration test seeds more than one page of a type and asserts every document lands
  in the destination, so the page boundary is covered rather than assumed.

No breaking changes: documents already copied are left as they are.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `data-model`: state that a migration reads every matching document, and that a
  migration whose recorded run was incomplete is repaired rather than skipped.

## Impact

- `src/client/db_migrations.cljs` — `copy-type!`, `run-task-data-payload!`, the migration
  list, plus the repair entry.
- `test/client/db_migrations_test.cljs` — a case that crosses the page boundary.
- `lib/db/src/db.cljc` — used only, not changed. `find-all` already exists there.
- Related: #308 tracks the same unbounded-`find` defect in the rest of the codebase. This
  change fixes only the migration path and leaves that sweep alone.
