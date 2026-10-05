# 0019. A write reaches memory through the change log

- Status: accepted, supersedes ADR-0017
- Date: 2026-10-04
- Supersedes: ADR-0017 (only its decision on how a write reaches memory; its decision that memory follows the change feed stands)

## Context

ADR-0017 had a write read the documents it wrote back from PouchDB by id and apply them to memory, beside the change feed. Two paths could then reach memory out of order: a feed batch read before the write could be applied after the write's read-back, and memory held the older revision until the feed brought the write's own change (#508; `a-write-is-made-to-the-stored-version` failed about one run in eight). Ignoring an older generation does not fix it, because a conflict can resolve to a branch of lower generation. Memory now keeps the feed position it has taken each database up to (ADR-0018), and the feed drops a change at or below it. What a write must guarantee is stated in `openspec/specs/learner-data-memory/spec.md`, requirement "Memory takes a write only after PouchDB accepted it".

## Decision

A write applies nothing to memory itself. It catches memory up from the change log of the database it wrote (`loader/catch-up!`), so that its documents reach memory through the same path and filter as every other change. Only that database is read, so that a failing or busy other database does not fail or slow the learner's writes.

Rejected: a seq on every entry of memory, so that a read-back by id could be ordered against the feed. It adds a second ordering rule beside the positions, grows the snapshot, and needs a read that returns sequences by id. Rejected: flushing the pending feed batch before a read-back. A feed that lags still delivers an older change afterwards.

## Consequences

- Memory equals each database at memory's position for that database, whoever wrote the documents.
- A write costs one read of its database's change log since memory's position instead of a read by id. When a pull is still being delivered, the write's catch-up also takes the pull's changes.
- The rest of ADR-0017 is unchanged: one load behind the splash, the change feeds followed from there, a catch-up when the page is shown again and before a decision that needs every collection, and writes made over the winning revision.
