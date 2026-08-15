## Why

The local-db split migration copies at most 25 documents per type. `copy-type!` calls
`db/find` with no `:limit`, and pouchdb-find defaults to `limit = 25`. The task-data
payload migration reads the same way. Both write their marker unconditionally, so any
document past the 25th of a type stays in `local-db` and the migration never runs again.

Nobody saw it because the test seeds two documents per type, well under the default.

## What Changes

- Migrations read every matching document instead of the first page: `db/find-all`
  replaces `db/find` in `copy-type!` and in the task-data payload rewrite.
- The migration test seeds more than one page of a type and asserts every document
  reaches the destination, so the page boundary is covered rather than assumed.

No repair pass for browsers that already recorded a truncated run: the owner confirmed no
such database is worth recovering, so the migration is fixed forward only.

No breaking changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `data-model`: state that a migration reads every matching document rather than the
  storage layer's default page.

## Impact

- `src/client/db_migrations.cljs` — the two migration reads.
- `test/client/db_migrations_test.cljs` — cases that cross the page boundary.
- `lib/db/src/db.cljc` — used only, not changed. `find-all` already exists there.
- Related: #308 tracks the same unbounded-`find` defect in the rest of the codebase. This
  change fixes only the migration path and leaves that sweep alone.
