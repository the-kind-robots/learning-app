## Context

`run-local-db-split!` copies documents out of the old `local-db` into `user-db` and
`device-db`, one `find` per document type, then writes the marker
`migration:local-db-split` into `device-db`. The marker is the only guard: on the next
boot the migration sees it and returns `:already-complete`.

`copy-type!` passes no `:limit`, and pouchdb-find defaults to 25
(`node_modules/pouchdb-find/lib/index-browser.js:1348`). So the copy stops at 25
documents per type and the marker still lands. `run-task-data-payload!` reads with the
same unbounded `find` and ends the same way.

In-force ADRs: 0001-0011, none superseded. None constrains this change.

## Goals / Non-Goals

**Goals:**

- Every document of a migrated type reaches its destination, whatever the count.
- The page boundary is covered by a test rather than assumed.

**Non-Goals:**

- Recovering browsers that already recorded a truncated run. The owner confirmed there is
  no vocabulary worth repairing, so the fix is forward-only and the code stays small.
- Sweeping unbounded `find` calls outside the migration path — that is #308.
- Changing `db/find` or its default, or adding pagination to `lib/db`.

## Decisions

### Read with `find-all`, not a bigger `:limit`

`db/find-all` (`lib/db/src/db.cljc:446`) asks `info` for `doc-count` and uses it as the
limit, so one call returns everything. Used in `copy-type!` and in the task-data rewrite.

Alternatives: a hardcoded `:limit 10000` is the same defect with a larger number and no
error when it is crossed; bookmark paging is real work for a query that runs once at boot
against a local database. `find-all` already exists and already has this exact job.

`doc-count` counts every document in the source, so as a limit for a per-type selector it
is an upper bound — never too small, which is the property that matters.

### No repair migration

A repair pass was written and then dropped on the owner's review: no existing browser
holds a vocabulary worth recovering. Recovering one would have needed more than a re-run —
copying would have to skip ids the destination already knows, tombstones included, or a
word deleted after the truncated run comes back. That machinery is not worth carrying for
data nobody wants, so the change stays at two call sites.

## Risks / Trade-offs

- **Browsers that already recorded a truncated run keep their stranded documents** →
  Accepted deliberately; see above. `local-db` is never destroyed, so the documents remain
  on disk if that decision is ever revisited.
- **`find-all` holds every matching document in memory at once** → Same shape as the copy
  that follows it, which already builds one promise per document. A local vocabulary is
  thousands of small documents, not millions.

## Migration Plan

The change ships as client code and takes effect on the next boot after the browser picks
up the new bundle, before storage-backed ports are exposed
(`specs/main-thread-runtime/spec.md`). Rollback is reverting the release.

## Open Questions

None. No in-force ADR needs revisiting for this change.
