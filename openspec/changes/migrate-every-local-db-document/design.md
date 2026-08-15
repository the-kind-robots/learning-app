## Context

`run-local-db-split!` copies documents out of the old `local-db` into `user-db` and
`device-db`, one `find` per document type, then writes the marker
`migration:local-db-split` into `device-db`. The marker is the only guard: on the next
boot the migration sees it and returns `:already-complete`.

`copy-type!` passes no `:limit`, and pouchdb-find defaults to 25
(`node_modules/pouchdb-find/lib/index-browser.js:1348`). So the copy stops at 25
documents per type and the marker still lands. `run-task-data-payload!` reads with the
same unbounded `find` and ends the same way.

Two consequences, and they need different answers:

- New browsers just need the read fixed.
- Browsers that already ran the truncated migration will never run it again. Their data is
  not lost — nothing destroys `local-db` (`db/destroy` is called only for `user-db`, in
  `sync.cljs`) — but it is unreachable until something re-reads it.

In-force ADRs: 0001-0011, none superseded. 0008 (content-addressed vocab ids) matters
here: a vocab document's `_id` is derived from its value, so re-copying the same word
targets the same `_id` rather than creating a duplicate.

## Goals / Non-Goals

**Goals:**

- Every document of a migrated type reaches its destination, whatever the count.
- A browser that recorded a truncated run gets the remainder without losing or reverting
  anything it has done since.
- The page boundary is covered by a test rather than assumed.

**Non-Goals:**

- Sweeping unbounded `find` calls outside the migration path — that is #308.
- Changing `db/find` or its default, or adding pagination to `lib/db`.
- Restructuring the migration list into a generic versioned framework.

## Decisions

### Read with `find-all`, not a bigger `:limit`

`db/find-all` (`lib/db/src/db.cljc:446`) asks `info` for `doc-count` and uses it as the
limit, so one call returns everything. Used in `copy-type!` and in the task-data rewrite.

Alternatives: a hardcoded `:limit 10000` is the same defect with a larger number and no
error when it is crossed; bookmark paging is real work for a query that runs once at boot
against a local database. `find-all` already exists and already has this exact job.

`doc-count` counts every document in the source, so as a limit for a per-type selector it
is an upper bound — never too small, which is the property that matters.

### Repair as its own migration, not a bumped marker id

A new entry `migration:local-db-split-repair` runs the copy passes again under its own
marker. Browsers that migrated cleanly pay one no-op scan; browsers that truncated get
their remainder.

Alternative — renaming the existing marker to `...-v2` — rejected: the old marker stays
in `device-db` forever with nothing recording why it was abandoned, and the boolean "did
the split happen" loses its meaning. A separate marker keeps both facts: the split ran,
and the truncation was repaired.

Alternative — comparing source and destination counts to detect truncation — rejected:
the comparison cannot distinguish a truncated copy from documents the user deleted since,
so it would need the same skip rule as below and buys nothing for the extra code.

### Copying skips ids the destination already knows, tombstones included

This is the part a repair pass cannot do naively. `delete-word!`
(`src/client/adapters/progress_store.cljs:144`) tombstones the vocab document and its
reviews in `user-db`; `examples.cljs` tombstones examples in `device-db`. A copy that
inserts a stripped document under an id whose only trace is a tombstone recreates it. So
a user who deleted a word after the truncated run would watch it come back.

The 409 handling in `copy-doc!` does not cover this: a deleted document is not a
conflict, it is an absent one.

Before copying a type, the pass asks the destination for the ids it already holds —
`all-docs` with an explicit `keys` list, which returns a row for a deleted document too
(carrying `deleted: true`) and an error row only for an id that was never there. Ids with
a row are skipped. On a first migration the destination is empty, nothing is known, and
everything is copied, so the same rule serves both passes.

`copy-doc!` keeps its 409 skip as a backstop for concurrent writes.

## Risks / Trade-offs

- **The repair scans `local-db` on every browser, including ones that never needed it** →
  It is one `info` plus one `find` per type against a local database, once, behind a
  marker. Measured against a stranded vocabulary, that is a good trade.
- **`all-docs` with `keys` is a wider read than the old per-doc conflict dance** → It is
  one call per type returning ids only (no `include-docs`), against the destination that
  the migration is about to write to anyway.
- **A browser that deleted its `local-db` by hand gets an empty repair** → `find-all`
  returns `{:docs []}` for a zero-count database and the pass writes its marker; nothing
  breaks, there is simply nothing to recover.
- **`find-all` holds every matching document in memory at once** → Same shape as the copy
  that follows it, which already builds one promise per document. A local vocabulary is
  thousands of small documents, not millions.

## Migration Plan

The change ships as client code; the repair runs on the next boot after the browser picks
up the new bundle, before storage-backed ports are exposed
(`specs/main-thread-runtime/spec.md`). Rollback is reverting the release: the repair
marker left behind is inert, and a re-released repair would re-run only where the marker
is missing.

## Open Questions

None. No in-force ADR needs revisiting for this change.
