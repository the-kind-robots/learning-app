# 0017. Memory follows the change feed; writes go to PouchDB

- Status: accepted
- Date: 2026-10-03

## Context

Memory (ADR-0016) holds what the screens show of the learner's data. At
20 000 words the writes still read PouchDB through indexes and views, and
the first such query after a bulk import built its index over every
document (#508). What memory guarantees is stated in
`openspec/specs/learner-data-memory/spec.md`; this records how it is kept
and why.

Three facts shaped it. PouchDB, not memory, decides which revision of a
document wins, and it accepts a put on a losing branch without a conflict.
Everything that changes the learner's data — this tab, another tab, a
replication pass — ends up in the database's change log, in order. And a
started live feed in PouchDB 9 can drop a change and report no failure.

## Decision

Memory is a view the database keeps through its change log, built by
`adapters.learner.loader`:

- one load behind the splash: note the feed positions, read the kept
  document types of both databases, read what was stored meanwhile, hand
  memory over;
- from there, follow each change feed (`db.pouch/follow-changes`), and
  catch up by one read of the changes after the last one taken
  (`loader/catch-up!`) when the page becomes visible again and before a
  decision that needs every collection.

A write (`adapters.learner`) goes to PouchDB: it reads its document by id,
puts its change over the winning revision, once more on a conflict
(`db.pouch/write-latest!`), and reads what it wrote back into memory with
the same pure functions the feed uses. It never waits for the feed.

Rejected: a write that waits until the feed brings its change back. It
hangs when the feed drops a change, and it can resolve on an intermediate
revision. Rejected: applying the write's own documents to memory without
reading them back. Those are the revisions the write made, not the winners
PouchDB holds. Rejected: deciding a name or a word's collections by the
`type` index. After a large pull the index has every new document to take
in before it answers.

## Consequences

- The splash stays until everything is read; at 20 000 words that read is
  most of the start-up cost (#508).
- A change from elsewhere that the feed drops shows once the page is shown
  again, or once a decision catches up.
- Losing memory costs a reload, which rebuilds it from the databases.
